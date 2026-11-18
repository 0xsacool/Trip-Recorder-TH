package org.triprecorderng;

import android.content.Context;
import android.os.SystemClock;

public final class ChargeRecorderTest {
    private static final double N = Double.NaN;
    private static final long START = 1_800_000_000_000L;
    private static void check(boolean ok,String label) { if(!ok) throw new AssertionError(label); }
    private static final class Car implements Vehicle {
        double soc=49, speed=0, odo=1000, signed=N; double[] charging;
        public String name(){return "test";}
        public double speedKmh(){return speed;}
        public double socPercent(){return soc;}
        public double odometerKm(){return odo;}
        public double powerLevel(){return 3;}
        public double totalElecConsumption(){return N;}
        public double totalDrivingTime(){return N;}
        public String lastError(){return "";}
        public double[] charging(){return charging;}
        public double motorPowerKw(){return signed;}
    }
    private static final class Fixture {
        final Car car=new Car(); final TripDb db=new TripDb(); final ChargeRecorder r;
        long elapsed=5000;
        Fixture(){Prefs.last=null; ChargeRecorder.current=null; ChargeRecorder.status="";r=new ChargeRecorder(new Context(),db,null);}
        void tick(){SystemClock.now=elapsed;r.update(car,START+elapsed,elapsed);elapsed+=5000;}
        void seconds(int s){ for(int i=0;i<s;i+=5)tick(); }
        Charge one(){check(db.rows.size()==1,"one stored session, got "+db.rows.size());return db.charges().get(0);}
    }
    private static void awakeDcNoBms() {
        Fixture f=new Fixture(); f.seconds(90);
        for(int s=50;s<=64;s++){ f.car.soc=s;f.seconds(30); }
        Charge c=f.one();check(c.socStart==49 && c.socEnd==64,"DC 49->64 persisted, including original baseline");
        check(c.partial && c.windowOnly && c.energyIsEstimate(),"inferred data labeled partial");
        check(Math.abs(c.energyKwh(.6)-9)<.001,"estimated 9 kWh");
        check(!c.ended && !ChargeRecorder.status.isEmpty(),"live recording status");
        f.car.speed=30;f.car.soc=66;f.tick();c=f.one();
        check(c.ended && c.socEnd==64,"moving/regen cannot inflate end SOC");
        check(f.db.points.get(c.id).get(0)[1]==49,"original SOC included in graph");
        System.out.println("PASS: awake DC 49% -> 64%, missing charging signals");
    }
    private static void plugAndPower() {
        Fixture f=new Fixture(); f.tick(); f.car.charging=new double[]{N,3,N,40,N};f.seconds(10);
        Charge c=f.one();check(c.gun==3 && c.maxPowerKw==40,"DC cable and charger power despite missing BMS");
        f.car.soc=53;f.seconds(35);f.car.charging=new double[]{0,0,0,0,0};f.seconds(70);
        check(f.one().ended && f.one().socEnd==53,"normal completion after disconnect");
        System.out.println("PASS: DC plug+power and disconnect");
    }
    private static void normalAc() {
        Fixture f=new Fixture(); f.car.charging=new double[]{0,0,0,0,0};f.tick();
        f.car.charging=new double[]{1,2,1,7,N};f.seconds(130);f.car.soc=50;f.seconds(35);
        f.car.charging=new double[]{13,2,1,0,N};f.seconds(65);check(!f.one().ended,"paused AC retained");
        f.car.charging=new double[]{0,0,0,0,0};f.seconds(70);Charge c=f.one();
        check(c.ended && c.gun==2 && c.socStart==49 && c.socEnd==50 && !c.partial,"AC unchanged");
        System.out.println("PASS: AC BMS, pause, end");
    }
    private static void noFalseCharge() {
        Fixture f=new Fixture(); f.car.speed=60;f.car.charging=new double[]{1,3,1,40,N};f.seconds(90);
        f.car.soc=60;f.seconds(90);check(f.db.rows.isEmpty(),"driving with stale charge signal/regen");
        f.car.speed=0;f.car.charging=null;f.seconds(90);f.car.soc=61;f.seconds(90);
        check(f.db.rows.isEmpty(),"minor parked SOC recalibration");
        f.car.odo+=1;f.car.soc=70;f.tick();check(f.db.rows.isEmpty(),"odometer movement resets candidate");
        f.car.speed=N;f.car.soc=80;f.seconds(120);check(f.db.rows.isEmpty(),"missing speed cannot infer parked charge");
        check(ChargeDetection.signal(new double[]{N},0)==ChargeDetection.NONE,"short snapshot safe");
        System.out.println("PASS: regen, recalibration, odometer movement, missing speed and short snapshots");
    }
    private static void sleepingCharge() {
        Fixture f=new Fixture(); f.tick();f.elapsed+=600_000;f.car.soc=64;f.tick();Charge c=f.one();
        check(c.ended && c.partial && c.socStart==49 && c.socEnd==64,"sleep recovery unchanged");
        System.out.println("PASS: sleep gap recovery");
    }
    private static void fallbackQuietEnd() {
        Fixture f=new Fixture();f.car.charging=new double[]{N,3,N,N,N};f.seconds(90);
        f.car.soc=51;f.seconds(35);check(!f.one().ended,"plug and two-point gain starts fallback");
        f.car.charging=new double[]{N,0,N,N,N};f.seconds(70);check(f.one().ended,"plug loss closes fallback");
        System.out.println("PASS: fallback with cable and unplug");
    }
    private static void briefReadFailureAndQuietEnd() {
        Fixture f=new Fixture(); f.seconds(90); f.car.soc=51;f.seconds(30);
        f.car.soc=N;f.seconds(10);f.car.soc=54;f.seconds(35);
        check(f.one().socStart==49,"brief SOC read failure does not erase baseline");
        f.car.soc=64;f.seconds(790);check(f.one().ended && f.one().socEnd==64,"quiet fallback closes");
        System.out.println("PASS: intermittent SOC and quiet completion");
    }
    private static void actualAttoAc() {
        Fixture f=new Fixture(); f.car.soc=39;f.car.odo=N;f.car.signed=-6;f.seconds(300);
        check(f.db.rows.isEmpty(),"signed power alone cannot create record");
        f.car.soc=40;f.seconds(35);Charge c=f.one();
        check(c.socStart==39 && c.socEnd==40 && c.maxPowerKw==6 && c.partial,"AC -6kW and 1% gain captured");
        f.car.signed=N;f.seconds(10); f.car.signed=-6;f.car.soc=41;f.seconds(35);
        check(f.one().socEnd==41 && !f.one().ended,"AC continues after brief missing power");
        f.car.signed=0;f.seconds(70);c=f.one();check(c.ended && c.socEnd==41,"AC stops when power stops");
        check(Math.abs(c.energyKwh(.6)-1.2)<.001,"energy estimated, not charger-measured");
        System.out.println("PASS: actual Atto AC 39->41, -6 kW, unknown charge API, unplug");
    }
    private static void actualAttoDcAndRegen() {
        Fixture f=new Fixture();f.car.soc=49;f.car.signed=-60;f.car.speed=22;f.seconds(60);
        f.car.soc=51;f.seconds(60);check(f.db.rows.isEmpty(),"moving negative power rejected");
        f.car.soc=49;f.car.speed=0;f.car.signed=-60;f.seconds(60);
        check(f.db.rows.isEmpty(),"stale negative power while stopped rejected");
        f.car.soc=50;f.seconds(35);check(f.one().socStart==49,"DC inferred after SOC increase");
        f.car.soc=64;f.seconds(35);f.car.speed=30;f.car.soc=66;f.tick();
        check(f.one().ended && f.one().socEnd==64,"regen after DC not counted");
        System.out.println("PASS: DC -60 kW 49->64 and regen/stale power guard");
    }
    public static void main(String[] args){awakeDcNoBms();plugAndPower();normalAc();noFalseCharge();sleepingCharge();fallbackQuietEnd();briefReadFailureAndQuietEnd();actualAttoAc();actualAttoDcAndRegen();}
}

package org.triprecorderng;

import java.util.ArrayList;
import java.util.List;

final class NismoHybridRendererTest {
    static void check(boolean condition,String name) {
        if (!condition) throw new AssertionError(name);
    }
    static final class Surface implements EvoHybridRenderer.Surface {
        final List<String> values=new ArrayList<>();
        int plates, gauges, graphs, red, cyan;
        final List<float[]> segments=new ArrayList<>();
        final List<float[]> hits=new ArrayList<>();
        public void plate(float x,float y,float w,float h){plates++;}
        public void rect(float x,float y,float w,float h,int color){}
        public void line(float x,float y,float x2,float y2,int color,float weight){}
        public void outline(float x,float y,float w,float h,float r,int color,float t){}
        public void circle(float x,float y,float r,int color){}
        public void slant(float x,float y,float w,float h,float shear,int color){
            check(w>0 && w<=27,"finite bank");
            segments.add(new float[]{x,y,w,shear,color>>>24,color==NismoHybridRenderer.TRACK ? 0 : 1});
            if((color & 0xffffff)==(NismoHybridRenderer.RED & 0xffffff))red++;
            if((color & 0xffffff)==(NismoHybridRenderer.CYAN & 0xffffff))cyan++;
        }
        public void text(String v,float x,float y,float size,int color,boolean heavy,boolean slant,int align,float maxWidth){values.add(v);}
        public void gaugeBounds(int slot,float x,float y,float w,float h){gauges++;hit(x,y,w,h);}
        public void historyBounds(float x,float y,float w,float h){graphs++;hit(x,y,w,h);}
        void hit(float x,float y,float w,float h){
            for(float[] r:hits)check(x+w<=r[0]||x>=r[2]||y+h<=r[1]||y>=r[3],"overlapping target");
            hits.add(new float[]{x,y,x+w,y+h});
        }
    }
    public static void main(String[] args) {
        SharedMotionController.Gauge gauge=new SharedMotionController.Gauge();
        for(SharedMotionController.Mode mode:SharedMotionController.Mode.values()){
            gauge.update(0,mode,Double.NaN);
            check(gauge.update(1,mode,63)==63,"acquisition");
            double first=gauge.update(30,mode,-50);
            check(first<=0 && first>=-50,"no stale positive indication at regen");
            double middle=gauge.update(90,mode,-50);
            check(middle<0 && middle>=-50,"bounded regen");
            check(gauge.update(280,mode,-50)==-50 && !gauge.active(),"settled");
            check(gauge.update(300,mode,0)==0 && !gauge.active(),"coast");
            check(Double.isNaN(gauge.update(320,mode,Double.NaN)) && !gauge.active(),"signal loss");
        }
        EvoHybridRenderer.Data d=new EvoHybridRenderer.Data();
        d.speed=86;d.power=-48;d.leftLevel=-.17;d.rightLevel=.64;d.soc=64;d.liveRpm=4280;
        Surface s=new Surface();
        NismoHybridRenderer.draw(s,d);
        check(s.plates==1 && s.gauges==4 && s.graphs==1,"material and picker registrations");
        check(s.values.contains("86") && s.values.contains("-48") && !s.values.contains("-17"),"direct readings");
        check(s.values.contains("REGEN") && s.red==0 && s.cyan>0,"signed bank");
        d.soc=d.rightLevel=d.speed=d.power=d.leftLevel=d.liveRpm=Double.NaN;
        s=new Surface();NismoHybridRenderer.draw(s,d);
        check(java.util.Collections.frequency(s.values,"--")>=5 && !s.values.contains("COAST") && s.red==0 && s.cyan==0,"missing data");
        d.results=true;d.historyMetric=EvoHybridRenderer.Metric.MOTOR_RPM;
        d.gauges[0]=EvoHybridRenderer.GaugeMetric.SPEED;
        s=new Surface();NismoHybridRenderer.draw(s,d);
        check(s.values.contains("TRIP RESULTS") && s.values.contains("LIVE"),"results and live RPM selection");
        for(double fraction:new double[]{0,.25,.5,.75,1}) {
            Surface left=new Surface(),right=new Surface();
            NismoHybridRenderer.bank(left,false,fraction,NismoHybridRenderer.RED);
            NismoHybridRenderer.bank(right,true,fraction,NismoHybridRenderer.RED);
            float lastWidth=0,lit=0;
            for(int i=0;i<left.segments.size();i++){
                float[] a=left.segments.get(i),b=right.segments.get(i);
                check(Math.abs(a[0]+b[0]+a[2]-1200)<.001 && a[3]==-b[3],"mirror geometry");
                if(a[5]==0){check(a[2]>lastWidth,"progressive widths");lastWidth=a[2];}
                else{check(a[2]==lastWidth,"whole segment");lit+=a[4]/255f;}
            }
            check(Math.abs(lit-11*fraction)<.003,"segment opacity");
        }
        d.power=100; d.speed=130; d.liveRpm=6000; d.soc=50; d.avgConsumption=15;
        d.batteryTemp=30;d.chargingVoltage=400;d.remainingRange=250;
        for(EvoHybridRenderer.GaugeMetric m:EvoHybridRenderer.GaugeMetric.values()){
            if(m!=EvoHybridRenderer.GaugeMetric.DEFAULT)check(NismoHybridRenderer.level(m,d)==.5,"range for "+m);
        }
        NismoHybridRenderer.BankMotion bank=new NismoHybridRenderer.BankMotion();
        bank.sample(0,SharedMotionController.Mode.FULL,EvoHybridRenderer.GaugeMetric.RPM,d);
        d.liveRpm=10000;
        bank.sample(20,SharedMotionController.Mode.FULL,EvoHybridRenderer.GaugeMetric.RPM,d);
        d.soc=25;
        check(bank.sample(50,SharedMotionController.Mode.FULL,EvoHybridRenderer.GaugeMetric.SOC,d)==.25,"metric swap");
        d.soc=Double.NaN;
        check(Double.isNaN(bank.sample(51,SharedMotionController.Mode.FULL,EvoHybridRenderer.GaugeMetric.SOC,d))&&!bank.active(),"stale metric");
        System.out.println("PASS: four targets, all eight metrics, five levels, mirrored geometry, opacity, loss, mode and metric transitions");
    }
}

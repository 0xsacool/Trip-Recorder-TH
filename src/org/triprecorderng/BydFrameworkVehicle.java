package org.triprecorderng;

import android.content.Context;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Reads vehicle data through BYD's own framework classes (android.hardware.bydauto.*), the same
 * API the stock My Car app uses. Everything is done by reflection so the app compiles against the
 * plain Android SDK and degrades cleanly on units that do not have these classes.
 */
final class BydFrameworkVehicle implements Vehicle {
    private static final String P = "android.hardware.bydauto.";

    private final Context ctx;
    private Object speed, statistic, bodywork, charging, instrument, engine, manager;
    private Method mEnginePower;
    private Integer frontRpmId;
    private boolean rpmListenerTried;
    private final MotorRpm.Confirmation rpmConfirmation=new MotorRpm.Confirmation();
    private Object rpmListener;
    private volatile MotorRpm.Sample rpmEvent;
    private MotorRpm.Sample rpmPoll;
    private long lastRpmPoll;
    private Integer tempMinId, tempMaxId, cellMinId, cellMaxId, chargeVoltId, powerId, speedId, socId;
    private Method[] mCharging;
    private Method mFuel, mEvKm, mHevKm, mFuelPct, mElecRange, mFuelRange, mJourney;
    private Method mSpeed, mTotalMileage, mTotalElec, mDriveTime, mSoc, mPower;
    private String error = "";

    BydFrameworkVehicle(Context ctx) {
        Context app = ctx.getApplicationContext();
        this.ctx = app == null ? ctx : app;
    }

    /** Resolves the device objects and methods. Returns null on success or an error text. */
    String bind() {
        try {
            HiddenApi.exempt();
            speed = optionalDevice(P + "speed.BYDAutoSpeedDevice");
            statistic = optionalDevice(P + "statistic.BYDAutoStatisticDevice");
            bodywork = optionalDevice(P + "bodywork.BYDAutoBodyworkDevice");
            engine = optionalDevice(P + "engine.BYDAutoEngineDevice");
            manager = optionalDevice(P + "BYDAutoDeviceManager");
            mEnginePower = optional(engine, "getEnginePower");
            tempMinId = feature("Statistic", "STATISTIC_LOWEST_BATTERY_TEMP");
            tempMaxId = feature("Statistic", "STATISTIC_HIGHEST_BATTERY_TEMP");
            cellMinId = feature("Statistic", "STATISTIC_LOWEST_BATTERY_VOLTAGE");
            cellMaxId = feature("Statistic", "STATISTIC_HIGHEST_BATTERY_VOLTAGE");
            chargeVoltId = feature("Charging", "CHARGING_CHARGE_BATTERY_VOLT");
            powerId = feature("Engine", "ENGINE_POWER");
            frontRpmId = feature("Engine", "ENGINE_FRONT_MOTOR_SPEED");
            speedId = feature("Speed", "SPEED_AUTO_SPEED");
            socId = feature("Statistic", "STATISTIC_ELEC_PERCENTAGE");
            mSpeed = optional(speed, "getCurrentSpeed");
            mTotalMileage = optional(statistic, "getTotalMileageValue");
            mTotalElec = optional(statistic, "getTotalElecConValue");
            mDriveTime = optional(statistic, "getDrivingTimeValue");
            mSoc = optional(statistic, "getElecPercentageValue");
            mPower = optional(bodywork, "getPowerLevel");
            mFuel = optional(statistic, "getTotalFuelConValue");
            mEvKm = optional(statistic, "getEVMileageValue");
            mHevKm = optional(statistic, "getHEVMileageValue");
            mFuelPct = optional(statistic, "getFuelPercentageValue");
            mElecRange = optional(statistic, "getElecDrivingRangeValue");
            mFuelRange = optional(statistic, "getFuelDrivingRangeValue");
            bindInstrument();
            bindCharging();
            if (speed == null && statistic == null && manager == null) {
                error = "BYD speed and statistic devices unavailable";
                return error;
            }
            return null;
        } catch (Throwable t) {
            error = describe(t);
            return error;
        }
    }

    /** A statistic getter that a unit may not have (a pure EV has no fuel, for example). */
    private static Method optional(Object target, String name) {
        if (target == null) return null;
        try {
            return target.getClass().getMethod(name);
        } catch (Throwable t) {
            return null;
        }
    }

    /** The instrument device gives the cluster's journey counter; optional like the charging device. */
    private void bindInstrument() {
        try {
            instrument = device(P + "instrument.BYDAutoInstrumentDevice");
            mJourney = optional(instrument, "getCurrentJourneyDriveMileage");
        } catch (Throwable t) {
            instrument = null;
            mJourney = null;
        }
    }

    /** The charging device is optional: a unit without it still records trips. */
    private void bindCharging() {
        try {
            charging = device(P + "charging.BYDAutoChargingDevice");
            String[] names = {"getBatteryManagementDeviceState", "getChargingGunState",
                    "getChargerWorkState", "getChargingPower", "getChargingCapacity"};
            mCharging = new Method[names.length];
            for (int i = 0; i < names.length; i++) mCharging[i] = optional(charging, names[i]);
        } catch (Throwable t) {
            charging = null;
            mCharging = null;
        }
    }

    private Object device(String cls) throws Exception {
        Class<?> c = Class.forName(cls);
        Object o;
        try {
            o = c.getMethod("getInstance", Context.class).invoke(null, ctx);
        } catch (NoSuchMethodException e) {
            o = c.getMethod("getInstance").invoke(null);
        }
        if (o == null) throw new IllegalStateException(cls + ".getInstance returned null");
        return o;
    }

    private Object optionalDevice(String cls) {
        try { return device(cls); }
        catch (Throwable t) { error = cls + ": " + describe(t); return null; }
    }

    // Resolve symbols on the actual head unit rather than guessing firmware-specific numeric IDs.
    static Integer feature(String group, String name) {
        String base = P + "BYDAutoFeatureIds";
        for (String cls : new String[]{base + "$" + group, base}) {
            try { return ((Number) Class.forName(cls).getField(name).get(null)).intValue(); }
            catch (Throwable ignored) { }
        }
        return null;
    }

    /** BYDAutoDevice.get is read-only. Feature encoding follows the actual FID type. */
    private double fid(Object target, int dev, Integer id, boolean floating) {
        if (id == null) return Double.NaN;
        if (manager != null) {
            try {
                Object raw = manager.getClass().getMethod(floating ? "getDouble" : "getInt",
                        Integer.TYPE, Integer.TYPE).invoke(manager, dev, id);
                if (raw instanceof Number) return ((Number) raw).doubleValue();
            } catch (Throwable ignored) { }
        }
        if (target == null) return Double.NaN;
        try {
            Object event = target.getClass().getMethod("get", int[].class, Class.class)
                    .invoke(target, new int[]{id}, floating ? Float.TYPE : Integer.TYPE);
            if (event == null) return Double.NaN;
            Object raw = event.getClass().getField(floating ? "floatValue" : "intValue").get(event);
            return raw instanceof Number ? ((Number) raw).doubleValue() : Double.NaN;
        } catch (Throwable t) {
            return Double.NaN;
        }
    }

    private static double between(double v, double min, double max) {
        return v >= min && v <= max ? v : Double.NaN;
    }

    private static double temperature(double raw) {
        // Statistic temperature is offset by 40 on the mapped FID; raw zero is treated
        // as not available rather than showing a misleading -40 C on unsupported units.
        return between(raw, 1, 140) - 40.0;
    }

    @Override public double[] telemetry() {
        double min = temperature(fid(statistic, 1014, tempMinId, false));
        double max = temperature(fid(statistic, 1014, tempMaxId, false));
        if (!Double.isNaN(min) && !Double.isNaN(max) && min > max) {
            min = max = Double.NaN;
        }
        double power = motorPowerKw();
        double voltage = between(fid(charging, 1009, chargeVoltId, false), 50, 1000);
        double cellMin = between(fid(statistic, 1014, cellMinId, false) / 1000, 1, 5);
        double cellMax = between(fid(statistic, 1014, cellMaxId, false) / 1000, 1, 5);
        if (!Double.isNaN(cellMin) && !Double.isNaN(cellMax) && cellMin > cellMax) {
            cellMin = cellMax = Double.NaN;
        }
        return new double[]{min, max, power, voltage, cellMin, cellMax};
    }

    private static String describe(Throwable t) {
        Throwable c = t;
        if (t instanceof InvocationTargetException && t.getCause() != null) c = t.getCause();
        return c.getClass().getSimpleName() + ": " + c.getMessage();
    }

    private double num(Object target, Method m) {
        if (target == null || m == null) return Double.NaN;
        try {
            Object r = m.invoke(target);
            if (r instanceof Number) return ((Number) r).doubleValue();
            return Double.NaN;
        } catch (Throwable t) {
            error = m.getName() + ": " + describe(t);
            return Double.NaN;
        }
    }

    @Override public String name() { return "BYD framework API"; }

    @Override public double speedKmh() { double v = between(num(speed, mSpeed), 0, 350); return Double.isNaN(v) ? between(fid(speed, 1013, speedId, true), 0, 350) : v; }

    @Override public double motorPowerKw() {
        double v = between(fid(engine, 1012, powerId, false), -500, 1000);
        return Double.isNaN(v) ? between(num(engine, mEnginePower), -500, 1000) : v;
    }

    /** Explicit read-event subscription; no set/control request. Only the long-lived shell helper
     * subscribes, so transient direct-provider probes cannot leak listeners. */
    private void bindRpmListener(){
        rpmListenerTried=true;
        if(engine==null||android.os.Process.myUid()!=2000)return;
        final int id=frontRpmId==null?MotorRpm.FRONT_FEATURE:frontRpmId;
        try {
            Class<?> iface=Class.forName(P+"IBYDAutoListener");
            Object listener=java.lang.reflect.Proxy.newProxyInstance(iface.getClassLoader(),new Class<?>[]{iface},(proxy,method,args)->{
                if("onDataEventChanged".equals(method.getName())&&args!=null&&args.length>=2&&args[0] instanceof Number&&((Number)args[0]).intValue()==id&&args[1]!=null){
                    Object ev=args[1];double raw=Double.NaN;
                    try{raw=((Number)ev.getClass().getField("intValue").get(ev)).doubleValue();}
                    catch(Throwable ignored){try{raw=((Number)ev.getClass().getField("doubleValue").get(ev)).doubleValue();}catch(Throwable ignoredToo){}}
                    rpmEvent=new MotorRpm.Sample(raw,android.os.SystemClock.elapsedRealtime());
                }
                if("hashCode".equals(method.getName()))return System.identityHashCode(proxy);
                if("equals".equals(method.getName()))return args!=null&&args.length==1&&proxy==args[0];
                if("toString".equals(method.getName()))return "FrontRpmListener";
                return null;
            });
            for(Method method:engine.getClass().getMethods()){
                Class<?>[] params=method.getParameterTypes();
                if(method.getName().equals("registerListener")&&params.length==2&&params[0].isAssignableFrom(listener.getClass())&&params[1]==int[].class){
                    method.invoke(engine,listener,new int[]{id});rpmListener=listener;break;
                }
            }
        }catch(Throwable ignored){}
    }

    @Override public synchronized double frontMotorRpm(){
        if(!rpmListenerTried)bindRpmListener();
        long now=android.os.SystemClock.elapsedRealtime();
        MotorRpm.Sample event=rpmEvent;
        double n=event==null?Double.NaN:event.fresh(now);
        if(!Double.isFinite(n)){
            // Only use a synchronous getter when the FID was resolved on this car.
            if(frontRpmId!=null&&(rpmPoll==null||now-lastRpmPoll>=1000)){
                lastRpmPoll=now;
                rpmPoll=new MotorRpm.Sample(fid(engine,1012,frontRpmId,false),now);
            }
            n=rpmPoll==null?Double.NaN:rpmPoll.fresh(now);
        }
        return rpmConfirmation.accept(n);
    }

    @Override public double powerLevel() { return clean(num(bodywork, mPower)); }

    @Override public double socPercent() { double v = between(num(statistic, mSoc), 0, 100); return Double.isNaN(v) ? between(fid(statistic, 1014, socId, true), 0, 100) : v; }

    @Override public double totalElecConsumption() { return clean(num(statistic, mTotalElec)); }

    @Override public double totalDrivingTime() { return clean(num(statistic, mDriveTime)); }

    /** BYD returns the odometer in 0.1 km units on CAN-FD cars, already scaled by the API. */
    @Override public double odometerKm() { return clean(num(statistic, mTotalMileage)); }

    @Override public double journeyKm() { return clean(num(instrument, mJourney)); }

    @Override public double totalFuelLitres() { return clean(num(statistic, mFuel)); }

    @Override public double evMileageKm() { return clean(num(statistic, mEvKm)); }

    @Override public double hevMileageKm() { return clean(num(statistic, mHevKm)); }

    @Override public double fuelPercent() { return clean(num(statistic, mFuelPct)); }

    @Override public double elecRangeKm() { return clean(num(statistic, mElecRange)); }

    @Override public double fuelRangeKm() { return clean(num(statistic, mFuelRange)); }

    @Override public double[] charging() {
        if (charging == null || mCharging == null) return null;
        double[] r = new double[mCharging.length];
        for (int i = 0; i < r.length; i++) {
            double v = num(charging, mCharging[i]);
            r[i] = Double.isNaN(v) || v >= 65535 || v <= -2e9 ? Double.NaN : v;
        }
        return r;
    }

    @Override public String lastError() { return error; }

    private static double clean(double v) {
        return Vehicle.valid(v) ? v : Double.NaN;
    }
}

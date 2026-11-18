package org.triprecorderng;

import android.content.Context;

abstract class TestVehicle implements Vehicle {
    abstract boolean usable();
    public double speedKmh(){return usable()?0:Double.NaN;}
    public double socPercent(){return usable()?39:Double.NaN;}
    public double powerLevel(){return Double.NaN;}
    public double odometerKm(){return Double.NaN;}
    public double totalElecConsumption(){return Double.NaN;}
    public double totalDrivingTime(){return Double.NaN;}
    public String lastError(){return "test";}
}
final class BydFrameworkVehicle extends TestVehicle {
    static boolean available, bindFails;
    BydFrameworkVehicle(Context c){}
    String bind(){return available&&!bindFails?null:"unavailable";}
    boolean usable(){return available;}
    public String name(){return "direct";}
}
final class BinderVehicle extends TestVehicle {
    String bind(){return "unavailable";}
    boolean usable(){return false;}
    public String name(){return "binder";}
}
final class HelperVehicle extends TestVehicle {
    static boolean available;
    boolean closed;
    HelperVehicle(String token){}
    String connect(){return available?null:"unavailable";}
    boolean connected(){return available&&!closed;}
    void close(){closed=true;}
    boolean usable(){return connected();}
    public String name(){return "helper";}
}
final class GpsVehicle extends TestVehicle {
    GpsVehicle(Gps g){}
    boolean usable(){return true;}
    public String name(){return "gps";}
}
final class Gps { boolean isStarted(){return true;} }
final class Prefs { static boolean linkEnabled(Context c){return true;} }
final class HelperLauncher {
    static String token(Context c){return "test";}
    static void ensureAsync(Context c,boolean force){}
}
final class Diag { static void log(String s){} }

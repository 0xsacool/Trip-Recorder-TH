package org.triprecorderng;

import android.content.Context;

/** JVM regression test for retaining a working direct/helper source during self-test/backoff. */
public final class VehicleProviderTest {
    private static void check(boolean b, String label) { if (!b) throw new AssertionError(label); }
    public static void main(String[] args) {
        Context c = new Context(); Gps gps = new Gps();
        BydFrameworkVehicle.available = true;
        VehicleProvider p = new VehicleProvider(c,gps);
        check(p.get() instanceof BydFrameworkVehicle,"direct selected");
        Vehicle direct = p.get();
        p.probe(); p.probe();
        check(p.get() == direct,"self-test preserves direct during backoff");
        BydFrameworkVehicle.bindFails = true;
        p.requestProbe(); p.probe();
        check(p.get() == direct,"failed direct rebind retains still-readable direct");
        BydFrameworkVehicle.available = false;
        HelperVehicle.available = true;
        p.requestProbe(); p.probe();
        check(p.get() instanceof HelperVehicle,"helper on direct failure");
        Vehicle helper=p.get(); p.probe();
        check(p.get()==helper,"working helper preserved during backoff");
        p.requestProbe();p.probe();
        check(p.get()==helper,"working helper preserved during failed rebind");
        BydFrameworkVehicle.bindFails=false;BydFrameworkVehicle.available=true;
        p.requestProbe();p.probe();
        check(p.get() instanceof BydFrameworkVehicle && ((HelperVehicle)helper).closed,"upgrade helper to direct");
        BydFrameworkVehicle.available=false;HelperVehicle.available=false;
        p.requestProbe();p.probe();check(p.get() instanceof GpsVehicle,"GPS only after both unavailable");
        System.out.println("PASS: direct/helper self-test retention, fallback and recovery");
    }
}

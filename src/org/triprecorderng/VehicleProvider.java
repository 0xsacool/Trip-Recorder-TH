package org.triprecorderng;

import android.content.Context;

/**
 * Chooses the best working source of vehicle data and keeps re-checking:
 *  1. BYD's framework API called directly (works only if the car grants this app access),
 *  2. the autoservice binder called directly (same restriction),
 *  3. the shell-user helper started through the car's loopback ADB (only if the user switched
 *     on "Vehicle data link" in Setting) - full BYD API: ignition, battery, energy, odometer,
 *  4. GPS only (needs nothing from the car; trips are detected by movement).
 * On the tested unit the car refuses 1 and 2 for a normal app.
 */
final class VehicleProvider {
    private final Context ctx;
    private final Gps gps;
    private Vehicle current;
    private long lastProbeMs;
    private String report = "not probed yet";
    private String lastLoggedReport = "";
    private long lastLogMs;
    /** Direct API can become ready after boot, so retry with a bounded backoff. */
    private long lastDirectProbeMs;

    VehicleProvider(Context ctx, Gps gps) {
        this.ctx = ctx.getApplicationContext();
        this.gps = gps;
    }

    /** The active source, or null if nothing works right now. */
    synchronized Vehicle get() {
        long now = System.currentTimeMillis();
        boolean linkWanted = Prefs.linkEnabled(ctx);
        if (current instanceof HelperVehicle) {
            HelperVehicle h = (HelperVehicle) current;
            if (!h.connected() || !linkWanted) {
                h.close();
                current = null;
                lastProbeMs = 0;
            }
        }
        long every;
        if (current == null) every = 3_000;
        else if (current instanceof GpsVehicle) every = linkWanted ? 3_000 : 60_000;
        else if (current instanceof HelperVehicle) every = 30_000;
        else every = 30_000;
        if (lastProbeMs == 0 || now - lastProbeMs >= every) probe();
        return current;
    }

    synchronized String report() {
        return report;
    }

    /** Make the next get() re-check the sources immediately. */
    synchronized void requestProbe() {
        lastProbeMs = 0;
        lastDirectProbeMs = 0;
    }

    /** Re-run the selection immediately (also used by the self-test button). */
    synchronized String probe() {
        long now = System.currentTimeMillis();
        boolean directDue = lastDirectProbeMs == 0 || now - lastDirectProbeMs >= 30_000;
        Vehicle previous = current;
        boolean keep = previous != null && !(previous instanceof GpsVehicle)
                && (!(previous instanceof HelperVehicle)
                    || (Prefs.linkEnabled(ctx) && ((HelperVehicle) previous).connected()))
                && usable(previous);
        // A self-test during backoff must never discard a working vehicle data source.
        if (!directDue && keep) return report;
        lastProbeMs = now;
        StringBuilder sb = new StringBuilder();
        current = null;

        if (directDue) {
            lastDirectProbeMs = lastProbeMs;
            BydFrameworkVehicle fw = new BydFrameworkVehicle(ctx);
            String err = fw.bind();
            if (err == null && usable(fw)) {
                current = fw;
                sb.append("direct BYD API: works\n");
            } else {
                sb.append("direct BYD API: ").append(err != null ? err : "no data: " + fw.lastError())
                  .append('\n');
            }

            if (current == null) {
                BinderVehicle bv = new BinderVehicle();
                String err2 = bv.bind();
                if (err2 == null && usable(bv)) {
                    current = bv;
                    sb.append("autoservice binder: works\n");
                } else {
                    sb.append("autoservice binder: ").append(err2 != null ? err2 : bv.lastError())
                      .append('\n');
                    // A temporary startup failure must not permanently disable direct access.
                }
            }
        } else {
            sb.append("direct BYD API: retry on backoff\n");
        }

        // Keep the prior source if rebinding fails. A working helper may be upgraded
        // to direct API at the next scheduled probe, but is not closed until then.
        if (current == null && keep) {
            current = previous;
            sb.append("retained: ").append(previous.name()).append('\n');
        }
        if (previous instanceof HelperVehicle && current != previous) ((HelperVehicle) previous).close();

        if (current == null && Prefs.linkEnabled(ctx)) {
            HelperVehicle hv = new HelperVehicle(HelperLauncher.token(ctx));
            String err3 = hv.connect();
            if (err3 == null && usable(hv)) {
                current = hv;
                sb.append("helper process: connected\n");
            } else {
                hv.close();
                sb.append("helper process: ").append(err3 != null ? err3 : "no data: " + hv.lastError()).append('\n');
                HelperLauncher.ensureAsync(ctx, false);
            }
        } else if (current == null) {
            sb.append("helper process: off (vehicle data link is switched off)\n");
        }

        if (current == null && gps != null && gps.isStarted()) {
            current = new GpsVehicle(gps);
            sb.append("GPS only: active\n");
        }

        sb.append("selected: ").append(current == null ? "none" : current.name());
        report = sb.toString();
        long nowMs = System.currentTimeMillis();
        if (!report.equals(lastLoggedReport) || nowMs - lastLogMs > 600_000) {
            lastLoggedReport = report;
            lastLogMs = nowMs;
            Diag.log("probe -> " + report.replace('\n', ' '));
        }
        return report;
    }

    private static boolean usable(Vehicle v) {
        double speed = v.speedKmh(), soc = v.socPercent();
        return (speed >= 0 && speed <= 350) || (soc >= 0 && soc <= 100);
    }
}

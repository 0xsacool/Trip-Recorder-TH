package org.triprecorderng;

/** Read-only, firmware-tolerant charging evidence. No dependency on Android or the database. */
final class ChargeDetection {
    private static final long PARKED_MS = 60_000;
    private static final long QUIET_MS = 12 * 60_000;
    static final int NONE = 0, BMS = 1, PLUG_POWER = 2, SOC_GAIN = 3;
    private long parkedSince = -1, baseWall, lastGainWall, lastValidSocWall;
    private double baseSoc = Double.NaN, highSoc = Double.NaN, parkedOdo = Double.NaN;
    private int rises;
    private boolean inferred, sawPlug;

    static double field(double[] c, int i) {
        return c != null && i < c.length && Double.isFinite(c[i]) ? c[i] : Double.NaN;
    }
    static int code(double[] c, int i) {
        double f = field(c, i);
        return f >= 0 && f < 65535 && f == Math.rint(f) ? (int) f : -1;
    }
    static boolean plugged(int gun) { return gun == 2 || gun == 3 || gun == 4; }
    static boolean validSoc(double s) { return Double.isFinite(s) && s >= 0 && s <= 100; }
    static boolean moving(double speed) { return Double.isFinite(speed) && speed >= 1; }
    static boolean validOdo(double odo) { return Double.isFinite(odo) && odo >= 0; }

    static int signal(double[] c, double speed) {
        if (moving(speed)) return NONE; // even a stale charging flag must not count regen
        int bms = code(c, 0), gun = code(c, 1);
        boolean cable = plugged(gun);
        if ((bms == 1 || bms == 13) && (cable || Double.isNaN(speed) || (speed >= 0 && speed < 1))) return BMS;
        double power = field(c, 3); // dedicated charger power, never traction motor power
        return cable && power >= 0.3 && power <= 1000 ? PLUG_POWER : NONE;
    }

    void reset() {
        parkedSince = -1;
        baseSoc = highSoc = parkedOdo = Double.NaN;
        baseWall = lastGainWall = lastValidSocWall = 0;
        rises = 0;
        inferred = sawPlug = false;
    }

    int update(double[] c, double speed, double odo, double soc, long wall) {
        int direct = signal(c, speed);
        boolean cable = plugged(code(c, 1));
        if (direct != NONE) { reset(); return direct; }
        // SOC-only fallback must have affirmative stationary speed and no odometer travel.
        if (!(speed >= 0 && speed < 1)) { reset(); return NONE; }
        if (parkedSince >= 0 && validOdo(parkedOdo) && validOdo(odo)
                && Math.abs(odo - parkedOdo) > 0.05) { reset(); return NONE; }
        if (!validSoc(soc)) {
            // A brief SOC read failure must not erase the pre-charge baseline.
            if (parkedSince >= 0 && wall - lastValidSocWall <= 60_000)
                return inferred ? SOC_GAIN : NONE;
            reset(); return NONE;
        }
        if (parkedSince < 0 || wall < baseWall) {
            reset(); parkedSince = wall; baseWall = wall; lastGainWall = lastValidSocWall = wall;
            baseSoc = highSoc = soc; parkedOdo = odo; sawPlug = cable;
            return NONE;
        }
        lastValidSocWall = wall;
        if (!validOdo(parkedOdo) && validOdo(odo)) parkedOdo = odo;
        sawPlug |= cable;
        if (soc < highSoc - 1 || (inferred && sawPlug && code(c, 1) >= 0 && !cable)) {
            reset(); return NONE;
        }
        if (soc > highSoc + 0.05) {
            highSoc = soc; rises++; lastGainWall = wall;
        } else if (!inferred && rises == 0 && soc <= baseSoc) {
            baseSoc = highSoc = soc; baseWall = wall;
        }
        if (wall - lastGainWall >= QUIET_MS && (inferred || rises > 0)) {
            reset(); return NONE;
        }
        double gain = highSoc - baseSoc;
        // A cable is corroborating evidence. Without it require a sustained climb, or a large jump
        // while continuously stationary; minor battery recalibration alone must not make a session.
        if (!inferred && wall - parkedSince >= PARKED_MS
                && (sawPlug ? gain >= 2 : (gain >= 5 && rises >= 2)
                    || (gain >= 10 && wall - parkedSince >= 2 * PARKED_MS))) inferred = true;
        return inferred ? SOC_GAIN : NONE;
    }

    double startSoc() { return baseSoc; }
    long startWall() { return baseWall; }
}

package org.triprecorderng;

/** Read-only, firmware-tolerant charging evidence. No dependency on Android or the database. */
final class ChargeDetection {
    private static final long PARKED_MS = 60_000;
    private static final long QUIET_MS = 12 * 60_000;
    static final int NONE = 0, BMS = 1, PLUG_POWER = 2, SOC_GAIN = 3, BATTERY_INFLOW = 4;
    private static final long INFLOW_CONFIRM_MS = 30_000;
    private long parkedSince = -1, baseWall, lastGainWall, lastValidSocWall;
    private double baseSoc = Double.NaN, highSoc = Double.NaN, parkedOdo = Double.NaN;
    private int rises;
    private boolean inferred, sawPlug;
    private long inflowSince = -1, lastInflowWall;
    private double inflowSoc = Double.NaN;
    private boolean inflowConfirmed;

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
        clearInflow();
    }

    private void clearInflow() {
        inflowSince = -1; lastInflowWall = 0; inflowSoc = Double.NaN; inflowConfirmed = false;
    }

    static boolean validInflow(double speed, double signedPower) {
        return speed >= 0 && speed < 0.5 && signedPower <= -1 && signedPower >= -500;
    }

    int update(double[] c, double speed, double odo, double soc, double signedPower, long wall) {
        int direct = signal(c, speed);
        boolean cable = plugged(code(c, 1));
        if (direct != NONE) { reset(); return direct; }
        // SOC-only fallback must have affirmative stationary speed and no odometer travel.
        if (!(speed >= 0 && speed < 1)) { reset(); return NONE; }
        if (parkedSince >= 0 && validOdo(parkedOdo) && validOdo(odo)
                && Math.abs(odo - parkedOdo) > 0.05) { reset(); return NONE; }
        // Atto 3 reports charging as negative signed battery/drive power even when its
        // dedicated charging API is unavailable. Never trust a negative value by itself:
        // require stationary samples for >=30s and cable or an observed SOC increase.
        boolean inflow = validInflow(speed, signedPower);
        if (inflow) lastInflowWall = wall;
        // A temporary unavailable power sample is not a known zero; tolerate up to 15s
        // only for a confirmed stationary session (never start from missing power).
        if (inflowConfirmed && Double.isNaN(signedPower) && speed >= 0 && speed < 0.5
                && wall - lastInflowWall <= 15_000) return BATTERY_INFLOW;
        if (!inflow) {
            if (inflowConfirmed) { reset(); return NONE; }
            clearInflow();
        } else if (validSoc(soc)) {
            if (inflowSince < 0 || wall < inflowSince || soc < inflowSoc - 1) {
                clearInflow(); inflowSince = lastInflowWall = wall; inflowSoc = soc;
            }
            if (wall - inflowSince >= INFLOW_CONFIRM_MS && (cable || soc - inflowSoc >= 0.99))
                inflowConfirmed = true;
        }
        if (inflowConfirmed && inflow) {
            // A momentary missing SOC is acceptable only after confirmation; motion or a
            // non-negative power sample ends this evidence and lets the recorder debounce.
            if (validSoc(soc)) lastValidSocWall = wall;
            if (wall - lastValidSocWall <= 60_000) return BATTERY_INFLOW;
            reset(); return NONE;
        }
        if (!validSoc(soc)) {
            // A brief SOC read failure must not erase the pre-charge baseline.
            if (parkedSince >= 0 && wall - lastValidSocWall <= 60_000)
                return inferred ? SOC_GAIN : NONE;
            reset(); return NONE;
        }
        if (parkedSince < 0 || wall < baseWall) {
            parkedSince = wall; baseWall = wall; lastGainWall = lastValidSocWall = wall;
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

    double startSoc() { return inflowConfirmed ? inflowSoc : baseSoc; }
    long startWall() { return inflowConfirmed ? inflowSince : baseWall; }
    boolean checkingInflow() { return inflowSince >= 0 && !inflowConfirmed; }
}

package org.triprecorderng;

/** UI-only, one-shot decorative startup policy. Never interpolates telemetry. */
final class SharedMotionController {
    enum Mode { OFF, REDUCED, FULL }
    private boolean consumed;
    private long start = -1;
    private int duration;
    static boolean stationary(double speed) {
        return !Double.isNaN(speed) && !Double.isInfinite(speed) && speed == 0;
    }
    void begin(long now, Mode mode, double speed, boolean parkedConfirmed) {
        if (consumed) return;
        consumed = true;
        if (mode == Mode.OFF || !parkedConfirmed || !stationary(speed)) return;
        duration = mode == Mode.FULL ? 1200 : 650;
        start = now;
    }
    void cancel() { start = -1; consumed = true; }
    float frame(long now, double speed, boolean parkedConfirmed) {
        if (start < 0) return 0;
        if (!parkedConfirmed || !stationary(speed) || now < start || now-start >= duration) {
            cancel(); return 0;
        }
        float t = (float)(now-start)/duration;
        return t < .65f ? t/.65f : (1-t)/.35f;
    }
    boolean active() { return start >= 0; }
}

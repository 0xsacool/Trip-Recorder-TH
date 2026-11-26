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
    // Decorative intensity only; the actual power needle and number remain direct.
    private float accent;
    private long accentStart = -1;
    private float from, target;
    private int accentDuration;
    float powerAccent(long now, Mode mode, double power) {
        if (mode == Mode.OFF || Double.isNaN(power) || Double.isInfinite(power)) {
            accent = from = target = 0; accentStart = -1; return 0;
        }
        if (accentStart >= 0) {
            float t = Math.max(0, Math.min(1, (float)(now-accentStart)/accentDuration));
            accent = from + (target-from)*t;
            if (t >= 1) accentStart = -1;
        }
        float next = (float)Math.min(1, Math.abs(power)/100);
        if (Math.abs(next-target) >= .02f || (next == 0 && target != 0)) {
            from = accent; target = next; accentStart = now;
            accentDuration = mode == Mode.FULL ? 180 : 100;
        }
        return accent;
    }
    boolean powerActive() { return accentStart >= 0; }
    private double gaugeValue=Double.NaN, gaugeFrom, gaugeTarget;
    private long gaugeStart=-1;
    private int gaugeDuration;
    double gauge(long now, Mode mode, double power) {
        if (!Double.isFinite(power) || mode==Mode.OFF || !Double.isFinite(gaugeValue)) {
            gaugeValue=gaugeTarget=power; gaugeStart=-1; return power;
        }
        if(gaugeStart>=0){
            double t=Math.max(0,Math.min(1,(double)(now-gaugeStart)/gaugeDuration));
            gaugeValue=gaugeFrom+(gaugeTarget-gaugeFrom)*t*(2-t);
            if(t>=1)gaugeStart=-1;
        }
        if(power!=gaugeTarget){
            gaugeFrom=gaugeValue; gaugeTarget=power; gaugeStart=now;
            gaugeDuration=mode==Mode.FULL?180:100;
        }
        return gaugeValue;
    }
    boolean gaugeActive(){return gaugeStart>=0;}
    boolean active() { return start >= 0; }
}

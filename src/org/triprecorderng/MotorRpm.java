package org.triprecorderng;

/** Front traction motor only; reject invalid CAN encodings and stale event readings. */
final class MotorRpm {
    static final int FRONT_FEATURE=1141899272;
    static final long FRESH_MS=3000;
    static double normalize(double raw){
        if(!Double.isFinite(raw)||raw== -1)return Double.NaN;
        double n=Math.abs(raw);
        if(n>30000||n==8191||n==16383||n==32767||n==65535)return Double.NaN;
        return n;
    }
    static double plausible(double rpm,double speed){
        double n=normalize(rpm);
        // A zero from an unsupported getter must not look like a stopped traction motor while moving.
        return n==0&&speed>3?Double.NaN:n;
    }
    static final class Confirmation {
        private boolean seenPositive;
        double accept(double rpm){double n=normalize(rpm);if(n>0)seenPositive=true;return seenPositive?n:Double.NaN;}
    }
    static final class Sample {
        final double value;final long time;
        Sample(double raw,long time){value=normalize(raw);this.time=time;}
        double fresh(long now){return now>=time&&now-time<=FRESH_MS?value:Double.NaN;}
    }
}

package org.triprecorderng;

public final class MotorRpmTest {
    private static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);}
    public static void main(String[] args){
        check(MotorRpm.normalize(-2450)==2450,"direction magnitude");
        check(MotorRpm.normalize(0)==0,"measured stop");
        for(double bad:new double[]{-1,8191,-8191,16383,32767,65535,30001,Double.NaN,Double.POSITIVE_INFINITY})
            check(Double.isNaN(MotorRpm.normalize(bad)),"invalid signal "+bad);
        MotorRpm.Confirmation confirm=new MotorRpm.Confirmation();
        check(Double.isNaN(confirm.accept(0)),"unsupported zero does not unlock graph");
        check(confirm.accept(2500)==2500&&confirm.accept(0)==0,"positive data confirms a real sensor");
        check(Double.isNaN(confirm.accept(8191)),"invalid signal after confirmation");
        check(Double.isNaN(MotorRpm.plausible(0,30)),"moving zero rejected");
        check(MotorRpm.plausible(0,0)==0,"stationary zero accepted");
        MotorRpm.Sample reading=new MotorRpm.Sample(4500,1000);
        check(reading.fresh(2000)==4500&&Double.isNaN(reading.fresh(4001))&&Double.isNaN(reading.fresh(999)),"freshness guard");
        System.out.println("MotorRpmTest PASS");
    }
}

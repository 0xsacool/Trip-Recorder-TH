package org.triprecorderng;

final class ChargeBillingTest {
    private static void check(boolean value,String description){
        if(!value)throw new AssertionError(description);
    }
    private static void same(double expected,double actual,String description){
        check(Math.abs(expected-actual)<0.000001,description+": "+actual);
    }
    public static void main(String[] args){
        double na=Double.NaN;
        ChargeBilling.Profile home=new ChargeBilling.Profile("home","Home AC",2,3.4,true);
        ChargeBilling old=home.snapshot();
        same(121.38,old.cost(35.7),"AC rate");
        ChargeBilling.Profile updated=new ChargeBilling.Profile("home","Home AC",2,4.5,true);
        same(121.38,old.cost(35.7),"old snapshot unchanged after price update");
        same(160.65,updated.snapshot().cost(35.7),"new profile rate");
        ChargeBilling dc=new ChargeBilling("dc","Station",3,7.5,na,na,true);
        same(267.75,dc.cost(35.7),"DC custom rate");
        same(300,new ChargeBilling("dc","Station",3,7.5,40,na,true).cost(35.7),"actual billed kWh overrides estimate");
        ChargeBilling actual=new ChargeBilling("dc","Station",3,7.5,40,315,true);
        same(315,actual.cost(35.7),"receipt total overrides all");
        check(actual.actual(),"receipt label");
        same(0,new ChargeBilling("free","Free",2,0,na,na,true).cost(35.7),"zero rate supported");
        same(0,new ChargeBilling("","",0,na,na,0,true).cost(na),"zero actual supported");
        check(Double.isNaN(new ChargeBilling("","",0,na,na,na,false).cost(35.7)),"unknown rate stays unknown");
        check(Double.isNaN(dc.cost(na)),"unknown energy stays unknown");
        check(!ChargeBilling.valid(-1)&&!ChargeBilling.valid(Double.POSITIVE_INFINITY),"invalid tariff");
        check("AC".equals(old.typeName())&&"DC".equals(dc.typeName()),"type");
        check(new ChargeBilling("","",4,na,na,na,false).typeName().isEmpty(),"ambiguous type is unknown");
        System.out.println("ChargeBillingTest PASS (13 scenarios)");
    }
}

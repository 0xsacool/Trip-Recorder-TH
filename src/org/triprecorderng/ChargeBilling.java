package org.triprecorderng;

/** Billing data is separate from recorded vehicle telemetry. NaN denotes an unknown value. */
final class ChargeBilling {
    static final int UNKNOWN=0, AC=2, DC=3;
    final String profileId, name;
    final int type;
    final double rate, billedKwh, actualCost;
    final boolean edited;

    ChargeBilling(String profileId,String name,int type,double rate,double billedKwh,double actualCost,boolean edited) {
        this.profileId=profileId==null?"":profileId;
        this.name=name==null?"":name;
        this.type=type==AC||type==DC?type:UNKNOWN;
        this.rate=valid(rate)?rate:Double.NaN;
        this.billedKwh=valid(billedKwh)?billedKwh:Double.NaN;
        this.actualCost=valid(actualCost)?actualCost:Double.NaN;
        this.edited=edited;
    }
    static boolean valid(double n) { return !Double.isNaN(n)&&!Double.isInfinite(n)&&n>=0; }
    double cost(double recordedKwh) {
        if(valid(actualCost)) return actualCost;
        double e=valid(billedKwh)?billedKwh:recordedKwh;
        double cost=e*rate;
        return valid(e)&&valid(rate)&&valid(cost)?cost:Double.NaN;
    }
    boolean actual() { return valid(actualCost); }
    String typeName() { return type==AC?"AC":type==DC?"DC":""; }

    static final class Profile {
        final String id,name;
        final int type;
        final double rate;
        final boolean preferred;
        Profile(String id,String name,int type,double rate,boolean preferred) {
            this.id=id; this.name=name; this.type=type; this.rate=rate; this.preferred=preferred;
        }
        ChargeBilling snapshot() { return new ChargeBilling(id,name,type,rate,Double.NaN,Double.NaN,false); }
    }
}

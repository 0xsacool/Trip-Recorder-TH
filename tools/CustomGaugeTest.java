package org.triprecorderng;

final class CustomGaugeTest {
    static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
    static final class Trace implements EvoHybridRenderer.Surface {
        final boolean[] hit=new boolean[2];
        final java.util.Set<String> text=new java.util.HashSet<>();
        public void plate(float x,float y,float w,float h){}
        public void rect(float x,float y,float w,float h,int c){check(w>=0&&h>=0,"rect geometry");}
        public void outline(float x,float y,float w,float h,float radius,int c,float t){}
        public void line(float x,float y,float r,float b,int c,float t){check(Float.isFinite(x)&&Float.isFinite(y)&&Float.isFinite(r)&&Float.isFinite(b),"finite lines");}
        public void circle(float x,float y,float r,int c){}
        public void text(String v,float x,float y,float size,int c,boolean heavy,boolean slant,int align,float w){text.add(v);}
        public void gaugeBounds(int slot,float x,float y,float w,float h){check(slot>=0&&slot<2&&!hit[slot]&&x>=0&&y>=0&&w>0&&h>0&&x+w<=1200&&y+h<=470,"valid unique bounds");hit[slot]=true;}
    }
    static void draw(Trace t,EvoHybridRenderer.Data d,int theme){
        switch(theme){
            case 0:EvoHybridRenderer.draw(t,d);break;
            case 1:GtrHybridRenderer.draw(t,d,d.avgConsumption,null,0);break;
            case 2:TypeRHybridRenderer.draw(t,d);break;
            default:AdditionalHybridRenderer.draw(t,d,new String[]{AdditionalHybridRenderer.GR,AdditionalHybridRenderer.N,AdditionalHybridRenderer.RS,AdditionalHybridRenderer.TAYCAN,AdditionalHybridRenderer.XTREME}[theme-3],d.avgConsumption);
        }
    }
    public static void main(String[] args){
        EvoHybridRenderer.Data d=new EvoHybridRenderer.Data();
        d.power=-47;d.speed=83;d.soc=61;d.liveRpm=4250;d.avgConsumption=17.2;d.average="≈17.2 kWh/100 km";
        d.batteryTemp=36;d.chargingVoltage=442;d.remainingRange=213;
        for(int theme=0;theme<8;theme++){
            for(EvoHybridRenderer.GaugeMetric m:EvoHybridRenderer.GaugeMetric.values()){
                d.gauges[0]=m;d.gauges[1]=EvoHybridRenderer.GaugeMetric.DEFAULT;
                Trace t=new Trace();draw(t,d,theme);check(t.hit[0]&&t.hit[1],"both gauges selectable");
                if(m!=EvoHybridRenderer.GaugeMetric.DEFAULT)check(t.text.contains(m.title),"slot 0 title "+theme+" "+m);
                d.gauges[0]=EvoHybridRenderer.GaugeMetric.DEFAULT;d.gauges[1]=m;
                t=new Trace();draw(t,d,theme);
                if(m!=EvoHybridRenderer.GaugeMetric.DEFAULT)check(t.text.contains(m.title),"slot 1 title "+theme+" "+m);
            }
            d.gauges[0]=EvoHybridRenderer.GaugeMetric.RPM;d.gauges[1]=EvoHybridRenderer.GaugeMetric.TEMPERATURE;
            Trace t=new Trace();draw(t,d,theme);check(t.text.contains("FRONT MOTOR RPM")&&t.text.contains("BATTERY TEMP MAX"),"independent selection");
        }
        check("4250".equals(EvoHybridRenderer.GaugeMetric.RPM.reading(d)),"live RPM");
        d.liveRpm=Double.NaN;check("--".equals(EvoHybridRenderer.GaugeMetric.RPM.reading(d)),"missing RPM not zero");
        d.liveRpm=0;check("0".equals(EvoHybridRenderer.GaugeMetric.RPM.reading(d)),"measured zero RPM");
        check("≈17.2".equals(EvoHybridRenderer.GaugeMetric.CONSUMPTION.reading(d)),"estimate marker");
        check("≈213".equals(EvoHybridRenderer.GaugeMetric.RANGE.reading(d)),"range estimate marker");
        System.out.println("CustomGaugeTest PASS (8 themes, 2 slots, 9 choices)");
    }
}

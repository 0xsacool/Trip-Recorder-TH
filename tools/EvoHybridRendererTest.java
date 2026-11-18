package org.triprecorderng;

/** Pure Java checks for the native renderer geometry and missing values. */
public final class EvoHybridRendererTest {
    private static void check(boolean pass, String msg) {
        if (!pass) throw new AssertionError(msg);
    }
    static final class Trace implements EvoHybridRenderer.Surface {
        int plates, needles;
        boolean missingSpeed, missingPower, missingSoc;
        public void plate(float x,float y,float w,float h) {
            plates++;
            check(Math.abs(w/h-1903f/826)<.001,"plate aspect ratio");
        }
        public void rect(float x,float y,float w,float h,int color) {}
        public void outline(float x,float y,float w,float h,float radius,int color,float thickness) {}
        public void line(float x1,float y1,float x2,float y2,int color,float thickness) {
            check(!Float.isNaN(x1)&&!Float.isNaN(y1)&&!Float.isNaN(x2)&&!Float.isNaN(y2),"invalid geometry");
            if(color==0xff53151b) needles++;
        }
        public void circle(float cx,float cy,float radius,int color) {}
        public void text(String value,float x,float baseline,float size,int color,boolean heavy,boolean slant,int align,float maxWidth) {
            if("-- km/h".equals(value)) missingSpeed=true;
            if("-- kW".equals(value)) missingPower=true;
            if("--%".equals(value)) missingSoc=true;
        }
    }
    public static void main(String[] args) {
        EvoHybridRenderer.History h=new EvoHybridRenderer.History();
        h.sample(1000,20);h.sample(1500,24);h.sample(3000,-48);
        check(h.count==3&&h.samples[0]==24&&Double.isNaN(h.samples[1])&&h.samples[2]==-48,"history sampling and gap");
        for(int i=4;i<=70;i++)h.sample(i*1000L, i);
        check(h.count==60&&h.samples[0]==11&&h.samples[59]==70,"rolling history capacity");
        h.sample(140000,Double.NaN);check(h.count==1&&Double.isNaN(h.samples[0]),"clear old history after long gap");
        EvoHybridRenderer.TelemetryHistory history=new EvoHybridRenderer.TelemetryHistory();
        check(!history.available(EvoHybridRenderer.Metric.BATTERY_TEMP),"unsupported sensor hidden");
        for(int i=1;i<=310;i++)history.sample(i*1000L,24,i,17.1,34,71);
        history.sample(312000,Double.NaN,82,Double.NaN,Double.NaN,71);
        EvoHybridRenderer.Data chart=new EvoHybridRenderer.Data();
        history.select(chart,EvoHybridRenderer.Metric.SPEED,60);
        check(chart.history.length==60&&chart.historyCount==60&&chart.history[59]==82&&Double.isNaN(chart.history[58]),"60-second selected metric and gap");
        history.select(chart,EvoHybridRenderer.Metric.BATTERY_TEMP,300);
        check(history.available(EvoHybridRenderer.Metric.BATTERY_TEMP)&&chart.history.length==300&&chart.historyCount==300&&chart.history[0]==34&&Double.isNaN(chart.history[299]),"five-minute history and stale readings");
        history.select(chart,EvoHybridRenderer.Metric.BATTERY_LEVEL,60);
        check(chart.historyMetric==EvoHybridRenderer.Metric.BATTERY_LEVEL&&chart.history[59]==71,"all metrics sampled together");
        check(EvoHybridRenderer.angle(-100,-100,200)==140,"regen limit");
        check(EvoHybridRenderer.angle(200,-100,200)==400,"power limit");
        check(EvoHybridRenderer.angle(900,-100,200)==400,"high clamp");
        check(EvoHybridRenderer.angle(-900,-100,200)==140,"low clamp");
        check(Math.abs(EvoHybridRenderer.angle(82,0,260)-222)<.001,"speed pointer");
        check(Double.isNaN(EvoHybridRenderer.angle(Double.NaN,0,260)),"missing data");
        EvoHybridRenderer.Data d=new EvoHybridRenderer.Data();
        d.soc=71;d.speed=82;d.power=-48;
        Trace trace=new Trace();EvoHybridRenderer.draw(trace,d);
        check(trace.plates==1&&trace.needles==2,"live draw");
        d.soc=d.speed=d.power=Double.NaN;
        trace=new Trace();EvoHybridRenderer.draw(trace,d);
        check(trace.plates==1&&trace.needles==0,"offline: no fake needles");
        check(trace.missingSpeed&&trace.missingPower&&trace.missingSoc,"offline placeholders");
        System.out.println("EvoHybridRendererTest PASS");
    }
}

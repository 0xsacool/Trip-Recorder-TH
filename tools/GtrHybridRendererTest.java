package org.triprecorderng;

public final class GtrHybridRendererTest {
    static void check(boolean pass,String msg){if(!pass)throw new AssertionError(msg);}
    static final class Trace implements EvoHybridRenderer.Surface {
        int plates,needles,graphLines; boolean missingPower,missingSoc,missingAverage,missingSpeed,selectedGraph;
        public void plate(float x,float y,float w,float h){plates++;check(Math.abs(w/h-2061f/763)<.001,"asset aspect");}
        public void rect(float x,float y,float w,float h,int color){}
        public void outline(float x,float y,float w,float h,float radius,int color,float thickness){}
        public void line(float x,float y,float x2,float y2,int color,float width){
            check(Float.isFinite(x)&&Float.isFinite(y)&&Float.isFinite(x2)&&Float.isFinite(y2),"finite geometry");
            if(color==GtrHybridRenderer.NEEDLE)needles++;
            if(color==GtrHybridRenderer.CYAN&&width==2.2f)graphLines++;
        }
        public void circle(float x,float y,float radius,int color){}
        public void text(String v,float x,float y,float size,int color,boolean heavy,boolean slant,int align,float width){
            if("--".equals(v)&&size==48&&x<400)missingPower=true;
            if("--".equals(v)&&size==48&&x>800)missingAverage=true;
            if("--".equals(v)&&size==92)missingSpeed=true;
            if("--%".equals(v))missingSoc=true;
            if("SPEED HISTORY (km/h)".equals(v))selectedGraph=true;
        }
    }
    public static void main(String[] args){
        check(GtrHybridRenderer.angle(-100,-100,200)==140,"regen minimum");
        check(GtrHybridRenderer.angle(200,-100,200)==400,"power maximum");
        check(GtrHybridRenderer.angle(50,0,30)==400,"overflow clamps needle");
        check(Double.isNaN(GtrHybridRenderer.angle(Double.NaN,0,30)),"missing needle");
        EvoHybridRenderer.Data d=new EvoHybridRenderer.Data();d.soc=71;d.speed=82;d.power=-48;d.average="≈17.1 kWh/100 km";
        Trace t=new Trace();GtrHybridRenderer.draw(t,d,17.1,new double[]{0,24,Double.NaN,-48,-20},5);
        check(t.plates==1&&t.needles==2&&t.graphLines==2,"live gauges and gap in history");
        d.historyMetric=EvoHybridRenderer.Metric.SPEED;d.historySeconds=300;
        t=new Trace();GtrHybridRenderer.draw(t,d,17.1,new double[]{82,85},2);check(t.selectedGraph,"selected metric title");
        d.soc=d.speed=d.power=Double.NaN;d.average="-- kWh/100 km";
        t=new Trace();GtrHybridRenderer.draw(t,d,Double.NaN,new double[]{Double.NaN},1);
        check(t.needles==0&&t.graphLines==0&&t.missingPower&&t.missingSoc&&t.missingAverage&&t.missingSpeed,"offline placeholders");
        System.out.println("GtrHybridRendererTest PASS");
    }
}

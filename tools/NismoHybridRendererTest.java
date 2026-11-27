package org.triprecorderng;

import java.util.ArrayList;
import java.util.List;

final class NismoHybridRendererTest {
    static void check(boolean condition,String name) {
        if (!condition) throw new AssertionError(name);
    }
    static final class Surface implements EvoHybridRenderer.Surface {
        final List<String> values=new ArrayList<>();
        int plates, gauges, graphs, red, cyan;
        public void plate(float x,float y,float w,float h){plates++;}
        public void rect(float x,float y,float w,float h,int color){}
        public void line(float x,float y,float x2,float y2,int color,float weight){}
        public void outline(float x,float y,float w,float h,float r,int color,float t){}
        public void circle(float x,float y,float r,int color){}
        public void slant(float x,float y,float w,float h,float shear,int color){
            check(w>=0 && w<=19,"finite bank");
            if(color==NismoHybridRenderer.RED)red++;
            if(color==NismoHybridRenderer.CYAN)cyan++;
        }
        public void text(String v,float x,float y,float size,int color,boolean heavy,boolean slant,int align,float maxWidth){values.add(v);}
        public void gaugeBounds(int slot,float x,float y,float w,float h){gauges++;}
        public void historyBounds(float x,float y,float w,float h){graphs++;}
    }
    public static void main(String[] args) {
        SharedMotionController.Gauge gauge=new SharedMotionController.Gauge();
        for(SharedMotionController.Mode mode:SharedMotionController.Mode.values()){
            gauge.update(0,mode,Double.NaN);
            check(gauge.update(1,mode,63)==63,"acquisition");
            double first=gauge.update(30,mode,-50);
            check(first<=0 && first>=-50,"no stale positive indication at regen");
            double middle=gauge.update(90,mode,-50);
            check(middle<0 && middle>=-50,"bounded regen");
            check(gauge.update(280,mode,-50)==-50 && !gauge.active(),"settled");
            check(gauge.update(300,mode,0)==0 && !gauge.active(),"coast");
            check(Double.isNaN(gauge.update(320,mode,Double.NaN)) && !gauge.active(),"signal loss");
        }
        EvoHybridRenderer.Data d=new EvoHybridRenderer.Data();
        d.speed=86;d.power=-48;d.nismoPower=-17;d.nismoSoc=64;d.soc=64;d.liveRpm=4280;
        Surface s=new Surface();
        NismoHybridRenderer.draw(s,d);
        check(s.plates==1 && s.gauges==2 && s.graphs==1,"material and picker registrations");
        check(s.values.contains("86") && s.values.contains("-48") && !s.values.contains("-17"),"direct readings");
        check(s.values.contains("REGEN") && s.red==0 && s.cyan>0,"signed bank");
        d.soc=d.nismoSoc=d.speed=d.power=d.nismoPower=d.liveRpm=Double.NaN;
        s=new Surface();NismoHybridRenderer.draw(s,d);
        check(java.util.Collections.frequency(s.values,"--")>=5 && !s.values.contains("COAST") && s.red==0 && s.cyan==0,"missing data");
        d.results=true;d.historyMetric=EvoHybridRenderer.Metric.MOTOR_RPM;
        d.gauges[0]=EvoHybridRenderer.GaugeMetric.SPEED;
        s=new Surface();NismoHybridRenderer.draw(s,d);
        check(s.values.contains("TRIP RESULTS") && s.values.contains("LIVE"),"results and live RPM selection");
        System.out.println("GT3 renderer and all motion modes passed");
    }
}

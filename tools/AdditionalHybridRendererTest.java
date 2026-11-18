package org.triprecorderng;

import java.util.HashSet;
import java.util.Set;

/** Renderer-level regression tests, independent from Android and vehicle HAL. */
public final class AdditionalHybridRendererTest {
    static void check(boolean pass,String message){if(!pass)throw new AssertionError(message);}
    static final class Trace implements EvoHybridRenderer.Surface {
        final Set<String> text=new HashSet<>();
        int plates,red,blue,cyan,orange;
        void finite(float... values){for(float v:values)check(Float.isFinite(v),"non-finite graphics geometry");}
        public void plate(float x,float y,float w,float h){plates++;finite(x,y,w,h);check(Math.abs(w/h-2061f/763)<.001,"aspect");}
        public void rect(float x,float y,float w,float h,int c){finite(x,y,w,h);check(w>=0&&h>=0,"negative bounds");}
        public void outline(float x,float y,float w,float h,float r,int c,float th){finite(x,y,w,h,r,th);}
        public void line(float x,float y,float xx,float yy,int c,float th){finite(x,y,xx,yy,th);}
        public void circle(float x,float y,float r,int c){finite(x,y,r);}
        public void slant(float x,float y,float w,float h,float shear,int col){finite(x,y,w,h,shear);check(w>0,"segment width");color(col);}
        public void arc(float x,float y,float r,float start,float sweep,float w,int col){finite(x,y,r,start,sweep,w);check(sweep>0,"arc sweep");color(col);}
        private void color(int c){if(c==AdditionalHybridRenderer.RED)red++;if(c==AdditionalHybridRenderer.BLUE)blue++;if(c==AdditionalHybridRenderer.CYAN)cyan++;if(c==AdditionalHybridRenderer.ORANGE)orange++;}
        public void text(String v,float x,float y,float size,int c,boolean heavy,boolean slant,int align,float maxWidth){finite(x,y,size,maxWidth);text.add(v);}
    }
    private static EvoHybridRenderer.Data data(){
        EvoHybridRenderer.Data d=new EvoHybridRenderer.Data();d.soc=71;d.speed=82;d.power=24;d.range="≈249 km";d.average="≈17.1 kWh/100 km";
        d.source="Vehicle connected";d.status="Recording";d.tripLabel="Current trip";d.temp="32 / 34 °C";d.cells="3.296 / 3.302 V";d.voltage="416 V";
        d.distance="24.6 km";d.elapsed="28 min";d.energy="≈4.2 kWh";d.money="≈14.28 THB";
        return d;
    }
    private static Trace render(String theme,EvoHybridRenderer.Data d,double avg){Trace t=new Trace();AdditionalHybridRenderer.draw(t,d,theme,avg);return t;}
    public static void main(String[] args){
        check(AdditionalHybridRenderer.fraction(250,200)==1,"clamped power");check(AdditionalHybridRenderer.fraction(Double.NaN,200)==0,"missing power");
        for(String theme:new String[]{AdditionalHybridRenderer.GR,AdditionalHybridRenderer.N,AdditionalHybridRenderer.RS,AdditionalHybridRenderer.TAYCAN,AdditionalHybridRenderer.XTREME}){
            EvoHybridRenderer.Data d=data();Trace t=render(theme,d,17.1);
            check(t.plates==1&&AdditionalHybridRenderer.asset(theme)!=null,"plate "+theme);
            for(String expected:new String[]{"82","24 kW",d.range,d.temp,d.cells,d.voltage,d.distance,d.elapsed,d.energy,"POWER HISTORY (kW)"})check(t.text.contains(expected),"missing "+expected+" in "+theme);
            check(!t.text.contains(d.money),"cost removed "+theme);
            check(t.text.contains(d.average)||t.text.contains("≈17.1"),"average "+theme);
            if(theme.equals(AdditionalHybridRenderer.GR))check(t.red>0&&t.blue==0,"GR traction");
            if(theme.equals(AdditionalHybridRenderer.N))check(t.blue>0&&t.cyan==0,"N traction");
            if(theme.equals(AdditionalHybridRenderer.XTREME))check(t.orange>0&&t.cyan==0,"Xtreme traction");
            d.power=-48;t=render(theme,d,17.1);check(t.text.contains("-48 kW"),"signed regeneration "+theme);
            if(theme.equals(AdditionalHybridRenderer.GR))check(t.red==0&&t.blue>0,"GR regen");
            if(theme.equals(AdditionalHybridRenderer.N))check(t.blue==0&&t.cyan>0,"N regen");
            if(theme.equals(AdditionalHybridRenderer.XTREME))check(t.orange==0&&t.cyan>0,"Xtreme regen");
            d.soc=d.speed=d.power=Double.NaN;d.range="-- km";d.average="-- kWh/100 km";
            t=render(theme,d,Double.NaN);check(t.text.contains("--")&&t.text.contains("-- kW"),"offline readings "+theme);
            check(t.red==0&&t.blue==0&&t.cyan==0&&t.orange==0,"offline no active gauges "+theme);
            for(EvoHybridRenderer.Metric m:EvoHybridRenderer.Metric.values()){
                d.historyMetric=m;d.historySeconds=300;d.history=new double[]{12,Double.NaN,18};d.historyCount=3;
                d.liveRpm=4250;
                t=render(theme,d,Double.NaN);check(t.text.contains(m.title)&&t.text.contains(m==EvoHybridRenderer.Metric.MOTOR_RPM?"LIVE":"5 min"),"selected data "+theme+" "+m);
                if(m==EvoHybridRenderer.Metric.MOTOR_RPM)check(t.text.contains("4250")&&!t.text.contains("5 min"),"RPM number only "+theme);
            }
        }
        System.out.println("AdditionalHybridRendererTest PASS (five themes, drive/regen/offline)");
    }
}

package org.triprecorderng;
import static org.triprecorderng.SharedMotionController.Mode.*;
import static org.triprecorderng.SequentialGt3.Scale.*;
final class SequentialGt3Test {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){
        SequentialGt3 bank=new SequentialGt3();
        for(SequentialGt3.Scale scale:SequentialGt3.Scale.values()){
            int max=scale.standard, count=scale.bands;
            String prefix=scale==RPM?"R":scale==REGEN?"B":"P";
            check(!scale.valid(0)&&!scale.valid(-1)&&!scale.valid(scale.max+1)&&!scale.valid(Double.NaN),"validation");
            for(int i=1;i<=count;i++){
                double edge=(double)i*max/count;
                double fill=bank.sample(i,OFF,scale,edge-.0001,max);
                check(bank.label().equals(prefix+i)&&fill>.999,"below "+edge);
                check(bank.sample(i,OFF,scale,edge,max)>.999 && bank.label().equals(prefix+i),"exact");
                fill=bank.sample(i,OFF,scale,edge+.0001,max);
                check(i==count ? fill==1 : fill<.001&&bank.label().equals(prefix+(i+1)),"above");
                check(bank.color(false)!=bank.color(true),"day palette");
            }
            check(bank.sample(100,OFF,scale,0,max)==0 && bank.label().equals(prefix+1),"zero");
        }
        bank.reset();bank.sample(0,FULL,POWER,32,200);
        for(int t=0;t<1000;t+=100){
            bank.sample(t,FULL,POWER,t%200==0?34:32,200);check("P1".equals(bank.label()),"chatter");
        }
        bank.sample(1100,FULL,POWER,34,200);bank.sample(1410,FULL,POWER,34,200);
        check("P2".equals(bank.label()),"stable crossing");
        bank.sample(1450,FULL,POWER,184,200);check("P6".equals(bank.label()),"jump");
        bank.sample(1480,REDUCED,POWER,82,200);check("P3".equals(bank.label()),"down");
        bank.sample(1500,FULL,REGEN,48,100);check("B2".equals(bank.label()),"regen");
        check(bank.sample(1501,FULL,POWER,201,200)==1 && "P6".equals(bank.label()),"drive reacquired");
        check(Double.isNaN(bank.sample(1510,FULL,POWER,Double.NaN,200))&&bank.label()==null&&!bank.active(),"stale");
        EvoHybridRenderer.Data d=new EvoHybridRenderer.Data();d.power=75;d.soc=75;
        NismoHybridRenderer.BankMotion slot=new NismoHybridRenderer.BankMotion();slot.maxPower=300;slot.sequential=true;
        check(slot.sample(0,OFF,EvoHybridRenderer.GaugeMetric.POWER,d)==.5 && "P2".equals(slot.label()),"adaptive scale");
        check(slot.sample(1,FULL,EvoHybridRenderer.GaugeMetric.SOC,d)==.75&&slot.label()==null,"continuous metric");
        slot.sequential=false;
        check(slot.sample(2,OFF,EvoHybridRenderer.GaugeMetric.POWER,d)==.375&&slot.label()==null,"Dev 5 style");
        System.out.println("PASS sequential boundaries, hysteresis, reversals, limits, palette and style");
    }
}

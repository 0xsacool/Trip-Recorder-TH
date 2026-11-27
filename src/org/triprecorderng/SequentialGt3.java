package org.triprecorderng;

/** Per-instrument visual bands. The source reading is never modified. */
final class SequentialGt3 {
    enum Scale {
        POWER(200,1,5000,6,.006,"kW"), RPM(12000,100,100000,6,.003,"rpm"), REGEN(100,1,3000,3,.012,"kW");
        final int standard,min,max,bands;
        final double deadband;
        final String unit;
        Scale(int standard,int min,int max,int bands,double deadband,String unit){
            this.standard=standard;this.min=min;this.max=max;this.bands=bands;this.deadband=deadband;this.unit=unit;
        }
        boolean valid(double n){return Double.isFinite(n)&&n>=min&&n<=max;}
    }
    private static final int[] DRIVE={0xff22c55e,0xff84cc16,0xffeacc15,0xfff59e0b,0xfff97316,0xffef233c};
    private static final int[] DAY={0xff08742d,0xff476b08,0xff796300,0xff985500,0xffb34608,0xffbb132b};
    private static final int[] BRAKE={0xff67e8f9,0xff06b6d4,0xff2563eb};
    private static final int[] DAY_BRAKE={0xff007683,0xff006d9a,0xff1946b5};
    private static final String[][] LABELS={{"P1","P2","P3","P4","P5","P6"},{"R1","R2","R3","R4","R5","R6"},{"B1","B2","B3"}};
    private final SharedMotionController.Gauge drive=new SharedMotionController.Gauge(),brake=new SharedMotionController.Gauge();
    private Scale scale;
    private int stage=-1,pending=-1;
    private long pendingSince;
    private double maximum;
    private SharedMotionController.Gauge current;
    void reset(){stage=pending=-1;current=null;}
    String label(){return stage<0 ? null : LABELS[scale.ordinal()][stage];}
    int color(boolean day){
        if(stage<0)return 0;
        return (scale==Scale.REGEN ? (day?DAY_BRAKE:BRAKE) : (day?DAY:DRIVE))[stage];
    }
    boolean active(){return current!=null&&current.active();}
    double sample(long now,SharedMotionController.Mode mode,Scale next,double reading,double max){
        if(!Double.isFinite(reading)||reading<0||!next.valid(max)){reset();return Double.NaN;}
        double fraction=Math.min(1,reading/max);
        int index=Math.max(0,(int)Math.ceil(fraction*next.bands)-1);
        boolean fresh=stage<0||scale!=next||maximum!=max;
        if(!fresh && mode!=SharedMotionController.Mode.OFF && fraction>0 && fraction<1 && index!=stage){
            double edge=(index>stage ? stage+1 : stage)/(double)next.bands;
            if(Math.abs(fraction-edge)<=next.deadband){
                if(pending!=index){pending=index;pendingSince=now;}
                if(now-pendingSince<300)index=stage;
            }else pending=-1;
        }else pending=-1;
        current=next==Scale.REGEN ? brake : drive;
        double target=Math.max(0,Math.min(1,fraction*next.bands-index));
        if(fresh || stage!=index){
            double entry=fresh ? target : index>stage ? 0 : mode==SharedMotionController.Mode.FULL ? 1 : target;
            current.update(now,SharedMotionController.Mode.OFF,entry);
        }
        stage=index;scale=next;maximum=max;
        return current.update(now,fraction==1 ? SharedMotionController.Mode.OFF : mode,target);
    }
}

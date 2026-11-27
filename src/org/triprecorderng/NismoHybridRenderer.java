package org.triprecorderng;

/** Live instruments in 1200 x 470 coordinates registered to both GT3 material plates. */
final class NismoHybridRenderer {
    static final int WHITE = 0xfff5f5f6, MUTED = 0xffb6bbc0, RED = 0xffff3845;
    static final int CYAN = 0xff28d9ec, TRACK = 0xff303338;
    private static final int STEPS = 11;
    private static final String[] DIGITS = new String[1001];
    static {
        for (int i = 0; i < DIGITS.length; i++) DIGITS[i] = Integer.toString(i - 200);
    }

    static String whole(double value) {
        if (!EvoHybridRenderer.valid(value)) return "--";
        long n = Math.round(value);
        return n >= -200 && n <= 800 ? DIGITS[(int)n + 200] : Long.toString(n);
    }
    // Slot indices 0/1 are the two main instruments persisted by Build 51.
    private static final EvoHybridRenderer.GaugeMetric[] DEFAULTS = {
        EvoHybridRenderer.GaugeMetric.POWER, EvoHybridRenderer.GaugeMetric.RPM,
        EvoHybridRenderer.GaugeMetric.SOC, EvoHybridRenderer.GaugeMetric.RANGE
    };
    static EvoHybridRenderer.GaugeMetric selected(EvoHybridRenderer.Data d,int slot) {
        return d.gauges[slot] == EvoHybridRenderer.GaugeMetric.DEFAULT ? DEFAULTS[slot] : d.gauges[slot];
    }
    static double level(EvoHybridRenderer.GaugeMetric metric,EvoHybridRenderer.Data d) {
        double v = metric.read(d);
        if (!Double.isFinite(v)) return Double.NaN;
        if (metric == EvoHybridRenderer.GaugeMetric.POWER && v < 0)
            return Math.max(-1,v/-metric.min);
        return Math.max(0,Math.min(1,(v-Math.max(0,metric.min))/(metric.upper(d)-Math.max(0,metric.min))));
    }
    static final class BankMotion {
        private final SharedMotionController.Gauge gauge = new SharedMotionController.Gauge();
        private EvoHybridRenderer.GaugeMetric previous;
        private final SequentialGt3 bands=new SequentialGt3();
        boolean sequential;
        int maxPower=200,maxRpm=12000,maxRegen=100;
        String label(){return bands.label();}
        int color(boolean day){return bands.color(day);}
        double sample(long now,SharedMotionController.Mode mode,EvoHybridRenderer.GaugeMetric metric,EvoHybridRenderer.Data d) {
            if (previous != metric) {
                bands.reset();
                previous = metric;
                gauge.update(now,SharedMotionController.Mode.OFF,Double.NaN);
            }
            if(sequential && (metric==EvoHybridRenderer.GaugeMetric.POWER || metric==EvoHybridRenderer.GaugeMetric.RPM)){
                boolean rpm=metric==EvoHybridRenderer.GaugeMetric.RPM;
                double raw=metric.read(d);
                boolean regen=!rpm && raw<0;
                return bands.sample(now,mode,rpm ? SequentialGt3.Scale.RPM : regen ? SequentialGt3.Scale.REGEN : SequentialGt3.Scale.POWER,
                        regen ? -raw : raw,rpm ? maxRpm : regen ? maxRegen : maxPower);
            }
            bands.reset();
            return gauge.update(now,mode,level(metric,d));
        }
        boolean active() { return bands.active() || gauge.active(); }
    }
    static void bank(EvoHybridRenderer.Surface s, boolean right, double fraction, int ink) {
        for (int i=0;i<STEPS;i++) {
            float width=12+1.5f*i;
            float center=392.5f-4.55f*i;
            float x=(right ? 1200-center : center)-width/2;
            float shear=right ? 3.3f : -3.3f;
            float y=282-14*i;
            s.slant(x,y,width,10,shear,TRACK);
            int alpha=(int)Math.round(255*Math.max(0,Math.min(1,Math.abs(fraction)*STEPS-i)));
            if (alpha>0) s.slant(x,y,width,10,shear,(ink & 0xffffff) | alpha<<24);
        }
    }
    private static String label(EvoHybridRenderer.GaugeMetric m,EvoHybridRenderer.Data d) {
        switch (m) {
            case RANGE: return d.rangeLabel;
            case SOC: return d.batteryLabel;
            case CONSUMPTION: return d.avgCaption;
            case RPM: return d.thai ? "รอบมอเตอร์หน้า" : "FRONT MOTOR";
            case POWER: return d.thai ? "กำลังมอเตอร์" : "MOTOR POWER";
            case SPEED: return d.thai ? "ความเร็ว" : "SPEED";
            case TEMPERATURE: return d.thai ? "อุณหภูมิแบตฯ สูงสุด" : "BATTERY TEMP MAX";
            case VOLTAGE: return d.thai ? "แรงดันชาร์จ" : "CHARGING VOLTAGE";
            default: return m.title;
        }
    }
    private static String reading(EvoHybridRenderer.GaugeMetric m,EvoHybridRenderer.Data d) {
        return m == EvoHybridRenderer.GaugeMetric.CONSUMPTION || m == EvoHybridRenderer.GaugeMetric.RANGE
                ? m.reading(d) : whole(m.read(d));
    }
    private static void chevron(EvoHybridRenderer.Surface s,float x,float y) {
        s.line(x-4,y-2,x,y+2,MUTED,1.5f); s.line(x,y+2,x+4,y-2,MUTED,1.5f);
    }
    private static void instruments(EvoHybridRenderer.Surface s,EvoHybridRenderer.Data d,boolean right) {
        int primary=right ? 1 : 0, secondary=right ? 3 : 2;
        EvoHybridRenderer.GaugeMetric m=selected(d,primary), small=selected(d,secondary);
        int ink=m == EvoHybridRenderer.GaugeMetric.POWER ? (d.power<0 ? CYAN : RED) : (right ? CYAN : RED);
        int align=right ? 1 : -1, x=right ? 1110 : 88;
        s.gaugeBounds(primary,right ? 860 : 70,95,270,154);
        s.text(label(m,d),x,134,22,MUTED,true,true,align,206);
        chevron(s,right ? 882 : 317,126);
        String value=reading(m,d);
        s.readingWithUnit(value,"--".equals(value) ? "" : m.unit,x,211,78,24,align,235,WHITE,MUTED);
        if (m==EvoHybridRenderer.GaugeMetric.POWER && Double.isFinite(d.power))
            s.text(d.power<0 ? "REGEN" : d.power>0 ? "DRIVE" : "COAST",x,241,19,ink,true,true,align,221);
        String stage=right ? d.rightStage : d.leftStage;
        if(stage!=null){
            ink=right ? d.rightInk : d.leftInk;
            s.text(stage,right ? 890 : 306,240,18,ink,true,false,right ? -1 : 1,42);
        }
        bank(s,right,right ? d.rightLevel : d.leftLevel,ink);

        int left=right ? 880 : 72;
        s.gaugeBounds(secondary,left-6,252,262,54);
        s.text(label(small,d),left,286,16,MUTED,true,true,-1,98);
        value=reading(small,d);
        s.readingWithUnit(value,"--".equals(value) ? "" : small.unit,left+228,290,34,17,1,125,WHITE,MUTED);
        chevron(s,left+241,280);
    }
    private static void metric(EvoHybridRenderer.Surface s, String name, String value, int x, int y, int width) {
        s.text(name,x,y,13,MUTED,true,true,-1,width);
        s.text(value,x,y+27,28,WHITE,true,true,-1,width);
    }

    static void draw(EvoHybridRenderer.Surface s, EvoHybridRenderer.Data d) {
        s.rect(0,0,1200,470,0xff08090b);
        s.plate(0,0,1200,470);
        s.nismoLogo();
        s.text("GT3",230,58,21,MUTED,true,true,-1,50);
        s.text(d.results ? "TRIP RESULTS" : d.charging ? "CHARGING" : "EV TripLog",
                301,58,23,d.charging ? CYAN : WHITE,true,true,-1,230);
        s.text(d.avgCaption,525,42,12,MUTED,true,true,-1,220);
        s.text(d.average,525,64,20,WHITE,true,true,-1,225);
        s.text(d.source,1114,57,15,MUTED,false,false,1,342);
        if (d.startupAccent > 0)
            s.line(301,69,301+192*d.startupAccent,69,RED,2);
        if (d.results) {
            for(int row=0;row<2;row++) for(int col=0;col<6;col++)
                if((row+col)%2==0) s.slant(465+col*5,44+row*5,5,5,1,WHITE);
        }

        instruments(s,d,false);
        instruments(s,d,true);

        s.text("SPEED",600,143,20,MUTED,true,true,0,190);
        s.text(whole(d.speed),600,239,119,WHITE,true,true,0,294);
        s.text("km/h",600,263,22,MUTED,true,true,0,110);
        if (d.recording) {
            s.circle(514,295,4.5f,RED);
            s.text(d.status,604,301,17,WHITE,true,true,0,161);
        } else {
            if(d.charging && d.chargePhase>=0) s.circle(514,295,3+1.5f*(1-Math.abs(2*d.chargePhase-1)),CYAN);
            s.text(d.status,600,301,17,d.statusColor,true,true,0,184);
        }

        s.line(62,386,748,386,0xff484c50,1);
        s.line(271,341,258,380,0xff484c50,1);
        s.line(494,341,481,380,0xff484c50,1);
        // Small instrument legends mirror the two physical telemetry rows.
        s.line(72,373,79,347,MUTED,2); s.line(93,373,86,347,MUTED,2);
        s.line(83,351,83,357,MUTED,2); s.line(83,362,83,369,MUTED,2);
        s.arc(295,361,11,0,360,2,MUTED); s.line(295,353,295,361,MUTED,2);
        s.line(295,361,301,364,MUTED,2);
        s.line(528,347,516,362,RED,3); s.line(516,362,526,362,RED,3); s.line(526,362,517,375,RED,3);
        metric(s,d.tripCaption,d.distance,107,351,146);
        metric(s,d.timeCaption,d.elapsed,318,351,155);
        metric(s,d.energyCaption,d.energy,543,351,198);
        s.outline(79,400,7,20,3,RED,1.6f); s.circle(82.5f,423,5,RED);
        s.outline(285,404,18,22,2,CYAN,1.6f); s.rect(291,400,6,2,CYAN);
        s.line(513,416,518,416,CYAN,2); s.line(518,416,522,405,CYAN,2);
        s.line(522,405,527,424,CYAN,2); s.line(527,424,531,416,CYAN,2);
        metric(s,"BATTERY MIN / MAX",d.temp,107,407,150);
        metric(s,"CELL MIN / MAX",d.cells,318,407,181);
        metric(s,"CV",d.voltage,543,407,198);
        EvoHybridRenderer.history(s,d,790,337,359,101);
        if (d.pressedSlot >= 0 || d.selectionAccent > 0) {
            int slot = d.pressedSlot;
            if (slot >= 0 && slot < 4) {
                float x = slot%2 == 0 ? 98 : 891, y=slot<2 ? 249 : 301;
                s.line(x,y,x+198,y,slot%2 == 0 ? RED : CYAN,2);
            } else s.line(804,435,1137,435,RED,1 + 2*d.selectionAccent);
        }
    }
}

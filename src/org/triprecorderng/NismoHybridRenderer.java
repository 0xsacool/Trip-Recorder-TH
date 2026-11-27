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
    private static void bank(EvoHybridRenderer.Surface s, boolean right, double fraction, int ink,
                             float phase, float startup, float acceleration) {
        for (int i = 0; i < STEPS; i++) {
            float y = 282 - 14 * i, x = 383 - 4.55f * i;
            if (right) x = 1200 - x - 19;
            float fill = (float)Math.max(0, Math.min(1, fraction * STEPS - i));
            float shear = right ? 3.3f : -3.3f;
            s.slant(x, y, 19, 10, shear, TRACK);
            if (fill > 0) s.slant(x, y, 19 * fill, 10, shear, ink);
            if (acceleration > 0 && fill > 0)
                s.line(x+shear,y,x+shear+19*fill,y,WHITE,acceleration*1.6f);
            if ((phase >= 0 && i == (int)(phase * STEPS) && fill > 0) ||
                    (startup > 0 && i == Math.round(startup * (STEPS - 1))))
                s.line(x, y + 10, x + (phase >= 0 ? 19 * fill : 19), y + 10, MUTED, 1);
        }
    }
    private static void metric(EvoHybridRenderer.Surface s, String name, String value, int x, int y, int width) {
        s.text(name,x,y,13,MUTED,true,true,-1,width);
        s.text(value,x,y+25,24,WHITE,true,true,-1,width);
    }

    static void draw(EvoHybridRenderer.Surface s, EvoHybridRenderer.Data d) {
        s.rect(0,0,1200,470,0xff08090b);
        s.plate(0,0,1200,470);
        s.text("NIS",87,59,32,WHITE,true,true,-1,61);
        s.text("MO",140,59,32,RED,true,true,-1,60);
        s.text("GT3",210,58,22,MUTED,true,true,-1,58);
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

        int powerColor = d.power < 0 ? CYAN : RED;
        s.gaugeBounds(0,78,99,250,153);
        s.gaugeBounds(1,872,99,253,153);
        if (EvoHybridRenderer.custom(d,0)) EvoHybridRenderer.customDigital(s,d,0,78,99,250,149,powerColor,true);
        else {
            s.text("MOTOR POWER",98,132,21,MUTED,true,true,-1,216);
            s.readingWithUnit(whole(d.power),"kW",98,203,71,25,-1,218,WHITE,MUTED);
            String direction = !EvoHybridRenderer.valid(d.power) ? "--" :
                    d.power < 0 ? "REGEN" : d.power > 0 ? "DRIVE" : "COAST";
            s.text(direction,100,234,20,powerColor,true,true,-1,180);
        }
        metric(s,"FRONT MOTOR · rpm",whole(d.liveRpm),84,275,235);
        if (EvoHybridRenderer.custom(d,1)) EvoHybridRenderer.customDigital(s,d,1,872,99,253,149,CYAN,true);
        else {
            s.text(d.batteryLabel,1111,132,21,MUTED,true,true,1,225);
            s.readingWithUnit(whole(d.soc),"%",1109,206,72,28,1,218,WHITE,MUTED);
        }
        s.text(d.rangeLabel,1110,275,14,MUTED,true,true,1,210);
        s.text(d.range,1110,301,26,WHITE,true,true,1,210);
        bank(s,false,Math.abs(d.nismoPower)/(d.power < 0 ? 100 : 200),powerColor,-1,d.startupAccent,d.powerAccent);
        bank(s,true,d.nismoSoc/100,CYAN,d.chargePhase,d.startupAccent,0);

        s.text("SPEED",600,143,20,MUTED,true,true,0,190);
        s.text(whole(d.speed),600,238,110,WHITE,true,true,0,290);
        s.text("km/h",600,263,22,MUTED,true,true,0,110);
        if (d.recording) {
            s.circle(514,295,4.5f,RED);
            s.text(d.status,604,301,17,WHITE,true,true,0,161);
        } else s.text(d.status,600,301,17,d.statusColor,true,true,0,184);

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
            if (slot == 0 || slot == 1) {
                float x = slot == 0 ? 98 : 891;
                s.line(x,250,x+198,250,slot == 0 ? powerColor : CYAN,2);
            } else s.line(804,435,1137,435,RED,1 + 2*d.selectionAccent);
        }
    }
}

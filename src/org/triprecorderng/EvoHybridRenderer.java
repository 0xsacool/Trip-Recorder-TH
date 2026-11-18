package org.triprecorderng;

/** UI-only renderer. The physical housing is immutable; all instrument data is drawn. */
final class EvoHybridRenderer {
    static final float WIDTH = 1200, HEIGHT = 470;
    static final int BG = 0xff080808, WHITE = 0xfff3f2f3, RED = 0xffff4148,
        BLUE = 0xff35aafb, GRAY = 0xffb3b3b3, EDGE = 0xff5b5b5e;
    static final float PLATE_W = 975, PLATE_H = PLATE_W * 826 / 1903;
    static final float PLATE_SCALE = PLATE_W / 1903;
    static final float START = 140, SWEEP = 260;
    interface Surface {
        void plate(float x, float y, float w, float h);
        void rect(float x,float y,float w,float h,int color);
        void outline(float x,float y,float w,float h,float radius,int color,float thickness);
        void line(float x1,float y1,float x2,float y2,int color,float thickness);
        void circle(float cx,float cy,float radius,int color);
        // align: -1 left, 0 center, +1 right
        void text(String value,float x,float baseline,float size,int color,boolean heavy,boolean slant,int align,float maxWidth);
    }
    static final class Data {
        double soc, speed, power;
        String range, source, status, tripLabel, temp, cells, voltage;
        String distance, elapsed, energy, average, money;
        String batteryLabel, rangeLabel, tripCaption, timeCaption, energyCaption, avgCaption, costCaption;
        int statusColor;
    }
    static boolean valid(double v) { return !Double.isNaN(v) && !Double.isInfinite(v); }
    static double angle(double value, double min, double max) {
        if (!valid(value)) return Double.NaN;
        return START + SWEEP * Math.max(0,Math.min(1,(value-min)/(max-min)));
    }
    private static float px(float cx,float r,double angle) {
        return cx + r*(float)Math.cos(Math.toRadians(angle));
    }
    private static float py(float cy,float r,double angle) {
        return cy + r*(float)Math.sin(Math.toRadians(angle));
    }
    private static void mark(Surface s,float cx,float cy,float r,double a,float length,int color,float weight) {
        s.line(px(cx,r-length,a),py(cy,r-length,a),px(cx,r,a),py(cy,r,a),color,weight);
    }
    private static String value(double n,String units) {
        return valid(n) ? Math.round(n) + units : "--" + units;
    }
    private static void dial(Surface s,float cx,float cy,float r,double min,double max,int divisions,int subdivisions,double measured,boolean power) {
        for(int i=0;i<=divisions*subdivisions;i++){
            double v=min+(max-min)*i/(divisions*subdivisions);
            double a=angle(v,min,max);
            boolean major=i%subdivisions==0;
            int color=power&&v<0?BLUE:RED;
            mark(s,cx,cy,r*.94f,a,r*(major?.13f:.065f),color,major?3.2f:1.25f);
            if(major) s.text(""+Math.round(v),px(cx,r*.70f,a),py(cy,r*.70f,a)+r*.045f,
                r*(power?.107f:.112f),WHITE,true,true,0,r*.42f);
        }
        double a=angle(measured,min,max);
        if(valid(a)){
            s.line(px(cx,r*.12f,a+180),py(cy,r*.12f,a+180),px(cx,r*.81f,a),py(cy,r*.81f,a),0xff53151b,7);
            s.line(px(cx,r*.12f,a+180),py(cy,r*.12f,a+180),px(cx,r*.81f,a),py(cy,r*.81f,a),RED,3.5f);
            s.line(cx,cy,px(cx,r*.78f,a),py(cy,r*.78f,a),0xffffb3b1,1.2f);
        }
        s.circle(cx,cy,r*.105f,0xff525256);
        s.circle(cx,cy,r*.085f,0xff202024);
        s.circle(cx-r*.026f,cy,r*.012f,0xff080808);
        s.circle(cx+r*.026f,cy,r*.012f,0xff080808);
        float w=r*1.03f,h=r*.22f, y=cy+r*.57f;
        s.text(power?"POWER / REGEN":"SPEED",cx,y-7,r*.085f,RED,true,true,0,w);
        s.outline(cx-w/2,y,w,h,5,RED,1.3f);
        s.text(value(measured,power?" kW":" km/h"),cx,y+h*.76f,r*.18f,WHITE,true,true,0,w-10);
    }
    private static void telemetry(Surface s,String label,String val,float y) {
        s.text(label,987,y,15,RED,true,true,-1,199);
        s.text(val,987,y+32,26,WHITE,true,false,-1,199);
    }
    static void draw(Surface s,Data d) {
        s.rect(0,0,WIDTH,HEIGHT,BG);
        s.plate(0,0,PLATE_W,PLATE_H);
        for(int i=0;i<3;i++) s.line(19+i*12,27,32+i*12,5,0xffde2732,7);
        s.text("JDM EVO IX",67,28,32,WHITE,true,true,-1,275);
        s.text("CT9A INSPIRED • EV EDITION",20,48,14,GRAY,true,true,-1,325);
        s.text(d.source,20,64,10,GRAY,false,false,-1,321);
        s.line(20,72,321,72,RED,1.5f);
        s.text(d.status,1185,25,19,d.statusColor,true,false,1,300);
        s.text(d.tripLabel,1185,44,12,GRAY,false,false,1,210);
        s.line(810,52,1185,52,RED,1.2f);
        float lx=331*PLATE_SCALE,ly=486*PLATE_SCALE,lr=229*PLATE_SCALE;
        for(int i=0;i<=12;i++) mark(s,lx,ly,lr*.94f,150+i*20,lr*.08f,RED,2.3f);
        // SOC icon: only the actual filled segments are colored.
        s.outline(lx-28,ly-78,56,22,3,RED,2);
        s.rect(lx+28,ly-72,4,10,RED);
        for(int i=0;i<5;i++) {
            s.rect(lx-24+i*10,ly-74,8,14,0xff353537);
            if(valid(d.soc)) s.rect(lx-24+i*10,ly-74,8*Math.max(0,Math.min(1,(float)d.soc/20-i)),14,RED);
        }
        s.text(d.batteryLabel,lx,ly-35,16,RED,true,true,0,lr*1.5f);
        s.text(value(d.soc,"%"),lx,ly+2,35,WHITE,true,true,0,lr*1.45f);
        s.line(lx-lr*.68f,ly+15,lx+lr*.68f,ly+15,RED,1.2f);
        s.text(d.rangeLabel,lx,ly+40,15,RED,true,true,0,lr*1.5f);
        s.text(d.range,lx,ly+74,30,WHITE,true,true,0,lr*1.45f);
        dial(s,934*PLATE_SCALE,421*PLATE_SCALE,337*PLATE_SCALE,-100,200,6,5,d.power,true);
        dial(s,1553*PLATE_SCALE,480*PLATE_SCALE,249*PLATE_SCALE,0,260,13,4,d.speed,false);
        s.line(970,100,970,390,EDGE,1);
        telemetry(s,"BATTERY TEMP",d.temp,136);
        s.line(987,188,1185,188,EDGE,1);
        telemetry(s,"CELL VOLTAGE",d.cells,222);
        s.line(987,274,1185,274,EDGE,1);
        telemetry(s,"CHARGING VOLTAGE",d.voltage,308);
        s.outline(9,420,1182,46,5,EDGE,1.25f);
        String[] heads={d.tripCaption,d.timeCaption,d.energyCaption,d.avgCaption,d.costCaption};
        String[] vals={d.distance,d.elapsed,d.energy,d.average,d.money};
        for(int i=0;i<5;i++){
            float x=9+i*236.4f;
            if(i!=0)s.line(x,424,x,462,EDGE,1);
            s.text(heads[i],x+118.2f,436,12,GRAY,true,false,0,222);
            s.text(vals[i],x+118.2f,459,24,WHITE,true,false,0,222);
        }
    }
}

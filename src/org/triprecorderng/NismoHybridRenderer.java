package org.triprecorderng;

/** Flat racing HUD. Motion affects visual power only, never measured speed. */
final class NismoHybridRenderer {
    static final int BG=0xff08090b, PANEL=0xff101113, WHITE=0xfff5f5f6,
        MUTED=0xffb6bbc0, RED=0xffff3845, CYAN=0xff28d9ec, EDGE=0xff484c50, TRACK=0xff303337;
    static boolean valid(double n){return EvoHybridRenderer.valid(n);}
    static String whole(double n){return valid(n)?Long.toString(Math.round(n)):"--";}
    static float fraction(double n,double max){return valid(n)?(float)Math.max(0,Math.min(1,n/max)):0;}
    static void metric(EvoHybridRenderer.Surface s,String label,String value,float x,float y,float w){
        s.text(label,x+12,y+17,13,MUTED,true,false,-1,w-24);
        s.text(value,x+12,y+44,25,WHITE,true,false,-1,w-24);
    }
    static void draw(EvoHybridRenderer.Surface s,EvoHybridRenderer.Data d,double avg,double[] samples,int count){
        s.rect(0,0,1200,470,BG);
        s.text("NISMO",24,32,28,WHITE,true,true,-1,130);
        s.text("GT3",150,32,28,RED,true,true,-1,90);
        s.text(d.source,600,28,13,MUTED,false,false,0,350);
        s.text(d.status,1176,28,17,d.statusColor,true,false,1,270);
        s.line(24,45,1176,45,EDGE,1);
        s.rect(24,62,300,264,PANEL);
        s.rect(876,62,300,264,PANEL);
        s.gaugeBounds(0,24,62,300,125);
        s.gaugeBounds(1,876,62,300,125);
        double visual=valid(d.power)?(valid(d.nismoPower)?d.nismoPower:d.power):Double.NaN;
        int accent=valid(visual)&&visual<0?CYAN:RED;
        if(EvoHybridRenderer.custom(d,0))EvoHybridRenderer.customDigital(s,d,0,24,62,300,125,accent,true);
        else {
            s.text("MOTOR POWER",40,88,16,MUTED,true,true,-1,268);
            s.text(whole(d.power),40,153,65,WHITE,true,true,-1,210);
            s.text("kW",308,151,22,MUTED,true,false,1,55);
            s.text(valid(d.power)?(d.power<0?"REGEN":d.power>0?"DRIVE":"COASTING"):"--",40,179,16,accent,true,true,-1,260);
        }
        s.line(40,194,308,194,EDGE,1);
        metric(s,d.tripCaption,d.distance,28,201,290);
        metric(s,d.avgCaption,d.average,28,260,290);
        if(EvoHybridRenderer.custom(d,1))EvoHybridRenderer.customDigital(s,d,1,876,62,300,125,CYAN,true);
        else {
            s.text(d.batteryLabel,892,88,16,MUTED,true,true,-1,268);
            s.text(whole(d.soc)+"%",892,151,58,WHITE,true,true,-1,268);
            s.rect(892,163,268,12,TRACK);
            s.rect(892,163,268*fraction(d.soc,100),12,CYAN);
        }
        s.line(892,194,1160,194,EDGE,1);
        metric(s,d.rangeLabel,d.range,880,201,290);
        metric(s,d.timeCaption,d.elapsed,880,260,290);
        float cx=600,cy=190;
        float amount=fraction(Math.abs(visual),visual<0?100:200);
        for(int i=0;i<48;i++){
            float angle=160+i*220f/47;
            double a=Math.toRadians(angle);
            float co=(float)Math.cos(a),si=(float)Math.sin(a);
            float f=visual<0?(48-i)/48f:(i+1)/48f;
            s.line(cx+151*co,cy+151*si,cx+169*co,cy+169*si,f<=amount?accent:TRACK,6);
        }
        if(d.startupAccent>0)s.arc(cx,cy,178,160,220*d.startupAccent,2,RED);
        s.text(whole(d.speed),cx,213,112,WHITE,true,true,0,340);
        s.text("km/h",cx,242,22,MUTED,true,false,0,180);
        s.text(d.tripLabel,cx,286,14,MUTED,false,false,0,380);
        s.line(24,339,1176,339,EDGE,1);
        metric(s,"BATTERY TEMP MIN / MAX",d.temp,24,350,240);
        metric(s,"CELL VOLTAGE MIN / MAX",d.cells,264,350,260);
        metric(s,"CHARGING VOLTAGE",d.voltage,524,350,220);
        metric(s,d.energyCaption,d.energy,24,408,240);
        s.line(754,350,754,454,EDGE,1);
        EvoHybridRenderer.history(s,d,samples,count,766,350,410,104);
    }
}

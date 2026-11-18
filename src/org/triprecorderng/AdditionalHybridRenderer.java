package org.triprecorderng;

/** Remaining five cockpit themes. Immutable bitmap plates; all readings are live vector overlays. */
final class AdditionalHybridRenderer {
    static final String GR="gr_motorsport", N="n_performance", RS="rs_digital", TAYCAN="taycan_sport", XTREME="yangwang_u9_xtreme";
    static final int WHITE=0xfff5f7fa, MUTE=0xffb9c1cc, DIM=0xff30363d, EDGE=0xff4e5661, BG=0xff08090b;
    static final int RED=0xfff1293b, BLUE=0xff40aaff, CYAN=0xff46dcea, ORANGE=0xffff821d, GREEN=0xff66e57b;
    static String asset(String theme){
        switch(theme){case GR:return "gr_hybrid_plate.webp";case N:return "n_hybrid_plate.webp";case RS:return "rs_hybrid_plate.webp";case TAYCAN:return "taycan_hybrid_plate.webp";case XTREME:return "xtreme_hybrid_plate.webp";default:return null;}
    }
    static String name(String theme){switch(theme){case GR:return "GR MOTORSPORT EDITION";case N:return "N PERFORMANCE";case RS:return "RS DIGITAL COCKPIT";case TAYCAN:return "TAYCAN SPORT";case XTREME:return "BYD XTREME EDITION";default:return "";}}
    static double fraction(double v,double max){return EvoHybridRenderer.valid(v)?Math.max(0,Math.min(1,v/max)):0;}
    static String whole(double v){return EvoHybridRenderer.valid(v)?Long.toString(Math.round(v)):"--";}
    static String power(double v){return whole(v)+" kW";}
    private static boolean regen(double v){return EvoHybridRenderer.valid(v)&&v<0;}
    private static int batteryColor(double soc,int accent){return EvoHybridRenderer.valid(soc)&&soc<15?RED:EvoHybridRenderer.valid(soc)&&soc<30?ORANGE:accent;}
    private static void text(EvoHybridRenderer.Surface s,String v,float x,float y,float size,int color,boolean heavy,int align,float width){s.text(v,x,y,size,color,heavy,false,align,width);}
    private static void sport(EvoHybridRenderer.Surface s,String v,float x,float y,float size,int col,int align,float width){s.text(v,x,y,size,col,true,true,align,width);}
    private static void label(EvoHybridRenderer.Surface s,String v,float x,float y,float width,int align){text(s,v,x,y,15,MUTE,true,align,width);}
    private static void header(EvoHybridRenderer.Surface s,EvoHybridRenderer.Data d,String t,int accent){
        sport(s,name(t),24,29,27,WHITE,-1,510);
        text(s,d.source,24,t.equals(GR)||t.equals(XTREME)?56:49,13,MUTE,false,-1,490);
        sport(s,d.status,1172,t.equals(XTREME)?17:27,t.equals(XTREME)?16:20,d.statusColor,1,315);
        text(s,d.tripLabel,1172,t.equals(XTREME)?39:t.equals(GR)?56:49,13,MUTE,false,1,278);
    }
    private static void batt(EvoHybridRenderer.Surface s,double v,float x,float y,float w,float h,int accent){
        s.outline(x,y,w,h,4,EDGE,1.5f);s.rect(x+w+2,y+h*.28f,4,h*.44f,EDGE);
        if(EvoHybridRenderer.valid(v))s.rect(x+3,y+3,(w-6)*(float)fraction(v,100),h-6,batteryColor(v,accent));
    }
    private static void bar(EvoHybridRenderer.Surface s,double v,float x,float y,float w,float h,int accent,int n){
        double p=fraction(Math.abs(v),regen(v)?100:200)*n;float step=w/n;
        for(int i=0;i<n;i++){
            s.slant(x+i*step,y,step-4,h,6,DIM);
            float f=(float)Math.max(0,Math.min(1,p-i));
            if(f>0)s.slant(x+i*step,y,(step-4)*f,h,6,regen(v)?CYAN:accent);
        }
    }
    private static void metric(EvoHybridRenderer.Surface s,String label,String value,float x,float y,float width,float size,boolean italic,int align){
        float px=align<0?x+8:align>0?x+width-8:x+width/2;
        text(s,label,px,y,13,MUTE,true,align,width-16);
        if(italic)sport(s,value,px,y+size+5,size,WHITE,align,width-16);else text(s,value,px,y+size+5,size,WHITE,true,align,width-16);
    }
    private static void trips(EvoHybridRenderer.Surface s,EvoHybridRenderer.Data d,float y,int accent,boolean italic){
        s.rect(0,y,1200,470-y,0xff111316);s.line(12,y,1188,y,accent,1.6f);
        String[] labels={d.tripCaption,d.timeCaption,d.energyCaption,d.avgCaption};String[] vals={d.distance,d.elapsed,d.energy,d.average};
        float[] bounds={12,200,388,578,828};
        for(int i=0;i<4;i++){if(i>0)s.line(bounds[i],y+9,bounds[i],464,EDGE,1);metric(s,labels[i],vals[i],bounds[i],y+Math.min(20,(470-y)*.3f),bounds[i+1]-bounds[i],Math.min(31,(470-y)*.44f),italic,0);}
        s.line(828,y+5,828,464,EDGE,1);
        EvoHybridRenderer.history(s,d,838,y+4,350,462-y);
    }
    private static void diagnostics(EvoHybridRenderer.Surface s,EvoHybridRenderer.Data d,float x,float y,float w,float gap,boolean italic){
        String[] labels={"BATTERY TEMP MIN / MAX","CELL VOLTAGE MIN / MAX","CHARGING VOLTAGE"};String[] vals={d.temp,d.cells,d.voltage};
        for(int i=0;i<3;i++){if(i>0)s.line(x+8,y+i*gap-15,x+w-8,y+i*gap-15,EDGE,1);metric(s,labels[i],vals[i],x,y+i*gap,w,Math.min(27,gap-29),italic,-1);}
    }
    private static float atX(float cx,float r,double a){return cx+r*(float)Math.cos(Math.toRadians(a));}
    private static float atY(float cy,float r,double a){return cy+r*(float)Math.sin(Math.toRadians(a));}
    private static void gr(EvoHybridRenderer.Surface s,EvoHybridRenderer.Data d){
        header(s,d,GR,RED);
        float cx=593,cy=357,r=255;
        s.gaugeBounds(0,330,80,526,143);
        if(EvoHybridRenderer.custom(d,0)){
            EvoHybridRenderer.GaugeMetric m=d.gauges[0];double v=m.read(d),hi=m.upper(d);
            s.arc(cx,cy,r,180,180,22,DIM);
            if(EvoHybridRenderer.valid(v)){
                double zero=m.min<0?-m.min/(hi-m.min):0,f=Math.max(0,Math.min(1,(v-m.min)/(hi-m.min)));
                s.arc(cx,cy,r,(float)(180+180*Math.min(f,zero)),(float)(180*Math.abs(f-zero)),22,v<0?BLUE:RED);
            }
            sport(s,whole(m.min),cx-r,378,16,WHITE,0,85);sport(s,whole(hi),cx+r,378,16,WHITE,0,85);
            EvoHybridRenderer.customDigital(s,d,0,cx-105,143,210,77,RED,true);
        }else{
        for(int i=0;i<10;i++){
            float a=271+i*9,b=180+i*9+1;
            s.arc(cx,cy,r,a,7,22,0xff4b2026);s.arc(cx,cy,r,b,7,22,0xff203c50);
            float dp=(float)Math.max(0,Math.min(1,fraction(d.power,200)*10-i));
            float rp=(float)Math.max(0,Math.min(1,fraction(-d.power,100)*10-(9-i)));
            if(dp>0)s.arc(cx,cy,r,a,7*dp,22,RED);
            if(rp>0)s.arc(cx,cy,r,b+7*(1-rp),7*rp,22,BLUE);
        }
        if(EvoHybridRenderer.valid(d.power)){
            double a=270+(regen(d.power)?-fraction(-d.power,100):fraction(d.power,200))*90;
            s.line(atX(cx,r-17,a),atY(cy,r-17,a),atX(cx,r+17,a),atY(cy,r+17,a),WHITE,3);
        }
        sport(s,"0",cx,89,17,WHITE,0,50);sport(s,"−100",cx-r,378,16,BLUE,0,85);sport(s,"+200",cx+r,378,16,RED,0,85);
        sport(s,"REGEN",cx-r+58,377,12,BLUE,0,80);sport(s,"DRIVE",cx+r-54,377,12,RED,0,80);
        label(s,"POWER",cx,164,170,0);sport(s,power(d.power),cx,211,47,WHITE,0,210);
        }
        s.line(cx-100,226,cx+100,226,EDGE,1.5f);sport(s,whole(d.speed),cx,324,105,WHITE,0,260);label(s,"km/h",cx,346,130,0);
        s.gaugeBounds(1,35,95,245,134);
        if(EvoHybridRenderer.custom(d,1))EvoHybridRenderer.customDigital(s,d,1,35,95,245,134,RED,true);
        else{label(s,d.batteryLabel,40,121,260,-1);sport(s,whole(d.soc)+"%",40,191,62,WHITE,-1,250);batt(s,d.soc,42,207,225,17,GREEN);}
        s.line(42,242,271,242,EDGE,1.3f);label(s,d.rangeLabel,42,270,240,-1);sport(s,d.range,42,323,45,WHITE,-1,240);
        diagnostics(s,d,910,105,260,82,true);trips(s,d,393,RED,true);
    }
    private static void n(EvoHybridRenderer.Surface s,EvoHybridRenderer.Data d){
        header(s,d,N,BLUE);
        label(s,"SPEED",164,128,260,0);sport(s,whole(d.speed),164,279,144,WHITE,0,280);label(s,"km/h",164,306,180,0);
        s.line(64,333,264,333,BLUE,1.5f);sport(s,regen(d.power)?"REGEN":d.power>0?"DRIVE":"POWER",164,379,26,regen(d.power)?CYAN:BLUE,0,230);
        s.gaugeBounds(0,400,94,165,78);
        if(EvoHybridRenderer.custom(d,0))EvoHybridRenderer.customDigital(s,d,0,400,94,165,78,BLUE,true);
        else{metric(s,d.batteryLabel,whole(d.soc)+"%",405,104,165,39,true,-1);batt(s,d.soc,412,157,137,10,BLUE);}
        metric(s,d.rangeLabel,d.range,573,104,189,32,true,-1);
        s.line(394,178,765,178,EDGE,1);s.gaugeBounds(1,380,185,380,95);
        if(EvoHybridRenderer.custom(d,1))EvoHybridRenderer.customBar(s,d,1,380,190,370,85,BLUE);
        else{label(s,regen(d.power)?"REGEN":"LIVE POWER",390,202,170,-1);sport(s,power(d.power),750,229,29,WHITE,1,176);
        bar(s,d.power,380,242,351,17,BLUE,12);text(s,regen(d.power)?"0–100 kW":"0–200 kW",380,277,12,MUTE,false,-1,190);}
        diagnostics(s,d,351,295,354,49,false);
        String[] heads={d.tripCaption,d.timeCaption,d.energyCaption,d.avgCaption};String[] vals={d.distance,d.elapsed,d.energy,d.average};
        for(int i=0;i<4;i++){float y=99+i*64;float x=839-i*18;metric(s,heads[i],vals[i],x,y,341,30,false,1);s.line(x+10,y+48,x+330,y+48,EDGE,1);}
        EvoHybridRenderer.history(s,d,778,348,315,75);
    }
    private static void rs(EvoHybridRenderer.Surface s,EvoHybridRenderer.Data d){
        header(s,d,RS,RED);
        s.gaugeBounds(0,30,105,280,112);
        if(EvoHybridRenderer.custom(d,0))EvoHybridRenderer.customDigital(s,d,0,30,105,280,112,RED,false);
        else{metric(s,d.batteryLabel,whole(d.soc)+"%",30,122,280,54,false,-1);batt(s,d.soc,40,192,235,13,GREEN);}
        metric(s,d.rangeLabel,d.range,30,258,270,39,false,-1);
        label(s,"SPEED",600,143,185,0);text(s,whole(d.speed),600,257,107,WHITE,true,0,290);label(s,"km/h",600,282,120,0);
        s.line(520,300,680,300,EDGE,1.5f);s.gaugeBounds(1,510,305,180,70);
        if(EvoHybridRenderer.custom(d,1))EvoHybridRenderer.customDigital(s,d,1,510,305,180,70,RED,false);
        else{label(s,regen(d.power)?"REGEN":"LIVE POWER",600,323,180,0);text(s,power(d.power),600,355,30,regen(d.power)?CYAN:WHITE,true,0,180);}
        diagnostics(s,d,915,118,262,79,false);trips(s,d,400,RED,false);
    }
    private static void curve(EvoHybridRenderer.Surface s,float cx,float cy,float r,double val,double max,String caption,String number,String unit,int color,float size){
        s.arc(cx,cy,r,135,270,7,DIM);float sweep=(float)(270*fraction(val,max));if(sweep>0)s.arc(cx,cy,r,135,sweep,7,color);
        for(int i=0;i<=16;i++){double a=135+270.0*i/16;float outer=r-10,inner=outer-(i%4==0?10:5);s.line(atX(cx,inner,a),atY(cy,inner,a),atX(cx,outer,a),atY(cy,outer,a),i%4==0?WHITE:EDGE,i%4==0?1.6f:1);}
        label(s,caption,cx,cy-24,r*1.5f,0);text(s,number,cx,cy+size*.43f,size,WHITE,true,0,r*1.55f);label(s,unit,cx,cy+size*.43f+19,r*1.6f,0);
    }
    private static void taycan(EvoHybridRenderer.Surface s,EvoHybridRenderer.Data d,double avg){
        header(s,d,TAYCAN,CYAN);
        String av=d.average==null?"--":d.average.replace(" kWh/100 km","");
        s.gaugeBounds(0,103,119,186,186);
        if(EvoHybridRenderer.custom(d,0))EvoHybridRenderer.customDial(s,d,0,196,212,93,CYAN,false);
        else curve(s,196,212,93,d.soc,100,d.batteryLabel,whole(d.soc),"%",batteryColor(d.soc,CYAN),59);
        curve(s,479,204,125,d.speed,200,"SPEED",whole(d.speed),"km/h",CYAN,83);
        s.gaugeBounds(1,668,119,186,186);
        if(EvoHybridRenderer.custom(d,1))EvoHybridRenderer.customDial(s,d,1,761,212,93,CYAN,false);
        else curve(s,761,212,93,avg,40,d.avgCaption,av,"kWh/100 km",CYAN,45);
        s.rect(12,384,1176,83,0xff13191e);s.outline(12,384,1176,83,8,EDGE,1);
        String[] diag={d.rangeLabel,regen(d.power)?"REGEN":"LIVE POWER","BATTERY TEMP MIN / MAX","CELL VOLTAGE MIN / MAX","CHARGING VOLTAGE"};
        String[] readings={d.range,power(d.power),d.temp,d.cells,d.voltage};
        float[] bounds={12,231,447,686,978,1188};
        for(int i=0;i<5;i++){
            if(i>0)s.line(bounds[i],397,bounds[i],456,EDGE,1);
            metric(s,diag[i],readings[i],bounds[i],405,bounds[i+1]-bounds[i],25,false,0);
        }
        String[] labels={d.tripCaption,d.timeCaption,d.energyCaption};String[] vals={d.distance,d.elapsed,d.energy};
        for(int i=0;i<3;i++){float y=103+i*65;metric(s,labels[i],vals[i],946,y,239,30,false,1);s.line(955,y+50,1184,y+50,EDGE,1);}
        EvoHybridRenderer.history(s,d,946,291,239,65);
    }
    private static void xtreme(EvoHybridRenderer.Surface s,EvoHybridRenderer.Data d){
        header(s,d,XTREME,ORANGE);
        s.gaugeBounds(0,35,100,245,135);
        if(EvoHybridRenderer.custom(d,0))EvoHybridRenderer.customDigital(s,d,0,35,100,245,135,ORANGE,true);
        else{label(s,d.batteryLabel,40,124,245,-1);sport(s,whole(d.soc)+"%",40,192,64,WHITE,-1,250);batt(s,d.soc,42,210,218,19,ORANGE);}
        s.line(42,248,257,248,EDGE,1);label(s,d.rangeLabel,42,278,230,-1);sport(s,d.range,42,329,43,WHITE,-1,209);
        // Split scales from a real zero. Negative values illuminate towards the left; positive to the right.
        double driveLimit=EvoHybridRenderer.valid(d.power)?Math.max(200,Math.ceil(Math.max(0,d.power)/100)*100):200;
        double regenLimit=EvoHybridRenderer.valid(d.power)?Math.max(100,Math.ceil(Math.max(0,-d.power)/50)*50):100;
        int n=10;float step=18;
        for(int i=0;i<n;i++){
            float xx=628+i*step, yy=143-i*.7f;
            s.slant(xx,yy,15,16,4,DIM);float f=(float)Math.max(0,Math.min(1,fraction(d.power,driveLimit)*n-i));if(f>0)s.slant(xx,yy,15*f,16,4,ORANGE);
            xx=608-i*step;yy=144+i*2.1f;s.slant(xx,yy,15,16,4,DIM);f=(float)Math.max(0,Math.min(1,fraction(-d.power,regenLimit)*n-i));if(f>0)s.slant(xx+15*(1-f),yy,15*f,16,4,CYAN);
        }
        sport(s,"−"+whole(regenLimit),446,139,12,CYAN,0,70);sport(s,"0",630,134,14,WHITE,0,40);sport(s,"+"+whole(driveLimit)+" kW",800,128,12,ORANGE,0,90);
        s.line(628,138,628,163,WHITE,2);
        sport(s,whole(d.speed),605,276,139,WHITE,0,350);label(s,"km/h",605,299,130,0);
        sport(s,"‹‹ REGEN",510,323,16,regen(d.power)?CYAN:MUTE,0,125);sport(s,"POWER ››",704,323,16,d.power>0?ORANGE:MUTE,0,125);
        s.gaugeBounds(1,915,82,265,80);
        if(EvoHybridRenderer.custom(d,1))EvoHybridRenderer.customDigital(s,d,1,915,82,265,80,ORANGE,true);
        else{sport(s,power(d.power),1164,123,43,regen(d.power)?CYAN:WHITE,1,245);label(s,regen(d.power)?"REGEN POWER":"LIVE POWER",1164,145,250,1);}
        diagnostics(s,d,927,167,248,67,true);trips(s,d,395,ORANGE,true);
    }
    static void draw(EvoHybridRenderer.Surface s,EvoHybridRenderer.Data d,String theme,double avg){
        s.rect(0,0,1200,470,BG);s.plate(0,0,1200,1200f*763/2061);
        switch(theme){case GR:gr(s,d);break;case N:n(s,d);break;case RS:rs(s,d);break;case TAYCAN:taycan(s,d,avg);break;case XTREME:xtreme(s,d);break;default:throw new IllegalArgumentException("Unknown theme: "+theme);}
    }
}

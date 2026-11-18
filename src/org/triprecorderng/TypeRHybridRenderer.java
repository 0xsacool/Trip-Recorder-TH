package org.triprecorderng;

/** FL5-inspired static faceplate, live power bar and native telemetry. */
final class TypeRHybridRenderer {
    static final int BG=0xff050506, WHITE=0xfff8f8f9, MUTED=0xffb6b8bc,
        RED=0xffed1429, CYAN=0xff32d4ed, EDGE=0xff36383e, DIM=0xff25262a;
    static final int SEGMENTS=12;
    static double progress(double power){
        return EvoHybridRenderer.valid(power)?Math.min(1,Math.abs(power)/(power<0?100:200)):0;
    }
    static int accent(double power){return EvoHybridRenderer.valid(power)&&power<0?CYAN:RED;}
    private static String whole(double v){return EvoHybridRenderer.valid(v)?Long.toString(Math.round(v)):"--";}
    private static String signed(double v){return EvoHybridRenderer.valid(v)&&v>0?"+"+whole(v):whole(v);}
    private static void sport(EvoHybridRenderer.Surface s,String value,float x,float y,float size,int color,int align,float width){
        s.text(value,x,y,size,color,true,true,align,width);
    }
    private static void label(EvoHybridRenderer.Surface s,String value,float x,float y,float width,int align){
        s.text(value,x,y,15,MUTED,true,false,align,width);
    }
    private static void metric(EvoHybridRenderer.Surface s,String label,String value,float x,float y,float width,int size){
        s.text(label,x+width/2,y+12,12,MUTED,true,false,0,width-14);
        sport(s,value,x+width/2,y+33,size,WHITE,0,width-14);
    }
    static void draw(EvoHybridRenderer.Surface s,EvoHybridRenderer.Data d){
        s.rect(0,0,EvoHybridRenderer.WIDTH,EvoHybridRenderer.HEIGHT,BG);
        s.plate(0,0,1200,1200f*763/2061);
        sport(s,"TYPE-R EDITION",25,27,28,WHITE,-1,295);
        s.text(d.source,25,51,13,MUTED,false,false,-1,650);
        sport(s,d.status,1175,23,19,d.statusColor,1,285);
        s.text(d.tripLabel,1175,51,13,MUTED,false,false,1,240);
        boolean regen=EvoHybridRenderer.valid(d.power)&&d.power<0;
        boolean drive=EvoHybridRenderer.valid(d.power)&&d.power>0;
        int accent=accent(d.power);
        s.gaugeBounds(0,25,70,1150,52);
        if(EvoHybridRenderer.custom(d,0))EvoHybridRenderer.customBar(s,d,0,30,68,1140,58,RED);
        else{
        sport(s,regen?"REGEN":"POWER",30,91,25,WHITE,-1,145);
        s.text(regen?"0–100 kW":"0–200 kW",30,109,12,MUTED,false,false,-1,140);
        double amount=progress(d.power)*SEGMENTS;
        for(int i=0;i<SEGMENTS;i++){
            float xx=180+i*65;
            s.slant(xx,79,59,27,12,DIM);
            float part=(float)Math.max(0,Math.min(1,amount-i));
            if(part>0)s.slant(xx,79,59*part,27,12,accent);
        }
        sport(s,signed(d.power)+" kW",1170,94,32,WHITE,1,200);
        }

        // Main trip fields: use the faceplate's angled dividers without redrawing them.
        s.gaugeBounds(1,30,146,275,102);
        if(EvoHybridRenderer.custom(d,1))EvoHybridRenderer.customDigital(s,d,1,30,146,275,102,RED,true);
        else{
        label(s,d.batteryLabel,35,170,270,-1);
        s.outline(36,185,59,32,5,WHITE,2.5f);s.rect(95,195,5,12,WHITE);
        for(int i=0;i<4;i++){
            s.rect(41+i*13,190,11,22,DIM);
            if(EvoHybridRenderer.valid(d.soc)){
                float part=Math.max(0,Math.min(1,(float)d.soc/25-i));
                if(part>0)s.rect(41+i*13,190,11*part,22,d.soc<15?RED:WHITE);
            }
        }
        sport(s,whole(d.soc)+"%",112,225,55,WHITE,-1,180);
        }
        label(s,d.rangeLabel,35,290,260,-1);
        sport(s,d.range,35,345,47,WHITE,-1,245);

        s.text("SPEED",600,178,15,MUTED,true,false,0,190);
        sport(s,whole(d.speed),580,290,151,WHITE,0,375);
        sport(s,"km/h",799,285,28,MUTED,1,75);
        s.slant(400,320,190,35,18,drive?RED:DIM);
        s.slant(603,320,190,35,18,regen?CYAN:DIM);
        sport(s,"POWER",500,345,19,WHITE,0,155);
        sport(s,"REGEN",703,345,19,regen?BG:WHITE,0,155);

        label(s,d.tripCaption,1168,170,250,1);
        sport(s,d.distance,1168,225,45,WHITE,1,265);
        label(s,d.timeCaption,1168,290,250,1);
        sport(s,d.elapsed,1168,345,44,WHITE,1,275);

        // Retain every trip metric and battery diagnostic from the old Type-R theme.
        s.rect(0,395,1200,75,BG);
        String[] names={d.energyCaption,d.avgCaption};
        String[] values={d.energy,d.average};
        for(int i=0;i<2;i++){
            if(i>0)s.line(i*410,399,i*410,430,EDGE,1);
            metric(s,names[i],values[i],i*410,395,410,25);
        }
        s.line(20,434,820,434,EDGE,1);
        String[] labels={"BATTERY TEMP MIN / MAX","CELL VOLTAGE MIN / MAX","CHARGING VOLTAGE"};
        String[] data={d.temp,d.cells,d.voltage};
        for(int i=0;i<3;i++){
            if(i>0)s.line(i*273.3f,439,i*273.3f,467,EDGE,1);
            metric(s,labels[i],data[i],i*273.3f,435,273.3f,19);
        }
        s.line(830,399,830,467,EDGE,1);
        EvoHybridRenderer.history(s,d,840,398,350,68);
    }
}

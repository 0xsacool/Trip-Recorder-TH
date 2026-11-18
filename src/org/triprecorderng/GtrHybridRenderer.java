package org.triprecorderng;

/** R35-inspired immutable metal housing with live, native vector instruments. */
final class GtrHybridRenderer {
    static final int BG=0xff08090b, WHITE=0xfff5f5f6, MUTED=0xffb6bbc0,
        RED=0xffff3845, CYAN=0xff28d9ec, EDGE=0xff484c50, NEEDLE=0xffff2638;
    static final float PLATE_X=64, PLATE_W=1072, PLATE_SCALE=PLATE_W/2061,
        PLATE_H=763*PLATE_SCALE;
    static final float START=140, SWEEP=260;
    static double angle(double n,double min,double max) {
        return EvoHybridRenderer.valid(n)?START+SWEEP*Math.max(0,Math.min(1,(n-min)/(max-min))):Double.NaN;
    }
    private static float x(float c,float r,double a){return c+r*(float)Math.cos(Math.toRadians(a));}
    private static float y(float c,float r,double a){return c+r*(float)Math.sin(Math.toRadians(a));}
    private static void mark(EvoHybridRenderer.Surface s,float cx,float cy,float r,double a,float length,int col,float w){
        s.line(x(cx,r-length,a),y(cy,r-length,a),x(cx,r,a),y(cy,r,a),col,w);
    }
    private static String whole(double n){return EvoHybridRenderer.valid(n)?Long.toString(Math.round(n)):"--";}
    private static String consumption(String value){
        if(value==null)return "--";
        return value.replace(" kWh/100 km", "");
    }
    private static void gauge(EvoHybridRenderer.Surface s,float cx,float cy,float r,double min,double max,double n,
                              String label,String value,String unit,boolean power){
        int div=6,sub=5;
        for(int i=0;i<=div*sub;i++){
            double v=min+(max-min)*i/(div*sub),a=angle(v,min,max);
            int color=power?(v<0?CYAN:v>=150?RED:WHITE):(v<=10?CYAN:v>=25?RED:WHITE);
            boolean major=i%sub==0;
            mark(s,cx,cy,r*.96f,a,r*(major?.12f:.055f),color,major?3.2f:1.5f);
            if(major)s.text(whole(v),x(cx,r*.72f,a),y(cy,r*.72f,a)+6,17,WHITE,true,true,0,47);
        }
        // The center is an LCD-style readout. Keep the needle in the outer annulus.
        double a=angle(n,min,max);
        if(EvoHybridRenderer.valid(a)){
            s.line(x(cx,r*.52f,a),y(cy,r*.52f,a),x(cx,r*.92f,a),y(cy,r*.92f,a),NEEDLE,4.5f);
            s.line(x(cx,r*.52f,a),y(cy,r*.52f,a),x(cx,r*.92f,a),y(cy,r*.92f,a),0xffffd0d0,1f);
        }
        s.circle(cx,cy,r*.51f,0xff101113);
        s.text(label,cx,cy-24,14,MUTED,true,true,0,125);
        s.text(value,cx,cy+24,48,WHITE,true,true,0,130);
        s.text(unit,cx,cy+47,16,MUTED,true,true,0,127);
        if(power)s.text("REGEN / POWER",cx,cy+r*.78f,13,CYAN,true,true,0,146);
        else s.text("EFFICIENCY",cx,cy+r*.78f,13,MUTED,true,true,0,146);
    }
    private static void metric(EvoHybridRenderer.Surface s,String name,String value,float x,float y,float width){
        s.text(name,x+width/2,y+12,11,MUTED,true,false,0,width-10);
        s.text(value,x+width/2,y+31,20,WHITE,true,false,0,width-10);
    }
    private static void history(EvoHybridRenderer.Surface s,double[] samples,int count){
        float l=890,r=1182,t=414,b=462;
        s.text("POWER HISTORY (kW)",l,409,12,MUTED,true,true,-1,r-l);
        for(int i=0;i<4;i++){
            float yy=t+(b-t)*i/3;
            s.line(l,yy,r,yy,i==2?0xff637078:0xff303337,1);
            s.text(whole(200-100*i),l-5,yy+3,10,MUTED,false,false,1,34);
        }
        for(int i=0;i<7;i++)s.line(l+(r-l)*i/6,t,l+(r-l)*i/6,b,0xff303337,1);
        boolean started=false;float px=0,py=0;
        int capacity=samples==null?0:samples.length;
        count=Math.max(0,Math.min(capacity,count));
        for(int i=0;i<count;i++){
            if(!EvoHybridRenderer.valid(samples[i])){started=false;continue;}
            float xx=l+(r-l)*(capacity-count+i)/Math.max(1,capacity-1);
            float yy=b-(b-t)*(float)((Math.max(-100,Math.min(200,samples[i]))+100)/300);
            if(started)s.line(px,py,xx,yy,CYAN,2.2f);
            else s.circle(xx,yy,2.3f,CYAN);
            started=true;px=xx;py=yy;
        }
    }
    static void draw(EvoHybridRenderer.Surface s,EvoHybridRenderer.Data d,double avg,double[] samples,int count){
        s.rect(0,0,EvoHybridRenderer.WIDTH,EvoHybridRenderer.HEIGHT,BG);
        s.plate(PLATE_X,0,PLATE_W,PLATE_H);
        float cy=386*PLATE_SCALE, radius=276*PLATE_SCALE;
        gauge(s,PLATE_X+385*PLATE_SCALE,cy,radius,-100,200,d.power,"MOTOR POWER",whole(d.power),"kW",true);
        gauge(s,PLATE_X+1674*PLATE_SCALE,cy,radius,0,30,avg,"AVG. CONSUMPTION",consumption(d.average),"kWh/100 km",false);
        s.text("GT-R PERFORMANCE",600,40,22,WHITE,true,true,0,275);
        s.text(d.source,600,73,12,MUTED,false,false,0,320);
        s.text(whole(d.speed),590,151,92,WHITE,true,true,0,265);
        s.text("km/h",755,145,19,MUTED,true,true,1,65);
        s.line(450,169,750,169,RED,2.1f);
        s.text(d.batteryLabel,600,192,14,MUTED,true,true,0,230);
        s.text(whole(d.soc)+"%",600,243,48,WHITE,true,true,0,220);
        s.outline(467,254,266,12,5,EDGE,1.6f);
        if(EvoHybridRenderer.valid(d.soc))s.rect(469,256,262*Math.max(0,Math.min(1,(float)d.soc/100)),8,CYAN);
        s.text(d.rangeLabel,600,289,15,MUTED,true,true,0,260);
        s.text(d.range,600,326,40,WHITE,true,true,0,286);
        s.text(d.status,600,349,16,d.statusColor,true,false,0,280);
        s.text(d.tripLabel,600,365,12,MUTED,false,false,0,280);
        s.line(10,395,1190,395,EDGE,1.2f);
        String[] head={d.tripCaption,d.timeCaption,d.energyCaption,d.costCaption};
        String[] values={d.distance,d.elapsed,d.energy,d.money};
        for(int i=0;i<4;i++){
            float xx=10+i*202;
            if(i>0)s.line(xx,399,xx,435,EDGE,1);
            metric(s,head[i],values[i],xx,398,202);
        }
        s.line(10,436,818,436,EDGE,1);
        String[] labels={"BATTERY TEMP MIN / MAX","CELL VOLTAGE MIN / MAX","CHARGING VOLTAGE"};
        String[] data={d.temp,d.cells,d.voltage};
        for(int i=0;i<3;i++){
            float xx=10+i*269.3f;
            if(i>0)s.line(xx,438,xx,468,EDGE,1);
            metric(s,labels[i],data[i],xx,437,269.3f);
        }
        s.line(833,399,833,467,EDGE,1);
        history(s,samples,count);
    }
}

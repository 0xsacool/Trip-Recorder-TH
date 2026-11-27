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
        default void nismoLogo() {}
        default void beginTrace() {}
        default void traceLine(float x,float y,float xx,float yy,int color) { line(x,y,xx,yy,color,2.2f); }
        default void endTrace() {}
        void plate(float x, float y, float w, float h);
        void rect(float x,float y,float w,float h,int color);
        void outline(float x,float y,float w,float h,float radius,int color,float thickness);
        void line(float x1,float y1,float x2,float y2,int color,float thickness);
        void circle(float cx,float cy,float radius,int color);
        default void historyBounds(float x,float y,float w,float h) {}
        default void gaugeBounds(int slot,float x,float y,float w,float h) {}
        // Forward-sloping filled parallelogram. Native canvas overrides with a single path.
        default void slant(float x,float y,float w,float h,float shear,int color) {
            for(int i=0;i<h;i++){
                float shift=shear*(1-i/h);
                line(x+shift,y+i,x+shift+w,y+i,color,1f);
            }
        }
        default void arc(float cx,float cy,float radius,float start,float sweep,float width,int color) {
            if(sweep<=0)return;
            int steps=Math.max(1,(int)Math.ceil(sweep/3));
            for(int i=0;i<steps;i++){
                double a=Math.toRadians(start+sweep*i/steps),b=Math.toRadians(start+sweep*(i+1)/steps);
                line(cx+radius*(float)Math.cos(a),cy+radius*(float)Math.sin(a),cx+radius*(float)Math.cos(b),cy+radius*(float)Math.sin(b),color,width);
            }
        }
        // align: -1 left, 0 center, +1 right
        void text(String value,float x,float baseline,float size,int color,boolean heavy,boolean slant,int align,float maxWidth);
        default void readingWithUnit(String value,String unit,float x,float y,float size,float unitSize,int align,float width,int ink,int muted) {
            float numberWidth=Math.min(width-unitSize*1.6f,size*.59f*value.length());
            float left=align>0 ? x-numberWidth-unitSize*1.6f : x;
            text(value,left,y,size,ink,true,true,-1,numberWidth);
            text(unit,left+numberWidth+4,y,unitSize,muted,true,true,-1,width-numberWidth-4);
        }
    }
    static final class Data {
        double soc, speed, power;
        float startupAccent, powerAccent;
        double leftLevel=Double.NaN, rightLevel=Double.NaN;
        boolean thai;
        boolean charging, recording, results;
        float chargePhase, historyOffset, selectionAccent, materialBlend;
        int pressedSlot = -1;
        long historySecond = -1;
        private History selectedHistory;
        private long historyRevision = -1;
        double liveRpm=Double.NaN;
        double avgConsumption=Double.NaN,batteryTemp=Double.NaN,chargingVoltage=Double.NaN,remainingRange=Double.NaN;
        GaugeMetric[] gauges={GaugeMetric.DEFAULT,GaugeMetric.DEFAULT,GaugeMetric.DEFAULT,GaugeMetric.DEFAULT};
        String range, source, status, tripLabel, temp, cells, voltage;
        String distance, elapsed, energy, average, money;
        String batteryLabel, rangeLabel, tripCaption, timeCaption, energyCaption, avgCaption, costCaption;
        double[] history;
        int historyCount;
        Metric historyMetric=Metric.POWER;
        int historySeconds=60;
        int historyColor=0xff28d9ec;
        boolean historyLight, nismoHistory;
        int statusColor;
    }
    enum GaugeMetric {
        DEFAULT("Default","DEFAULT","",0,100,5),
        POWER("Motor power","MOTOR POWER","kW",-100,200,6),
        RPM("Front motor RPM","FRONT MOTOR RPM","rpm",0,12000,6),
        SPEED("Speed","SPEED","km/h",0,260,13),
        CONSUMPTION("Average consumption","AVG. CONSUMPTION","kWh/100 km",0,30,6),
        SOC("Battery level","BATTERY LEVEL","%",0,100,5),
        TEMPERATURE("Battery temperature (max)","BATTERY TEMP MAX","°C",0,60,6),
        VOLTAGE("Charging voltage","CHARGING VOLTAGE","V",0,800,4),
        RANGE("Estimated remaining range","EST. RANGE","km",0,500,5);
        final String label,title,unit;final double min,max;final int divisions;
        GaugeMetric(String label,String title,String unit,double min,double max,int divisions){
            this.label=label;this.title=title;this.unit=unit;this.min=min;this.max=max;this.divisions=divisions;
        }
        double read(Data d){
            switch(this){
                case POWER:return d.power;case RPM:return d.liveRpm;case SPEED:return d.speed;
                case CONSUMPTION:return d.avgConsumption;case SOC:return d.soc;case TEMPERATURE:return d.batteryTemp;
                case VOLTAGE:return d.chargingVoltage;case RANGE:return d.remainingRange;default:return Double.NaN;
            }
        }
        String reading(Data d){
            double n=read(d);if(!valid(n))return "--";
            if(this==CONSUMPTION)return d.average==null?String.format(java.util.Locale.US,"%.1f",n):d.average.replace(" kWh/100 km","");
            if(this==RANGE)return "≈"+Math.round(n);
            return Long.toString(Math.round(n));
        }
        double upper(Data d){
            double n=read(d);return valid(n)&&n>max?Math.ceil(n/(max/divisions))*(max/divisions):max;
        }
    }
    static boolean custom(Data d,int slot){return d.gauges[slot]!=GaugeMetric.DEFAULT;}
    static void customDigital(Surface s,Data d,int slot,float x,float y,float w,float h,int accent,boolean italic){
        GaugeMetric m=d.gauges[slot];
        s.text(m.title,x+w/2,y+h*.22f,Math.min(15,h*.19f),accent,true,italic,0,w-12);
        s.text(m.reading(d),x+w/2,y+h*.67f,Math.min(52,h*.43f),WHITE,true,italic,0,w-12);
        s.text(m.unit,x+w/2,y+h*.91f,Math.min(16,h*.18f),GRAY,true,italic,0,w-12);
    }
    static void customBar(Surface s,Data d,int slot,float x,float y,float w,float h,int accent){
        GaugeMetric m=d.gauges[slot];double n=m.read(d),hi=m.upper(d);
        s.text(m.title,x,y+16,15,GRAY,true,true,-1,w*.58f);
        s.text(m.reading(d)+" "+m.unit,x+w,y+20,24,WHITE,true,true,1,w*.55f);
        float f=valid(n)?(float)Math.max(0,Math.min(1,(n-m.min)/(hi-m.min))):0;
        float zero=m.min<0?(float)(-m.min/(hi-m.min)):0;
        s.rect(x,y+27,w,Math.max(4,h-46),0xff303338);
        if(valid(n))s.rect(x+w*Math.min(f,zero),y+27,w*Math.abs(f-zero),Math.max(4,h-46),n<0?BLUE:accent);
        s.text(Long.toString(Math.round(m.min)),x,y+h,11,GRAY,false,false,-1,w/3);
        s.text(Long.toString(Math.round(hi)),x+w,y+h,11,GRAY,false,false,1,w/3);
    }
    static void customDial(Surface s,Data d,int slot,float cx,float cy,float r,int accent,boolean italic){
        GaugeMetric m=d.gauges[slot];double n=m.read(d),hi=m.upper(d);
        int div=m.divisions,sub=4;
        for(int i=0;i<=div*sub;i++){
            double v=m.min+(hi-m.min)*i/(div*sub),a=angle(v,m.min,hi);
            boolean major=i%sub==0;
            mark(s,cx,cy,r*.96f,a,r*(major?.12f:.055f),v<0?BLUE:accent,major?3:1.3f);
            if(major)s.text(Long.toString(Math.round(v)),px(cx,r*.75f,a),py(cy,r*.75f,a)+r*.045f,r*.106f,WHITE,true,italic,0,r*.39f);
        }
        double a=angle(n,m.min,hi);
        if(valid(a))s.line(px(cx,r*.53f,a),py(cy,r*.53f,a),px(cx,r*.92f,a),py(cy,r*.92f,a),accent,4);
        s.circle(cx,cy,r*.51f,0xff101113);
        s.text(m.title,cx,cy-r*.17f,r*.105f,GRAY,true,italic,0,r*.94f);
        s.text(m.reading(d),cx,cy+r*.18f,r*.34f,WHITE,true,italic,0,r*.95f);
        s.text(m.unit,cx,cy+r*.34f,r*.115f,GRAY,true,italic,0,r*.94f);
    }
    /** One measurement per second; missing seconds remain gaps, not zeroes. */
    static final class History {
        final double[] samples;
        History(){this(60);}
        History(int capacity){samples=new double[capacity];}
        int count;
        private long lastSecond=-1;
        private long revision;
        void sample(long elapsedMillis,double value){
            revision++;
            long second=elapsedMillis/1000;
            if(second==lastSecond&&count>0){samples[count-1]=valid(value)?value:Double.NaN;return;}
            if(lastSecond>=0&&second>lastSecond){
                long gap=second-lastSecond-1;
                if(gap>=samples.length)count=0;
                else for(int i=0;i<gap;i++)append(Double.NaN);
            } else if(lastSecond>=0)count=0;
            append(valid(value)?value:Double.NaN);lastSecond=second;
        }
        private void append(double n){
            if(count<samples.length)samples[count++]=n;
            else{System.arraycopy(samples,1,samples,0,samples.length-1);samples[samples.length-1]=n;}
        }
    }
    enum Metric {
        POWER("Power (kW)","POWER HISTORY (kW)",-100,200,100),
        SPEED("Speed (km/h)","SPEED HISTORY (km/h)",0,120,40),
        CONSUMPTION("Avg. consumption (kWh/100 km)","AVG. CONSUMPTION (kWh/100 km)",0,30,10),
        BATTERY_TEMP("Battery temp (max °C)","BATTERY TEMP (MAX °C)",0,60,20),
        BATTERY_LEVEL("Battery level (%)","BATTERY LEVEL (%)",0,100,25),
        MOTOR_RPM("Front motor RPM (Live)","FRONT MOTOR RPM",0,12000,4000);
        final String label,title;final double min,max,step;
        Metric(String label,String title,double min,double max,double step){this.label=label;this.title=title;this.min=min;this.max=max;this.step=step;}
    }
    /** Keep all supported measurements concurrently, so switching views never starts a blank graph. */
    static final class TelemetryHistory {
        private final History[] series=new History[Metric.values().length];
        private final boolean[] seen=new boolean[series.length];
        TelemetryHistory(){for(int i=0;i<series.length;i++)series[i]=new History(300);}
        void sample(long elapsed,double power,double speed,double consumption,double temp,double level){sample(elapsed,power,speed,consumption,temp,level,Double.NaN);}
        void sample(long elapsed,double power,double speed,double consumption,double temp,double level,double rpm){
            double[] values={power,speed,consumption,temp,level,rpm};
            for(int i=0;i<series.length;i++){
                if(i!=Metric.MOTOR_RPM.ordinal())series[i].sample(elapsed,values[i]);
                if(valid(values[i])&&(i!=Metric.MOTOR_RPM.ordinal()||values[i]>0))seen[i]=true;
            }
        }
        boolean available(Metric m){return m==Metric.POWER||m==Metric.SPEED||seen[m.ordinal()];}
        void select(Data d,Metric m,int seconds){
            int capacity=seconds==300?300:60;
            if(m==Metric.MOTOR_RPM){d.historyCount=0;d.historyMetric=m;d.historySeconds=capacity;return;}
            History h=series[m.ordinal()];
            if (d.selectedHistory==h && d.historyRevision==h.revision && d.historySeconds==capacity && d.historyMetric==m) return;
            d.selectedHistory=h;
            d.historyRevision=h.revision;
            d.historySecond = h.lastSecond;
            if(d.history==null||d.history.length!=capacity)d.history=new double[capacity];
            d.historyCount=Math.min(capacity,h.count);
            System.arraycopy(h.samples,h.count-d.historyCount,d.history,0,d.historyCount);
            d.historyMetric=m;d.historySeconds=capacity;
        }
    }
    /** Shared GT-R-style plot. Missing readings break the trace; zero is a measured value only. */
    static void history(Surface s,double[] samples,int count,float x,float y,float width,float height) {
        history(s,samples,count,Metric.POWER,60,0xff28d9ec,x,y,width,height);
    }
    static void history(Surface s,double[] samples,int count,Metric metric,int seconds,int accent,float x,float y,float width,float height) {
        history(s,samples,count,metric,seconds,accent,false,x,y,width,height);
    }
    static void history(Surface s,double[] samples,int count,Metric metric,int seconds,int accent,boolean light,float x,float y,float width,float height) {
        history(s,samples,count,metric,seconds,accent,light,x,y,width,height,0,false);
    }
    private static void history(Surface s,double[] samples,int count,Metric metric,int seconds,int accent,boolean light,float x,float y,float width,float height,float offset,boolean nismo) {
        s.historyBounds(x,y,width,height);
        float l=x+32,r=x+width-5,t=y+15,b=y+height-4;
        final int muted=light?0xff526574:0xffb6bbc0, grid=light?0xffd0dbe2:0xff303337;
        s.text(metric.title,l,y+11,12,muted,true,true,-1,r-l-46);
        s.text(seconds==300?"5 min":"60s",r,y+11,11,muted,false,false,1,37);
        s.line(r-44,y+5,r-40,y+9,muted,1.6f);s.line(r-40,y+9,r-36,y+5,muted,1.6f);
        double step=metric.step,lo=metric.min,hi=metric.max;
        int capacity=samples==null?0:samples.length;
        count=Math.max(0,Math.min(capacity,count));
        for(int i=0;i<count;i++)if(valid(samples[i])){
            if(metric==Metric.POWER)step=Math.max(step,Math.ceil(Math.max(samples[i]/2,-samples[i])/100)*100);
            else {lo=Math.min(lo,Math.floor(samples[i]/step)*step);hi=Math.max(hi,Math.ceil(samples[i]/step)*step);}
        }
        if(metric==Metric.POWER){hi=step*2;lo=-step;}
        int divisions=metric==Metric.BATTERY_LEVEL?4:3;
        for(int tick=0;tick<=divisions;tick++){
            double v=hi-(hi-lo)*tick/divisions;
            float yy=b-(b-t)*(float)((v-lo)/(hi-lo));
            s.line(l,yy,r,yy,v==0?0xff637078:grid,1);
            s.text(Long.toString(Math.round(v)),l-5,yy+3,10,muted,false,false,1,27);
        }
        for(int i=0;i<7;i++)s.line(l+(r-l)*i/6,t,l+(r-l)*i/6,b,grid,1);
        boolean started=false, any=false;float px=0,py=0;
        float zeroY=b-(b-t)*(float)(-lo/(hi-lo));
        boolean signed=nismo && metric==Metric.POWER;
        if(nismo)s.beginTrace();
        for(int i=0;i<count;i++){
            if(!valid(samples[i])){started=false;continue;}
            any=true;
            float xx=capacity==1?r:l+(r-l)*(capacity-count+i-offset)/Math.max(1,capacity-1);
            float yy=b-(b-t)*(float)((samples[i]-lo)/(hi-lo));
            if(started && xx>=l){
                float leftY=px<l ? py+(yy-py)*(l-px)/(xx-px) : py;
                float left=Math.max(l,px);
                if(signed && (leftY-zeroY)*(yy-zeroY)<0){
                    float cross=left+(xx-left)*(zeroY-leftY)/(yy-leftY);
                    s.traceLine(left,leftY,cross,zeroY,leftY>zeroY ? 0xff28d9ec : accent);
                    left=cross;leftY=zeroY;
                }
                if(nismo)s.traceLine(left,leftY,xx,yy,signed && (yy+leftY)/2>zeroY ? 0xff28d9ec : accent);
                else s.line(left,leftY,xx,yy,accent,2.2f);
            }else if(xx>=l)s.circle(xx,yy,2.3f,signed && yy>zeroY ? 0xff28d9ec : accent);
            started=true;px=xx;py=yy;
        }
        if(nismo){
            s.endTrace();
            if(!any)s.text("--",(l+r)/2,(t+b)/2,18,muted,true,false,0,r-l);
        }
    }
    static void history(Surface s,Data d,float x,float y,float width,float height){
        history(s,d,d.history,d.historyCount,x,y,width,height);
    }
    static void history(Surface s,Data d,double[] samples,int count,float x,float y,float width,float height){
        if(d.historyMetric!=Metric.MOTOR_RPM){history(s,samples,count,d.historyMetric,d.historySeconds,d.historyColor,d.historyLight,x,y,width,height,d.historyOffset,d.nismoHistory);return;}
        // A live instrument, not a historical plot. Keep the hitbox and header in exactly the same place.
        s.historyBounds(x,y,width,height);
        float l=x+8,r=x+width-6;
        s.text("FRONT MOTOR RPM",l,y+12,12,d.historyLight?0xff526574:0xffb6bbc0,true,true,-1,r-l-55);
        s.text("LIVE",r,y+12,10,d.historyColor,true,false,1,36);
        s.line(r-48,y+6,r-44,y+10,d.historyLight?0xff526574:0xffb6bbc0,1.6f);s.line(r-44,y+10,r-40,y+6,d.historyLight?0xff526574:0xffb6bbc0,1.6f);
        s.text(valid(d.liveRpm)?Long.toString(Math.round(d.liveRpm)):"--",x+width*.52f,y+height*.79f,Math.min(42,height*.62f),d.historyLight?0xff142633:0xfff3f2f3,true,true,0,width-55);
        s.text("rpm",r,y+height*.79f,13,d.historyLight?0xff526574:0xffb6bbc0,true,false,1,35);
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
        s.gaugeBounds(0,lx-lr,ly-lr,lr*2,lr*2);
        if(custom(d,0))customDial(s,d,0,lx,ly,lr,RED,true);
        else {
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
        }
        float pcx=934*PLATE_SCALE,pcy=421*PLATE_SCALE,pr=337*PLATE_SCALE;
        s.gaugeBounds(1,pcx-pr,pcy-pr,pr*2,pr*2);
        if(custom(d,1))customDial(s,d,1,pcx,pcy,pr,RED,true);
        else dial(s,pcx,pcy,pr,-100,200,6,5,d.power,true);
        dial(s,1553*PLATE_SCALE,480*PLATE_SCALE,249*PLATE_SCALE,0,260,13,4,d.speed,false);
        s.line(970,100,970,390,EDGE,1);
        telemetry(s,"BATTERY TEMP",d.temp,136);
        s.line(987,188,1185,188,EDGE,1);
        telemetry(s,"CELL VOLTAGE",d.cells,222);
        s.line(987,274,1185,274,EDGE,1);
        telemetry(s,"CHARGING VOLTAGE",d.voltage,308);
        s.outline(9,420,812,46,5,EDGE,1.25f);
        String[] heads={d.tripCaption,d.timeCaption,d.energyCaption,d.avgCaption};
        String[] vals={d.distance,d.elapsed,d.energy,d.average};
        for(int i=0;i<4;i++){
            float x=9+i*203;
            if(i!=0)s.line(x,424,x,462,EDGE,1);
            s.text(heads[i],x+101.5f,436,12,GRAY,true,false,0,190);
            s.text(vals[i],x+101.5f,459,24,WHITE,true,false,0,190);
        }
        s.rect(829,398,363,70,BG);
        s.outline(829,398,363,68,5,EDGE,1.25f);
        history(s,d,835,400,350,64);
    }
}

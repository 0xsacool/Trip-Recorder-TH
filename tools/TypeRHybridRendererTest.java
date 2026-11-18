package org.triprecorderng;

public final class TypeRHybridRendererTest {
    static void check(boolean pass,String message){if(!pass)throw new AssertionError(message);}
    static final class Trace implements EvoHybridRenderer.Surface {
        int plate,red,cyan,powerMode,regenMode;float litWidth;
        boolean missingPower,missingSoc,missingSpeed;
        public void plate(float x,float y,float w,float h){plate++;check(Math.abs(w/h-2061f/763)<.001,"plate aspect");}
        public void rect(float x,float y,float w,float h,int color){}
        public void outline(float x,float y,float w,float h,float r,int c,float th){}
        public void line(float x,float y,float x2,float y2,int c,float th){}
        public void circle(float x,float y,float r,int c){}
        public void slant(float x,float y,float w,float h,float shear,int col){
            check(w>0&&Float.isFinite(w),"valid segment geometry");
            if(y==79){if(col==TypeRHybridRenderer.RED){red++;litWidth+=w;}if(col==TypeRHybridRenderer.CYAN){cyan++;litWidth+=w;}}
            if(y==320){if(col==TypeRHybridRenderer.RED)powerMode++;if(col==TypeRHybridRenderer.CYAN)regenMode++;}
        }
        public void text(String v,float x,float y,float size,int c,boolean heavy,boolean slant,int align,float width){
            if("-- kW".equals(v))missingPower=true;
            if("--%".equals(v))missingSoc=true;
            if("--".equals(v)&&size==151)missingSpeed=true;
        }
    }
    public static void main(String[] args){
        check(TypeRHybridRenderer.progress(100)==.5,"positive traction scale");
        check(TypeRHybridRenderer.progress(-50)==.5,"negative regeneration scale");
        check(TypeRHybridRenderer.progress(-500)==1,"clamp");
        check(TypeRHybridRenderer.progress(Double.NaN)==0,"missing");
        EvoHybridRenderer.Data d=new EvoHybridRenderer.Data();d.soc=71;d.speed=82;d.power=25;
        Trace t=new Trace();TypeRHybridRenderer.draw(t,d);
        check(t.plate==1&&t.red==2&&t.cyan==0&&t.powerMode==1&&t.regenMode==0&&t.litWidth==88.5f,"traction + partial segment");
        d.power=-50;t=new Trace();TypeRHybridRenderer.draw(t,d);
        check(t.red==0&&t.cyan==6&&t.powerMode==0&&t.regenMode==1,"regen");
        d.power=0;t=new Trace();TypeRHybridRenderer.draw(t,d);
        check(t.red==0&&t.cyan==0&&t.powerMode==0&&t.regenMode==0,"idle");
        d.power=d.speed=d.soc=Double.NaN;t=new Trace();TypeRHybridRenderer.draw(t,d);
        check(t.red==0&&t.cyan==0&&t.powerMode==0&&t.regenMode==0&&t.missingPower&&t.missingSoc&&t.missingSpeed,"offline placeholders");
        System.out.println("TypeRHybridRendererTest PASS");
    }
}

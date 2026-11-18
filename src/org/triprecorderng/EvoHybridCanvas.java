package org.triprecorderng;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import java.io.InputStream;

/** Android adapter; only decodes the housing once per dashboard lifetime. */
final class EvoHybridCanvas implements EvoHybridRenderer.Surface {
    private final Bitmap bitmap;
    private final CockpitPalette palette;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF box = new RectF();
    private final Path shape = new Path();
    private final Typeface plain=Typeface.create("sans-serif-condensed",Typeface.NORMAL);
    private final Typeface bold=Typeface.create("sans-serif-condensed",Typeface.BOLD);
    private final Typeface italic=Typeface.create("sans-serif-condensed",Typeface.BOLD_ITALIC);
    private Canvas c;
    private final RectF graphBounds=new RectF();
    private final RectF[] gaugeBounds={new RectF(),new RectF()};
    private float graphScale=1,graphX,graphY;
    RectF graphBounds(){return new RectF(graphBounds);}
    private void transform(float scale,float x,float y){graphScale=scale;graphX=x;graphY=y;graphBounds.setEmpty();for(RectF r:gaugeBounds)r.setEmpty();c.translate(x,y);c.scale(scale,scale);}
    RectF gaugeBounds(int slot){return new RectF(gaugeBounds[slot]);}
    int gaugeAt(float x,float y){for(int i=0;i<gaugeBounds.length;i++)if(gaugeBounds[i].contains(x,y))return i;return -1;}
    @Override public void gaugeBounds(int slot,float x,float y,float w,float h){gaugeBounds[slot].set(graphX+x*graphScale,graphY+y*graphScale,graphX+(x+w)*graphScale,graphY+(y+h)*graphScale);}
    @Override public void historyBounds(float x,float y,float w,float h){graphBounds.set(graphX+x*graphScale,graphY+y*graphScale,graphX+(x+w)*graphScale,graphY+(y+h)*graphScale);}
    EvoHybridCanvas(Context context) { this(context,"evo_hybrid_plate.webp"); }
    EvoHybridCanvas(Context context,String asset) {
        palette=new CockpitPalette(Prefs.dashboardTheme(context),Prefs.sportTheme(context)&&Prefs.lightAppearance(context));
        Bitmap b=null;
        if(asset!=null)try(InputStream in=context.getAssets().open(asset)){
            BitmapFactory.Options o=new BitmapFactory.Options();
            o.inScaled=false;
            // Transform the plate once, in-place, off the live rendering path.
            o.inMutable=palette.light;
            o.inPreferredConfig=Bitmap.Config.ARGB_8888;
            b=BitmapFactory.decodeStream(in,null,o);
            if(b!=null&&palette.light){
                if(!b.isMutable()){
                    Bitmap original=b;b=original.copy(Bitmap.Config.ARGB_8888,true);
                    original.recycle();
                }
                if(b!=null){
                    int w=b.getWidth();int[] row=new int[w];
                    for(int y=0;y<b.getHeight();y++){
                        b.getPixels(row,0,w,0,y,w,1);
                        for(int x=0;x<w;x++)row[x]=palette.platePixel(row[x],x,y,w);
                        b.setPixels(row,0,w,0,y,w,1);
                    }
                }
            }
        }catch(java.io.IOException e){android.util.Log.w("EvoHybrid","Backplate unavailable",e);}
        bitmap=b;
    }
    void drawHistory(Canvas canvas,int width,int height,EvoHybridRenderer.Data history){
        if(width<=0||height<=0)return;
        float scale=Math.min(width/160f,height/68f);
        c=canvas;c.save();transform(scale,0,(height-68*scale)/2);
        EvoHybridRenderer.history(this,history,0,0,width/scale,68);
        c.restore();c=null;
    }
    void draw(Canvas canvas,int width,int height,EvoHybridRenderer.Data d){
        float scale=Math.min(width/EvoHybridRenderer.WIDTH,height/EvoHybridRenderer.HEIGHT);
        c=canvas;
        c.save();transform(scale,(width-EvoHybridRenderer.WIDTH*scale)/2f,(height-EvoHybridRenderer.HEIGHT*scale)/2f);
        EvoHybridRenderer.draw(this,d);
        c.restore();c=null;
    }
    void drawGtr(Canvas canvas,int width,int height,EvoHybridRenderer.Data d,double average,double[] history,int count){
        float scale=Math.min(width/EvoHybridRenderer.WIDTH,height/EvoHybridRenderer.HEIGHT);
        c=canvas;
        c.save();transform(scale,(width-EvoHybridRenderer.WIDTH*scale)/2f,(height-EvoHybridRenderer.HEIGHT*scale)/2f);
        GtrHybridRenderer.draw(this,d,average,history,count);
        c.restore();c=null;
    }
    void drawTypeR(Canvas canvas,int width,int height,EvoHybridRenderer.Data d){
        float scale=Math.min(width/EvoHybridRenderer.WIDTH,height/EvoHybridRenderer.HEIGHT);
        c=canvas;
        c.save();transform(scale,(width-EvoHybridRenderer.WIDTH*scale)/2f,(height-EvoHybridRenderer.HEIGHT*scale)/2f);
        TypeRHybridRenderer.draw(this,d);
        c.restore();c=null;
    }
    void drawAdditional(Canvas canvas,int width,int height,EvoHybridRenderer.Data d,String theme,double average){
        float scale=Math.min(width/EvoHybridRenderer.WIDTH,height/EvoHybridRenderer.HEIGHT);
        c=canvas;
        c.save();transform(scale,(width-EvoHybridRenderer.WIDTH*scale)/2f,(height-EvoHybridRenderer.HEIGHT*scale)/2f);
        AdditionalHybridRenderer.draw(this,d,theme,average);
        c.restore();c=null;
    }
    private void fill(int color){p.setColor(color);p.setStyle(Paint.Style.FILL);p.setStrokeWidth(1);}
    public void plate(float x,float y,float w,float h){
        if(bitmap==null)return;
        fill(0xffffffff);box.set(x,y,x+w,y+h);c.drawBitmap(bitmap,null,box,p);
    }
    public void rect(float x,float y,float w,float h,int color){fill(palette.fill(color));c.drawRect(x,y,x+w,y+h,p);}
    public void outline(float x,float y,float w,float h,float radius,int color,float thickness){
        fill(palette.stroke(color));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(thickness);box.set(x,y,x+w,y+h);c.drawRoundRect(box,radius,radius,p);
    }
    public void line(float x1,float y1,float x2,float y2,int color,float thickness){
        fill(palette.stroke(color));p.setStrokeWidth(thickness);c.drawLine(x1,y1,x2,y2,p);
    }
    public void circle(float cx,float cy,float r,int color){fill(palette.fill(color));c.drawCircle(cx,cy,r,p);}
    public void slant(float x,float y,float w,float h,float shear,int color){
        fill(palette.fill(color));shape.reset();shape.moveTo(x+shear,y);shape.lineTo(x+shear+w,y);
        shape.lineTo(x+w,y+h);shape.lineTo(x,y+h);shape.close();c.drawPath(shape,p);
    }
    public void arc(float cx,float cy,float radius,float start,float sweep,float width,int color){
        if(sweep<=0)return;
        fill(palette.fill(color));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(width);
        box.set(cx-radius,cy-radius,cx+radius,cy+radius);c.drawArc(box,start,sweep,false,p);
        p.setStyle(Paint.Style.FILL);
    }
    public void text(String v,float x,float y,float size,int color,boolean heavy,boolean slant,int align,float maxWidth){
        if(v==null)v="--";
        fill(palette.text(color));p.setTypeface(slant?italic:heavy?bold:plain);
        p.setTextSize(size);p.setTextAlign(align<0?Paint.Align.LEFT:align>0?Paint.Align.RIGHT:Paint.Align.CENTER);
        float actual=p.measureText(v);if(actual>maxWidth)p.setTextSize(size*maxWidth/actual);
        c.drawText(v,x,y,p);
    }
}

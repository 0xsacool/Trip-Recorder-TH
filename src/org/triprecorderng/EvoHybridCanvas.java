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
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF box = new RectF();
    private final Path shape = new Path();
    private final Typeface plain=Typeface.create("sans-serif-condensed",Typeface.NORMAL);
    private final Typeface bold=Typeface.create("sans-serif-condensed",Typeface.BOLD);
    private final Typeface italic=Typeface.create("sans-serif-condensed",Typeface.BOLD_ITALIC);
    private Canvas c;
    EvoHybridCanvas(Context context) { this(context,"evo_hybrid_plate.webp"); }
    EvoHybridCanvas(Context context,String asset) {
        Bitmap b=null;
        try(InputStream in=context.getAssets().open(asset)){
            BitmapFactory.Options o=new BitmapFactory.Options();
            o.inScaled=false;
            b=BitmapFactory.decodeStream(in,null,o);
        }catch(java.io.IOException e){android.util.Log.w("EvoHybrid","Backplate unavailable",e);}
        bitmap=b;
    }
    void draw(Canvas canvas,int width,int height,EvoHybridRenderer.Data d){
        float scale=Math.min(width/EvoHybridRenderer.WIDTH,height/EvoHybridRenderer.HEIGHT);
        c=canvas;
        c.save();c.translate((width-EvoHybridRenderer.WIDTH*scale)/2f,(height-EvoHybridRenderer.HEIGHT*scale)/2f);
        c.scale(scale,scale);
        EvoHybridRenderer.draw(this,d);
        c.restore();c=null;
    }
    void drawGtr(Canvas canvas,int width,int height,EvoHybridRenderer.Data d,double average,double[] history,int count){
        float scale=Math.min(width/EvoHybridRenderer.WIDTH,height/EvoHybridRenderer.HEIGHT);
        c=canvas;
        c.save();c.translate((width-EvoHybridRenderer.WIDTH*scale)/2f,(height-EvoHybridRenderer.HEIGHT*scale)/2f);
        c.scale(scale,scale);
        GtrHybridRenderer.draw(this,d,average,history,count);
        c.restore();c=null;
    }
    void drawTypeR(Canvas canvas,int width,int height,EvoHybridRenderer.Data d){
        float scale=Math.min(width/EvoHybridRenderer.WIDTH,height/EvoHybridRenderer.HEIGHT);
        c=canvas;
        c.save();c.translate((width-EvoHybridRenderer.WIDTH*scale)/2f,(height-EvoHybridRenderer.HEIGHT*scale)/2f);
        c.scale(scale,scale);
        TypeRHybridRenderer.draw(this,d);
        c.restore();c=null;
    }
    private void fill(int color){p.setColor(color);p.setStyle(Paint.Style.FILL);p.setStrokeWidth(1);}
    public void plate(float x,float y,float w,float h){
        if(bitmap==null)return;
        fill(0xffffffff);box.set(x,y,x+w,y+h);c.drawBitmap(bitmap,null,box,p);
    }
    public void rect(float x,float y,float w,float h,int color){fill(color);c.drawRect(x,y,x+w,y+h,p);}
    public void outline(float x,float y,float w,float h,float radius,int color,float thickness){
        fill(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(thickness);box.set(x,y,x+w,y+h);c.drawRoundRect(box,radius,radius,p);
    }
    public void line(float x1,float y1,float x2,float y2,int color,float thickness){
        fill(color);p.setStrokeWidth(thickness);c.drawLine(x1,y1,x2,y2,p);
    }
    public void circle(float cx,float cy,float r,int color){fill(color);c.drawCircle(cx,cy,r,p);}
    public void slant(float x,float y,float w,float h,float shear,int color){
        fill(color);shape.reset();shape.moveTo(x+shear,y);shape.lineTo(x+shear+w,y);
        shape.lineTo(x+w,y+h);shape.lineTo(x,y+h);shape.close();c.drawPath(shape,p);
    }
    public void text(String v,float x,float y,float size,int color,boolean heavy,boolean slant,int align,float maxWidth){
        if(v==null)v="--";
        fill(color);p.setTypeface(slant?italic:heavy?bold:plain);
        p.setTextSize(size);p.setTextAlign(align<0?Paint.Align.LEFT:align>0?Paint.Align.RIGHT:Paint.Align.CENTER);
        float actual=p.measureText(v);if(actual>maxWidth)p.setTextSize(size*maxWidth/actual);
        c.drawText(v,x,y,p);
    }
}

package org.triprecorderng;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

/** Shared dashboard selector. No vehicle reads or commands from this UI. */
final class TelemetryPicker {
    private static final EvoHybridRenderer.TelemetryHistory HISTORY=new EvoHybridRenderer.TelemetryHistory();
    private static boolean popupOpen;
    static boolean isOpen(){return popupOpen;}
    private final Context c;
    private final SharedPreferences prefs;
    private final int accent;
    private EvoHybridRenderer.Metric metric;
    private int seconds;
    private PopupWindow popup;

    TelemetryPicker(Context c){
        this.c=c;
        prefs=c.getApplicationContext().getSharedPreferences("triprecng",Context.MODE_PRIVATE);
        try{metric=EvoHybridRenderer.Metric.valueOf(prefs.getString("telemetry_metric","POWER"));}
        catch(IllegalArgumentException e){metric=EvoHybridRenderer.Metric.POWER;}
        seconds=prefs.getInt("telemetry_seconds",60)==300?300:60;
        int themeAccent=Prefs.newThemeAccent(c);
        accent=themeAccent!=0?themeAccent:0xff28d9ec;
    }
    void sample(long elapsed,double power,double speed,double consumption,double temp,double level){
        HISTORY.sample(elapsed,power,speed,consumption,temp,level);
    }
    void select(EvoHybridRenderer.Data d){
        HISTORY.select(d,metric,seconds);d.historyColor=accent;
    }
    String description(){return metric.title+", "+(seconds==300?"5 min":"60s")+". Tap to choose graph";}
    private int dp(float v){return Math.round(v*c.getResources().getDisplayMetrics().density);}
    private TextView row(String label,boolean selected){
        TextView t=new TextView(c);t.setText(label+(selected?"   ✓":""));t.setTextColor(selected?accent:Color.WHITE);
        t.setGravity(Gravity.CENTER_VERTICAL);t.setTextSize(16);t.setTypeface(Typeface.DEFAULT,selected?Typeface.BOLD:Typeface.NORMAL);
        t.setPadding(dp(12),0,dp(12),0);t.setMinHeight(dp(44));return t;
    }
    private void save(){prefs.edit().putString("telemetry_metric",metric.name()).putInt("telemetry_seconds",seconds).apply();}
    private void choose(View anchor){save();dismiss();anchor.invalidate();}
    void dismiss(){if(popup!=null){popup.dismiss();popup=null;}}
    void show(View anchor,RectF graph){
        if(popup!=null&&popup.isShowing()){dismiss();return;}
        if(graph.isEmpty())return;
        LinearLayout list=new LinearLayout(c);list.setOrientation(LinearLayout.VERTICAL);list.setPadding(dp(4),dp(4),dp(4),dp(4));
        GradientDrawable bg=new GradientDrawable();bg.setColor(0xff181c20);bg.setCornerRadius(dp(6));bg.setStroke(dp(1),0xff4f555c);list.setBackground(bg);
        for(EvoHybridRenderer.Metric m:EvoHybridRenderer.Metric.values()){
            if(!HISTORY.available(m)&&m!=metric)continue;
            TextView item=row(m.label,m==metric);list.addView(item,new LinearLayout.LayoutParams(-1,-2));
            item.setOnClickListener(v->{metric=m;choose(anchor);});
        }
        View divider=new View(c);divider.setBackgroundColor(0xff4f555c);list.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
        for(int window:new int[]{60,300}){
            TextView item=row(window==60?"60s":"5 min",window==seconds);list.addView(item,new LinearLayout.LayoutParams(-1,-2));
            item.setOnClickListener(v->{seconds=window;choose(anchor);});
        }
        Rect visible=new Rect();anchor.getWindowVisibleDisplayFrame(visible);
        int width=Math.min(visible.width(),Math.max(dp(275),Math.round(graph.width())));
        if(width<=0)return;
        list.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        int[] screen=new int[2];anchor.getLocationOnScreen(screen);
        int top=screen[1]+Math.round(graph.top),bottom=screen[1]+Math.round(graph.bottom);
        int above=Math.max(0,top-visible.top-dp(4)),below=Math.max(0,visible.bottom-bottom-dp(4));
        boolean upward=above>=list.getMeasuredHeight()||above>=below;
        int height=Math.min(list.getMeasuredHeight(),Math.max(dp(96),upward?above:below));
        ScrollView scroll=new ScrollView(c);scroll.setFillViewport(true);scroll.addView(list);
        popup=new PopupWindow(scroll,width,height,true);popup.setBackgroundDrawable(bg);popup.setOutsideTouchable(true);popup.setElevation(dp(5));
        popup.setOnDismissListener(()->popupOpen=false);
        int left=Math.max(visible.left,Math.min(visible.right-width,screen[0]+Math.round(graph.right)-width));
        int y=upward?top-height-dp(4):bottom+dp(4);y=Math.max(visible.top,Math.min(visible.bottom-height,y));
        popup.showAtLocation(anchor,Gravity.TOP|Gravity.LEFT,left,y);
        popupOpen=popup.isShowing();
    }
}

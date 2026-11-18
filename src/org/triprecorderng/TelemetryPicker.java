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
    private final String gaugeTheme;
    private final EvoHybridRenderer.GaugeMetric[] gauges=new EvoHybridRenderer.GaugeMetric[2];

    TelemetryPicker(Context c){
        this.c=c;
        prefs=c.getApplicationContext().getSharedPreferences("triprecng",Context.MODE_PRIVATE);
        gaugeTheme=Prefs.dashboardTheme(c);
        for(int i=0;i<gauges.length;i++){
            try{gauges[i]=EvoHybridRenderer.GaugeMetric.valueOf(prefs.getString(gaugeKey(i),"DEFAULT"));}
            catch(IllegalArgumentException e){gauges[i]=EvoHybridRenderer.GaugeMetric.DEFAULT;}
        }
        try{metric=EvoHybridRenderer.Metric.valueOf(prefs.getString("telemetry_metric","POWER"));}
        catch(IllegalArgumentException e){metric=EvoHybridRenderer.Metric.POWER;}
        seconds=prefs.getInt("telemetry_seconds",60)==300?300:60;
        int themeAccent=Prefs.newThemeAccent(c);
        accent=themeAccent!=0?themeAccent:0xff28d9ec;
    }
    void sample(long elapsed,double power,double speed,double consumption,double temp,double level,double rpm){
        HISTORY.sample(elapsed,power,speed,consumption,temp,level,rpm);
    }
    void select(EvoHybridRenderer.Data d){
        HISTORY.select(d,metric,seconds);d.historyColor=accent;
        d.gauges[0]=gauges[0];d.gauges[1]=gauges[1];
    }
    private String gaugeKey(int slot){return "custom_gauge_"+gaugeTheme+"_"+slot;}
    void showGauge(View anchor,RectF bounds,int slot){
        if(slot<0||slot>=gauges.length||bounds.isEmpty())return;
        dismiss();
        LinearLayout list=new LinearLayout(c);list.setOrientation(LinearLayout.VERTICAL);list.setPadding(dp(4),dp(4),dp(4),dp(4));
        GradientDrawable bg=new GradientDrawable();bg.setColor(0xff181c20);bg.setCornerRadius(dp(6));bg.setStroke(dp(1),0xff4f555c);list.setBackground(bg);
        TextView title=row("CUSTOM GAUGE "+(slot+1),false);title.setTextColor(accent);title.setTextSize(13);list.addView(title,new LinearLayout.LayoutParams(-1,-2));
        for(EvoHybridRenderer.GaugeMetric m:EvoHybridRenderer.GaugeMetric.values()){
            TextView item=row(m.label+(m.unit.isEmpty()?"":" ("+m.unit+")"),m==gauges[slot]);list.addView(item,new LinearLayout.LayoutParams(-1,-2));
            item.setOnClickListener(v->{gauges[slot]=m;prefs.edit().putString(gaugeKey(slot),m.name()).apply();dismiss();anchor.invalidate();});
        }
        TextView reset=row("Reset theme gauges",false);list.addView(reset,new LinearLayout.LayoutParams(-1,-2));
        reset.setOnClickListener(v->{gauges[0]=gauges[1]=EvoHybridRenderer.GaugeMetric.DEFAULT;prefs.edit().remove(gaugeKey(0)).remove(gaugeKey(1)).apply();dismiss();anchor.invalidate();});
        showList(anchor,bounds,list,bg,true);
    }
    String description(){return metric.title+", "+(metric==EvoHybridRenderer.Metric.MOTOR_RPM?"Live":seconds==300?"5 min":"60s")+". Tap to choose data";}
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
        if(metric!=EvoHybridRenderer.Metric.MOTOR_RPM){
        View divider=new View(c);divider.setBackgroundColor(0xff4f555c);list.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
        for(int window:new int[]{60,300}){
            TextView item=row(window==60?"60s":"5 min",window==seconds);list.addView(item,new LinearLayout.LayoutParams(-1,-2));
            item.setOnClickListener(v->{seconds=window;choose(anchor);});
        }
        }
        showList(anchor,graph,list,bg,false);
    }
    private void showList(View anchor,RectF graph,LinearLayout list,GradientDrawable bg,boolean gauge){
        Rect visible=new Rect();anchor.getWindowVisibleDisplayFrame(visible);
        int width=Math.min(visible.width(),gauge?dp(320):Math.max(dp(275),Math.round(graph.width())));
        if(width<=0)return;
        list.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        int[] screen=new int[2];anchor.getLocationOnScreen(screen);
        int top=screen[1]+Math.round(graph.top),bottom=screen[1]+Math.round(graph.bottom);
        int above=Math.max(0,top-visible.top-dp(4)),below=Math.max(0,visible.bottom-bottom-dp(4));
        boolean upward=above>=list.getMeasuredHeight()||above>=below;
        int height=Math.min(list.getMeasuredHeight(),gauge?Math.max(1,visible.height()-dp(16)):Math.max(dp(96),upward?above:below));
        ScrollView scroll=new ScrollView(c);scroll.setFillViewport(true);scroll.addView(list);
        popup=new PopupWindow(scroll,width,height,true);popup.setBackgroundDrawable(bg);popup.setOutsideTouchable(true);popup.setElevation(dp(5));
        popup.setOnDismissListener(()->popupOpen=false);
        int left=Math.max(visible.left,Math.min(visible.right-width,screen[0]+Math.round(graph.right)-width));
        int y=gauge?visible.centerY()-height/2:upward?top-height-dp(4):bottom+dp(4);
        y=Math.max(visible.top,Math.min(visible.bottom-height,y));
        popup.showAtLocation(anchor,Gravity.TOP|Gravity.LEFT,left,y);
        popupOpen=popup.isShowing();
    }
}

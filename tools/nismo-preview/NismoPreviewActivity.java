package org.triprecorderng;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Collections;
import java.util.List;

/** Installed only in the isolated QA APK. Exercises the production dashboard entry point. */
public final class NismoPreviewActivity extends Activity implements ThisTripPage.Host {
    private final Handler handler = new Handler();
    private final Trip trip = new Trip();
    private Runnable refresh;
    private String scene;
    private int tick;
    private final Runnable advance = new Runnable() {
        public void run() {
            sample();
            refresh.run();
            handler.postDelayed(this,250);
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        scene = getIntent().getStringExtra("scene");
        if (scene == null) scene = "drive";
        if(scene.equals("header")) {
            L.setSetting(this,"en");
            Prefs.setDashboardTheme(this,Prefs.THEME_NISMO);
            int header=getIntent().getIntExtra("header",-1);
            if(header<0)getSharedPreferences("triprecng",MODE_PRIVATE).edit()
                    .remove("dashboard_header_mode").remove("nismo_header_mode").remove("dashboard_menu_mode").apply();
            else Prefs.setDashboardHeaderMode(this,header);
            startActivity(new android.content.Intent(this,MainActivity.class).putExtra("skip_alert",true));
            finish();return;
        }
        String mode = getIntent().getStringExtra("motion");
        boolean light = getIntent().getBooleanExtra("light",false);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        L.setSetting(this,scene.equals("thai") ? "th" : "en");
        Prefs.setDashboardTheme(this,Prefs.THEME_NISMO);
        Prefs.setAppearanceMode(this,light ? Appearance.LIGHT : Appearance.DARK);
        light = Prefs.lightAppearance(this);
        Ui.setAppearance(light);
        Prefs.setPerformanceRefresh(this,!scene.equals("eco"));
        Prefs.setMotionMode(this,SharedMotionController.Mode.valueOf(mode == null ? "FULL" : mode));
        Prefs.setEpaRangeKm(this,387.5);
        if(scene.equals("custom-range")){
            Prefs.setGt3Limits(this,new int[]{300,15000,120});
            Prefs.setSequentialGt3(this,false);
        }else if(scene.equals("range-restart")){
            if(Prefs.sequentialGt3(this) || Prefs.gt3Limit(this,SequentialGt3.Scale.POWER)!=300 ||
                    Prefs.gt3Limit(this,SequentialGt3.Scale.RPM)!=15000 || Prefs.gt3Limit(this,SequentialGt3.Scale.REGEN)!=120)
                throw new AssertionError("GT3 settings lost after process restart");
            Prefs.setSequentialGt3(this,true);
        }else {
            Prefs.setGt3Limits(this,new int[]{200,12000,100});
            Prefs.setSequentialGt3(this,true);
        }
        boolean selector = scene.equals("selector");
        if(!scene.equals("restore")) getSharedPreferences("triprecng",MODE_PRIVATE).edit()
                .putString("custom_gauge_gtr_nismo_0",selector ? "RPM" : "DEFAULT")
                .remove("custom_gauge_gtr_nismo_1").remove("custom_gauge_gtr_nismo_2").remove("custom_gauge_gtr_nismo_3")
                .putString("telemetry_metric",selector ? "SPEED" : "POWER")
                .putInt("telemetry_seconds",selector ? 300 : 60).apply();
        trip.distanceKm = 28.4;
        trip.startMs = System.currentTimeMillis()-1920000;
        trip.lastUpdateMs = System.currentTimeMillis();
        trip.movingSec = 1920;
        trip.elecStart = 100;
        trip.elecEnd = 104.8;
        trip.ended = scene.equals("oldtrip");
        if (trip.ended) trip.endMs = trip.lastUpdateMs;
        sample();
        if (!scene.equals("missing")) {
            TelemetryPicker history = new TelemetryPicker(this);
            long end = SystemClock.elapsedRealtime();
            for(int i=60;i>0;i--) {
                double wave = 58+22*Math.sin(i*.31)+18*Math.sin(i*.83);
                history.sample(end-1000L*i,i==22 ? Double.NaN : wave,86,17,33,64,4280);
            }
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(light ? 0xffeff1f3 : 0xff08090b);
        TextView label = new TextView(this);
        label.setTextColor(light ? 0xff152434 : 0xffbdc3ca);
        label.setGravity(Gravity.CENTER);
        label.setTextSize(13);
        label.setText("SIMULATED TELEMETRY  /  "+scene+"  /  "+(light ? "DAY" : "NIGHT")+"  /  "+mode);
        root.addView(label,new LinearLayout.LayoutParams(-1,Ui.dp(this,24)));
        root.addView(PerformanceDashboard.build(this,this),new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
        handler.postDelayed(advance,250);
    }
    private void sample() {
        long now = System.currentTimeMillis();
        boolean linked = !scene.equals("missing");
        boolean finishing=scene.equals("results");
        boolean drive = scene.equals("drive") || scene.equals("dynamic") || scene.equals("regen") || scene.equals("selector")
                || scene.equals("eco") || scene.equals("thai") || scene.equals("custom-range") || scene.equals("range-restart") || (finishing && tick<2);
        if(finishing && tick>=2 && !trip.ended){trip.ended=true;trip.endMs=now;}
        RecorderService.Live live = RecorderService.live;
        live.running = linked;
        live.lastTickWall = live.driveWall = live.motorPowerWall = live.frontRpmWall = live.telemetryWall = linked ? now : 0;
        live.vehicle = linked ? "QA" : "none";
        if(scene.equals("stale")){
            live.lastTickWall=live.driveWall=live.motorPowerWall=live.frontRpmWall=live.telemetryWall=now-11000;
        }
        live.speed = linked ? drive ? 86 : 0 : Double.NaN;
        live.motorPower = linked ? drive ? scene.equals("regen") ? -48 : 68 : 0 : Double.NaN;
        if (scene.equals("dynamic")) {
            live.speed = 70+10*Math.sin(tick*.21);
            live.motorPower = 28+110*Math.sin(tick*.25);
        }
        live.tripActive = drive;
        live.soc = linked ? 64 : Double.NaN;
        live.frontRpm = linked ? drive ? 4280 : 0 : Double.NaN;
        live.telemetry = new double[]{31,33,Double.NaN,407,3.230,3.236};
        live.gpsFix = linked;
        RecorderService.current = drive ? trip : null;
        if (scene.equals("charging")) {
            if (ChargeRecorder.current == null) ChargeRecorder.current = new Charge();
            ChargeRecorder.status = "Charging (AC)";
        } else { ChargeRecorder.current = null; ChargeRecorder.status = ""; }
        tick++;
    }
    public List<Trip> trips() { return trip.ended ? Collections.singletonList(trip) : Collections.emptyList(); }
    public void openTrip(Trip t) {}
    public void openSetting() {}
    public void refresher(Runnable r) { refresh = r; }
    @Override protected void onDestroy() { handler.removeCallbacks(advance); super.onDestroy(); }
}

package org.triprecorderng;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

/** Exercises the real Activity, including its handler, theme and view hierarchy, in the QA package. */
public final class DashboardPolicyTest extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }

    private <T> T ui(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        runOnMainSync(task);
        return task.get();
    }

    private static View find(View view, String description) {
        if (description.contentEquals(view.getContentDescription()==null ? "" : view.getContentDescription())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i=0;i<group.getChildCount();i++) {
                View match=find(group.getChildAt(i),description);
                if (match!=null) return match;
            }
        }
        return null;
    }

    private void testTheme(Context c, String theme, int mode) throws Exception {
        Prefs.setDashboardTheme(c,theme);
        Prefs.setDashboardHeaderMode(c,mode);
        Activity a=startActivitySync(new Intent(c,MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("skip_alert",true));
        try {
            waitForIdleSync();
            View decor=a.getWindow().getDecorView();
            View settings=ui(()->find(decor,"Setting"));
            View restore=ui(()->find(decor,"Show dashboard header"));
            boolean classic=Prefs.THEME_CLASSIC.equals(theme);
            check(settings!=null && restore!=null,theme+" shell");
            check(ui(()->Ui.lightAppearance)==classic,theme+" shell appearance");
            check(Prefs.appearanceMode(c)==Appearance.LIGHT,theme+" stored appearance");
            check(ui(()->settings.isShown() && !restore.isShown()),theme+" entry");
            View bar=(View)settings.getParent();
            check(ui(()->bar.getHeight())==Ui.dp(c,mode==1?48:64),theme+" bar height");
            if (mode==2) {
                SystemClock.sleep(DashboardMenu.AUTO_HIDE_MS+500);
                check(ui(()->restore.isShown() && !bar.isShown()),theme+" idle hide");
                Bitmap image=getUiAutomation().takeScreenshot();
                try (FileOutputStream out=new FileOutputStream(new File(c.getExternalFilesDir(null),theme+".png"))) {
                    check(image!=null && image.compress(Bitmap.CompressFormat.PNG,100,out),theme+" capture");
                } finally { if(image!=null)image.recycle(); }
                check(ui(()->restore.performClick()),theme+" restore");
                check(ui(()->settings.isShown() && settings.performClick()),theme+" settings");
                SystemClock.sleep(DashboardMenu.AUTO_HIDE_MS+300);
                check(ui(()->bar.isShown() && !restore.isShown()),theme+" settings timer");
            }
        } finally { ui(()->{a.finish();return true;}); waitForIdleSync(); }
    }

    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            Context c=getTargetContext();
            L.setSetting(c,"en");
            SharedPreferences p=c.getSharedPreferences("triprecng",Context.MODE_PRIVATE);
            for(int mode=0;mode<3;mode++){
                p.edit().remove("dashboard_header_mode").putInt("nismo_header_mode",mode).commit();
                check(Prefs.dashboardHeaderMode(c)==mode && p.getInt("dashboard_header_mode",-1)==mode,"migration "+mode);
            }
            p.edit().remove("dashboard_header_mode").remove("nismo_header_mode").putInt("dashboard_menu_mode",0).commit();
            check(Prefs.dashboardHeaderMode(c)==2,"independent default");
            Prefs.setAppearanceMode(c,Appearance.LIGHT);
            for(String theme:Prefs.dashboardThemes()){
                for(int mode=0;mode<3;mode++) testTheme(c,theme,mode);
                android.util.Log.i("DashboardPolicyTest","PASS "+theme);
            }
            Prefs.setDashboardTheme(c,Prefs.THEME_CLASSIC);
            check(Prefs.lightAppearance(c),"restore Classic Light");
            Prefs.setAppearanceMode(c,Appearance.DARK);
            check(!Prefs.lightAppearance(c),"Classic Dark");
            result.putString("result","PASS universal dashboard");
            finish(Activity.RESULT_OK,result);
        } catch(Throwable e){
            result.putString("failure",android.util.Log.getStackTraceString(e));
            finish(Activity.RESULT_CANCELED,result);
        }
    }
}

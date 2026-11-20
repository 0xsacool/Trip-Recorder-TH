package org.triprecorderng;

import android.content.Context;
import android.os.SystemClock;

/** One user-requested attempt. ADB approval is required, and failure leaves the BYD dialog open. */
final class AutoStartRepair {
    enum Result { FIXED, ALREADY_OFF, ADB_UNAVAILABLE, NOT_RECOGNIZED, NOT_VERIFIED, MOVING }
    static volatile boolean busy;
    private static final String DUMP = "uiautomator dump /data/local/tmp/evtriplog_autostart.xml >/dev/null 2>&1 " +
            "&& cat /data/local/tmp/evtriplog_autostart.xml; rm -f /data/local/tmp/evtriplog_autostart.xml";
    private static AdbLoopback.Result shell(Context c,String command) {
        return new AdbLoopback(c).runShell(command,20_000);
    }
    static boolean moving() {
        RecorderService.Live live=RecorderService.live;
        long age=System.currentTimeMillis()-live.driveWall;
        return age>=0&&age<3000&&Double.isFinite(live.speed)&&Math.abs(live.speed)>0.5;
    }
    private static AutoStartUi.Target read(Context c) {
        AdbLoopback.Result r=shell(c,DUMP);
        if(r.status!=AdbLoopback.Status.OK)return null;
        return AutoStartUi.inspect(r.output);
    }
    static Result run(Context c) {
        if(moving())return Result.MOVING;
        SystemClock.sleep(500);
        AutoStartUi.Target first=read(c);
        if(first==null)return Result.ADB_UNAVAILABLE;
        // Only scroll a uniquely identified BYD list. Never swipe at fixed screen coordinates.
        for(int i=0;i<4&&first.state==AutoStartUi.State.NOT_FOUND&&first.endY>0;i++) {
            if(moving())return Result.MOVING;
            if(shell(c,"input swipe "+first.x+" "+first.y+" "+first.x+" "+first.endY+" 350").status!=AdbLoopback.Status.OK)
                return Result.NOT_RECOGNIZED;
            SystemClock.sleep(350);
            first=read(c);
            if(first==null)return Result.ADB_UNAVAILABLE;
        }
        if(first.state==AutoStartUi.State.OFF)return Result.ALREADY_OFF;
        if(first.state!=AutoStartUi.State.ON)return Result.NOT_RECOGNIZED;
        // Require an independent, fresh read of the same recognized control before tapping.
        AutoStartUi.Target target=read(c);
        if(target==null)return Result.ADB_UNAVAILABLE;
        if(target.state==AutoStartUi.State.OFF)return Result.ALREADY_OFF;
        if(target.state!=AutoStartUi.State.ON||target.x!=first.x||target.y!=first.y)return Result.NOT_RECOGNIZED;
        if(moving())return Result.MOVING;
        if(shell(c,"input tap "+target.x+" "+target.y).status!=AdbLoopback.Status.OK)return Result.NOT_VERIFIED;
        SystemClock.sleep(500);
        AutoStartUi.Target verified=read(c);
        return verified!=null&&verified.state==AutoStartUi.State.OFF?Result.FIXED:Result.NOT_VERIFIED;
    }
    static void returnToApp(Context c) {
        if(!moving())shell(c,"am start -n com.evtriplog.app/org.triprecorderng.MainActivity");
    }
}

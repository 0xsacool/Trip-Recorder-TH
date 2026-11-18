package org.triprecorderng;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Tiny rolling diagnostics log kept next to the app's other files. */
final class Diag {
    private static final String TAG = "TripRec";
    private static final long MAX_BYTES = 512 * 1024;
    private static File file;
    private static final SimpleDateFormat FMT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

    private Diag() {}

    static synchronized void init(Context ctx) {
        if (file != null) return;
        File dir = ctx.getExternalFilesDir(null);
        if (dir == null) dir = ctx.getFilesDir();
        file = new File(dir, "diag.log");
    }

    static File file() {
        return file;
    }

    /** User-triggered export: only charging/source selection diagnostics, not trip location data. */
    static synchronized File exportCharging(Context ctx) throws java.io.IOException {
        init(ctx);
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        File out = new File(Exporter.dir(ctx), "charging-diagnostics-" + stamp + ".log");
        try (FileWriter w = new FileWriter(out)) {
            for (File src : new File[]{new File(file.getPath() + ".1"), file}) {
                if (!src.isFile()) continue;
                try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(src))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (line.contains("  charge: ") || line.contains("  probe -> ")
                                || line.contains("  tick error")) w.write(line + "\n");
                    }
                }
            }
        }
        return out;
    }

    static synchronized void log(String msg) {
        Log.i(TAG, msg);
        if (file == null) return;
        try {
            if (file.length() > MAX_BYTES) {
                File old = new File(file.getPath() + ".1");
                //noinspection ResultOfMethodCallIgnored
                old.delete();
                //noinspection ResultOfMethodCallIgnored
                file.renameTo(old);
            }
            FileWriter w = new FileWriter(file, true);
            w.write(FMT.format(new Date()) + "  " + msg + "\n");
            w.close();
        } catch (Throwable ignored) {
            // logging must never break recording
        }
    }

    static void log(String msg, Throwable t) {
        log(msg + ": " + t.getClass().getSimpleName() + ": " + t.getMessage());
    }
}

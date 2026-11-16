package org.triprecorderng;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import java.util.Arrays;
import java.util.List;

/**
 * Optional GT-R-inspired dashboard; consumes the same read-only snapshots as ThisTripPage.
 * No changes to recording, vehicle commands, database, or signing. Motor power is not exposed
 * by the existing vehicle source, so this displays battery and consumption gauges and real speed samples.
 */
final class PerformanceDashboard extends View {
    private static final int INK = 0xFFF2F4F7, MUTED = 0xFFADB6C2;
    private static final int CYAN = 0xFF43D9EC, RED = 0xFFFF535D, SILVER = 0xFF9AA7B4;
    private static final int PANEL = 0xFF13191F, LINE = 0xFF343D46;
    private static final int SAMPLES = 60;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Typeface normal = Typeface.create("sans-serif", Typeface.NORMAL);
    private final Typeface bold = Typeface.create("sans-serif-condensed", Typeface.BOLD);
    private final Context c;
    private final ThisTripPage.Host host;
    private final double[] history = new double[SAMPLES];
    private int sampleCount;
    private long lastSample;
    private double speed = Double.NaN, soc = Double.NaN, range = Double.NaN;
    private double average = Double.NaN, distance = Double.NaN, energy = Double.NaN;
    private String elapsed = "--", money = "--", tripLabel = "", status = "", source = "";
    private int statusColor = MUTED;
    private Trip shown;

    static View build(Context c, ThisTripPage.Host host) {
        final PerformanceDashboard view = new PerformanceDashboard(c, host);
        host.refresher(new Runnable() {
            @Override public void run() { view.refresh(); }
        });
        view.refresh();
        return view;
    }

    private PerformanceDashboard(Context c, ThisTripPage.Host host) {
        super(c);
        this.c = c;
        this.host = host;
        Arrays.fill(history, Double.NaN);
        setBackgroundColor(0xFF090D11);
        setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                if (shown != null) host.openTrip(shown);
                else host.openSetting();
            }
        });
    }

    private static boolean valid(double v) { return !Double.isNaN(v) && !Double.isInfinite(v); }
    private static double positive(double v) { return valid(v) && v >= 0 ? v : Double.NaN; }
    private static String number(double v, String pattern) { return valid(v) ? Ui.f(v, pattern) : "--"; }

    private void refresh() {
        RecorderService.Live live = RecorderService.live;
        long now = System.currentTimeMillis();
        boolean fresh = live.running && live.lastTickWall > 0
                && now >= live.lastTickWall && now - live.lastTickWall < 10000;
        boolean linked = fresh && !"none".equals(live.vehicle) && !"GPS only".equals(live.vehicle);
        speed = fresh ? positive(live.speed) : Double.NaN;
        soc = linked && valid(live.soc) && live.soc >= 0 && live.soc <= 100 ? live.soc : Double.NaN;
        range = linked ? positive(live.elecRange) : Double.NaN;

        Trip current = RecorderService.current;
        List<Trip> trips = host.trips();
        shown = current != null ? current : (trips.isEmpty() ? null : trips.get(0));
        tripLabel = current != null ? L.t("Current trip") : L.t("Last trip");
        average = shown == null ? Double.NaN : positive(shown.energyPer100Km());
        distance = shown == null ? Double.NaN : positive(shown.distanceKm);
        energy = shown == null ? Double.NaN : positive(shown.energyUsed());
        elapsed = shown == null ? "--" : Ui.duration(shown.drivingSec());
        double cost = shown == null ? Double.NaN : Cost.total(shown, c);
        money = valid(cost) ? Cost.money(c, cost) : "--";

        String charging = ChargeRecorder.status;
        if (!live.running) {
            status = L.t("Service stopped"); statusColor = RED;
        } else if (!fresh) {
            status = L.t("Waiting for vehicle data"); statusColor = MUTED;
        } else if (charging != null && !charging.isEmpty()) {
            status = charging; statusColor = CYAN;
        } else {
            status = current != null ? L.t("Recording") : L.t("Not recording");
            statusColor = current != null ? CYAN : MUTED;
        }
        source = L.t("Vehicle data") + ": " + (linked ? L.t("Connected")
                : fresh && "GPS only".equals(live.vehicle) ? L.t("GPS only") : "--");
        source += "  |  GPS: " + (fresh && live.gpsFix ? L.t("fix") : "--");
        if (Prefs.autoStartHealth(c) == Prefs.AUTOSTART_BLOCKED)
            source = L.t("Auto-start is blocked by BYD - see Setting");

        if (now - lastSample >= 900 || now < lastSample) {
            if (lastSample != 0 && (now < lastSample || now - lastSample > 3500)) sampleCount = 0;
            if (sampleCount < SAMPLES) history[sampleCount++] = speed;
            else {
                System.arraycopy(history, 1, history, 0, SAMPLES - 1);
                history[SAMPLES - 1] = speed;
            }
            lastSample = now;
        }
        setContentDescription("GT-R Performance. " + status + ". " + source + ". "
                + L.t("Speed") + " " + number(speed, "%.0f") + " km/h. "
                + L.t("Battery") + " " + number(soc, "%.0f") + "%. "
                + tripLabel + ": " + number(distance, "%.1f") + " km, "
                + L.t("Energy") + " " + number(energy, "%.1f") + " kWh, "
                + L.t("Cost") + " " + money + ".");
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() <= 0 || getHeight() <= 0) return;
        float scale = Math.min(getWidth() / 1000f, getHeight() / 470f);
        canvas.save();
        canvas.translate((getWidth() - 1000f * scale) / 2f, (getHeight() - 470f * scale) / 2f);
        canvas.scale(scale, scale);
        fill(canvas, 0, 0, 1000, 470, 0xFF090D11);
        fill(canvas, 0, 0, 1000, 62, PANEL);
        fill(canvas, 0, 61, 1000, 63, RED);
        text(canvas, "GT-R PERFORMANCE", 20, 26, 23, INK, true, Paint.Align.LEFT, 380);
        text(canvas, source, 20, 49, 17, MUTED, false, Paint.Align.LEFT, 650);
        text(canvas, status, 980, 28, 21, statusColor, true, Paint.Align.RIGHT, 330);
        text(canvas, tripLabel, 980, 50, 17, MUTED, false, Paint.Align.RIGHT, 260);

        int batteryColor = valid(soc) && soc < 15 ? RED : valid(soc) && soc < 30 ? 0xFFFFB44D : CYAN;
        gauge(canvas, 184, 211, 132, 100, soc, batteryColor,
                L.t("Battery"), number(soc, "%.0f"), "%", 4);
        // Consumption dial is trip average, not a false real-time motor reading.
        gauge(canvas, 816, 211, 132, 40, average, CYAN,
                L.t("Average consumption"), number(average, "%.1f"), "kWh/100 km", 4);
        text(canvas, L.t("Speed"), 500, 109, 22, MUTED, false, Paint.Align.CENTER, 290);
        text(canvas, number(speed, "%.0f"), 500, 207, 105, INK, true, Paint.Align.CENTER, 290);
        text(canvas, "km/h", 500, 244, 26, MUTED, false, Paint.Align.CENTER, 200);
        fill(canvas, 366, 260, 634, 262, LINE);
        text(canvas, L.t("Remaining range"), 500, 292, 20, MUTED, false, Paint.Align.CENTER, 280);
        text(canvas, number(range, "%.0f") + " km", 500, 335, 41, INK, true, Paint.Align.CENTER, 285);

        fill(canvas, 0, 359, 1000, 470, PANEL);
        fill(canvas, 0, 359, 1000, 361, LINE);
        cell(canvas, 0, 175, L.t("Trip"), number(distance, "%.1f") + " km");
        cell(canvas, 175, 345, L.t("Time"), elapsed);
        cell(canvas, 345, 510, L.t("Energy"), number(energy, "%.1f") + " kWh");
        cell(canvas, 510, 685, L.t("Cost"), money);
        graph(canvas, 709, 374, 983, 449);
        canvas.restore();
    }

    private void gauge(Canvas cv, float x, float y, float r, int max, double value,
                       int accent, String title, String reading, String unit, int divisions) {
        p.setStyle(Paint.Style.FILL); p.setColor(PANEL);
        cv.drawCircle(x, y, r, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(4); p.setColor(SILVER);
        cv.drawCircle(x, y, r, p);
        p.setStrokeWidth(2); p.setColor(LINE);
        cv.drawCircle(x, y, r - 7, p);
        RectF arc = new RectF(x-r+13, y-r+13, x+r-13, y+r-13);
        p.setStrokeWidth(8); p.setColor(LINE);
        cv.drawArc(arc, 135, 270, false, p);
        if (valid(value)) {
            p.setColor(accent);
            cv.drawArc(arc, 135, (float)(270 * Math.min(max, value) / max), false, p);
        }
        for (int i = 0; i <= divisions * 5; i++) {
            double a = Math.toRadians(135 + 270.0 * i / (divisions * 5));
            boolean major = i % 5 == 0;
            float co = (float)Math.cos(a), si = (float)Math.sin(a);
            p.setColor(major ? INK : SILVER);
            p.setStrokeWidth(major ? 2.5f : 1.2f);
            cv.drawLine(x + co*(r-20), y + si*(r-20), x + co*(r-(major?32:25)), y + si*(r-(major?32:25)), p);
            if (major) text(cv, String.valueOf(max * i / (divisions*5)), x+co*(r-45),
                    y+si*(r-45)+5, 15, MUTED, false, Paint.Align.CENTER, 35);
        }
        text(cv, title, x, y-24, 19, MUTED, false, Paint.Align.CENTER, r*1.35f);
        text(cv, reading, x, y+28, 54, INK, true, Paint.Align.CENTER, r*1.32f);
        text(cv, unit, x, y+52, 17, MUTED, false, Paint.Align.CENTER, r*1.3f);
        // Short outer needle shows the dial value without covering the readout.
        if (valid(value)) {
            double a = Math.toRadians(135 + 270.0*Math.min(max, value)/max);
            float co = (float)Math.cos(a), si = (float)Math.sin(a);
            p.setStyle(Paint.Style.STROKE); p.setColor(RED); p.setStrokeWidth(4);
            cv.drawLine(x+co*(r-30), y+si*(r-30), x+co*(r-9), y+si*(r-9), p);
        }
        p.setStyle(Paint.Style.FILL);
    }

    private void cell(Canvas cv, float left, float right, String label, String value) {
        float x = (left+right)/2;
        text(cv, label, x, 397, 18, MUTED, false, Paint.Align.CENTER, right-left-12);
        text(cv, value, x, 444, 32, INK, true, Paint.Align.CENTER, right-left-12);
        fill(cv, right-1, 379, right+1, 451, LINE);
    }

    private void graph(Canvas cv, float x, float y, float r, float b) {
        text(cv, L.t("Speed history") + " (km/h)", x, y+14, 15, MUTED, false, Paint.Align.LEFT, r-x);
        float top = y+24;
        fill(cv, x, top, r, b, 0xFF0A1014);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1); p.setColor(LINE);
        cv.drawLine(x, (top+b)/2, r, (top+b)/2, p);
        cv.drawLine(x, b, r, b, p);
        Path path = new Path();
        boolean started = false;
        for (int i=0; i<sampleCount; i++) {
            if (!valid(history[i])) { started = false; continue; }
            float sx=x+(r-x)*(SAMPLES-sampleCount+i)/(SAMPLES-1);
            float sy=b-(b-top)*(float)Math.min(180, history[i])/180;
            if (started) path.lineTo(sx, sy); else path.moveTo(sx, sy);
            started = true;
        }
        p.setStrokeWidth(2.5f); p.setColor(CYAN);
        cv.drawPath(path,p);
        p.setStyle(Paint.Style.FILL);
    }

    private void fill(Canvas cv, float x, float y, float r, float b, int color) {
        p.setStyle(Paint.Style.FILL); p.setColor(color); cv.drawRect(x,y,r,b,p);
    }

    private void text(Canvas cv, String s, float x, float baseline, float size, int color,
                      boolean heavy, Paint.Align align, float maxWidth) {
        p.setStyle(Paint.Style.FILL); p.setTypeface(heavy ? bold : normal);
        p.setTextAlign(align); p.setTextSize(size); p.setColor(color);
        float width = p.measureText(s);
        if (width > maxWidth) p.setTextSize(size * maxWidth / width);
        cv.drawText(s,x,baseline,p);
    }
}

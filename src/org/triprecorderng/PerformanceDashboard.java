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
 * Optional GT-R and Type R inspired dashboards; consume the same read-only snapshots as ThisTripPage.
 * No vehicle commands. Power and thermal values are read-only, never simulated.
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
    private final boolean typeR;
    private final Typeface italic = Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC);
    private final ThisTripPage.Host host;
    private final double[] history = new double[SAMPLES];
    private int sampleCount;
    private long lastSample;
    private double speed = Double.NaN, soc = Double.NaN, range = Double.NaN;
    private double average = Double.NaN, distance = Double.NaN, energy = Double.NaN;
    private double motorPower = Double.NaN, minTemp = Double.NaN, maxTemp = Double.NaN;
    private double minCell = Double.NaN, maxCell = Double.NaN, chargeVoltage = Double.NaN;
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
        this.typeR = Prefs.typeRTheme(c);
        this.host = host;
        Arrays.fill(history, Double.NaN);
        setBackgroundColor(typeR ? 0xFF08090B : 0xFF090D11);
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
        boolean telemetryFresh = linked && live.telemetryWall > 0 && now >= live.telemetryWall
                && now - live.telemetryWall < 10000;
        double[] data = live.telemetry;
        motorPower = telemetryFresh ? data[2] : Double.NaN;
        minTemp = telemetryFresh ? data[0] : Double.NaN;
        maxTemp = telemetryFresh ? data[1] : Double.NaN;
        chargeVoltage = telemetryFresh ? data[3] : Double.NaN;
        minCell = telemetryFresh ? data[4] : Double.NaN;
        maxCell = telemetryFresh ? data[5] : Double.NaN;

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

        if (live.telemetryWall != lastSample || (!telemetryFresh && sampleCount > 0 && valid(history[sampleCount-1]))) {
            if (!telemetryFresh || (lastSample != 0 && (now < lastSample || now - lastSample > 12000))) sampleCount = 0;
            if (sampleCount < SAMPLES) history[sampleCount++] = motorPower;
            else {
                System.arraycopy(history, 1, history, 0, SAMPLES - 1);
                history[SAMPLES - 1] = motorPower;
            }
            lastSample = live.telemetryWall;
        }
        setContentDescription((typeR ? "Type R Edition. " : "GT-R Performance. ") + status + ". " + source + ". "
                + L.t("Speed") + " " + number(speed, "%.0f") + " km/h. "
                + L.t("Battery") + " " + number(soc, "%.0f") + "%. "
                + "Cell temperature min/max " + number(minTemp, "%.0f") + "/"
                + number(maxTemp, "%.0f") + " C. Power " + number(motorPower, "%.1f") + " kW. "
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
        if (typeR) {
            drawTypeR(canvas);
            canvas.restore();
            return;
        }
        fill(canvas, 0, 0, 1000, 470, 0xFF090D11);
        fill(canvas, 0, 0, 1000, 62, PANEL);
        fill(canvas, 0, 61, 1000, 63, RED);
        text(canvas, "GT-R PERFORMANCE", 20, 26, 23, INK, true, Paint.Align.LEFT, 380);
        text(canvas, source, 20, 49, 17, MUTED, false, Paint.Align.LEFT, 650);
        text(canvas, status, 980, 28, 21, statusColor, true, Paint.Align.RIGHT, 330);
        text(canvas, tripLabel, 980, 50, 17, MUTED, false, Paint.Align.RIGHT, 260);

        int batteryColor = valid(soc) && soc < 15 ? RED : valid(soc) && soc < 30 ? 0xFFFFB44D : CYAN;
        gauge(canvas, 176, 190, 121, -100, 200, motorPower, motorPower < 0 ? CYAN : RED,
                motorPower < 0 ? "REGEN" : "DRIVE POWER", number(motorPower, "%.0f"), "kW", 6);
        gauge(canvas, 824, 190, 121, 0, 40, average, CYAN,
                L.t("Average consumption"), number(average, "%.1f"), "kWh/100 km", 4);
        text(canvas, L.t("Speed"), 500, 88, 17, MUTED, false, Paint.Align.CENTER, 260);
        text(canvas, number(speed, "%.0f"), 500, 171, 91, INK, true, Paint.Align.CENTER, 260);
        text(canvas, "km/h", 500, 195, 20, MUTED, false, Paint.Align.CENTER, 200);
        fill(canvas, 365, 204, 635, 206, RED);
        text(canvas, L.t("Battery") + "  " + number(soc, "%.0f") + "%", 500, 236, 29, INK, true, Paint.Align.CENTER, 250);
        fill(canvas, 370, 246, 630, 255, LINE);
        if (valid(soc)) fill(canvas, 370, 246, 370+(float)soc*2.6f, 255, batteryColor);
        text(canvas, L.t("Remaining range"), 500, 277, 16, MUTED, false, Paint.Align.CENTER, 250);
        text(canvas, number(range, "%.0f") + " km", 500, 307, 29, INK, true, Paint.Align.CENTER, 250);

        fill(canvas, 0, 319, 1000, 372, PANEL);
        telemetryCell(canvas, 0, 346, "BATTERY TEMP MIN / MAX", number(minTemp, "%.0f") + " / " + number(maxTemp, "%.0f") + " °C");
        telemetryCell(canvas, 346, 690, "CELL VOLTAGE MIN / MAX", number(minCell, "%.3f") + " / " + number(maxCell, "%.3f") + " V");
        telemetryCell(canvas, 690, 1000, "CHARGING VOLTAGE", number(chargeVoltage, "%.0f") + " V");
        fill(canvas, 0, 377, 1000, 470, PANEL);
        fill(canvas, 0, 377, 1000, 379, LINE);
        cell(canvas, 0, 175, L.t("Trip"), number(distance, "%.1f") + " km");
        cell(canvas, 175, 345, L.t("Time"), elapsed);
        cell(canvas, 345, 510, L.t("Energy"), number(energy, "%.1f") + " kWh");
        cell(canvas, 510, 685, L.t("Cost"), money);
        graph(canvas, 709, 386, 983, 459);
        canvas.restore();
    }

    private static final int TYPE_R_RED = 0xFFF01930;
    private static final int TYPE_R_PANEL = 0xFF121315;
    private static final int TYPE_R_LINE = 0xFF33363A;

    /** Type R layout: large central speed, segmented signed power, balanced side metrics. */
    private void drawTypeR(Canvas cv) {
        fill(cv, 0, 0, 1000, 470, 0xFF08090B);
        fill(cv, 0, 0, 1000, 55, TYPE_R_PANEL);
        sportText(cv, "TYPE R EDITION", 18, 28, 26, INK, Paint.Align.LEFT, 340);
        text(cv, source, 18, 47, 14, MUTED, false, Paint.Align.LEFT, 650);
        text(cv, status, 980, 25, 19, statusColor, true, Paint.Align.RIGHT, 300);
        text(cv, tripLabel, 980, 46, 15, MUTED, false, Paint.Align.RIGHT, 240);
        fill(cv, 0, 55, 1000, 58, TYPE_R_RED);

        // The bar is actual signed traction power in kW, not battery percentage.
        boolean regen = valid(motorPower) && motorPower < 0;
        int accent = regen ? CYAN : TYPE_R_RED;
        double maxPower = regen ? 100.0 : 200.0;
        double progress = valid(motorPower) ? Math.min(1.0, Math.abs(motorPower) / maxPower) : 0;
        text(cv, regen ? "REGEN" : "POWER", 18, 84, 20, INK, true, Paint.Align.LEFT, 148);
        text(cv, regen ? "0 - 100 kW" : "0 - 200 kW", 18, 106, 12, MUTED, false, Paint.Align.LEFT, 148);
        segmentedPower(cv, 166, 71, 818, 106, progress, accent);
        sportText(cv, number(motorPower, "%.0f") + " kW", 980, 100, 32, INK, Paint.Align.RIGHT, 150);
        fill(cv, 18, 117, 982, 119, TYPE_R_LINE);

        // Keep the visual hierarchy of the approved Type R concept.
        p.setStyle(Paint.Style.STROKE); p.setColor(TYPE_R_LINE); p.setStrokeWidth(2);
        cv.drawLine(316, 130, 276, 306, p);
        cv.drawLine(724, 130, 684, 306, p);
        p.setStyle(Paint.Style.FILL);

        text(cv, L.t("Battery"), 22, 151, 17, MUTED, false, Paint.Align.LEFT, 235);
        sportText(cv, number(soc, "%.0f") + "%", 22, 202, 51, INK, Paint.Align.LEFT, 235);
        fill(cv, 22, 211, 251, 218, TYPE_R_LINE);
        if (valid(soc)) fill(cv, 22, 211, 22 + (float) soc * 2.29f, 218,
                soc < 15 ? TYPE_R_RED : soc < 30 ? 0xFFFFB44D : INK);
        fill(cv, 22, 228, 252, 230, TYPE_R_RED);
        text(cv, L.t("Remaining range"), 22, 255, 16, MUTED, false, Paint.Align.LEFT, 235);
        sportText(cv, number(range, "%.0f") + " km", 22, 297, 41, INK, Paint.Align.LEFT, 235);

        text(cv, L.t("Speed"), 500, 151, 16, MUTED, false, Paint.Align.CENTER, 320);
        sportText(cv, number(speed, "%.0f"), 500, 252, 119, INK, Paint.Align.CENTER, 340);
        text(cv, "km/h", 500, 277, 21, MUTED, true, Paint.Align.CENTER, 200);
        // These are indicators, not buttons or mode commands.
        boolean driving = valid(motorPower) && motorPower > 0;
        boolean recovering = valid(motorPower) && motorPower < 0;
        slant(cv, 362, 287, 496, 310, driving ? TYPE_R_RED : TYPE_R_LINE);
        slant(cv, 506, 287, 640, 310, recovering ? CYAN : TYPE_R_LINE);
        text(cv, "POWER", 429, 304, 14, INK, true, Paint.Align.CENTER, 112);
        text(cv, "REGEN", 573, 304, 14, recovering ? 0xFF08090B : INK, true, Paint.Align.CENTER, 112);

        text(cv, L.t("Trip"), 744, 151, 17, MUTED, false, Paint.Align.LEFT, 235);
        sportText(cv, number(distance, "%.1f") + " km", 978, 199, 42, INK, Paint.Align.RIGHT, 235);
        fill(cv, 744, 228, 978, 230, TYPE_R_RED);
        text(cv, L.t("Time"), 744, 255, 16, MUTED, false, Paint.Align.LEFT, 235);
        sportText(cv, elapsed, 978, 297, 43, INK, Paint.Align.RIGHT, 235);

        fill(cv, 0, 321, 1000, 371, TYPE_R_PANEL);
        typeRMetric(cv, 18, 339, 362, 330, "BATTERY TEMP MIN / MAX",
                number(minTemp, "%.0f") + " / " + number(maxTemp, "%.0f") + " °C", 22);
        typeRMetric(cv, 353, 339, 362, 323, "CELL VOLTAGE MIN / MAX",
                number(minCell, "%.3f") + " / " + number(maxCell, "%.3f") + " V", 22);
        typeRMetric(cv, 710, 339, 362, 272, "CHARGING VOLTAGE",
                number(chargeVoltage, "%.0f") + " V", 22);
        fill(cv, 338, 329, 340, 363, TYPE_R_LINE);
        fill(cv, 695, 329, 697, 363, TYPE_R_LINE);

        fill(cv, 18, 381, 982, 383, TYPE_R_RED);
        typeRMetric(cv, 22, 409, 451, 302, L.t("Energy"), number(energy, "%.1f") + " kWh", 35);
        typeRMetric(cv, 353, 409, 451, 325, L.t("Average consumption"),
                number(average, "%.1f") + " kWh/100 km", 35);
        typeRMetric(cv, 710, 409, 451, 272, L.t("Cost"), money, 35);
        fill(cv, 338, 396, 340, 451, TYPE_R_LINE);
        fill(cv, 695, 396, 697, 451, TYPE_R_LINE);
    }

    private void typeRMetric(Canvas cv, float left, float labelY, float valueY, float maxWidth,
                             String label, String value, float size) {
        text(cv, label, left, labelY, 14, MUTED, false, Paint.Align.LEFT, maxWidth);
        sportText(cv, value, left, valueY, size, INK, Paint.Align.LEFT, maxWidth);
    }

    private void segmentedPower(Canvas cv, float left, float top, float right, float bottom,
                                double percent, int color) {
        final int count = 12;
        float pitch = (right - left) / count;
        for (int i = 0; i < count; i++) {
            float x = left + i * pitch;
            slant(cv, x, top, x + pitch - 3, bottom, TYPE_R_LINE);
            double fraction = Math.max(0, Math.min(1, percent * count - i));
            if (fraction > 0) {
                cv.save();
                cv.clipRect(x, top, x + (float) fraction * (pitch - 3), bottom);
                slant(cv, x, top, x + pitch - 3, bottom, color);
                cv.restore();
            }
        }
    }

    private void slant(Canvas cv, float left, float top, float right, float bottom, int color) {
        float skew = Math.min(12, (right-left)/4);
        Path path = new Path();
        path.moveTo(left + skew, top);
        path.lineTo(right, top);
        path.lineTo(right - skew, bottom);
        path.lineTo(left, bottom);
        path.close();
        p.setStyle(Paint.Style.FILL); p.setColor(color);
        cv.drawPath(path, p);
    }

    private void sportText(Canvas cv, String value, float x, float y, float size, int color,
                           Paint.Align align, float maxWidth) {
        p.setStyle(Paint.Style.FILL); p.setTypeface(italic);
        p.setTextAlign(align); p.setTextSize(size); p.setColor(color);
        float width = p.measureText(value);
        if (width > maxWidth) p.setTextSize(size * maxWidth / width);
        cv.drawText(value, x, y, p);
    }

    private void gauge(Canvas cv, float x, float y, float r, int min, int max, double value,
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
            double clamped = Math.max(min, Math.min(max, value));
            double zero = Math.max(min, 0);
            float a = (float)(135 + 270 * (zero - min) / (max - min));
            cv.drawArc(arc, a, (float)(270 * (clamped - zero) / (max - min)), false, p);
        }
        for (int i = 0; i <= divisions * 5; i++) {
            double a = Math.toRadians(135 + 270.0 * i / (divisions * 5));
            boolean major = i % 5 == 0;
            float co = (float)Math.cos(a), si = (float)Math.sin(a);
            p.setColor(major ? INK : SILVER);
            p.setStrokeWidth(major ? 2.5f : 1.2f);
            cv.drawLine(x + co*(r-20), y + si*(r-20), x + co*(r-(major?32:25)), y + si*(r-(major?32:25)), p);
            if (major) text(cv, String.valueOf(min + (max-min) * i / (divisions*5)), x+co*(r-41),
                    y+si*(r-45)+5, 15, MUTED, false, Paint.Align.CENTER, 35);
        }
        // Keep titles entirely inside the inner dial, away from tick numbers.
        text(cv, title, x, y-23, 15, MUTED, false, Paint.Align.CENTER, r*0.96f);
        text(cv, reading, x, y+24, 49, INK, true, Paint.Align.CENTER, r*1.07f);
        text(cv, unit, x, y+46, 15, MUTED, false, Paint.Align.CENTER, r*1.0f);
        // Short outer needle shows the dial value without covering the readout.
        if (valid(value)) {
            double a = Math.toRadians(135 + 270.0*(Math.max(min, Math.min(max, value))-min)/(max-min));
            float co = (float)Math.cos(a), si = (float)Math.sin(a);
            p.setStyle(Paint.Style.STROKE); p.setColor(RED); p.setStrokeWidth(4);
            cv.drawLine(x+co*(r-30), y+si*(r-30), x+co*(r-9), y+si*(r-9), p);
        }
        p.setStyle(Paint.Style.FILL);
    }

    private void telemetryCell(Canvas cv, float left, float right, String label, String reading) {
        float x = (left+right)/2;
        text(cv, label, x, 337, 13, MUTED, false, Paint.Align.CENTER, right-left-14);
        text(cv, reading, x, 362, 24, INK, true, Paint.Align.CENTER, right-left-14);
        fill(cv, right-1, 326, right+1, 365, LINE);
    }

    private void cell(Canvas cv, float left, float right, String label, String value) {
        float x = (left+right)/2;
        text(cv, label, x, 407, 16, MUTED, false, Paint.Align.CENTER, right-left-12);
        text(cv, value, x, 448, 28, INK, true, Paint.Align.CENTER, right-left-12);
        fill(cv, right-1, 391, right+1, 457, LINE);
    }

    private void graph(Canvas cv, float x, float y, float r, float b) {
        text(cv, "POWER HISTORY (kW)", x, y+14, 15, MUTED, false, Paint.Align.LEFT, r-x);
        float top = y+24;
        fill(cv, x, top, r, b, 0xFF0A1014);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1); p.setColor(LINE);
        cv.drawLine(x, b-(b-top)/3, r, b-(b-top)/3, p);
        cv.drawLine(x, b, r, b, p);
        Path path = new Path();
        boolean started = false;
        for (int i=0; i<sampleCount; i++) {
            if (!valid(history[i])) { started = false; continue; }
            float sx=x+(r-x)*(SAMPLES-sampleCount+i)/(SAMPLES-1);
            float sy=b-(b-top)*(float)(Math.max(-100, Math.min(200, history[i]))+100)/300;
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

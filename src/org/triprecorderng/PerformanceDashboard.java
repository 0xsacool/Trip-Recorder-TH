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
 * Optional sport-inspired dashboards; consume the same read-only snapshots as ThisTripPage.
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
    private final boolean typeR, gr;
    private final String theme;
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
        this.gr = Prefs.grTheme(c);
        this.theme = Prefs.dashboardTheme(c);
        this.host = host;
        Arrays.fill(history, Double.NaN);
        setBackgroundColor(gr ? 0xFF080808 : typeR ? 0xFF08090B : 0xFF090D11);
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

    private String energyNumber(double v) { return shown == null ? "--" : shown.energyText(v, "%.1f"); }

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
        money = valid(cost) ? shown.energyLabel(Cost.money(c, cost)) : "--";

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
        setContentDescription(Prefs.themeName(theme) + ". " + status + ". " + source + ". "
                + L.t("Speed") + " " + number(speed, "%.0f") + " km/h. "
                + L.t("Battery") + " " + number(soc, "%.0f") + "%. "
                + "Cell temperature min/max " + number(minTemp, "%.0f") + "/"
                + number(maxTemp, "%.0f") + " C. Power " + number(motorPower, "%.1f") + " kW. "
                + tripLabel + ": " + number(distance, "%.1f") + " km, "
                + L.t("Energy") + " " + energyNumber(energy) + " kWh, "
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
        if (Prefs.THEME_U9_XTREME.equals(theme)) {
            drawU9Xtreme(canvas);
            canvas.restore();
            return;
        }
        if (Prefs.THEME_N.equals(theme) || Prefs.THEME_RS.equals(theme) || Prefs.THEME_TAYCAN.equals(theme)) {
            if (Prefs.THEME_N.equals(theme)) drawN(canvas);
            else if (Prefs.THEME_RS.equals(theme)) drawRS(canvas);
            else drawTaycan(canvas);
            canvas.restore();
            return;
        }
        if (gr) {
            drawGR(canvas);
            canvas.restore();
            return;
        }
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
                L.t("Average consumption"), energyNumber(average), "kWh/100 km", 4);
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
        cell(canvas, 345, 510, L.t("Energy"), energyNumber(energy) + " kWh");
        cell(canvas, 510, 685, L.t("Cost"), money);
        graph(canvas, 709, 386, 983, 459);
        canvas.restore();
    }

    private static final int U9_ORANGE = 0xFFFF8536, U9_REGEN = 0xFF43D9EC;
    private static final int U9_PANEL = 0xFF171A1E, U9_BORDER = 0xFF3B4045;

    /** Hypercar-inspired theme, not a vehicle model/profile or a driving-mode control. */
    private void drawU9Xtreme(Canvas cv) {
        fill(cv, 0, 0, 1000, 470, 0xFF080A0C);
        // A restrained carbon-style diagonal header, with a distinct racing-orange accent.
        fill(cv, 0, 0, 1000, 58, 0xFF121518);
        for (int i = 0; i < 12; i++) slant(cv, 390+i*14, 2, 400+i*14, 30, 0xFF202428);
        slant(cv, 12, 7, 24, 31, U9_ORANGE);
        slant(cv, 26, 7, 38, 31, U9_ORANGE);
        sportText(cv, "YANGWANG U9 XTREME EDITION", 47, 28, 25, INK, Paint.Align.LEFT, 340);
        text(cv, source, 18, 48, 15, MUTED, false, Paint.Align.LEFT, 650);
        text(cv, status, 982, 25, 19, statusColor, true, Paint.Align.RIGHT, 300);
        text(cv, tripLabel, 982, 47, 15, MUTED, false, Paint.Align.RIGHT, 260);
        fill(cv, 0, 57, 1000, 60, U9_ORANGE);

        // Keep the two signed-power directions visible at the same time. Scale grows
        // when a car exceeds the nominal display range; the number is never clamped.
        boolean measured = valid(motorPower);
        double driveMax = measured ? Math.max(200, Math.ceil(Math.max(0, motorPower)/100)*100) : 200;
        double regenMax = measured ? Math.max(100, Math.ceil(Math.max(0, -motorPower)/50)*50) : 100;
        double drive = measured ? Math.max(0, motorPower)/driveMax : 0;
        double regen = measured ? Math.max(0, -motorPower)/regenMax : 0;
        text(cv, "REGEN", 20, 82, 16, U9_REGEN, true, Paint.Align.LEFT, 180);
        text(cv, "DRIVE POWER", 980, 82, 16, U9_ORANGE, true, Paint.Align.RIGHT, 180);
        sportText(cv, number(motorPower, "%.0f") + " kW", 500, 95, 32, INK, Paint.Align.CENTER, 240);
        u9PowerHalf(cv, 20, 497, 105, 135, regen, U9_REGEN, true);
        u9PowerHalf(cv, 503, 980, 105, 135, drive, U9_ORANGE, false);
        fill(cv, 498, 103, 502, 138, INK);
        text(cv, "-" + number(regenMax, "%.0f") + " kW", 20, 153, 13, MUTED, false, Paint.Align.LEFT, 150);
        text(cv, "0", 500, 153, 13, MUTED, false, Paint.Align.CENTER, 50);
        text(cv, "+" + number(driveMax, "%.0f") + " kW", 980, 153, 13, MUTED, false, Paint.Align.RIGHT, 150);

        sportPanel(cv, new float[]{18,170, 280,170, 302,199, 273,344, 18,344}, U9_PANEL, U9_BORDER);
        sportPanel(cv, new float[]{720,170, 982,170, 982,344, 727,344, 698,199}, U9_PANEL, U9_BORDER);
        sportPanel(cv, new float[]{333,170, 667,170, 703,219, 662,344, 338,344, 297,219},
                0xFF101317, U9_ORANGE);
        fill(cv, 18, 171, 93, 174, U9_ORANGE);
        fill(cv, 907, 171, 982, 174, U9_ORANGE);

        metric(cv, 34, 195, 235, L.t("Battery") + " / SOC", number(soc, "%.0f") + "%", 40);
        fill(cv, 34, 244, 248, 252, U9_BORDER);
        if (valid(soc)) fill(cv, 34, 244, 34 + (float)soc*2.14f, 252, batteryAccent(U9_ORANGE));
        thinLine(cv, 34, 265, 260, U9_BORDER);
        text(cv, L.t("Remaining range"), 34, 286, 16, MUTED, false, Paint.Align.LEFT, 228);
        sportText(cv, number(range, "%.0f") + " km", 34, 326, 37, INK, Paint.Align.LEFT, 228);

        text(cv, L.t("Speed"), 500, 195, 17, MUTED, true, Paint.Align.CENTER, 260);
        sportText(cv, number(speed, "%.0f"), 500, 289, 112, INK, Paint.Align.CENTER, 300);
        text(cv, "km/h", 500, 317, 22, MUTED, false, Paint.Align.CENTER, 190);
        fill(cv, 399, 331, 601, 334, U9_ORANGE);

        metric(cv, 742, 191, 224, "BATTERY TEMP MIN / MAX", tempReading(), 25);
        thinLine(cv, 740, 233, 963, U9_BORDER);
        metric(cv, 742, 252, 224, "CELL VOLTAGE MIN / MAX", cellReading(), 24);
        thinLine(cv, 740, 293, 963, U9_BORDER);
        text(cv, "CHARGING VOLTAGE", 742, 312, 13, MUTED, false, Paint.Align.LEFT, 224);
        text(cv, number(chargeVoltage, "%.0f") + " V", 963, 334, 22, INK, true, Paint.Align.RIGHT, 200);

        // Same trip source and estimated-energy notation as all the other themes.
        sportPanel(cv, new float[]{18,360, 982,360, 982,453, 18,453}, U9_PANEL, U9_BORDER);
        fill(cv, 18, 360, 982, 363, U9_ORANGE);
        u9TripCell(cv, 18, 200, L.t("Trip"), number(distance, "%.1f") + " km");
        u9TripCell(cv, 200, 372, L.t("Time"), elapsed);
        u9TripCell(cv, 372, 554, L.t("Energy"), energyNumber(energy) + " kWh");
        u9TripCell(cv, 554, 782, L.t("Average consumption"), energyNumber(average) + " kWh/100 km");
        u9TripCell(cv, 782, 982, L.t("Cost"), money);
    }

    private void u9PowerHalf(Canvas cv, float left, float right, float top, float bottom,
                             double fraction, int color, boolean reverse) {
        final int count = 12;
        float pitch = (right-left)/count;
        for (int i = 0; i < count; i++) {
            float x = left + (reverse ? count-1-i : i)*pitch;
            float end = x + pitch - 3;
            slant(cv, x, top, end, bottom, U9_BORDER);
            float part = (float)Math.max(0, Math.min(1, fraction*count-i));
            if (part > 0) {
                cv.save();
                float width = (end-x)*part;
                cv.clipRect(reverse ? end-width : x, top, reverse ? end : x+width, bottom);
                slant(cv, x, top, end, bottom, color);
                cv.restore();
            }
        }
    }

    private void u9TripCell(Canvas cv, float left, float right, String label, String value) {
        float center = (left+right)/2;
        text(cv, label, center, 388, 15, MUTED, false, Paint.Align.CENTER, right-left-16);
        sportText(cv, value, center, 433, 32, INK, Paint.Align.CENTER, right-left-16);
        if (right < 982) fill(cv, right-1, 375, right, 442, U9_BORDER);
    }

    private static final int N_BLUE = 0xFF42A9FF, N_RED = 0xFFF4525E;
    private static final int RS_RED = 0xFFF4525E, SPORT_GREY = 0xFF202832;
    private static final int TAYCAN_CYAN = 0xFF43D9EC;

    private void themeHeader(Canvas cv, String label, int accent) {
        fill(cv, 0, 0, 1000, 470, 0xFF090D11);
        fill(cv, 0, 0, 1000, 57, 0xFF13181D);
        slant(cv, 12, 9, 28, 30, accent);
        slant(cv, 30, 9, 46, 30, accent);
        sportText(cv, label, 54, 28, 25, INK, Paint.Align.LEFT, 370);
        text(cv, source, 18, 48, 15, MUTED, false, Paint.Align.LEFT, 660);
        text(cv, status, 982, 25, 19, statusColor, true, Paint.Align.RIGHT, 285);
        text(cv, tripLabel, 982, 47, 15, MUTED, false, Paint.Align.RIGHT, 245);
        fill(cv, 0, 57, 1000, 59, accent);
    }

    private void sportPanel(Canvas cv, float[] points, int color, int outline) {
        Path path = new Path();
        path.moveTo(points[0], points[1]);
        for (int i = 2; i < points.length; i += 2) path.lineTo(points[i], points[i+1]);
        path.close();
        p.setStyle(Paint.Style.FILL); p.setColor(color); cv.drawPath(path, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.8f); p.setColor(outline); cv.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void thinLine(Canvas cv, float left, float y, float right, int color) {
        fill(cv, left, y, right, y+1, color);
    }

    private void metric(Canvas cv, float x, float y, float width, String caption, String value, float size) {
        text(cv, caption, x, y, 15, MUTED, false, Paint.Align.LEFT, width);
        text(cv, value, x, y+32, size, INK, true, Paint.Align.LEFT, width);
    }

    private String tempReading() { return number(minTemp, "%.0f") + " / " + number(maxTemp, "%.0f") + " °C"; }
    private String cellReading() { return number(minCell, "%.3f") + " / " + number(maxCell, "%.3f") + " V"; }

    private int batteryAccent(int accent) {
        return valid(soc) && soc < 15 ? RED : valid(soc) && soc < 30 ? 0xFFFFB44D : accent;
    }

    /** N: asymmetric blue motorsport cockpit, large left speed and segmented signed power. */
    private void drawN(Canvas cv) {
        themeHeader(cv, "N PERFORMANCE", N_BLUE);
        sportPanel(cv, new float[]{12,76, 311,76, 262,453, 12,453}, 0xFF111A25, 0xFF3D5A73);
        sportPanel(cv, new float[]{331,76, 680,76, 631,453, 282,453}, 0xFF121A22, 0xFF354452);
        sportPanel(cv, new float[]{700,76, 988,76, 988,453, 651,453}, 0xFF131A20, 0xFF455262);
        slant(cv, 15, 78, 93, 102, N_BLUE);
        fill(cv, 12, 100, 20, 285, N_BLUE);
        slant(cv, 18, 408, 52, 451, N_BLUE);
        text(cv, L.t("Speed"), 147, 133, 20, MUTED, true, Paint.Align.CENTER, 240);
        sportText(cv, number(speed, "%.0f"), 148, 277, 145, INK, Paint.Align.CENTER, 250);
        text(cv, "km/h", 148, 309, 24, INK, true, Paint.Align.CENTER, 210);
        thinLine(cv, 64, 335, 240, N_BLUE);
        boolean regen = valid(motorPower) && motorPower < 0;
        sportText(cv, regen ? "REGEN" : valid(motorPower) && motorPower > 0 ? "DRIVE" : "POWER", 140, 382, 26,
                valid(motorPower) ? (regen ? TAYCAN_CYAN : N_BLUE) : MUTED, Paint.Align.CENTER, 190);

        metric(cv, 344, 102, 147, L.t("Battery"), number(soc, "%.0f") + "%", 36);
        metric(cv, 501, 102, 155, L.t("Remaining range"), number(range, "%.0f") + " km", 32);
        fill(cv, 344, 142, 484, 150, SPORT_GREY);
        if (valid(soc)) fill(cv, 344, 142, 344 + (float)soc * 1.4f, 150, batteryAccent(N_BLUE));
        thinLine(cv, 334, 164, 656, LINE);

        text(cv, regen ? "REGEN" : "LIVE POWER", 332, 190, 17, MUTED, true, Paint.Align.LEFT, 145);
        sportText(cv, number(motorPower, "%.0f") + " kW", 650, 211, 31, INK, Paint.Align.RIGHT, 155);
        double limit = regen ? 100.0 : 200.0;
        double progress = valid(motorPower) ? Math.min(1, Math.abs(motorPower) / limit) : 0;
        segmentedPower(cv, 327, 222, 641, 248, progress, regen ? TAYCAN_CYAN : N_BLUE);
        text(cv, regen ? "0 — 100 kW" : "0 — 200 kW", 327, 266, 13, MUTED, false, Paint.Align.LEFT, 250);
        thinLine(cv, 322, 278, 635, LINE);
        metric(cv, 321, 301, 304, "BATTERY TEMP MIN / MAX", tempReading(), 27);
        thinLine(cv, 316, 341, 624, LINE);
        metric(cv, 314, 362, 306, "CELL VOLTAGE MIN / MAX", cellReading(), 27);
        thinLine(cv, 308, 402, 614, LINE);
        text(cv, "CHARGING VOLTAGE", 307, 422, 14, MUTED, false, Paint.Align.LEFT, 170);
        text(cv, number(chargeVoltage, "%.0f") + " V", 606, 437, 24, INK, true, Paint.Align.RIGHT, 130);

        nTripRow(cv, 700, 91, L.t("Trip"), number(distance, "%.1f") + " km");
        nTripRow(cv, 691, 163, L.t("Time"), elapsed);
        nTripRow(cv, 682, 235, L.t("Energy"), energyNumber(energy) + " kWh");
        nTripRow(cv, 673, 307, L.t("Average consumption"), energyNumber(average) + " kWh/100 km");
        nTripRow(cv, 665, 379, L.t("Cost"), money);
        fill(cv, 810, 449, 986, 453, N_RED);
    }

    private void nTripRow(Canvas cv, float x, float top, String caption, String value) {
        text(cv, caption, x+13, top+17, 17, MUTED, false, Paint.Align.LEFT, 270);
        text(cv, value, 967, top+51, 32, INK, true, Paint.Align.RIGHT, 967-x-13);
        if (top < 379) thinLine(cv, x+5, top+66, 972, LINE);
    }

    /** RS: sharp geometric digital instrument, speed in the central hexagonal cluster. */
    private void drawRS(Canvas cv) {
        themeHeader(cv, "RS DIGITAL COCKPIT", RS_RED);
        sportPanel(cv, new float[]{13,81, 286,81, 319,145, 286,210, 13,210}, 0xFF181D24, 0xFF4A5059);
        metric(cv, 36, 111, 242, L.t("Battery"), number(soc, "%.0f") + "%", 43);
        fill(cv, 36, 168, 270, 182, SPORT_GREY);
        if (valid(soc)) fill(cv, 36, 168, 36 + (float)soc * 2.34f, 182, batteryAccent(0xFF60D390));
        sportPanel(cv, new float[]{13,223, 286,223, 319,284, 286,345, 13,345}, 0xFF181D24, 0xFF4A5059);
        metric(cv, 36, 254, 247, L.t("Remaining range"), number(range, "%.0f") + " km", 40);
        text(cv, "CHARGING VOLTAGE", 34, 387, 15, MUTED, false, Paint.Align.LEFT, 248);
        text(cv, number(chargeVoltage, "%.0f") + " V", 34, 426, 30, INK, true, Paint.Align.LEFT, 251);

        sportPanel(cv, new float[]{379,86, 552,86, 638,240, 552,394, 379,394, 293,240}, 0xFF0D1116, 0xFF9DA6AF);
        sportPanel(cv, new float[]{389,99, 542,99, 620,240, 542,381, 389,381, 311,240}, 0xFF10151A, RS_RED);
        fill(cv, 426, 95, 507, 102, RS_RED);
        text(cv, L.t("Speed"), 465, 151, 18, MUTED, false, Paint.Align.CENTER, 200);
        text(cv, number(speed, "%.0f"), 465, 262, 111, INK, true, Paint.Align.CENTER, 250);
        text(cv, "km/h", 465, 289, 22, MUTED, true, Paint.Align.CENTER, 180);
        thinLine(cv, 378, 304, 552, LINE);
        text(cv, valid(motorPower) && motorPower < 0 ? "REGEN" : "LIVE POWER", 465, 327, 15, MUTED, true, Paint.Align.CENTER, 180);
        text(cv, number(motorPower, "%.0f") + " kW", 465, 362, 29, INK, true, Paint.Align.CENTER, 167);

        sportPanel(cv, new float[]{659,81, 988,81, 988,211, 659,211, 623,146}, 0xFF181D24, 0xFF4A5059);
        metric(cv, 662, 108, 304, "BATTERY TEMP MIN / MAX", tempReading(), 26);
        thinLine(cv, 662, 150, 970, LINE);
        metric(cv, 662, 172, 304, "CELL VOLTAGE MIN / MAX", cellReading(), 24);
        sportPanel(cv, new float[]{659,223, 988,223, 988,451, 659,451, 623,337}, 0xFF181D24, 0xFF4A5059);
        metric(cv, 662, 248, 145, L.t("Trip"), number(distance, "%.1f") + " km", 28);
        metric(cv, 822, 248, 147, L.t("Time"), elapsed, 28);
        thinLine(cv, 662, 287, 970, LINE);
        metric(cv, 662, 311, 145, L.t("Energy"), energyNumber(energy) + " kWh", 29);
        metric(cv, 822, 311, 147, L.t("Cost"), money, 27);
        thinLine(cv, 662, 351, 970, LINE);
        metric(cv, 662, 377, 304, L.t("Average consumption"), energyNumber(average) + " kWh/100 km", 31);
        fill(cv, 13, 450, 988, 454, RS_RED);
    }

    /** Taycan: three restrained curved digital dials with clear whole-trip metrics on the right. */
    private void drawTaycan(Canvas cv) {
        themeHeader(cv, "TAYCAN SPORT", TAYCAN_CYAN);
        curvedDial(cv, 127, 219, 102, 100, soc, batteryAccent(TAYCAN_CYAN),
                L.t("Battery"), number(soc, "%.0f"), "%", 57);
        curvedDial(cv, 389, 219, 119, 200, speed, TAYCAN_CYAN,
                L.t("Speed"), number(speed, "%.0f"), "km/h", 81);
        curvedDial(cv, 651, 219, 102, 40, average, TAYCAN_CYAN,
                L.t("Average consumption"), energyNumber(average), "kWh/100 km", 49);
        text(cv, L.t("Remaining range"), 127, 344, 16, MUTED, false, Paint.Align.CENTER, 235);
        text(cv, number(range, "%.0f") + " km", 127, 377, 28, INK, true, Paint.Align.CENTER, 225);
        text(cv, valid(motorPower) && motorPower < 0 ? "REGEN" : "LIVE POWER", 389, 344, 16, MUTED, false, Paint.Align.CENTER, 235);
        text(cv, number(motorPower, "%.0f") + " kW", 389, 377, 29, INK, true, Paint.Align.CENTER, 235);
        text(cv, "CHARGING VOLTAGE", 651, 344, 16, MUTED, false, Paint.Align.CENTER, 230);
        text(cv, number(chargeVoltage, "%.0f") + " V", 651, 377, 28, INK, true, Paint.Align.CENTER, 230);

        p.setStyle(Paint.Style.FILL); p.setColor(0xFF151B21);
        cv.drawRoundRect(new RectF(16,394,764,456), 12, 12, p);
        metric(cv, 32, 413, 350, "BATTERY TEMP MIN / MAX", tempReading(), 24);
        fill(cv, 383, 405, 384, 445, LINE);
        metric(cv, 405, 413, 340, "CELL VOLTAGE MIN / MAX", cellReading(), 24);

        fill(cv, 780, 87, 781, 455, LINE);
        taycanRow(cv, 92, L.t("Trip"), number(distance, "%.1f") + " km");
        taycanRow(cv, 183, L.t("Time"), elapsed);
        taycanRow(cv, 274, L.t("Energy"), energyNumber(energy) + " kWh");
        taycanRow(cv, 365, L.t("Cost"), money);
    }

    private void taycanRow(Canvas cv, float y, String label, String reading) {
        text(cv, label, 799, y+18, 17, MUTED, false, Paint.Align.LEFT, 180);
        text(cv, reading, 978, y+54, 30, INK, true, Paint.Align.RIGHT, 190);
        if (y < 365) thinLine(cv, 799, y+78, 980, LINE);
    }

    private void curvedDial(Canvas cv, float x, float y, float radius, double max, double value, int color,
                            String caption, String reading, String unit, float readSize) {
        RectF outer = new RectF(x-radius, y-radius, x+radius, y+radius);
        p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeWidth(2); p.setColor(0xFF66737F);
        cv.drawArc(outer, 135, 270, false, p);
        RectF inner = new RectF(x-radius+12, y-radius+12, x+radius-12, y+radius-12);
        p.setStrokeWidth(10); p.setColor(0xFF27313A); cv.drawArc(inner, 135, 270, false, p);
        if (valid(value) && value >= 0) {
            p.setColor(color);
            cv.drawArc(inner, 135, (float)(270 * Math.min(1, value/max)), false, p);
        }
        p.setStrokeCap(Paint.Cap.BUTT);
        for (int i = 0; i <= 16; i++) {
            double a = Math.toRadians(135 + 270.0*i/16);
            float co = (float)Math.cos(a), si = (float)Math.sin(a);
            p.setStrokeWidth(i % 4 == 0 ? 2 : 1); p.setColor(i % 4 == 0 ? INK : MUTED);
            cv.drawLine(x+co*(radius-22), y+si*(radius-22), x+co*(radius-(i%4==0?31:27)), y+si*(radius-(i%4==0?31:27)), p);
        }
        p.setStyle(Paint.Style.FILL);
        text(cv, caption, x, y-30, 15, MUTED, false, Paint.Align.CENTER, radius*1.45f);
        text(cv, reading, x, y+28, readSize, INK, true, Paint.Align.CENTER, radius*1.48f);
        text(cv, unit, x, y+51, 17, MUTED, false, Paint.Align.CENTER, radius*1.55f);
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
        typeRMetric(cv, 22, 409, 451, 302, L.t("Energy"), energyNumber(energy) + " kWh", 35);
        typeRMetric(cv, 353, 409, 451, 325, L.t("Average consumption"),
                energyNumber(average) + " kWh/100 km", 35);
        typeRMetric(cv, 710, 409, 451, 272, L.t("Cost"), money, 35);
        fill(cv, 338, 396, 340, 451, TYPE_R_LINE);
        fill(cv, 695, 396, 697, 451, TYPE_R_LINE);
    }


    private static final int GR_RED = 0xFFF01829;
    private static final int GR_BLUE = 0xFF29A7EF;
    private static final int GR_BG = 0xFF080808;
    private static final int GR_PANEL = 0xFF151515;
    private static final int GR_LINE = 0xFF777C80;
    private static final int GR_DIM = 0xFF35373A;

    /** Rally-inspired layout, with a signed traction-power arc and live EV data only. */
    private void drawGR(Canvas cv) {
        fill(cv, 0, 0, 1000, 470, GR_BG);
        fill(cv, 0, 0, 1000, 57, 0xFF101010);
        slant(cv, 12, 7, 68, 31, INK);
        slant(cv, 39, 7, 68, 31, GR_RED);
        sportText(cv, "G", 28, 26, 20, GR_BG, Paint.Align.CENTER, 23);
        sportText(cv, "R", 52, 26, 20, INK, Paint.Align.CENTER, 23);
        sportText(cv, "MOTORSPORT EDITION", 78, 29, 23, INK, Paint.Align.LEFT, 280);
        slant(cv, 398, 0, 454, 36, INK);
        slant(cv, 453, 0, 509, 36, GR_RED);
        text(cv, source, 16, 49, 14, MUTED, false, Paint.Align.LEFT, 670);
        text(cv, status, 984, 25, 19, statusColor, true, Paint.Align.RIGHT, 300);
        text(cv, tripLabel, 984, 47, 15, MUTED, false, Paint.Align.RIGHT, 255);
        fill(cv, 0, 57, 1000, 59, GR_RED);

        // Geometric side panels, deliberately clear of the central gauge.
        grPolygon(cv, new float[]{16,82, 278,82, 242,329, 16,329}, GR_PANEL);
        grPolygon(cv, new float[]{758,82, 984,82, 984,329, 722,329}, GR_PANEL);
        slant(cv, 14, 83, 25, 130, GR_RED);
        slant(cv, 973, 83, 985, 130, GR_RED);

        text(cv, L.t("Battery") + " / SOC", 31, 114, 17, INK, true, Paint.Align.LEFT, 225);
        sportText(cv, number(soc, "%.0f") + "%", 31, 172, 58, INK, Paint.Align.LEFT, 220);
        fill(cv, 31, 185, 242, 199, GR_DIM);
        if (valid(soc)) fill(cv, 31, 185, 31 + (float)soc * 2.11f, 199,
                soc < 15 ? GR_RED : soc < 30 ? 0xFFFFB44D : 0xFF55D66A);
        fill(cv, 31, 219, 241, 220, GR_LINE);
        text(cv, L.t("Remaining range"), 31, 250, 17, MUTED, false, Paint.Align.LEFT, 205);
        sportText(cv, number(range, "%.0f") + " km", 31, 303, 43, INK, Paint.Align.LEFT, 205);

        text(cv, "BATTERY TEMP", 763, 113, 16, INK, true, Paint.Align.LEFT, 203);
        text(cv, "MIN", 763, 145, 15, MUTED, true, Paint.Align.LEFT, 65);
        sportText(cv, number(minTemp, "%.0f") + " °C", 966, 151, 32, INK, Paint.Align.RIGHT, 138);
        text(cv, "MAX", 763, 186, 15, MUTED, true, Paint.Align.LEFT, 65);
        sportText(cv, number(maxTemp, "%.0f") + " °C", 966, 192, 32, INK, Paint.Align.RIGHT, 138);
        fill(cv, 750, 218, 966, 219, GR_LINE);
        text(cv, "CELL VOLTAGE MIN / MAX", 750, 247, 14, MUTED, true, Paint.Align.LEFT, 216);
        sportText(cv, number(minCell, "%.3f") + " / " + number(maxCell, "%.3f") + " V",
                966, 285, 28, INK, Paint.Align.RIGHT, 220);
        text(cv, "CHARGING VOLTAGE " + number(chargeVoltage, "%.0f") + " V", 966, 312,
                13, MUTED, false, Paint.Align.RIGHT, 225);

        // Each half of this single arc has its own scale: -100 kW regeneration, +200 kW drive.
        // Values are never synthesized; out-of-range values saturate the arc but retain their true reading.
        grPowerArc(cv);
        text(cv, "POWER", 500, 180, 16, MUTED, true, Paint.Align.CENTER, 200);
        sportText(cv, number(motorPower, "%.0f") + " kW", 500, 219, 38, INK, Paint.Align.CENTER, 240);
        fill(cv, 411, 230, 589, 231, GR_LINE);
        sportText(cv, number(speed, "%.0f"), 500, 306, 92, INK, Paint.Align.CENTER, 220);
        text(cv, "km/h", 500, 329, 17, MUTED, true, Paint.Align.CENTER, 170);
        fill(cv, 354, 337, 646, 339, GR_RED);

        // Five live trip cells in a single bottom strip, as in the approved GR concept.
        grPolygon(cv, new float[]{29,354, 984,354, 971,448, 16,448}, GR_PANEL);
        grMetric(cv, 35, 214, L.t("Trip"), number(distance, "%.1f") + " km");
        grMetric(cv, 214, 395, L.t("Time"), elapsed);
        grMetric(cv, 395, 575, L.t("Energy"), energyNumber(energy) + " kWh");
        grMetric(cv, 575, 789, L.t("Average consumption"), energyNumber(average) + " kWh/100 km");
        grMetric(cv, 789, 965, L.t("Cost"), money);
    }

    private void grPowerArc(Canvas cv) {
        float cx = 500, cy = 313, radius = 204;
        RectF arc = new RectF(cx-radius, cy-radius, cx+radius, cy+radius);
        p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.BUTT);
        p.setStrokeWidth(2); p.setColor(GR_LINE);
        RectF outer = new RectF(cx-radius-18, cy-radius-18, cx+radius+18, cy+radius+18);
        cv.drawArc(outer, 180, 180, false, p);
        double drive = valid(motorPower) ? Math.min(1.0, Math.max(0, motorPower / 200.0)) : 0;
        double regen = valid(motorPower) ? Math.min(1.0, Math.max(0, -motorPower / 100.0)) : 0;
        p.setStrokeWidth(26);
        for (int i = 0; i < 10; i++) {
            float a = 270 + i * 9f + 1;
            float b = 270 - (i+1)*9f + 1;
            p.setColor(GR_DIM);
            cv.drawArc(arc, a, 7, false, p);
            cv.drawArc(arc, b, 7, false, p);
            float d = (float)Math.max(0, Math.min(1, drive*10-i));
            float r = (float)Math.max(0, Math.min(1, regen*10-i));
            if (d > 0) { p.setColor(GR_RED); cv.drawArc(arc, a, 7*d, false, p); }
            if (r > 0) { p.setColor(GR_BLUE); cv.drawArc(arc, b+7*(1-r), 7*r, false, p); }
        }
        if (valid(motorPower)) {
            double pos = motorPower < 0 ? -regen : drive;
            double a = Math.toRadians(270 + 90*pos);
            float co = (float)Math.cos(a), si = (float)Math.sin(a);
            p.setColor(INK); p.setStrokeWidth(3);
            cv.drawLine(cx+co*(radius-20), cy+si*(radius-20),
                    cx+co*(radius+17), cy+si*(radius+17), p);
        }
        p.setStyle(Paint.Style.FILL);
        text(cv, "0", 500, 85, 16, INK, true, Paint.Align.CENTER, 40);
        text(cv, "-100", 283, 332, 15, GR_BLUE, true, Paint.Align.CENTER, 58);
        text(cv, "+200", 717, 332, 15, GR_RED, true, Paint.Align.CENTER, 58);
        text(cv, "REGEN", 316, 349, 12, GR_BLUE, true, Paint.Align.CENTER, 82);
        text(cv, "DRIVE", 684, 349, 12, GR_RED, true, Paint.Align.CENTER, 82);
    }

    private void grPolygon(Canvas cv, float[] xy, int color) {
        Path path = new Path();
        path.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) path.lineTo(xy[i], xy[i+1]);
        path.close();
        p.setStyle(Paint.Style.FILL); p.setColor(color); cv.drawPath(path, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.5f); p.setColor(GR_LINE);
        cv.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void grMetric(Canvas cv, float left, float right, String label, String value) {
        float x = (left+right)/2;
        text(cv, label, x, 380, 15, MUTED, true, Paint.Align.CENTER, right-left-14);
        sportText(cv, value, x, 426, 31, INK, Paint.Align.CENTER, right-left-14);
        if (right < 965) fill(cv, right-1, 366, right, 437, GR_LINE);
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



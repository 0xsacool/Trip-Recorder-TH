package org.triprecorderng;

import android.content.Context;
import android.content.SharedPreferences;

/** Small app settings. */
final class Prefs {
    private Prefs() {}

    // Thai edition targets battery-electric BYD Atto 3; keep the upstream hybrid data model intact.
    static final boolean ATTO3_EDITION = true;
    static final double DEFAULT_BATTERY_KWH = 60.0;

    static int appearanceMode(Context c) {
        return Appearance.normalize(sp(c).getInt("appearance_mode", Appearance.DARK));
    }
    static void setAppearanceMode(Context c,int mode) {
        sp(c).edit().putInt("appearance_mode",Appearance.normalize(mode)).apply();
    }
    static boolean lightAppearance(Context c) {
        return Appearance.light(appearanceMode(c),c.getResources().getConfiguration().uiMode,lightAppearanceSupported(c));
    }
    static boolean lightAppearanceSupported(Context c) {
        String theme = dashboardTheme(c);
        return THEME_CLASSIC.equals(theme) || THEME_NISMO.equals(theme);
    }

    static SharedMotionController.Mode motionMode(Context c) {
        boolean nismo = THEME_NISMO.equals(dashboardTheme(c));
        SharedPreferences prefs = sp(c);
        String legacy = prefs.getString("dashboard_motion", nismo ? "FULL" : "REDUCED");
        String value = nismo ? prefs.getString("nismo_motion", legacy) : legacy;
        try { return SharedMotionController.Mode.valueOf(value); }
        catch (IllegalArgumentException e) { return SharedMotionController.Mode.REDUCED; }
    }
    static void setMotionMode(Context c, SharedMotionController.Mode mode) {
        boolean nismo = THEME_NISMO.equals(dashboardTheme(c));
        SharedPreferences prefs = sp(c);
        SharedPreferences.Editor edit = prefs.edit();
        if (!nismo && !prefs.contains("nismo_motion"))
            edit.putString("nismo_motion", prefs.getString("dashboard_motion", "FULL"));
        edit.putString(nismo ? "nismo_motion" : "dashboard_motion", mode.name()).apply();
    }

    static boolean performanceRefresh(Context c) {
        return sp(c).getBoolean("dashboard_performance_refresh", false);
    }

    static void setPerformanceRefresh(Context c, boolean value) {
        sp(c).edit().putBoolean("dashboard_performance_refresh", value).apply();
    }

    static int dashboardMenuMode(Context c) {
        return DashboardMenu.normalize(sp(c).getInt("dashboard_menu_mode", DashboardMenu.ALWAYS));
    }

    static void setDashboardMenuMode(Context c, int mode) {
        sp(c).edit().putInt("dashboard_menu_mode", DashboardMenu.normalize(mode)).apply();
    }

    static boolean dynamicRange(Context c) { return sp(c).getBoolean("range_dynamic", false); }

    static void setDynamicRange(Context c, boolean value) {
        sp(c).edit().putBoolean("range_dynamic", value).apply();
    }

    static double epaRangeKm(Context c) {
        return sp(c).getFloat("range_epa_km", (float) RangeEstimate.DEFAULT_EPA_KM);
    }

    static boolean setEpaRangeKm(Context c, double km) {
        if (!RangeEstimate.validReference(km)) return false;
        sp(c).edit().putFloat("range_epa_km", (float) km).apply();
        return true;
    }

    static RangeEstimate.Result rangeEstimate(Context c, double soc, java.util.List<Trip> trips) {
        return RangeEstimate.calculate(dynamicRange(c), soc, epaRangeKm(c), nominalBatteryKwh(c), trips);
    }

    static double nominalBatteryKwh(Context c) {
        return sp(c).getFloat("nominal_battery_kwh", (float) DEFAULT_BATTERY_KWH);
    }

    static boolean setNominalBatteryKwh(Context c, double kwh) {
        if (Double.isNaN(kwh) || Double.isInfinite(kwh) || kwh < 10 || kwh > 150) return false;
        sp(c).edit().putFloat("nominal_battery_kwh", (float) kwh).apply();
        Trip.setEstimateCapacity((float) kwh);
        return true;
    }

    /** True when this copy of the app runs in the main Android user (not BYD's secondary-display user). */
    static boolean isMainUser() {
        return android.os.Process.myUid() / 100000 == 0;
    }

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("triprecng", Context.MODE_PRIVATE);
    }

    static int distanceMode(Context c) { return DistanceCalibration.mode(sp(c).getInt("distance_mode", 0)); }
    static double distanceFactor(Context c) {
        double factor = sp(c).getFloat("distance_factor", 1f);
        return DistanceCalibration.validFactor(factor) ? factor : 1;
    }
    static String originalTire(Context c) { return sp(c).getString("original_tire", "215/55 R18"); }
    static String currentTire(Context c) { return sp(c).getString("current_tire", "215/55 R18"); }
    static void setDistanceMode(Context c, int mode) {
        sp(c).edit().putInt("distance_mode", DistanceCalibration.mode(mode)).apply();
    }
    static void setDistanceCalibration(Context c, double factor, String original, String current) {
        if (!DistanceCalibration.validFactor(factor)) return;
        SharedPreferences.Editor e = sp(c).edit().putInt("distance_mode", DistanceCalibration.CALIBRATED)
                .putFloat("distance_factor", (float)factor);
        if (original != null && current != null) e.putString("original_tire", original).putString("current_tire", current);
        e.apply();
    }

    /** Presentation only; preserve the selection made on Dev-5/6. */
    static final String THEME_CLASSIC = "classic";
    static final String THEME_GTR = "gtr";
    static final String THEME_NISMO = "gtr_nismo";
    static final String THEME_TYPE_R = "type_r";
    static final String THEME_GR = "gr_motorsport";
    static final String THEME_N = "n_performance";
    static final String THEME_RS = "rs_digital";
    static final String THEME_TAYCAN = "taycan_sport";
    static final String THEME_U9_XTREME = "yangwang_u9_xtreme";
    static final String THEME_EVO_HYBRID = "jdm_evo_ix";

    static String[] dashboardThemes() {
        return new String[]{THEME_CLASSIC, THEME_GTR, THEME_NISMO, THEME_TYPE_R, THEME_GR, THEME_N, THEME_RS, THEME_TAYCAN, THEME_U9_XTREME, THEME_EVO_HYBRID};
    }

    private static boolean knownTheme(String value) {
        for (String key : dashboardThemes()) if (key.equals(value)) return true;
        return false;
    }

    static String dashboardTheme(Context c) {
        SharedPreferences p = sp(c);
        String value = p.getString("dashboard_theme", "");
        if (knownTheme(value))
            return value;
        return p.getBoolean("performance_theme_gtr", false) ? THEME_GTR : THEME_CLASSIC;
    }

    static void setDashboardTheme(Context c, String value) {
        if (!knownTheme(value)) return;
        sp(c).edit().putString("dashboard_theme", value)
                .putBoolean("performance_theme_gtr", THEME_GTR.equals(value)).apply();
    }

    static String themeName(Context c) {
        return themeName(dashboardTheme(c));
    }

    static String themeName(String theme) {
        if (THEME_EVO_HYBRID.equals(theme)) return "JDM EVO IX";
        if (THEME_N.equals(theme)) return "N Performance";
        if (THEME_RS.equals(theme)) return "RS Digital Cockpit";
        if (THEME_TAYCAN.equals(theme)) return "Taycan Sport";
        if (THEME_U9_XTREME.equals(theme)) return "BYD XTREME EDITION";
        if (THEME_GR.equals(theme)) return "GR Motorsport Edition";
        if (THEME_TYPE_R.equals(theme)) return "Type R Edition";
        if (THEME_NISMO.equals(theme)) return "NISMO GT3 Edition";
        if (THEME_GTR.equals(theme)) return "GT-R Performance";
        return L.t("Classic");
    }

    static boolean performanceTheme(Context c) {
        return THEME_GTR.equals(dashboardTheme(c));
    }

    static boolean typeRTheme(Context c) {
        return THEME_TYPE_R.equals(dashboardTheme(c));
    }

    static boolean grTheme(Context c) {
        return THEME_GR.equals(dashboardTheme(c));
    }

    static boolean xtremeTheme(Context c) {
        return THEME_U9_XTREME.equals(dashboardTheme(c));
    }

    static int newThemeAccent(Context c) {
        String theme = dashboardTheme(c);
        if (THEME_EVO_HYBRID.equals(theme)) return 0xFFFF4148;
        if (THEME_N.equals(theme)) return 0xFF42A9FF;
        if (THEME_RS.equals(theme)) return 0xFFF4525E;
        if (THEME_TAYCAN.equals(theme)) return 0xFF43D9EC;
        if (THEME_U9_XTREME.equals(theme)) return 0xFFFF791A;
        return 0;
    }

    static boolean sportTheme(Context c) {
        return !THEME_CLASSIC.equals(dashboardTheme(c));
    }

    static void setPerformanceTheme(Context c, boolean enabled) {
        setDashboardTheme(c, enabled ? THEME_GTR : THEME_CLASSIC);
    }

    /** Boot counter of the Android system (changes on every real restart); -1 if unavailable. */
    static int currentBootCount(Context c) {
        try {
            return android.provider.Settings.Global.getInt(c.getContentResolver(), "boot_count", -1);
        } catch (Throwable t) {
            return -1;
        }
    }

    /** Called by the boot receiver: remembers that the system delivered the start broadcast this boot. */
    static void markBootReceiverRan(Context c) {
        sp(c).edit().putInt("boot_seen_v2", currentBootCount(c))
                .putLong("boot_seen_install", installStamp(c)).apply();
    }

    /**
     * Notes the first run after an install or update. BYD resets the app's "disable background"
     * switch whenever the package is installed or replaced, so the boot at which that happened
     * cannot show whether auto-start works.
     */
    static void noteRun(Context c) {
        try {
            long updated = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).lastUpdateTime;
            SharedPreferences p = sp(c);
            if (p.getLong("last_update", -1) != updated) {
                p.edit().putLong("last_update", updated).putInt("update_boot", currentBootCount(c)).apply();
            }
        } catch (Throwable ignored) {
            // cosmetic only
        }
    }

    static boolean autoStartSwitchVerifiedOff(Context c) {
        long stamp=installStamp(c);
        return stamp>0&&sp(c).getLong("auto_start_switch_off_install",-1)==stamp;
    }

    static void markAutoStartSwitchOff(Context c) {
        long stamp=installStamp(c);
        if(stamp>0)sp(c).edit().putLong("auto_start_switch_off_install",stamp).apply();
    }

    static void clearAutoStartSwitchOff(Context c) {
        sp(c).edit().remove("auto_start_switch_off_install").apply();
    }

    static boolean takeAutoStartRepairReminder(Context c) {
        long stamp=installStamp(c);
        if(stamp<=0||autoStartSwitchVerifiedOff(c)||autoStartHealth(c)==AUTOSTART_OK)return false;
        SharedPreferences p=sp(c);
        if(p.getLong("auto_start_repair_reminder_install",-1)==stamp)return false;
        p.edit().putLong("auto_start_repair_reminder_install",stamp).apply();
        return true;
    }

    static final int AUTOSTART_OK = 0;
    static final int AUTOSTART_UNKNOWN = 1;
    static final int AUTOSTART_BLOCKED = 2;

    /**
     * Confirm auto-start only with a boot signal associated with the installed APK version.
     * BYD's proprietary switch cannot be read; absence of a callback is unknown, not blocked.
     */
    static int autoStartHealth(Context c) {
        SharedPreferences p = sp(c);
        int boot = currentBootCount(c);
        long install = installStamp(c);
        // A quick-boot signal received after this version was installed is valid even if
        // BYD did not increment the physical Android boot counter.
        if (boot >= 0 && install > 0 && p.getLong("boot_seen_install", -1) == install
                && p.getInt("boot_seen_v2", -2) == boot) return AUTOSTART_OK;
        return AUTOSTART_UNKNOWN;
    }

    static boolean linkConsented(Context c) {
        return sp(c).getBoolean("link_consent", false);
    }

    static void setLinkConsented(Context c) {
        sp(c).edit().putBoolean("link_consent", true).apply();
    }

    /** How long the question about the Vehicle data link stays quiet after it was asked. */
    private static final long LINK_QUESTION_EVERY_MS = 7L * 24 * 60 * 60 * 1000;

    /**
     * Whether it is time to ask about the Vehicle data link: the first opening after an install or an update (they
     * change the package's update time), and again after a week; never at every opening.
     */
    static boolean linkQuestionDue(Context c) {
        SharedPreferences p = sp(c);
        if (p.getLong("link_asked_for", -1) != installStamp(c)) return true;
        return System.currentTimeMillis() - p.getLong("link_asked_at", 0) >= LINK_QUESTION_EVERY_MS;
    }

    static void setLinkAsked(Context c) {
        sp(c).edit().putLong("link_asked_for", installStamp(c)).putLong("link_asked_at", System.currentTimeMillis()).apply();
    }

    private static long installStamp(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).lastUpdateTime;
        } catch (Throwable t) {
            return 0;
        }
    }

    /** Whether the optional "vehicle data link" (ADB helper) is switched on. Off by default. */
    static boolean linkEnabled(Context c) {
        return sp(c).getBoolean("link_enabled", false);
    }

    static void setLinkEnabled(Context c, boolean on) {
        sp(c).edit().putBoolean("link_enabled", on).apply();
    }

    /** Whether each finished trip is also written to the backup folder. On by default. */
    static boolean autoBackup(Context c) {
        return sp(c).getBoolean("auto_backup", true);
    }

    static void setAutoBackup(Context c, boolean on) {
        sp(c).edit().putBoolean("auto_backup", on).apply();
    }

    /** The last battery level the recorder saw and when ({percent, wall ms}), or null if never. */
    static double[] lastSoc(Context c) {
        SharedPreferences p = sp(c);
        if (!p.contains("last_soc_wall")) return null;
        return new double[]{Double.longBitsToDouble(p.getLong("last_soc_bits", 0)), p.getLong("last_soc_wall", 0)};
    }

    static void setLastSoc(Context c, double soc, long wallMs) {
        sp(c).edit().putLong("last_soc_bits", Double.doubleToLongBits(soc)).putLong("last_soc_wall", wallMs).apply();
    }

    /** Real distance over integrated speedometer distance; starts at the measured default and is refined. */
    static double speedScale(Context c) {
        String s = sp(c).getString("speed_scale", "");
        try {
            double v = Double.parseDouble(s);
            return v >= 0.85 && v <= 1.1 ? v : JourneyTracker.DEFAULT_SCALE;
        } catch (NumberFormatException e) {
            return JourneyTracker.DEFAULT_SCALE;
        }
    }

    static void setSpeedScale(Context c, double v) {
        sp(c).edit().putString("speed_scale", String.valueOf(v)).apply();
    }

    /** Whether the Tweaks screen's "keep data roaming on" is switched on. Off by default. */
    static boolean keepRoaming(Context c) {
        return sp(c).getBoolean("keep_roaming", false);
    }

    static void setKeepRoaming(Context c, boolean on) {
        sp(c).edit().putBoolean("keep_roaming", on).apply();
    }

    /** Whether the Tweaks screen's "keep AVAS off" is switched on. Off by default. */
    static boolean keepAvasOff(Context c) {
        return sp(c).getBoolean("keep_avas_off", false);
    }

    static void setKeepAvasOff(Context c, boolean on) {
        sp(c).edit().putBoolean("keep_avas_off", on).apply();
    }

    /** Whether the Tweaks screen's blind-spot cameras (a side camera in a window while a turn signal is on) are on. Off by default. */
    static boolean blindSpot(Context c) {
        return sp(c).getBoolean("blind_spot", false);
    }

    static void setBlindSpot(Context c, boolean on) {
        sp(c).edit().putBoolean("blind_spot", on).apply();
    }

    /** What the blind-spot window shows: {@link SideCamera#VIEW_SIDE} (the car's own side view, rebuilt) or the fisheye. */
    static int blindSpotView(Context c) {
        return sp(c).getInt("blind_spot_view", SideCamera.VIEW_SIDE);
    }

    static void setBlindSpotView(Context c, int view) {
        sp(c).edit().putInt("blind_spot_view", view).apply();
    }

    /** Whether the picture is the fisheye while the car is in P, whatever {@link #blindSpotView} says. Off by default. */
    static boolean blindSpotFisheyeInPark(Context c) {
        return sp(c).getBoolean("blind_spot_fisheye_park", false);
    }

    static void setBlindSpotFisheyeInPark(Context c, boolean on) {
        sp(c).edit().putBoolean("blind_spot_fisheye_park", on).apply();
    }

    /** Whether the left signal's picture goes to the instrument cluster instead of the centre screen. Off by default. */
    static boolean blindSpotCluster(Context c) {
        return sp(c).getBoolean("blind_spot_cluster", false);
    }

    static void setBlindSpotCluster(Context c, boolean on) {
        sp(c).edit().putBoolean("blind_spot_cluster", on).apply();
    }

    /** Whether the blind-spot pictures only show while the speed is inside {@link #blindSpotSpeedRange}. Off by default: any speed. */
    static boolean blindSpotSpeedLimit(Context c) {
        return sp(c).getBoolean("blind_spot_speed_limit", false);
    }

    static void setBlindSpotSpeedLimit(Context c, boolean on) {
        sp(c).edit().putBoolean("blind_spot_speed_limit", on).apply();
    }

    /** The lowest and the highest speed, in km/h, at which the blind-spot pictures show up; a highest speed of {@link SpeedRange#TOP} has no limit. */
    static int[] blindSpotSpeedRange(Context c) {
        SharedPreferences p = sp(c);
        return SpeedRange.clamp(p.getInt("blind_spot_speed_min", SpeedRange.DEFAULT_MIN),
                p.getInt("blind_spot_speed_max", SpeedRange.DEFAULT_MAX));
    }

    static void setBlindSpotSpeedRange(Context c, int min, int max) {
        int[] r = SpeedRange.clamp(min, max);
        sp(c).edit().putInt("blind_spot_speed_min", r[0]).putInt("blind_spot_speed_max", r[1]).apply();
    }

    /** Where the blind-spot window was last left on the centre screen, as {x, y} in 2560 x 1440 pixels, or null. */
    static int[] blindSpotPlace(Context c) {
        SharedPreferences p = sp(c);
        if (!p.contains("blind_spot_x")) return null;
        return new int[]{p.getInt("blind_spot_x", 0), p.getInt("blind_spot_y", 0)};
    }

    static void setBlindSpotPlace(Context c, int x, int y) {
        sp(c).edit().putInt("blind_spot_x", x).putInt("blind_spot_y", y).apply();
    }

    /** Whether the modem is rebooted by itself when both mobile connections are down with no signal. Off by default. */
    static boolean autoModemReboot(Context c) {
        return sp(c).getBoolean("auto_modem_reboot", false);
    }

    static void setAutoModemReboot(Context c, boolean on) {
        sp(c).edit().putBoolean("auto_modem_reboot", on).apply();
    }

    /** Whether trips store their GPS route and event positions. On by default. */
    static boolean recordRoute(Context c) {
        return sp(c).getBoolean("record_route", true);
    }

    static void setRecordRoute(Context c, boolean on) {
        sp(c).edit().putBoolean("record_route", on).apply();
    }

    // ---- energy prices for the cost calculator: what the user typed, NaN if nothing (Cost applies defaults) ----
    static double elecPriceSet(Context c) {
        return price(c, "price_elec");
    }

    static double fuelPriceSet(Context c) {
        return price(c, "price_fuel");
    }

    private static double price(Context c, String key) {
        String s = sp(c).getString(key, "");
        try {
            double v = Double.parseDouble(s);
            return v > 0 ? v : Double.NaN;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    static String currency(Context c) {
        return sp(c).getString("price_currency", "THB");
    }

    /** "stable", "dev", or "" when never chosen (then the update check follows the installed build's channel). */
    static String updateChannel(Context c) {
        return sp(c).getString("update_channel", "");
    }

    static void setUpdateChannel(Context c, String channel) {
        sp(c).edit().putString("update_channel", channel).apply();
    }

    // ---- the auto-start reminder: "Don't show again" still reminds on every 15th opening ----------

    static final int MUTED_REMINDER_EVERY = 15;

    static boolean autoStartAlertMuted(Context c) {
        return sp(c).getBoolean("autostart_alert_muted", false);
    }

    /** Muting (or un-muting) starts the count of openings from zero. */
    static void setAutoStartAlertMuted(Context c, boolean muted) {
        sp(c).edit().putBoolean("autostart_alert_muted", muted).putInt("autostart_muted_opens", 0).apply();
    }

    /** True when this opening, the n-th since the count started, is the one that reminds again. */
    static boolean mutedReminderDue(int openingsBefore) {
        return openingsBefore + 1 >= MUTED_REMINDER_EVERY;
    }

    /**
     * Counts one opening of the app while the reminder is muted. Returns true on every 15th opening
     * (the count then starts again), when the reminder must be shown after all.
     */
    static boolean countMutedOpening(Context c) {
        int before = sp(c).getInt("autostart_muted_opens", 0);
        boolean due = mutedReminderDue(before);
        sp(c).edit().putInt("autostart_muted_opens", due ? 0 : before + 1).apply();
        return due;
    }

    /** Empty text clears a price, which then falls back to the default. */
    static void setPrices(Context c, String elec, String fuel, String currency) {
        sp(c).edit().putString("price_elec", elec).putString("price_fuel", fuel)
                .putString("price_currency", currency.isEmpty() ? "THB" : currency).apply();
    }
}

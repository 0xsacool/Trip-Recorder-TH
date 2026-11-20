package org.triprecorderng;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Distance selection is independent of the car's settings and is frozen per trip. */
final class DistanceCalibration {
    static final int VEHICLE = 0, GPS = 1, CALIBRATED = 2;
    private static final Pattern TIRE = Pattern.compile("^\\s*(\\d{3})\\s*/\\s*(\\d{2})\\s*[rR ]\\s*(\\d{2})\\s*$");
    private DistanceCalibration() {}

    static int mode(int value) {
        return value == GPS || value == CALIBRATED ? value : VEHICLE;
    }

    static boolean validFactor(double factor) {
        return Double.isFinite(factor) && factor >= 0.8 && factor <= 1.2;
    }

    static double diameter(String size) {
        Matcher m = TIRE.matcher(size == null ? "" : size);
        if (!m.matches()) return Double.NaN;
        int width = Integer.parseInt(m.group(1)), aspect = Integer.parseInt(m.group(2));
        int rim = Integer.parseInt(m.group(3));
        if (width < 125 || width > 355 || aspect < 25 || aspect > 85 || rim < 13 || rim > 24)
            return Double.NaN;
        return rim * 25.4 + 2.0 * width * aspect / 100.0;
    }

    static double tires(String original, String current) {
        return reference(diameter(current), diameter(original));
    }

    static double reference(double actual, double indicated) {
        if (!Double.isFinite(actual) || !Double.isFinite(indicated) || actual <= 0 || indicated <= 0)
            return Double.NaN;
        double ratio = actual / indicated;
        return validFactor(ratio) ? ratio : Double.NaN;
    }

    static void sampleGps(Trip t, double gpsSpeed, double vehicleSpeed, double seconds) {
        if (!Double.isFinite(seconds) || seconds <= 0) return;
        if (Double.isFinite(gpsSpeed) && gpsSpeed >= 0 && gpsSpeed <= 260) {
            t.gpsKm += (gpsSpeed < 3.0 ? 0 : gpsSpeed) * seconds / 3600;
            t.gpsCoveredSec += seconds;
        } else {
            t.gpsMissingSec += seconds;
            if (Double.isFinite(vehicleSpeed) && vehicleSpeed >= 0 && vehicleSpeed <= 260)
                t.gpsGapKm += vehicleSpeed * seconds / 3600;
        }
    }

    static void update(Trip t) {
        t.rawDistanceKm = JourneyTracker.distance(t);
        if (t.distanceMode == GPS) {
            // With no GPS fix at all, preserve the conventional vehicle estimate.
            // With intermittent reception use the speed estimate for gaps only.
            t.distanceKm = t.gpsCoveredSec > 0 ? t.gpsKm + t.gpsGapKm : t.rawDistanceKm;
        } else if (t.distanceMode == CALIBRATED) {
            double factor = validFactor(t.distanceFactor) ? t.distanceFactor : 1;
            if (!Double.isNaN(t.journeyAcc) && t.journeyAcc >= JourneyTracker.TRUST_KM) {
                t.distanceKm = Math.max(0, (t.rawDistanceKm - t.journeyGpsSeedRawKm) * factor + t.journeyGpsSeedKm);
            } else {
                double gps = Math.max(0, Math.min(t.rawDistanceKm, t.gpsFallbackKm));
                t.distanceKm = (t.rawDistanceKm - gps) * factor + gps;
            }
        } else {
            t.distanceKm = t.rawDistanceKm;
        }
    }
}

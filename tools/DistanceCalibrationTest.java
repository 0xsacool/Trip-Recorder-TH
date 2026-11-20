package org.triprecorderng;

public final class DistanceCalibrationTest {
    private static void near(double expected, double actual) {
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > 0.000001)
            throw new AssertionError("expected " + expected + ", got " + actual);
    }
    private static void invalid(double value) {
        if (!Double.isNaN(value)) throw new AssertionError("expected invalid input, got " + value);
    }
    public static void main(String[] args) {
        near(693.7, DistanceCalibration.diameter("215/55 R18"));
        near(682.2, DistanceCalibration.diameter("225/50r18"));
        near(692.2, DistanceCalibration.diameter("235/50 R18"));
        near(682.2 / 693.7, DistanceCalibration.tires("215/55 R18", "225/50 R18"));
        near(692.2 / 693.7, DistanceCalibration.tires("215/55 R18", "235/50 R18"));
        near(1.04, DistanceCalibration.reference(104, 100));
        invalid(DistanceCalibration.reference(100, 0));
        invalid(DistanceCalibration.reference(150, 100));
        invalid(DistanceCalibration.diameter("225/50"));
        invalid(DistanceCalibration.diameter("999/50 R18"));

        Trip t = new Trip();
        t.speedKm = 100;
        t.journeyAcc = 95;
        DistanceCalibration.update(t);
        near(95, t.distanceKm);
        near(95, t.rawDistanceKm);

        t.distanceMode = DistanceCalibration.CALIBRATED;
        t.distanceFactor = 0.98;
        DistanceCalibration.update(t);
        near(93.1, t.distanceKm);
        near(95, t.rawDistanceKm);
        t.journeyAcc = Double.NaN;
        t.gpsFallbackKm = 20;
        DistanceCalibration.update(t);
        near(98.4, t.distanceKm);
        t.gpsFallbackKm = 100;
        DistanceCalibration.update(t);
        near(100, t.distanceKm);
        t.journeyAcc = 100;
        t.journeyGpsSeedRawKm = 19;
        t.journeyGpsSeedKm = 20;
        DistanceCalibration.update(t);
        near(81 * 0.98 + 20, t.distanceKm);

        Trip gps = new Trip();
        gps.distanceMode = DistanceCalibration.GPS;
        gps.speedKm = 110;
        gps.journeyAcc = 105;
        DistanceCalibration.update(gps);
        near(105, gps.distanceKm);
        DistanceCalibration.sampleGps(gps, 100, 120, 3600);
        DistanceCalibration.sampleGps(gps, Double.NaN, 60, 60);
        DistanceCalibration.sampleGps(gps, 1, 0, 60);
        DistanceCalibration.update(gps);
        near(101, gps.distanceKm);
        near(100, gps.gpsKm);
        near(105, gps.rawDistanceKm);
        near(3660, gps.gpsCoveredSec);
        near(60, gps.gpsMissingSec);
        System.out.println("Distance calibration tests passed");
    }
}

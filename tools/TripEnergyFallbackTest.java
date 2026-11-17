package org.triprecorderng;

/** Pure-Java regression test, no Android runtime or vehicle connection required. */
public final class TripEnergyFallbackTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void equal(double expected, double actual) {
        check(Math.abs(expected - actual) < 0.000001, "Expected " + expected + ", got " + actual);
    }

    public static void main(String[] args) {
        Trip.setEstimateCapacity(60);
        Trip t = new Trip();
        t.socStart = 91;
        t.socEnd = 88;
        t.distanceKm = 11.6;
        equal(1.8, t.energyUsed());
        equal(1.8 / 11.6 * 100, t.energyPer100Km());
        equal(8.1, t.energyUsed() * 4.5);
        check(t.energyIsEstimate(), "SOC estimate not identified");
        check("≈1.8".equals(t.energyText(t.energyUsed(), "%.1f")), "Missing estimate marker");
        check(Double.isNaN(t.parkedEnergy()), "SOC estimate must not invent parking energy");
        check(Double.isNaN(t.measuredEnergy()), "Estimated value leaked into measured data");

        Trip.setEstimateCapacity(50);
        equal(1.5, t.energyUsed());  // old or restored trip follows the configured capacity
        Trip.setEstimateCapacity(60);
        t.elecStart = 0;
        t.elecEnd = 0;
        equal(1.8, t.energyUsed());  // unsupported counter reports zero
        t.elecStart = 100;
        t.elecEnd = 102;
        t.driveKwh = 1.5;
        equal(2, t.energyUsed());
        equal(1.5, t.driveEnergy());
        equal(0.5, t.parkedEnergy());
        check(!t.energyIsEstimate(), "Actual vehicle counter must take priority");
        check("2.0".equals(t.energyText(t.energyUsed(), "%.1f")), "Actual reading marked estimated");

        Trip missing = new Trip();
        check(Double.isNaN(missing.energyUsed()), "Missing readings must stay unknown");
        missing.socStart = 90;
        missing.socEnd = 90;
        check(Double.isNaN(missing.energyUsed()), "No SOC change cannot prove zero consumption");
        missing.socEnd = 95;
        check(Double.isNaN(missing.energyUsed()), "Charging must not become consumed energy");
        missing.socStart = 101;
        check(Double.isNaN(missing.energyUsed()), "Invalid SOC accepted");

        Trip shortTrip = new Trip();
        shortTrip.startMs = 1000;
        shortTrip.lastUpdateMs = 121000;
        shortTrip.distanceKm = 2.1;
        shortTrip.socStart = shortTrip.socEnd = 71;
        shortTrip.elecStart = shortTrip.elecEnd = 100;
        Trip.MotorEnergy motor = new Trip.MotorEnergy();
        for (int i = 0; i <= 120; i++) motor.add(shortTrip, i * 1000L, 12);
        equal(0.4, shortTrip.energyUsed());
        equal(0.4 / 2.1 * 100, shortTrip.energyPer100Km());
        check(shortTrip.energyIsMotorEstimate(), "Short-trip fallback source not recognized");
        check("≈0.4".equals(shortTrip.energyText(shortTrip.energyUsed(), "%.1f")), "Motor estimate not marked");
        check(Double.isNaN(shortTrip.parkedEnergy()), "Motor estimate cannot determine parking energy");

        double saved = shortTrip.motorNetKwh;
        motor.add(shortTrip, 121000, -12);
        equal(saved, shortTrip.motorNetKwh); // signed trapezoid, 12 to -12
        motor.add(shortTrip, 122000, -12);
        equal(saved - 12.0 / 3600, shortTrip.motorNetKwh);
        saved = shortTrip.motorNetKwh;
        motor.add(shortTrip, 130000, 90); // gap, do not extrapolate
        motor.add(shortTrip, 131000, Double.NaN);
        motor.add(shortTrip, 132000, 90);
        equal(saved, shortTrip.motorNetKwh);
        motor.reset();
        motor.add(shortTrip, 133000, 90);
        equal(saved, shortTrip.motorNetKwh);
        shortTrip.socEnd = 70;
        equal(0.6, shortTrip.energyUsed()); // battery SOC includes more than motor load
        check(!shortTrip.energyIsMotorEstimate(), "SOC should supersede motor estimate");
        shortTrip.elecEnd = 101;
        equal(1, shortTrip.energyUsed());
        check(!shortTrip.energyIsEstimate(), "Actual counter must supersede estimates");
        shortTrip.elecEnd = 99; // reset counter is not a zero-energy measurement
        shortTrip.socEnd = 71;
        equal(saved, shortTrip.energyUsed());
        shortTrip.lastUpdateMs = 600000;
        check(Double.isNaN(shortTrip.energyUsed()), "Sparse samples cannot estimate the entire trip");
        System.out.println("TripEnergyFallbackTest PASS");
    }
}


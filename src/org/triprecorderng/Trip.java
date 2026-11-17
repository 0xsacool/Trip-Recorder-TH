package org.triprecorderng;

/** One recorded trip (an ignition cycle, or a drive when ignition state is unavailable). */
final class Trip {
    // Read the configured capacity once at startup and whenever the user changes it. Keep the raw
    // counters untouched so older/restored trips can be estimated without a database migration.
    private static volatile double estimateCapacityKwh = 60.0;

    static void setEstimateCapacity(double kwh) {
        if (finite(kwh) && kwh >= 10 && kwh <= 150) estimateCapacityKwh = kwh;
    }

    private static boolean finite(double n) {
        return !Double.isNaN(n) && !Double.isInfinite(n);
    }

    String id;
    long startMs;
    long endMs;            // 0 while the trip is running
    long lastUpdateMs;
    boolean ended;
    String mode = "";      // "ignition" or "motion"

    double distanceKm;     // the distance shown: the car's journey counter when it is available, else speedKm
    double speedKm;        // integrated from the speedometer value (reads a few percent above the real distance)
    double journeyAcc = Double.NaN;   // km counted from the car's own journey (trip) counter, NaN if never read
    double journeyLast = Double.NaN;  // last reading of that counter (kept so a restart can carry on)
    long journeyLastWall;             // when journeyLast was read (not stored)
    double odoStartKm = Double.NaN;
    double odoEndKm = Double.NaN;
    double movingSec;
    double idleSec;
    double maxSpeedKmh;

    double socStart = Double.NaN;
    double socEnd = Double.NaN;
    double elecStart = Double.NaN;
    double elecEnd = Double.NaN;

    // plug-in hybrid counters: fuel burned (litres) and distance in electric / hybrid mode (km)
    double fuelStart = Double.NaN, fuelEnd = Double.NaN;
    double evStart = Double.NaN, evEnd = Double.NaN;
    double hevStart = Double.NaN, hevEnd = Double.NaN;

    // what the car did while driving, standstills of 5 minutes or more (parking) left out (see DriveSplit); NaN until
    // worked out. The parked part of the trip is the whole trip minus these.
    double driveKwh = Double.NaN;
    double driveSec = Double.NaN;

    int hardAccel;
    int hardBrake;

    double startLat = Double.NaN, startLon = Double.NaN;
    double endLat = Double.NaN, endLon = Double.NaN;

    /** From the start of the trip to its end (or now), parked time included. */
    long durationSec() {
        long end = ended && endMs > 0 ? endMs : lastUpdateMs;
        return Math.max(0, (end - startMs) / 1000);
    }

    /** Time spent driving: the whole duration when the parked time has not been worked out. */
    long drivingSec() {
        if (Double.isNaN(driveSec)) return durationSec();
        return Math.min(durationSec(), Math.round(driveSec));
    }

    /** Time the car stood parked in the trip (a standstill of 5 minutes or more, or time with no samples). */
    long parkedSec() {
        return Double.isNaN(driveSec) ? 0 : Math.max(0, durationSec() - drivingSec());
    }

    double avgSpeedKmh() {
        return movingSec > 5 ? distanceKm / (movingSec / 3600.0) : 0;
    }

    double odometerDeltaKm() {
        if (Double.isNaN(odoStartKm) || Double.isNaN(odoEndKm)) return Double.NaN;
        return Math.max(0, odoEndKm - odoStartKm);
    }

    /** Energy counter delta in the vehicle's own unit (kWh on the tested unit). */
    double measuredEnergy() {
        if (!finite(elecStart) || !finite(elecEnd) || elecStart < 0 || elecEnd < 0) return Double.NaN;
        // Some unsupported vehicle signals report a constant zero instead of being absent.
        if (elecStart == 0 && elecEnd == 0 && socUsed() > 0) return Double.NaN;
        return Math.max(0, elecEnd - elecStart);
    }

    private double estimatedEnergy() {
        if (!finite(socStart) || !finite(socEnd) || socStart > 100 || socEnd < 0 || socStart <= socEnd)
            return Double.NaN;
        return (socStart - socEnd) * estimateCapacityKwh / 100.0;
    }

    double energyUsed() {
        double actual = measuredEnergy();
        return Double.isNaN(actual) ? estimatedEnergy() : actual;
    }

    boolean energyIsEstimate() {
        return Double.isNaN(measuredEnergy()) && !Double.isNaN(estimatedEnergy());
    }

    String energyLabel(String value) {
        return energyIsEstimate() && !"--".equals(value) ? "≈" + value : value;
    }

    String energyText(double value, String format) {
        return finite(value) ? energyLabel(String.format(java.util.Locale.US, format, value)) : "--";
    }

    static boolean anyEstimated(java.util.List<Trip> trips) {
        for (Trip t : trips) if (t.energyIsEstimate()) return true;
        return false;
    }

    /** Energy used while driving; all of it when the parked part has not been worked out. */
    double driveEnergy() {
        // SOC can estimate net whole-trip energy, not divide it reliably into driving and parking.
        if (energyIsEstimate()) return energyUsed();
        if (Double.isNaN(driveKwh)) return energyUsed();
        return Math.max(0, driveKwh);
    }

    /** Energy used while parked (air conditioning, the cable in), NaN when it cannot be told. */
    double parkedEnergy() {
        if (energyIsEstimate()) return Double.NaN;
        double e = energyUsed();
        if (Double.isNaN(e) || Double.isNaN(driveKwh)) return Double.NaN;
        return Math.max(0, e - Math.max(0, driveKwh));
    }

    /** kWh per 100 km of the driving: the energy of a parked car is not part of it. */
    double energyPer100Km() {
        double e = driveEnergy();
        if (Double.isNaN(e) || distanceKm < 0.5) return Double.NaN;
        return e / distanceKm * 100.0;
    }

    /** kWh per 100 km of the whole trip, parked time included. */
    double wholeEnergyPer100Km() {
        double e = energyUsed();
        if (Double.isNaN(e) || distanceKm < 0.5) return Double.NaN;
        return e / distanceKm * 100.0;
    }

    private static double delta(double a, double b) {
        if (Double.isNaN(a) || Double.isNaN(b)) return Double.NaN;
        return Math.max(0, b - a);
    }

    /** Fuel burned during the trip in litres (NaN if the counter was not recorded). */
    double fuelUsed() { return delta(fuelStart, fuelEnd); }

    double evKm() { return delta(evStart, evEnd); }

    double hevKm() { return delta(hevStart, hevEnd); }

    double fuelPer100Km() {
        double f = fuelUsed();
        if (Double.isNaN(f) || distanceKm < 0.5) return Double.NaN;
        return f / distanceKm * 100.0;
    }

    double socUsed() {
        if (Double.isNaN(socStart) || Double.isNaN(socEnd)) return Double.NaN;
        return socStart - socEnd;
    }
}


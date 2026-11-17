package org.triprecorderng;

import java.util.List;

/** Read-only range estimates; independent of the vehicle's NEDC range signal. */
final class RangeEstimate {
    static final double DEFAULT_EPA_KM = 350.0;
    static final String EPA = "EPA Estimate";
    static final String DYNAMIC = "Dynamic Range";
    private RangeEstimate() {}

    static boolean valid(double n) { return !Double.isNaN(n) && !Double.isInfinite(n); }

    static boolean validReference(double km) { return valid(km) && km >= 50 && km <= 1500; }

    static final class Result {
        final double km;
        final String label;
        Result(double km, String label) { this.km = km; this.label = label; }
        String text() { return valid(km) ? "≈" + Math.round(km) + " km" : "-- km"; }
    }

    /** Newest first; use up to five completed trips, weighted by distance. */
    static double consumption(List<Trip> history) {
        double km = 0, kwh = 0;
        int count = 0;
        for (Trip t : history) {
            if (!t.ended || !valid(t.distanceKm) || t.distanceKm < 3) continue;
            double energy = t.energyUsed();
            double per100 = energy / t.distanceKm * 100.0;
            if (!valid(per100) || per100 < 5 || per100 > 100) continue;
            km += t.distanceKm;
            kwh += energy;
            if (++count >= 5) break;
        }
        return km >= 10 && count >= 2 ? kwh / km * 100.0 : Double.NaN;
    }

    static Result calculate(boolean dynamic, double soc, double epaKm, double capacity, List<Trip> history) {
        if (!valid(soc) || soc < 0 || soc > 100) return new Result(Double.NaN, dynamic ? DYNAMIC : EPA);
        if (dynamic && valid(capacity) && capacity >= 10 && capacity <= 150) {
            double per100 = consumption(history);
            if (valid(per100)) return new Result(capacity * soc / per100, DYNAMIC);
        }
        return new Result(validReference(epaKm) ? epaKm * soc / 100.0 : Double.NaN,
                dynamic ? EPA + " (fallback)" : EPA);
    }
}

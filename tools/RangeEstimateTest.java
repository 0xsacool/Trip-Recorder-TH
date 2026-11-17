package org.triprecorderng;

import java.util.Arrays;
import java.util.Collections;

public final class RangeEstimateTest {
    private static void close(double expected, double actual) {
        if (!RangeEstimate.valid(actual) || Math.abs(expected - actual) > 0.0001)
            throw new AssertionError(expected + " != " + actual);
    }
    private static Trip trip(double km, double kwh) {
        Trip t = new Trip();
        t.ended = true;
        t.distanceKm = km;
        t.elecStart = 0;
        t.elecEnd = kwh;
        return t;
    }
    public static void main(String[] args) {
        close(297.5, RangeEstimate.calculate(false, 85, 350, 60, Collections.emptyList()).km);
        close(0, RangeEstimate.calculate(false, 0, 350, 60, Collections.emptyList()).km);
        RangeEstimate.Result fallback = RangeEstimate.calculate(true, 85, 350, 60, Arrays.asList(trip(10, 1.5)));
        close(297.5, fallback.km);
        if (!fallback.label.contains("fallback")) throw new AssertionError("missing fallback label");
        // 10 km at 10 and 20 km at 25 kWh/100 -> 20 kWh/100 weighted.
        Trip unfinished = trip(50, 1); unfinished.ended = false;
        RangeEstimate.Result dyn = RangeEstimate.calculate(true, 85, 350, 60,
                Arrays.asList(unfinished, trip(10, 1), trip(20, 5)));
        close(255, dyn.km);
        if (!RangeEstimate.DYNAMIC.equals(dyn.label)) throw new AssertionError("wrong mode");
        if (!Double.isNaN(RangeEstimate.calculate(true, Double.NaN, 350, 60, Collections.emptyList()).km))
            throw new AssertionError("missing SOC must not give a distance");
        if (!Double.isNaN(RangeEstimate.calculate(false, 101, 350, 60, Collections.emptyList()).km))
            throw new AssertionError("invalid SOC");
        System.out.println("RangeEstimate tests passed");
    }
}

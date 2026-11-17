package org.triprecorderng;

/** Presentation-only refresh policy. The recorder's 250 ms speed sampling never changes. */
final class DashboardRefresh {
    private DashboardRefresh() {}

    static long uiMs(boolean performance, boolean visible) {
        return performance && visible ? 250 : 1000;
    }

    static long motorMs(boolean performance, boolean visible) {
        return !visible ? 5000 : performance ? 250 : 1000;
    }

    static boolean due(long now, long last, long interval) {
        return last == 0 || now < last || now - last >= interval;
    }
}

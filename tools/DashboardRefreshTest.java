package org.triprecorderng;

public final class DashboardRefreshTest {
    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Unexpected refresh policy");
    }

    public static void main(String[] args) {
        check(DashboardRefresh.uiMs(false, true) == 1000);
        check(DashboardRefresh.uiMs(true, true) == 250);
        check(DashboardRefresh.uiMs(true, false) == 1000);
        check(DashboardRefresh.motorMs(false, true) == 1000);
        check(DashboardRefresh.motorMs(true, true) == 250);
        check(DashboardRefresh.motorMs(true, false) == 5000);
        check(DashboardRefresh.due(1000, 0, 1000));
        check(!DashboardRefresh.due(1249, 1000, 250));
        check(DashboardRefresh.due(1250, 1000, 250));
        check(!DashboardRefresh.due(1999, 1000, 1000));
        check(DashboardRefresh.due(2000, 1000, 1000));
        check(DashboardRefresh.due(900, 1000, 250));
        System.out.println("DashboardRefresh tests passed");
    }
}

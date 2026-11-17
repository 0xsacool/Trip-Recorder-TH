package org.triprecorderng;

public final class DashboardMenuTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        DashboardMenu m = new DashboardMenu();
        check(m.showNavigation(true, false), "default should show menu");
        check(!m.showToggle(true, false), "Always Show needs no toggle");
        m.toggle();
        check(m.showNavigation(true, false), "Always Show cannot hide");
        m.configure(DashboardMenu.TAP);
        check(m.showToggle(true, false), "tap mode needs accessible control");
        m.toggle();
        check(!m.showNavigation(true, false), "tap mode hides");
        check(m.showToggle(true, false), "hidden menu remains restorable");
        check(!m.schedule(true, true, false), "manual mode never times out");
        check(m.showNavigation(false, false), "other tabs always show menu");
        m.enterDashboard();
        check(!m.showNavigation(true, false), "manual state survives return");
        m.toggle();
        check(m.showNavigation(true, false), "tap shows again");
        m.configure(DashboardMenu.AUTO);
        check(m.schedule(true, true, false), "auto schedules on foreground dashboard");
        check(!m.schedule(false, true, false), "background cancels timeout");
        check(!m.schedule(true, false, false), "other tabs cancel timeout");
        check(!m.schedule(true, true, true), "overlay cancels timeout");
        check(!m.showNavigation(true, true) && !m.showToggle(true, true), "overlays hide both controls");
        m.hideAutomatically();
        check(!m.showNavigation(true, false) && !m.schedule(true, true, false), "auto collapse stops timer");
        m.toggle();
        check(m.showNavigation(true, false) && m.schedule(true, true, false), "auto reveal rearms timer");
        m.hideAutomatically();
        m.enterDashboard();
        check(m.showNavigation(true, false), "return to dashboard shows for five seconds");
        m.configure(42);
        check(m.showNavigation(true, false) && !m.showToggle(true, false), "invalid pref restores default");
        check(DashboardMenu.AUTO_HIDE_MS == 5000L, "five second timeout");
        System.out.println("DashboardMenuTest PASS");
    }
}

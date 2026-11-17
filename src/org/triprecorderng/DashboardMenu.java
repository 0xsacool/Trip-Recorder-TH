package org.triprecorderng;

/** Presentation-only policy. The app's menu can collapse on the driving dashboard, never trapping a user in other pages. */
final class DashboardMenu {
    static final int ALWAYS = 0, TAP = 1, AUTO = 2;
    static final long AUTO_HIDE_MS = 5000L;
    private int mode = ALWAYS;
    private boolean hidden;

    static int normalize(int value) { return value == TAP || value == AUTO ? value : ALWAYS; }

    void configure(int value) {
        value = normalize(value);
        if (value != mode) { mode = value; hidden = false; }
    }

    boolean showNavigation(boolean dashboard, boolean overlay) {
        return !overlay && (!dashboard || mode == ALWAYS || !hidden);
    }

    boolean showToggle(boolean dashboard, boolean overlay) {
        return dashboard && !overlay && mode != ALWAYS;
    }

    boolean schedule(boolean resumed, boolean dashboard, boolean overlay) {
        return resumed && dashboard && !overlay && mode == AUTO && !hidden;
    }

    void toggle() { if (mode != ALWAYS) hidden = !hidden; }

    void hideAutomatically() { if (mode == AUTO) hidden = true; }

    /** On returning to the dashboard, auto mode grants a fresh period to reach the tabs. */
    void enterDashboard() { if (mode == AUTO) hidden = false; }
}

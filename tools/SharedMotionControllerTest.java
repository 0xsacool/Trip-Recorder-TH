package org.triprecorderng;
final class SharedMotionControllerTest {
    static void check(boolean b) { if (!b) throw new AssertionError(); }
    public static void main(String[] args) {
        for (SharedMotionController.Mode mode : SharedMotionController.Mode.values()) {
            SharedMotionController m = new SharedMotionController();
            m.begin(100, mode, 0, true);
            check(m.active() == (mode != SharedMotionController.Mode.OFF));
            m.frame(101, 1, true); check(!m.active());
            m.begin(102, mode, 0, true); check(!m.active());
        }
        SharedMotionController m = new SharedMotionController();
        m.begin(100, SharedMotionController.Mode.FULL, 0, true);
        check(m.frame(500,0,true)>0);
        check(m.frame(1300,0,true)==0 && !m.active());
        m = new SharedMotionController();
        m.begin(100, SharedMotionController.Mode.REDUCED, Double.NaN, true);
        check(!m.active());
        m = new SharedMotionController();
        m.begin(100, SharedMotionController.Mode.REDUCED, 0, false);
        check(!m.active());
        m = new SharedMotionController();
        m.begin(100, SharedMotionController.Mode.REDUCED, 0, true);
        check(m.frame(749,0,true)>0);
        check(m.frame(750,0,true)==0 && !m.active());
    }
}

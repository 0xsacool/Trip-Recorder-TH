package org.triprecorderng;

/** Application appearance. Light mode is temporarily restricted to Classic. */
final class Appearance {
    static final int DARK=0, LIGHT=1, AUTO=2;
    private Appearance(){}
    static int normalize(int mode){return mode==LIGHT||mode==AUTO?mode:DARK;}
    // Android Configuration.UI_MODE_NIGHT_MASK and UI_MODE_NIGHT_NO.
    // Unsupported or undefined system mode deliberately keeps the old dark appearance.
    static boolean light(int mode,int uiMode){
        return normalize(mode)==LIGHT||(normalize(mode)==AUTO&&(uiMode&0x30)==0x10);
    }
    static boolean light(int mode,int uiMode,boolean lightSupported){
        return lightSupported&&light(mode,uiMode);
    }
}

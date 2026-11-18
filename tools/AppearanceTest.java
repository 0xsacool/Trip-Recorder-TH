package org.triprecorderng;

final class AppearanceTest {
    private static void check(boolean pass,String description){if(!pass)throw new AssertionError(description);}
    public static void main(String[] args){
        check(Appearance.normalize(-1)==Appearance.DARK,"invalid settings fallback");
        check(!Appearance.light(Appearance.DARK,0x10),"explicit dark overrides day");
        check(Appearance.light(Appearance.LIGHT,0x20),"explicit light overrides night");
        check(Appearance.light(Appearance.AUTO,0x11),"auto follows day with normal UI type");
        check(!Appearance.light(Appearance.AUTO,0x23),"auto follows car/night");
        check(!Appearance.light(Appearance.AUTO,0),"auto falls back to dark when unknown");
        System.out.println("AppearanceTest PASS");
    }
}

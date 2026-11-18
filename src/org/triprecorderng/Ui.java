package org.triprecorderng;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Colors, sizes and small view factories that follow the look of the stock Driving behavior app. */
final class Ui {
    private Ui() {}

    // Values taken from the stock app's Fang Cheng Bao theme.
    static int BG = 0xFF0A0B0D;
    static int BG_CENTER = 0xFF16181C;
    static int CARD = 0x1AFFFFFF;
    static int TEXT_SELECTED = 0xFFFFFFFF;
    static int TEXT_VALUE = 0xCCFFFFFF;
    static int TEXT_DATE = 0x99FFFFFF;
    static int TEXT_LABEL = 0x66FFFFFF;
    static int DIVIDER = 0x1AFFFFFF;
    static int RING_TRACK = 0x33FFFFFF;
    static int GREEN = 0xFF3FD18B;
    static int AMBER = 0xFFF0B33A;
    static int RED = 0xFFEB5757;
    static int BLUE = 0xFF4AA3FF;

    static void applyAppearance(boolean light) {
        BG=light?0xFFF1F4F7:0xFF0A0B0D;
        BG_CENTER=light?0xFFFFFFFF:0xFF16181C;
        CARD=light?0xFFFFFFFF:0x1AFFFFFF;
        TEXT_SELECTED=light?0xFF12212D:0xFFFFFFFF;
        TEXT_VALUE=light?0xFF253746:0xCCFFFFFF;
        TEXT_DATE=light?0xFF526574:0x99FFFFFF;
        TEXT_LABEL=light?0xFF60717F:0x66FFFFFF;
        DIVIDER=light?0xFFD8E0E6:0x1AFFFFFF;
        RING_TRACK=light?0xFFD1DCE3:0x33FFFFFF;
        GREEN=light?0xFF137A4D:0xFF3FD18B;
        AMBER=light?0xFF966000:0xFFF0B33A;
        RED=light?0xFFB82F36:0xFFEB5757;
        BLUE=light?0xFF2167B0:0xFF4AA3FF;
    }
    static boolean lightAppearance;
    static void setAppearance(boolean light) {
        lightAppearance=light;
        applyAppearance(light);
        Kit.applyAppearance(light);
    }
    /** Text accents used by older pages; dark mode retains their original color. */
    static int readable(int color) {
        if(!lightAppearance)return color;
        switch(color){
            case 0xFFFF8D8D:return 0xFFB82F36;
            case 0xFF8FB2FF:return 0xFF245EA8;
            case 0xFFC3CAD1:return 0xFF526574;
            case 0xFFCFEFEA:return 0xFF265D57;
            case 0xFF8A8F98:return 0xFF586974;
            case 0xFFEB5757:return 0xFFB82F36;
            case 0xFF64B5F6:return 0xFF2167B0;
            case 0xFF8FD36A:return 0xFF367E35;
            case 0xFFB48CFF:return 0xFF7151B2;
            case 0xFFFF8A65:return 0xFFB44D29;
            case 0xFFFFB05C:return 0xFF9A5A15;
            default:return color;
        }
    }
    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static TextView text(Context c, String s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(readable(color));
        t.setIncludeFontPadding(false);
        // Arabic reads right to left; the lines then start at the right edge of the view (centered text stays centered)
        if (L.isArabic()) t.setTextDirection(View.TEXT_DIRECTION_RTL);
        return t;
    }

    /** Layout direction for the content area: right to left when the app language is Arabic. */
    static int contentDirection() {
        return L.isArabic() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR;
    }

    /** Shows a dialog laid out in the app language's direction (buttons and text on the right side). */
    static android.app.AlertDialog show(android.app.AlertDialog.Builder b) {
        android.app.AlertDialog d = b.create();
        d.show();
        try {
            d.getWindow().getDecorView().setLayoutDirection(contentDirection());
            // the stock dialog title follows the device locale, so give it the same direction as the rest
            android.view.View title = d.findViewById(
                    d.getContext().getResources().getIdentifier("alertTitle", "id", "android"));
            if (title != null) {
                title.setLayoutDirection(contentDirection());
                if (title.getParent() instanceof android.view.View) {
                    ((android.view.View) title.getParent()).setLayoutDirection(contentDirection());
                }
            }
        } catch (Throwable ignored) {
            // cosmetic only
        }
        return d;
    }

    static GradientDrawable round(int color, float radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    static GradientDrawable stroke(int color, int fill, float radiusDp, Context c) {
        GradientDrawable d = round(fill, radiusDp, c);
        d.setStroke(dp(c, 1), color);
        return d;
    }

    static String f(double v, String fmt) {
        return Double.isNaN(v) ? "--" : String.format(java.util.Locale.US, fmt, v);
    }

    static String duration(long sec) {
        long h = sec / 3600;
        long m = (sec % 3600) / 60;
        if (h > 0) return L.f("%1$dh %2$dm", h, m);
        return L.f("%d min", m);
    }
}

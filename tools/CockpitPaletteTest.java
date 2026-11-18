package org.triprecorderng;

final class CockpitPaletteTest {
    private static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
    private static double linear(int v){double n=v/255.;return n<=.04045?n/12.92:Math.pow((n+.055)/1.055,2.4);}
    private static double lum(int c){return .2126*linear((c>>>16)&255)+.7152*linear((c>>>8)&255)+.0722*linear(c&255);}
    private static double contrast(int a,int b){double x=lum(a),y=lum(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
    public static void main(String[] args){
        String[] themes={"gtr","type_r","jdm_evo_ix","gr_motorsport","n_performance","rs_digital","taycan_sport","yangwang_u9_xtreme"};
        int[] colors={0xff080808,0xfff5f5f6,0xffb6bbc0,0xff303338,0xffff821d,0xff28d9ec,0xffed1429,0xff40aaff,0xff66e57b};
        for(String theme:themes){
            CockpitPalette night=new CockpitPalette(theme,false),day=new CockpitPalette(theme,true);
            for(int c:colors)check(night.fill(c)==c&&night.text(c)==c&&night.stroke(c)==c&&night.platePixel(c)==c,theme+" night unchanged");
            int bg=day.background(),dial=day.platePixel(0xff090909);
            check(lum(bg)>.7&&lum(dial)>.7,theme+" daytime surface");
            check(contrast(day.text(0xfff5f5f6),dial)>7,theme+" primary ink");
            check(contrast(day.text(0xffb6bbc0),bg)>4.5,theme+" secondary ink");
            check(contrast(day.accent(),bg)>4.5,theme+" signature on background");
            check(contrast(0xffffffff,day.accent())>4.5,theme+" active nav inverse ink");
            check(day.platePixel(0xffff821d)==0xffff821d,theme+" orange trim intact");
            check(day.platePixel(0xffed1429)==0xffed1429,theme+" red trim intact");
            check(day.platePixel(0xff40aaff)==0xff40aaff,theme+" blue trim intact");
            check(day.text(0xffffffff)==0xffffffff,theme+" explicit reverse ink");
            check(day.stroke(0xffd0dbe2)==0xffd0dbe2,theme+" graph grid not remapped");
            check(day.text(0xff526574)==0xff526574,theme+" graph labels not remapped");
            check((day.platePixel(0x8a090909)>>>24)==0x8a,theme+" alpha intact");
        }
        System.out.println("CockpitPaletteTest PASS (eight material palettes, nighttime identity, contrast and graph ink)");
    }
}

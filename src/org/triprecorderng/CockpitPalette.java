package org.triprecorderng;

/** Daytime materials and instrument inks. Night colours are passed through unchanged. */
final class CockpitPalette {
    final boolean light;
    private final int tintR,tintG,tintB;
    private final int signature;
    private final boolean flatHousing;

    CockpitPalette(String theme,boolean light){
        this.light=light;
        flatHousing="type_r".equals(theme);
        // Each cockpit keeps its own material tint and racing accent.
        switch(theme==null?"":theme){
            case "n_performance": tintR=-13;tintG=0;tintB=9;signature=0xff176eab;break;
            case "taycan_sport": tintR=1;tintG=2;tintB=3;signature=0xff006a72;break;
            case "yangwang_u9_xtreme": tintR=0;tintG=1;tintB=4;signature=0xffb84d00;break;
            case "type_r": tintR=2;tintG=2;tintB=3;signature=0xffbc142a;break;
            case "gr_motorsport": tintR=3;tintG=3;tintB=3;signature=0xffba1930;break;
            case "rs_digital": tintR=-2;tintG=0;tintB=5;signature=0xffbb1828;break;
            case "jdm_evo_ix": tintR=1;tintG=1;tintB=2;signature=0xffbd182a;break;
            default: tintR=-3;tintG=0;tintB=4;signature=0xffbc182a;break;
        }
    }
    private static int clamp(int v){return Math.max(0,Math.min(255,v));}
    private int neutral(int v){return 0xff000000|(clamp(v+tintR)<<16)|(clamp(v+tintG)<<8)|clamp(v+tintB);}
    int background(){return neutral(240);}
    int accent(){return signature;}

    /** Tone-map the immutable housing, keeping metallic highlights and chromatic accents.
     * Only the original plate pixels are changed; no layout or reading is baked into it. */
    int platePixel(int c){return platePixel(c,0,0,0);}
    int platePixel(int c,int x,int py,int width){
        if(!light)return c;
        int r=(c>>>16)&255,g=(c>>>8)&255,b=c&255;
        int max=Math.max(r,Math.max(g,b)),min=Math.min(r,Math.min(g,b));
        int y=(54*r+183*g+19*b+128)>>8;
        // Strong colour (red motorsport stripe, blue N trim, orange XTREME frame)
        // stays the same hue, including its shading, rather than becoming a negative.
        if(max>=55&&max-min>=36&&max-min>=max*.37f)return c;
        // Silver composite retains the original painted highlights and bezel shading.
        int v=y<=105?243-(y*68/105):175+(y-105)*77/150;
        // 2-over-2 twill: alternating perpendicular fibre tows with fine relief.
        // Use logical coordinates so differently sized housing assets have the same weave.
        // Metallic bevels and coloured trim are excluded from the composite material.
        if(width>0&&y<80){
            int u=x*1200/width,w=py*1200/width;
            int tu=u/7,tw=w/7;
            boolean warp=((tu-tw)&3)<2;
            int across=warp?u%7:w%7;
            int along=warp?w%7:u%7;
            int relief=across==0||across==6?-10:across==1||across==5?-4:across==2||across==4?2:5;
            int weave=(warp?5:-6)+relief+(across%2==0?2:-2)-(along==0?3:0);
            // Side panels carry a more pronounced weave; the central speed display
            // remains quieter. Fade out at metallic highlights to avoid hard edges.
            int edge=Math.abs(u-600);
            int strength=42+Math.min(58,Math.max(0,(edge-180)*58/270));
            if(w<46||w>365)strength=Math.max(strength,85);
            // Near-black dial recesses become smooth satin faces, not patterned glass.
            int floor=flatHousing&&edge>260?65:25;
            strength=strength*(floor+Math.min(8,y)*(100-floor)/8)/100;
            strength=strength*Math.min(40,80-y)/40;
            v+=(-19+weave)*strength/100;
        }
        return (c&0xff000000)|(neutral(v)&0x00ffffff);
    }

    int text(int c){
        if(!light||c==0xffffffff)return c; // explicit reverse ink on active chips
        switch(c){
            case 0xff142633:case 0xff526574:case 0xff006a72:case 0xff176eab:
            case 0xffb84d00:case 0xffbc192d:case 0xffbc142a:case 0xffba1930:
            case 0xffbb1828:case 0xffbd182a:case 0xffbc182a:case 0xff227d3b:
                return c; // already daytime ink
            case 0xffb3b3b3:case 0xffb6b8bc:case 0xffb6bbc0:case 0xffb9c1cc:case 0xffadb6c2:
                return 0xff3f4b56;
            default:break;
        }
        int accent=inkAccent(c);
        return accent!=c?accent:0xff17212b;
    }
    int stroke(int c){
        if(!light)return c;
        switch(c){
            case 0xffd0dbe2:case 0xff637078:case 0xff526574:return c;
            case 0xff303337:return 0xffc4cbd2;
            case 0xff36383e:case 0xff484c50:case 0xff4e5661:case 0xff5b5b5e:return 0xff85919b;
            case 0xfff3f2f3:case 0xfff5f5f6:case 0xfff5f7fa:case 0xfff8f8f9:return 0xff303b43;
            case 0xffffb3b1:case 0xffffd0d0:case 0xff53151b:return c; // needle highlight/shadow
            default:return inkAccent(c);
        }
    }
    int fill(int c){
        if(!light)return c;
        switch(c){
            case 0xff050506:case 0xff080808:case 0xff08090b:return background();
            case 0xff101113:case 0xff111316:case 0xff13191e:return neutral(226);
            case 0xff202024:return 0xff40474c;
            case 0xff25262a:case 0xff303338:case 0xff30363d:case 0xff353537:return neutral(183);
            case 0xff525256:return 0xff747c82;
            case 0xff4b2026:return 0xffad7278;
            case 0xff203c50:return 0xff8aabba;
            case 0xfff3f2f3:case 0xfff5f5f6:case 0xfff5f7fa:case 0xfff8f8f9:return 0xff303b43;
            default:return inkAccent(c);
        }
    }
    private static int inkAccent(int c){
        switch(c){
            case 0xff28d9ec:case 0xff32d4ed:case 0xff46dcea:case 0xff43d9ec:return 0xff006a72;
            case 0xff35aafb:case 0xff40aaff:case 0xff42a9ff:return 0xff176eab;
            case 0xff66e57b:return 0xff227d3b;
            case 0xffff821d:case 0xffff791a:return 0xffb84d00;
            case 0xffde2732:case 0xffed1429:case 0xfff1293b:case 0xffff2638:case 0xffff3845:
            case 0xffff4148:case 0xffff5156:case 0xffff535d:case 0xfff4525e:return 0xffbc192d;
            default:return c;
        }
    }
}

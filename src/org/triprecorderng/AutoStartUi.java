package org.triprecorderng;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

/** Conservative interpretation of BYD's accessibility hierarchy. Never guess coordinates or state. */
final class AutoStartUi {
    static final String PACKAGE = "com.byd.appstartmanagement";
    enum State { ON, OFF, NOT_FOUND, UNSAFE }
    static final class Target {
        final State state;
        final int x, y, endY;
        Target(State s, int x, int y) { this(s,x,y,0); }
        Target(State s,int x,int y,int endY) { state=s; this.x=x; this.y=y; this.endY=endY; }
    }
    private static final Pattern BOUNDS=Pattern.compile("\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]");
    private static Target unknown(State s) { return new Target(s, 0, 0); }
    private static boolean byd(Element e) { return PACKAGE.equals(e.getAttribute("package")); }
    private static boolean yes(Element e,String a) { return "true".equals(e.getAttribute(a)); }
    private static int[] bounds(Element e) {
        Matcher m=BOUNDS.matcher(e.getAttribute("bounds"));
        if(!m.matches())return null;
        int[] b=new int[4];
        try {for(int i=0;i<4;i++)b[i]=Integer.parseInt(m.group(i+1));}
        catch(NumberFormatException ex){return null;}
        if(b[2]<=b[0]||b[3]<=b[1]||b[2]>20000||b[3]>20000)return null;
        return b;
    }
    private static List<Element> nodes(Element parent) {
        List<Element> out=new ArrayList<>();
        if("node".equals(parent.getTagName()))out.add(parent);
        NodeList children=parent.getElementsByTagName("node");
        for(int i=0;i<children.getLength();i++)out.add((Element)children.item(i));
        return out;
    }
    static Target inspect(String dump) {
        if(dump==null||dump.length()>2_000_000||dump.contains("<!DOCTYPE")||dump.contains("<!ENTITY"))return unknown(State.UNSAFE);
        int first=dump.indexOf("<hierarchy"), last=dump.lastIndexOf("</hierarchy>");
        if(first<0||last<first)return unknown(State.UNSAFE);
        try {
            DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();
            factory.setExpandEntityReferences(false);
            Element root=factory.newDocumentBuilder().parse(new InputSource(new StringReader(
                    dump.substring(first,last+"</hierarchy>".length())))).getDocumentElement();
            List<Element> labels=new ArrayList<>();
            boolean seen=false;
            for(Element e:nodes(root)) {
                if(!byd(e))continue;
                seen=true;
                if("EV TripLog".equals(e.getAttribute("text")))labels.add(e);
            }
            if(!seen)return unknown(State.UNSAFE);
            if(labels.isEmpty()) {
                List<Element> scrolls=new ArrayList<>();
                for(Element node:nodes(root))if(byd(node)&&yes(node,"scrollable")&&yes(node,"enabled"))scrolls.add(node);
                if(scrolls.size()==1) {
                    int[] b=bounds(scrolls.get(0));
                    if(b!=null&&b[3]-b[1]>=120&&b[2]-b[0]>=100)
                        return new Target(State.NOT_FOUND,(b[0]+b[2])/2,b[1]+3*(b[3]-b[1])/4,b[1]+(b[3]-b[1])/4);
                }
                return unknown(State.NOT_FOUND);
            }
            if(labels.size()!=1)return unknown(State.UNSAFE);
            Element label=labels.get(0);
            int[] lb=bounds(label);
            if(lb==null)return unknown(State.UNSAFE);
            // Smallest ancestor with a checkable control must be the app's own row.
            for(Node n=label.getParentNode();n instanceof Element;n=n.getParentNode()) {
                Element row=(Element)n;
                if(!byd(row))break;
                List<Element> switches=new ArrayList<>();
                for(Element e:nodes(row))if(byd(e)&&yes(e,"checkable"))switches.add(e);
                if(switches.isEmpty())continue;
                if(switches.size()!=1)return unknown(State.UNSAFE);
                Element sw=switches.get(0);
                if(!yes(sw,"enabled")||!yes(sw,"clickable"))return unknown(State.UNSAFE);
                int[] sb=bounds(sw), rb=bounds(row);
                if(sb==null||rb==null)return unknown(State.UNSAFE);
                int x=(sb[0]+sb[2])/2,y=(sb[1]+sb[3])/2;
                // Reject container-wide or unrelated controls and mismatched rows.
                if(x<lb[2]||y<lb[1]-Math.max(10,(lb[3]-lb[1])/2)
                        ||y>lb[3]+Math.max(10,(lb[3]-lb[1])/2)
                        ||x<rb[0]||x>=rb[2]||y<rb[1]||y>=rb[3])return unknown(State.UNSAFE);
                for(Element e:nodes(row)) {
                    if(e==label||e==sw)continue;
                    String t=e.getAttribute("text");
                    if(!t.isEmpty()&&!t.equals("EV TripLog"))return unknown(State.UNSAFE);
                }
                String checked=sw.getAttribute("checked");
                if("true".equals(checked))return new Target(State.ON,x,y);
                if("false".equals(checked))return new Target(State.OFF,x,y);
                return unknown(State.UNSAFE);
            }
        }catch(Exception ignored){}
        return unknown(State.UNSAFE);
    }
}

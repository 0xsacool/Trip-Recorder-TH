package org.triprecorderng;

public final class AutoStartUiTest {
    static String pkg=AutoStartUi.PACKAGE;
    static String node(String attrs,String body) {
        return "<node package=\""+pkg+"\" "+attrs+">"+body+"</node>";
    }
    static String row(String name,String state,int y,boolean checkable) {
        return node("bounds=\"[0,"+y+"][1000,"+(y+100)+"]\"",
            node("text=\""+name+"\" bounds=\"[100,"+(y+26)+"][300,"+(y+74)+"]\"","")+
            node("checkable=\""+checkable+"\" checked=\""+state+"\" enabled=\"true\" clickable=\"true\" bounds=\"[800,"+(y+20)+"][900,"+(y+80)+"]\"",""));
    }
    static String dump(String rows) {
        return "dump output\n<hierarchy rotation=\"0\">"+node("bounds=\"[0,0][1000,1000]\"",rows)+"</hierarchy>";
    }
    static void expect(AutoStartUi.State want,String xml) {
        AutoStartUi.Target actual=AutoStartUi.inspect(xml);
        if(actual.state!=want)throw new AssertionError("expected "+want+", got "+actual.state);
        if(want==AutoStartUi.State.ON&&(actual.x!=850||actual.y!=250))throw new AssertionError("wrong row");
    }
    public static void main(String[] args) {
        expect(AutoStartUi.State.ON,dump(row("Other","true",100,true)+row("EV TripLog","true",200,true)));
        expect(AutoStartUi.State.OFF,dump(row("Other","true",100,true)+row("EV TripLog","false",200,true)));
        expect(AutoStartUi.State.NOT_FOUND,dump(row("Other","true",100,true)));
        expect(AutoStartUi.State.UNSAFE,dump(row("EV TripLog","true",200,false)+row("Other","true",300,true)));
        expect(AutoStartUi.State.UNSAFE,dump(row("EV TripLog","true",200,true)+row("EV TripLog","false",300,true)));
        expect(AutoStartUi.State.UNSAFE,dump(row("EV TripLog","true",200,true)).replace(pkg,"com.other.app"));
        expect(AutoStartUi.State.UNSAFE,dump(row("EV TripLog","true",200,true)).replace("checked=\"true\"","checked=\"unknown\""));
        AutoStartUi.Target list=AutoStartUi.inspect(dump(node("scrollable=\"true\" enabled=\"true\" bounds=\"[0,100][1000,900]\"",row("Other","true",100,true))));
        if(list.state!=AutoStartUi.State.NOT_FOUND||list.x!=500||list.y!=700||list.endY!=300)throw new AssertionError("wrong scroll region");
        expect(AutoStartUi.State.UNSAFE,"not xml");
        System.out.println("BYD auto-start switch recognition tests passed");
    }
}

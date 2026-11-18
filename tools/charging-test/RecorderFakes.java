package org.triprecorderng;

import android.content.Context;
import android.location.Location;
import java.util.*;

// JVM-only adapters: exercise actual ChargeRecorder against a snapshotting in-memory store.
final class Prefs {
    static double[] last;
    static double[] lastSoc(Context c) { return last; }
    static void setLastSoc(Context c, double s, long t) { last = new double[]{s,t}; }
    static boolean autoBackup(Context c) { return false; }
}
final class Gps { Location fresh(long ms) { return null; } }
final class Diag { static void log(String s) {} static void log(String s, Throwable t) { throw new AssertionError(s,t); } }
final class L { static String t(String s) { return s; } }
final class Backup { static void writeCharge(Context c, Charge ch) {} }
final class TripDb {
    final Map<String,Charge> rows = new LinkedHashMap<>();
    final Map<String,List<double[]>> points = new HashMap<>();
    private Charge copy(Charge x) {
        Charge c = new Charge(); c.id=x.id; c.startMs=x.startMs; c.endMs=x.endMs; c.lastUpdateMs=x.lastUpdateMs;
        c.ended=x.ended; c.socStart=x.socStart; c.socEnd=x.socEnd; c.kwhSignal=x.kwhSignal;
        c.maxPowerKw=x.maxPowerKw; c.gun=x.gun; c.partial=x.partial; c.windowOnly=x.windowOnly; c.lat=x.lat; c.lon=x.lon;
        return c;
    }
    void upsertCharge(Charge c) { rows.put(c.id,copy(c)); }
    void addChargePoints(String id,List<double[]> p) { points.computeIfAbsent(id,k->new ArrayList<>()).addAll(p); }
    void deleteCharge(String id) { rows.remove(id); points.remove(id); }
    boolean hasTripBetween(long a,long b) { return false; }
    void moveChargePoints(String b,String a) { addChargePoints(a,points.getOrDefault(b,Collections.emptyList())); }
    List<Charge> charges() { List<Charge> l=new ArrayList<>(); for(Charge c:rows.values())l.add(copy(c)); l.sort((a,b)->Long.compare(b.startMs,a.startMs)); return l; }
    Charge lastOpenCharge() { return charges().stream().filter(c->!c.ended).findFirst().orElse(null); }
    Charge latestEndedCharge() { return charges().stream().filter(c->c.ended).findFirst().orElse(null); }
}

package dev.asaul.litematicainventories;
import java.util.*;
/** Pure logic, independent of Minecraft and mutable stacks. */
public final class ComparisonEngine {
    public record Amount<K>(K key,long count) {}
    public record Totals(long expected,long actual,long missing,long extra) {}
    public enum Status { MATCH, MISSING, EXTRA, WRONG, UNDER, OVER, UNKNOWN }
    public static <K> Status strict(Amount<K> e,Amount<K> a) {
        if(e==null||a==null) return Status.UNKNOWN;
        if(e.count==0 && a.count==0) return Status.MATCH;
        if(e.count==0) return Status.EXTRA;
        if(a.count==0) return Status.MISSING;
        if(!Objects.equals(e.key,a.key)) return Status.WRONG;
        return e.count==a.count?Status.MATCH:e.count>a.count?Status.UNDER:Status.OVER;
    }
    public static <K> Map<K,Long> counts(List<Amount<K>> slots) {
        Map<K,Long> result=new LinkedHashMap<>();
        for(var s:slots) if(s!=null && s.count>0) result.merge(s.key,s.count,Math::addExact);
        return result;
    }
    public static <K> Map<K,Totals> aggregate(List<Amount<K>> expected,List<Amount<K>> actual) {
        var e=counts(expected);var a=counts(actual);Set<K> keys=new LinkedHashSet<>(e.keySet());keys.addAll(a.keySet());
        Map<K,Totals> result=new LinkedHashMap<>();
        for(K k:keys) {long ec=e.getOrDefault(k,0L),ac=a.getOrDefault(k,0L);result.put(k,new Totals(ec,ac,Math.max(0,ec-ac),Math.max(0,ac-ec)));}
        return result;
    }
}

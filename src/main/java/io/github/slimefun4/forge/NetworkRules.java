package io.github.slimefun4.forge;

import java.util.*;

/** Internal pure rules shared by the live network and GEO/GPS services. */
public final class NetworkRules {
    public static int reserve(int base,int deviation,int offset){
        if(base<0||deviation<0||offset<0||deviation==0&&offset!=0||deviation>0&&offset>=deviation||base>Integer.MAX_VALUE-Math.max(0,deviation-1))throw new IllegalArgumentException("Invalid GEO base/deviation");
        return base==0?0:base+offset;
    }
    public static int sum(Collection<Integer> values){long total=0;for(int value:values){if(value<0)throw new IllegalArgumentException("Negative GPS signal");total=Math.min(Integer.MAX_VALUE,total+value);}return (int)total;}
    public static int[] distribute(long supply,List<Integer> demands){
        if(supply<0)throw new IllegalArgumentException("Negative energy supply");var result=new int[demands.size()];
        for(int i=0;i<result.length;i++){int demand=demands.get(i);if(demand<0)throw new IllegalArgumentException("Negative energy demand");result[i]=(int)Math.min(supply,demand);supply-=result[i];}return result;
    }
    public static String waypointId(String name){
        if(name==null||name.isBlank()||name.length()>256||name.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid waypoint name");
        var plain=name.replaceAll("(?i)[&§][0-9A-FK-ORX]","");if(plain.isBlank())throw new IllegalArgumentException("Empty waypoint ID");return plain.toUpperCase(Locale.ROOT).replace(' ','_');
    }
    private NetworkRules(){}
}

package io.github.slimefun4.forge;

import java.util.*;

/** Validates the fixed category tree once; paths include the category itself. */
final class GuideHierarchy {
    static Map<String,List<String>> paths(Map<String,String> parents){
        var result=new LinkedHashMap<String,List<String>>();
        for(String id:parents.keySet()){
            var path=new LinkedHashSet<String>();String next=id;
            while(!next.isEmpty()){
                if(!parents.containsKey(next))throw new IllegalArgumentException("Missing guide parent "+next+" for "+id);
                if(!path.add(next))throw new IllegalArgumentException("Cyclic guide category "+id+": "+path);
                next=Objects.requireNonNull(parents.get(next),"Guide parent "+next);
            }
            result.put(id,List.copyOf(path));
        }
        return Map.copyOf(result);
    }
    static boolean shared(String categoryOwner,String entryOwner,Set<String> sharedWith){return categoryOwner.equals(entryOwner)||sharedWith.contains(entryOwner);}
    private GuideHierarchy(){}
}

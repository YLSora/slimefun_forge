package io.github.slimefun4.forge;

import java.util.*;
import java.util.function.*;

/** Internal incremental graph; the native API supplies loaded positions and axis neighbours. */
public final class NetworkTopology<P extends Comparable<? super P>> {
    public enum Roots { EXCLUSIVE, LOWEST }
    public enum Role { ROOT, RELAY, TERMINAL }
    private record Node(Role role,int range) {}
    public record View<P>(P root,SortedSet<P> members,boolean complete,boolean overflow) {}
    private final class Grid {
        P leader;final long version;final SortedSet<P> members=new TreeSet<>();
        final Map<P,Boolean> seen=new LinkedHashMap<>();final Deque<P> pending=new ArrayDeque<>();boolean overflow;
        Grid(P root){leader=root;version=revision;seen.put(root,null);pending.add(root);}
    }
    private final Roots roots;private final int maxNodes;private final BiFunction<P,Integer,? extends Collection<P>> neighbours;
    private final Map<P,Node> nodes=new HashMap<>();private final Map<P,Grid> grids=new LinkedHashMap<>();private long revision;
    public NetworkTopology(Roots roots,int maxNodes,BiFunction<P,Integer,? extends Collection<P>> neighbours){
        this.roots=Objects.requireNonNull(roots);this.neighbours=Objects.requireNonNull(neighbours);if(maxNodes<1)throw new IllegalArgumentException("Empty network limit");this.maxNodes=maxNodes;
    }
    public void put(P pos,Role role,int range){if(range<0||range>64||role!=Role.TERMINAL&&range==0)throw new IllegalArgumentException("Network range must be 1..64 (terminal: 0..64)");var node=new Node(Objects.requireNonNull(role),range);if(!node.equals(nodes.put(pos,node)))revision++;}
    public void remove(P pos){if(nodes.remove(pos)!=null){revision++;grids.remove(pos);}}
    public void dirty(){revision++;}
    public Set<P> positions(){return Set.copyOf(nodes.keySet());}
    public void clear(){nodes.clear();grids.clear();revision++;}
    private boolean stale(Grid grid,Predicate<P> loaded){return grid.version!=revision||grid.seen.entrySet().stream().anyMatch(e->e.getValue()!=null&&e.getValue()!=loaded.test(e.getKey()));}
    public View<P> step(P root,int budget,Predicate<P> loaded){
        if(budget<1)throw new IllegalArgumentException("Network budget must be positive");
        if(!nodes.containsKey(root)||nodes.get(root).role()!=Role.ROOT)throw new IllegalArgumentException("Not a registered network root");
        grids.entrySet().removeIf(e->stale(e.getValue(),loaded));
        if(roots==Roots.EXCLUSIVE)for(var other:grids.entrySet())if(!other.getKey().equals(root)&&other.getValue().seen.containsKey(root))return view(other.getValue());
        var grid=grids.computeIfAbsent(root,Grid::new);
        for(int i=0;i<budget&&!grid.pending.isEmpty()&&!grid.overflow;i++){
            var pos=grid.pending.removeFirst();boolean live=loaded.test(pos);grid.seen.put(pos,live);var node=nodes.get(pos);if(!live||node==null)continue;
            if(node.role()==Role.ROOT&&!pos.equals(root)){if(roots==Roots.EXCLUSIVE)continue;if(pos.compareTo(grid.leader)<0)grid.leader=pos;}
            if(grid.members.size()==maxNodes){grid.overflow=true;grid.pending.clear();break;}
            grid.members.add(pos);
            if(node.role()!=Role.TERMINAL)for(var next:neighbours.apply(pos,node.range()))if(!grid.seen.containsKey(next)){grid.seen.put(next,null);grid.pending.addLast(next);}
        }
        return view(grid);
    }
    public Optional<View<P>> at(P pos,Predicate<P> loaded){grids.entrySet().removeIf(e->stale(e.getValue(),loaded));return grids.values().stream().filter(g->g.members.contains(pos)).findFirst().map(this::view);}
    private View<P> view(Grid grid){return new View<>(grid.leader,Collections.unmodifiableSortedSet(new TreeSet<>(grid.members)),grid.pending.isEmpty(),grid.overflow);}
}

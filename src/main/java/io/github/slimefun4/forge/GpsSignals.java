package io.github.slimefun4.forge;

import java.util.*;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import io.github.slimefun4.forge.api.*;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Only paid, loaded transmitters contribute; owner signals span dimensions within one server. */
public final class GpsSignals {
    private record Signal(java.lang.ref.WeakReference<BlockEntity> source,GpsApi.Signal snapshot){int strength(){return snapshot.strength();}int complexity(){return snapshot.complexity();}}
    private static final Map<MinecraftServer,Map<UUID,Map<GlobalPos,Signal>>> SERVERS=new WeakHashMap<>();
    public static void update(BlockEntity transmitter,boolean active) {
        if(!(transmitter.getLevel() instanceof ServerLevel level))return;NetworkApi.check(level);
        var server=level.getServer();var pos=GlobalPos.of(level.dimension(),transmitter.getBlockPos().immutable());
        var existing=SERVERS.get(server);if(existing!=null){for(var nodes:existing.values())nodes.entrySet().removeIf(e->e.getKey().equals(pos)&&e.getValue().source().get()==transmitter);existing.values().removeIf(Map::isEmpty);if(existing.isEmpty())SERVERS.remove(server);}
        if(active) {
            if(!(transmitter instanceof GpsApi.Transmitter provider)||!NetworkApi.live(level,transmitter)||provider.gpsOwner()==null)throw new IllegalArgumentException("Live owned GPS transmitter required");
            int strength=provider.signalStrength(),complexity=provider.signalComplexity();if(strength<0||complexity<0)throw new IllegalArgumentException("Negative GPS signal");
            var snapshot=new GpsApi.Signal(pos,strength,complexity,provider.gpsIcon());
            SERVERS.computeIfAbsent(server,k->new HashMap<>()).computeIfAbsent(provider.gpsOwner(),k->new HashMap<>()).put(pos,new Signal(new java.lang.ref.WeakReference<>(transmitter),snapshot));
        }
    }
    private static void prune(MinecraftServer server){
        if(!server.isSameThread())throw new IllegalStateException("GPS requires server thread");var owners=SERVERS.get(server);if(owners==null)return;
        for(var entry:owners.entrySet())entry.getValue().entrySet().removeIf(e->{var source=e.getValue().source().get();var level=server.getLevel(e.getKey().dimension());return source==null||level==null||!NetworkApi.live(level,source)||!(source instanceof GpsApi.Transmitter provider)||!entry.getKey().equals(provider.gpsOwner());});owners.values().removeIf(Map::isEmpty);if(owners.isEmpty())SERVERS.remove(server);
    }
    public static int complexity(MinecraftServer server,UUID owner) {
        prune(server);
        var owners=SERVERS.get(server); if(owners==null) return 0;
        var nodes=owners.get(owner);return nodes==null?0:NetworkRules.sum(nodes.values().stream().map(Signal::complexity).toList());
    }
    public static Map<GlobalPos,Integer> transmitters(MinecraftServer server,UUID owner) {
        prune(server);
        var owners=SERVERS.get(server);if(owners==null)return Map.of();
        var result=new HashMap<GlobalPos,Integer>();owners.getOrDefault(owner,Map.of()).forEach((pos,signal)->result.put(pos,signal.strength()));return Map.copyOf(result);
    }
    public static List<GpsApi.Signal> signals(MinecraftServer server,UUID owner){prune(server);var owners=SERVERS.get(server);return owners==null?List.of():owners.getOrDefault(owner,Map.of()).values().stream().map(Signal::snapshot).sorted(Comparator.comparing((GpsApi.Signal s)->s.position().dimension().location()).thenComparing(s->s.position().pos())).toList();}
    public static void unload(ServerLevel level) {
        var owners=SERVERS.get(level.getServer()); if(owners==null) return;
        for(var nodes:owners.values()) nodes.keySet().removeIf(pos->pos.dimension().equals(level.dimension()));
        owners.values().removeIf(Map::isEmpty); if(owners.isEmpty()) SERVERS.remove(level.getServer());
    }
    public static void stop(MinecraftServer server) { SERVERS.remove(server); }
    private GpsSignals() {}
}

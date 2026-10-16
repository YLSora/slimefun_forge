package io.github.slimefun4.forge.api;

import io.github.slimefun4.forge.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** UUID profiles aggregate existing native stores. Mutations require the server thread and caller authorization. */
public final class PlayerApi {
    public record Status(int level,long expiresAt){public Status{if(expiresAt<0)throw new IllegalArgumentException("Negative status deadline");}public boolean present(long now){return PlayerRules.present(expiresAt,now);}}
    public record Profile(UUID owner,boolean online,String name,CompoundTag data,Map<ResourceLocation,Status> statuses,
            List<ResearchApi.State> researches,Set<Integer> backpacks,List<GpsWaypoints.Waypoint> waypoints,
            List<CompoundTag> guideHistory,ArmorSnapshot armor,Set<ResourceLocation> titles,Map<ResourceLocation,Long> statistics,CompoundTag nativeData){
        public Profile{Objects.requireNonNull(owner);Objects.requireNonNull(name);data=data.copy();statuses=Map.copyOf(statuses);researches=List.copyOf(researches);backpacks=Set.copyOf(backpacks);waypoints=List.copyOf(waypoints);guideHistory=guideHistory.stream().map(CompoundTag::copy).toList();Objects.requireNonNull(armor);titles=Set.copyOf(titles);statistics=Map.copyOf(statistics);nativeData=nativeData.copy();}
        @Override public CompoundTag data(){return data.copy();}@Override public CompoundTag nativeData(){return nativeData.copy();}@Override public List<CompoundTag> guideHistory(){return guideHistory.stream().map(CompoundTag::copy).toList();}
        public Profile withNative(CompoundTag nativeData){return new Profile(owner,online,name,data,statuses,researches,backpacks,waypoints,guideHistory,online?armor:ArmorSnapshot.fromPlayerData(nativeData),titles,statistics,nativeData);}
    }
    /** Start on the server thread. Completion/failures return there; only the load event runs on IO.
     * Stop cancels pending requests; the server must still be running when scheduling subsequent work.
     */
    public static CompletableFuture<Optional<Profile>> request(MinecraftServer server,UUID owner){return PlayerData.request(server,owner);}
    public static CompletableFuture<Set<UUID>> players(MinecraftServer server){return PlayerData.players(server);}
    public static void edit(MinecraftServer server,UUID owner,ResourceLocation key,Consumer<CompoundTag> edit){PlayerData.get(server).edit(owner,key,edit);}
    public static CompoundTag data(MinecraftServer server,UUID owner,ResourceLocation key){return PlayerData.get(server).data(owner,key);}
    public static void status(MinecraftServer server,UUID owner,ResourceLocation key,int level,long duration,TimeUnit unit){PlayerData.get(server).status(owner,key,new Status(level,PlayerRules.deadline(System.currentTimeMillis(),duration,unit)));}
    public static void permanent(MinecraftServer server,UUID owner,ResourceLocation key,int level){PlayerData.get(server).status(owner,key,new Status(level,0));}
    public static boolean present(MinecraftServer server,UUID owner,ResourceLocation key){return PlayerData.get(server).present(owner,key);}
    /** Raw stored level, including an expired entry until present/clear removes it. */
    public static OptionalInt level(MinecraftServer server,UUID owner,ResourceLocation key){var status=PlayerData.get(server).status(owner,key);return status==null?OptionalInt.empty():OptionalInt.of(status.level());}
    public static void clear(MinecraftServer server,UUID owner,ResourceLocation key){PlayerData.get(server).status(owner,key,null);}
    public static Set<ResourceLocation> titles(MinecraftServer server,UUID owner){return PlayerData.get(server).titles(owner);}
    public static void title(MinecraftServer server,UUID owner,ResourceLocation key,boolean granted){PlayerData.get(server).title(owner,key,granted);}
    public static long statistic(MinecraftServer server,UUID owner,ResourceLocation key){return PlayerData.get(server).statistic(owner,key);}
    public static long addStatistic(MinecraftServer server,UUID owner,ResourceLocation key,long amount){return PlayerData.get(server).addStatistic(owner,key,amount);}
    public static ArmorSnapshot armor(ServerPlayer player){return PlayerData.armor(player);}
    /** Flush native SavedData, including each already-loaded research/backpack/waypoint store. No backup. */
    public static void save(MinecraftServer server){PlayerData.get(server);server.overworld().getDataStorage().save();}
    /** Offline only; clears Slimefun data, leaves vanilla player files and monotonic issued-ID counters intact. */
    public static boolean delete(MinecraftServer server,UUID owner){return PlayerData.delete(server,owner);}
    private PlayerApi(){}
}

package io.github.slimefun4.forge;

import io.github.slimefun4.forge.api.*;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Only addon-owned fields live here; native research, issued bags and GPS remain in their original stores. */
@Mod.EventBusSubscriber(modid="slimefun")
public final class PlayerData extends SavedData {
    private static final class Row {
        String name="";CompoundTag data=new CompoundTag();final Map<ResourceLocation,PlayerApi.Status> statuses=new HashMap<>();
        final Set<ResourceLocation> titles=new HashSet<>();final Map<ResourceLocation,Long> statistics=new HashMap<>();
    }
    private final Map<UUID,Row> players=new HashMap<>();private long revision;
    private static final Map<ServerPlayer,ArmorSnapshot> ARMOR=new WeakHashMap<>();
    private static final Map<MinecraftServer,Service> SERVICES=new IdentityHashMap<>();
    private static final class Service {
        final ExecutorService io=Executors.newSingleThreadExecutor(r->{var thread=new Thread(r,"Slimefun-profile-io");thread.setDaemon(true);return thread;});
        final Set<CompletableFuture<?>> requests=ConcurrentHashMap.newKeySet();volatile boolean closed;
    }
    public static PlayerData get(MinecraftServer server){return NativeData.get(server,"slimefun_players",PlayerData::load,PlayerData::new);}
    private void changed(){revision++;setDirty();}
    private Row row(UUID owner){return players.computeIfAbsent(Objects.requireNonNull(owner),id->new Row());}
    private static void key(ResourceLocation key){AddonContent.owned(key);}
    public CompoundTag data(UUID owner,ResourceLocation key){var row=players.get(owner);return row==null?new CompoundTag():row.data.getCompound(key.toString()).copy();}
    public void edit(UUID owner,ResourceLocation key,Consumer<CompoundTag> edit){key(key);var copy=data(owner,key);long before=revision;edit.accept(copy);if(revision!=before)throw new IllegalStateException("Reentrant profile edit changed the stored version");row(owner).data.put(key.toString(),copy.copy());changed();}
    public PlayerApi.Status status(UUID owner,ResourceLocation key){var row=players.get(owner);return row==null?null:row.statuses.get(key);}
    public void status(UUID owner,ResourceLocation key,PlayerApi.Status value){key(key);if(value==null){var row=players.get(owner);if(row!=null&&row.statuses.remove(key)!=null)changed();}else{row(owner).statuses.put(key,value);changed();}}
    public boolean present(UUID owner,ResourceLocation key){var status=status(owner,key);if(status==null)return false;if(status.present(System.currentTimeMillis()))return true;status(owner,key,null);return false;}
    public Set<ResourceLocation> titles(UUID owner){var row=players.get(owner);return row==null?Set.of():Set.copyOf(row.titles);}
    public void title(UUID owner,ResourceLocation key,boolean granted){key(key);var row=row(owner);if(granted?row.titles.add(key):row.titles.remove(key))changed();}
    public long statistic(UUID owner,ResourceLocation key){var row=players.get(owner);return row==null?0:row.statistics.getOrDefault(key,0L);}
    public long addStatistic(UUID owner,ResourceLocation key,long amount){key(key);if(amount<0)throw new IllegalArgumentException("Negative statistic increment");long next=Math.addExact(statistic(owner,key),amount);row(owner).statistics.put(key,next);changed();return next;}
    public static ArmorSnapshot armor(ServerPlayer player){var current=ArmorSnapshot.capture(player);var previous=ARMOR.get(player);if(!current.equals(previous))ARMOR.put(player,current);return ARMOR.get(player);}
    private static Set<UUID> known(MinecraftServer server){var ids=new HashSet<>(get(server).players.keySet());ids.addAll(ResearchData.get(server).players());ids.addAll(BackpackData.get(server).players());ids.addAll(GpsWaypoints.get(server).players());for(var player:server.getPlayerList().getPlayers())ids.add(player.getUUID());return ids;}
    private static PlayerApi.Profile snapshot(MinecraftServer server,UUID owner){
        var online=server.getPlayerList().getPlayer(owner);var row=get(server).players.getOrDefault(owner,new Row());
        return new PlayerApi.Profile(owner,online!=null,online==null?row.name:online.getGameProfile().getName(),row.data,row.statuses,ResearchApi.all(server,owner),BackpackData.get(server).ids(owner),GpsWaypoints.get(server).points(owner),online!=null&&online.containerMenu instanceof GuideMenu menu?menu.profileHistory():List.of(),online==null?new ArmorSnapshot(Map.of()):armor(online),row.titles,row.statistics,new CompoundTag());
    }
    private static <T> CompletableFuture<T> start(MinecraftServer server,BiConsumer<Service,CompletableFuture<T>> action){
        if(!server.isSameThread())throw new IllegalStateException("Profile requests must start on the server thread");
        var future=new CompletableFuture<T>();if(!server.isRunning()){future.completeExceptionally(new IllegalStateException("Server stopped"));return future;}
        Runnable begin=()->{try{var service=SERVICES.computeIfAbsent(server,s->new Service());if(service.closed)throw new IllegalStateException("Profile IO closed");service.requests.add(future);future.whenComplete((value,error)->service.requests.remove(future));action.accept(service,future);}catch(Throwable error){future.completeExceptionally(error);}};
        begin.run();return future;
    }
    private static <T> void finish(MinecraftServer server,Service service,CompletableFuture<T> future,T value,Throwable error){
        if(service.closed||future.isDone())return;server.execute(()->{if(service.closed||future.isDone())return;if(error==null)future.complete(value);else future.completeExceptionally(error);});
    }
    public static CompletableFuture<Optional<PlayerApi.Profile>> request(MinecraftServer server,UUID owner){Objects.requireNonNull(owner);return start(server,(service,future)->{
        var profile=snapshot(server,owner);boolean exists=known(server).contains(owner);var path=server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(owner+".dat");
        var online=server.getPlayerList().getPlayer(owner);CompoundTag current=online==null?null:online.saveWithoutId(new CompoundTag());
        service.io.execute(()->{try{
            CompoundTag nativeData=current;if(nativeData==null){if(Files.notExists(path)){if(!exists){finish(server,service,future,Optional.empty(),null);return;}nativeData=new CompoundTag();}else{nativeData=NbtIo.readCompressed(path.toFile());if(nativeData==null||!nativeData.hasUUID("UUID")||!owner.equals(nativeData.getUUID("UUID")))throw new IllegalArgumentException("Offline player file UUID mismatch: "+path);}}
            if(service.closed||future.isDone())return;var event=new AsyncProfileLoadEvent(owner,profile.withNative(nativeData));MinecraftForge.EVENT_BUS.post(event);finish(server,service,future,Optional.of(event.getProfile()),null);
        }catch(Throwable error){finish(server,service,future,null,error);}});
    });}
    public static CompletableFuture<Set<UUID>> players(MinecraftServer server){return start(server,(service,future)->{var known=known(server);var path=server.getWorldPath(LevelResource.PLAYER_DATA_DIR);service.io.execute(()->{try{if(!Files.notExists(path))try(var files=Files.list(path)){files.filter(p->p.getFileName().toString().endsWith(".dat")).forEach(p->{var name=p.getFileName().toString();try{known.add(UUID.fromString(name.substring(0,name.length()-4)));}catch(IllegalArgumentException ignored){}});}finish(server,service,future,Set.copyOf(known),null);}catch(Throwable error){finish(server,service,future,null,error);}});});}
    public static boolean delete(MinecraftServer server,UUID owner){
        var data=get(server);if(server.getPlayerList().getPlayer(owner)!=null)throw new IllegalStateException("Delete requires an offline player");
        boolean changed=data.players.remove(owner)!=null;changed|=ResearchData.get(server).delete(owner);changed|=BackpackData.get(server).delete(server,owner);changed|=GpsWaypoints.get(server).delete(owner);if(changed)data.changed();return changed;
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(event.getEntity() instanceof ServerPlayer player){var data=get(player.server);data.row(player.getUUID()).name=player.getGameProfile().getName();data.changed();}}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){ARMOR.remove(event.getEntity());}
    @SubscribeEvent public static void stop(ServerStoppedEvent event){var server=event.getServer();var service=SERVICES.remove(server);if(service!=null){service.closed=true;service.requests.forEach(f->f.completeExceptionally(new CancellationException("Server stopped")));service.io.shutdownNow();}ARMOR.keySet().removeIf(p->p.server==server);}
    private static ResourceLocation full(String key){if(!key.contains(":"))throw new IllegalArgumentException("Player data key requires a namespace");return new ResourceLocation(key);}
    public static PlayerData load(CompoundTag tag){
        var data=new PlayerData();if(!tag.contains("players",Tag.TAG_COMPOUND))throw new IllegalArgumentException("Missing player data table");var rows=tag.getCompound("players");
        for(var id:rows.getAllKeys()){var owner=UUID.fromString(id);if(!rows.contains(id,Tag.TAG_COMPOUND))throw new IllegalArgumentException("Invalid profile "+id);var saved=rows.getCompound(id);var row=new Row();row.name=saved.getString("name");
            for(String field:List.of("data","statuses","statistics"))if(!saved.contains(field,Tag.TAG_COMPOUND))throw new IllegalArgumentException("Missing profile "+field);
            row.data=saved.getCompound("data").copy();for(var key:row.data.getAllKeys()){full(key);if(!row.data.contains(key,Tag.TAG_COMPOUND))throw new IllegalArgumentException("Invalid profile extension "+key);}
            var statuses=saved.getCompound("statuses");for(var key:statuses.getAllKeys()){var value=statuses.getCompound(key);if(!value.contains("level",Tag.TAG_INT)||!value.contains("expires",Tag.TAG_LONG))throw new IllegalArgumentException("Invalid stored status "+key);row.statuses.put(full(key),new PlayerApi.Status(value.getInt("level"),value.getLong("expires")));}
            var statistics=saved.getCompound("statistics");for(var key:statistics.getAllKeys()){long value=statistics.getLong(key);if(!statistics.contains(key,Tag.TAG_LONG)||value<0)throw new IllegalArgumentException("Invalid stored statistic "+key);row.statistics.put(full(key),value);}
            if(!(saved.get("titles") instanceof ListTag titles)||!titles.isEmpty()&&titles.getElementType()!=Tag.TAG_STRING)throw new IllegalArgumentException("Invalid profile titles");for(int i=0;i<titles.size();i++)if(!row.titles.add(full(titles.getString(i))))throw new IllegalArgumentException("Duplicate profile title");data.players.put(owner,row);
        }return data;
    }
    @Override public CompoundTag save(CompoundTag tag){var rows=new CompoundTag();players.forEach((owner,row)->{var saved=new CompoundTag();saved.putString("name",row.name);saved.put("data",row.data.copy());var statuses=new CompoundTag();row.statuses.forEach((key,value)->{var status=new CompoundTag();status.putInt("level",value.level());status.putLong("expires",value.expiresAt());statuses.put(key.toString(),status);});saved.put("statuses",statuses);var stats=new CompoundTag();row.statistics.forEach((key,value)->stats.putLong(key.toString(),value));saved.put("statistics",stats);var titles=new ListTag();row.titles.stream().sorted().forEach(t->titles.add(StringTag.valueOf(t.toString())));saved.put("titles",titles);rows.put(owner.toString(),saved);});tag.put("players",rows);return tag;}
    @Override public void save(File file){NativeData.save(this,file);}
}

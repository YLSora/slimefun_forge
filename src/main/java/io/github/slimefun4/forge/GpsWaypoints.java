package io.github.slimefun4.forge;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Persistent points belong to one server; pending chat belongs to one live player session. */
public final class GpsWaypoints extends SavedData {
    public record Waypoint(String id,String name,ResourceKey<Level> dimension,Vec3 position,float yaw) {}
    private static final class Prompt {
        final ResourceKey<Level> dimension;final Vec3 position;final float yaw;
        Prompt(ResourceKey<Level> dimension,Vec3 position,float yaw){this.dimension=dimension;this.position=position;this.yaw=yaw;}
    }
    private static final Map<ServerPlayer,Prompt> PROMPTS=new ConcurrentHashMap<>();
    private final Map<UUID,List<Waypoint>> players=new HashMap<>();
    private final Set<UUID> creating=new HashSet<>();
    public static GpsWaypoints get(MinecraftServer server){return NativeData.get(server,"slimefun_gps",GpsWaypoints::load,GpsWaypoints::new);}
    Set<UUID> players(){return Set.copyOf(players.keySet());}
    boolean delete(UUID owner){if(creating.contains(owner))throw new IllegalStateException("Waypoint creation in progress");if(players.remove(owner)==null)return false;setDirty();return true;}
    @Override public void save(java.io.File file){NativeData.save(this,file);}
    public List<Waypoint> points(UUID player){return List.copyOf(players.getOrDefault(player,List.of()));}
    UUID owner(MinecraftServer server,String name){
        var online=server.getPlayerList().getPlayerByName(name);if(online!=null)return online.getUUID();
        try{var id=UUID.fromString(name);return players.containsKey(id)||server.getProfileCache().get(id).isPresent()?id:null;}catch(IllegalArgumentException ignored){}
        // UUID cache reads are local; name lookup may contact the authentication service.
        for(var id:players.keySet())if(server.getProfileCache().get(id).filter(p->p.getName().equalsIgnoreCase(name)).isPresent())return id;
        return null;
    }
    public static void death(net.minecraft.world.entity.player.Player player) {
        if(!(player instanceof ServerPlayer server))return;
        for(int slot=0;slot<player.getInventory().getContainerSize();slot++)if(player.getInventory().getItem(slot).is(SlimefunForge.item("gps_emergency_transmitter"))) {
            var date=java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("(MMM dd, yyyy @ hh:mm)",Locale.ROOT));
            // Waypoint names persist legacy color codes, not Component styles.
            String format=GuideContent.language(Guide.locale(server)).get("menu.slimefun.gps.deathpoint");
            get(server.server).create(server,server.getUUID(),"player:death "+Component.translatableWithFallback("",format,date).getString(),server.level().dimension(),Vec3.atLowerCornerOf(server.blockPosition()),0,true);return;
        }
    }
    public boolean remove(UUID player,String id){var list=players.get(player);if(list==null||!list.removeIf(point->point.id().equals(id)))return false;if(list.isEmpty())players.remove(player);setDirty();return true;}
    public static void cancelPrompt(ServerPlayer player){PROMPTS.remove(player);}
    public static void prompt(ServerPlayer player,BlockPos position){
        Guide.cancelSearch(player);AndroidScripts.cancelPrompt(player);
        ProjectorEntity.cancelEdit(player);ElevatorMenu.cancelPrompt(player);
        cancelPrompt(player);
        if(get(player.server).points(player.getUUID()).size()>=20){message(player,"max");return;}
        PROMPTS.put(player,new Prompt(player.level().dimension(),Vec3.atLowerCornerOf(position),player.getYRot()));
        message(player,"new");player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(),SoundSource.PLAYERS,0.5F,1);
    }
    @SubscribeEvent public static void chat(ServerChatEvent event){
        var player=event.getPlayer();var prompt=PROMPTS.get(player);if(prompt==null)return;
        event.setCanceled(true);var name=event.getRawText();
        // Forge's chat decorator can run asynchronously; only immutable prompt data crosses this boundary.
        player.server.execute(()->{if(PROMPTS.remove(player,prompt)&&!player.isRemoved())get(player.server).add(player,name,prompt.dimension,prompt.position,prompt.yaw);});
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity() instanceof ServerPlayer player)cancelPrompt(player);}
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event){PROMPTS.keySet().removeIf(player->player.server==event.getServer());}
    public boolean add(ServerPlayer player,String name,ResourceKey<Level> dimension,Vec3 position,float yaw){
        return create(player,player.getUUID(),name,dimension,position,yaw,false);
    }
    public boolean create(ServerPlayer player,UUID owner,String name,ResourceKey<Level> dimension,Vec3 position,float yaw,boolean deathpoint){
        io.github.slimefun4.forge.api.NetworkApi.check(player.serverLevel());
        if(this!=get(player.server)||!io.github.slimefun4.forge.api.GpsApi.mayAccess(player,owner)||deathpoint&&!owner.equals(player.getUUID())||!valid(name,position,yaw)||name.startsWith("player:death ")!=deathpoint||!creating.add(owner))return false;
        try{
            if(points(owner).size()>=20){message(player,"max");return false;}
            var event=new WaypointCreateEvent(player,owner,name,dimension,position,yaw,deathpoint);
            if(MinecraftForge.EVENT_BUS.post(event))return false;
            name=event.getName();dimension=event.getDimension();position=event.getPosition();yaw=event.getYaw();
            if(!valid(name,position,yaw)||name.startsWith("player:death ")!=deathpoint||player.server.getLevel(dimension)==null||!io.github.slimefun4.forge.api.GpsApi.mayAccess(player,owner)){message(player,"invalid");return false;}
            var id=NetworkRules.waypointId(name);
            var list=players.computeIfAbsent(owner,key->new ArrayList<>());
            if(list.size()>=20){message(player,"max");return false;}
            if(list.stream().anyMatch(point->point.id().equals(id))){message(player,"duplicate",name);return false;}
            list.add(new Waypoint(id,name,dimension,position,yaw));setDirty();message(player,"added");player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(),SoundSource.PLAYERS,1,1);return true;
        }finally{creating.remove(owner);}
    }
    public boolean update(ServerPlayer actor,UUID owner,String id,String name,ResourceKey<Level> dimension,Vec3 position,float yaw){
        io.github.slimefun4.forge.api.NetworkApi.check(actor.serverLevel());if(this!=get(actor.server)||!io.github.slimefun4.forge.api.GpsApi.mayAccess(actor,owner)||!valid(name,position,yaw)||actor.server.getLevel(dimension)==null||!creating.add(owner))return false;
        try{var list=players.get(owner);if(list==null)return false;var point=list.stream().filter(p->p.id().equals(id)).findFirst().orElse(null);if(point==null||name.startsWith("player:death ")!=point.name().startsWith("player:death "))return false;
            var next=NetworkRules.waypointId(name);if(list.stream().anyMatch(p->p!=point&&p.id().equals(next)))return false;list.set(list.indexOf(point),new Waypoint(next,name,dimension,position,yaw));setDirty();return true;
        }finally{creating.remove(owner);}
    }
    private static boolean valid(String name,Vec3 pos,float yaw){try{NetworkRules.waypointId(name);}catch(IllegalArgumentException invalid){return false;}return pos!=null&&Double.isFinite(pos.x)&&Double.isFinite(pos.y)&&Double.isFinite(pos.z)&&Math.abs(pos.x)<=30_000_000&&Math.abs(pos.z)<=30_000_000&&Math.abs(pos.y)<=30_000_000&&Float.isFinite(yaw);}
    private static void message(ServerPlayer player,String key,Object...args){Messages.chat(player,Component.translatable("message.slimefun.waypoint."+key,args));}
    public static GpsWaypoints load(CompoundTag tag){
        var data=new GpsWaypoints();
        for(var key:tag.getAllKeys()){
            var owner=UUID.fromString(key);if(!(tag.get(key) instanceof ListTag rows)||!rows.isEmpty()&&rows.getElementType()!=Tag.TAG_COMPOUND)throw new IllegalArgumentException("Invalid GPS waypoint list for "+owner);
            if(rows.size()>20)throw new IllegalArgumentException("Too many GPS waypoints for "+owner);
            var list=new ArrayList<Waypoint>();var ids=new HashSet<String>();
            for(int i=0;i<rows.size();i++){
                var row=rows.getCompound(i);
                if(!row.contains("id",Tag.TAG_STRING)||!row.contains("name",Tag.TAG_STRING)||!row.contains("dimension",Tag.TAG_STRING)||!row.contains("x",Tag.TAG_ANY_NUMERIC)||!row.contains("y",Tag.TAG_ANY_NUMERIC)||!row.contains("z",Tag.TAG_ANY_NUMERIC)||!row.contains("yaw",Tag.TAG_ANY_NUMERIC))throw new IllegalArgumentException("Incomplete GPS waypoint for "+owner);
                var id=row.getString("id");var name=row.getString("name");var dimension=ResourceKey.create(Registries.DIMENSION,new ResourceLocation(row.getString("dimension")));var pos=new Vec3(row.getDouble("x"),row.getDouble("y"),row.getDouble("z"));float yaw=row.getFloat("yaw");
                if(!valid(name,pos,yaw)||!NetworkRules.waypointId(name).equals(id)||!ids.add(id))throw new IllegalArgumentException("Invalid GPS waypoint "+id);list.add(new Waypoint(id,name,dimension,pos,yaw));
            }
            data.players.put(owner,list);
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag){
        players.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry->{var rows=new ListTag();for(var point:entry.getValue()){
            var row=new CompoundTag();row.putString("id",point.id());row.putString("name",point.name());row.putString("dimension",point.dimension().location().toString());row.putDouble("x",point.position().x);row.putDouble("y",point.position().y);row.putDouble("z",point.position().z);row.putFloat("yaw",point.yaw());rows.add(row);
        }tag.put(entry.getKey().toString(),rows);});return tag;
    }
}

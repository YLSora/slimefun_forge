package io.github.slimefun4.forge;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import io.github.slimefun4.forge.api.AndroidApi;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Shared by this server's dimensions; only naming requests belong to live sessions. */
public final class AndroidScripts extends SavedData {
    static final class Script {
        final UUID id,author;
        final String authorName,name;
        final List<ResourceLocation> code;
        final ResourceLocation type;
        int downloads;
        final Set<UUID> positive,negative;
        Script(UUID id,UUID author,String authorName,String name,List<ResourceLocation> code,ResourceLocation type,int downloads,Set<UUID> positive,Set<UUID> negative){this.id=id;this.author=author;this.authorName=authorName;this.name=name;this.code=List.copyOf(code);this.type=type;this.downloads=downloads;this.positive=positive;this.negative=negative;}
        boolean canRate(UUID player){return !author.equals(player)&&!positive.contains(player)&&!negative.contains(player);}
        float rating(){int up=positive.size()+1;return Math.round(up/(float)(up+negative.size())*100F)/100F;}
        String authorName(MinecraftServer server){var cache=server.getProfileCache();return cache==null?authorName:cache.get(author).map(com.mojang.authlib.GameProfile::getName).orElse(authorName);}
    }
    private static final class Prompt {
        final AndroidApi.Robot android;final AndroidMenu menu;final UUID owner;final List<ResourceLocation> code;final int page;
        Prompt(AndroidApi.Robot android,AndroidMenu menu,UUID owner,List<ResourceLocation> code,int page){this.android=android;this.menu=menu;this.owner=owner;this.code=List.copyOf(code);this.page=page;}
    }
    private static final Map<ServerPlayer,Prompt> PROMPTS=new ConcurrentHashMap<>();
    private final List<Script> scripts=new ArrayList<>();
    public static AndroidScripts get(MinecraftServer server){return NativeData.get(server,"slimefun_android_scripts",AndroidScripts::load,AndroidScripts::new);}
    List<Script> list(ResourceLocation type){
        var result=new ArrayList<Script>();for(var script:scripts)if(script.type.equals(type))result.add(script);
        for(var script:scripts)if(!script.type.equals(type)&&AndroidApi.type(type).actions().containsAll(script.code))result.add(script);
        // RC-37 sorts by total votes, including negative ones, and keeps type-first ties.
        result.sort(Comparator.comparingInt(s->-s.positive.size()-s.negative.size()));return List.copyOf(result);
    }
    private boolean duplicate(ResourceLocation type,List<ResourceLocation> code){return scripts.stream().anyMatch(s->s.type.equals(type)&&s.code.equals(code));}
    boolean download(io.github.slimefun4.forge.api.AndroidApi.Robot android,Script script){
        if(!scripts.contains(script)||!AndroidApi.type(android.type()).actions().containsAll(script.code)||script.downloads==Integer.MAX_VALUE)return false;
        android.setScript(script.code);script.downloads++;setDirty();return true;
    }
    void rate(ServerPlayer player,Script script,boolean positive){
        if(!scripts.contains(script))return;var voter=player.getUUID();
        if(!script.canRate(voter)){message(player,script.author.equals(voter)?"rating_own":"rating_already");return;}
        (positive?script.positive:script.negative).add(voter);setDirty();
    }
    public static void cancelPrompt(ServerPlayer player){PROMPTS.remove(player);}
    static void prompt(ServerPlayer player,io.github.slimefun4.forge.api.AndroidApi.Robot android,AndroidMenu menu,int page){
        if(get(player.server).duplicate(android.type(),android.script())){message(player,"duplicate");return;}
        Guide.cancelSearch(player);GpsWaypoints.cancelPrompt(player);ProjectorEntity.cancelEdit(player);ElevatorMenu.cancelPrompt(player);cancelPrompt(player);
        PROMPTS.put(player,new Prompt(android,menu,android.owner(),android.script(),page));player.closeContainer();message(player,"enter_name");
    }
    @SubscribeEvent public static void chat(ServerChatEvent event){
        var player=event.getPlayer();var prompt=PROMPTS.get(player);if(prompt==null)return;event.setCanceled(true);var raw=event.getRawText();
        // Keep the request until the main-thread commit so a newer prompt can invalidate it.
        player.server.execute(()->{
            if(!PROMPTS.remove(player,prompt)||!prompt.menu.stillValid(player)||player.containerMenu!=player.inventoryMenu||!prompt.owner.equals(prompt.android.owner())||!prompt.code.equals(prompt.android.script()))return;
            if(raw.length()>256){message(player,"invalid_name");return;}var name=raw.replaceAll("(?i)[&§][0-9A-FK-ORX]","");
            if(!validName(name)){message(player,"invalid_name");return;}
            var data=get(player.server);var type=prompt.android.type();if(data.duplicate(type,prompt.code)){message(player,"duplicate");return;}
            AndroidApi.validate(type,prompt.code);data.scripts.add(new Script(UUID.randomUUID(),player.getUUID(),player.getGameProfile().getName(),name,prompt.code,type,0,new LinkedHashSet<>(),new LinkedHashSet<>()));data.setDirty();message(player,"uploaded");
            player.openMenu(new net.minecraft.world.SimpleMenuProvider((id,inv,p)->AndroidApi.menu(id,inv,prompt.android),prompt.android.getDisplayName()));if(player.containerMenu instanceof AndroidMenu menu)menu.openLibrary(prompt.page);
        });
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity() instanceof ServerPlayer player)cancelPrompt(player);}
    @SubscribeEvent public static void stop(ServerStoppedEvent event){PROMPTS.keySet().removeIf(player->player.server==event.getServer());}
    private static boolean validName(String name){return name.length()<=256&&name.chars().allMatch(c->SharedConstants.isAllowedChatCharacter((char)c));}
    private static void message(ServerPlayer player,String key){if(key.equals("enter_name")||key.equals("uploaded"))Messages.lines(player,"message.slimefun.android_library."+key);else Messages.chat(player,Component.translatable("message.slimefun.android_library."+key));}
    private static ListTag list(CompoundTag tag,String key,int type){
        if(!(tag.get(key) instanceof ListTag list)||!list.isEmpty()&&list.getElementType()!=type)throw new IllegalArgumentException("Invalid android library "+key);return list;
    }
    private static Set<UUID> voters(CompoundTag row,String key,UUID author){
        var result=new LinkedHashSet<UUID>();for(var tag:list(row,key,Tag.TAG_INT_ARRAY)){var voter=NbtUtils.loadUUID(tag);if(voter.equals(author)||!result.add(voter))throw new IllegalArgumentException("Invalid android script voter");}return result;
    }
    public static AndroidScripts load(CompoundTag tag){
        var data=new AndroidScripts();var ids=new HashSet<UUID>();var codes=new HashSet<String>();
        for(var value:list(tag,"scripts",Tag.TAG_COMPOUND)){
            var row=(CompoundTag)value;if(!row.hasUUID("id")||!row.hasUUID("author")||!row.contains("downloads",Tag.TAG_INT))throw new IllegalArgumentException("Invalid android script identity/count");
            for(var key:List.of("author_name","name","type"))if(!row.contains(key,Tag.TAG_STRING))throw new IllegalArgumentException("Missing android script "+key);
            var id=row.getUUID("id");var author=row.getUUID("author");var authorName=row.getString("author_name");var name=row.getString("name");var code=AndroidApi.readProgram(row,"code");if(!row.getString("type").contains(":"))throw new IllegalArgumentException("Robot type requires namespace");var type=new ResourceLocation(row.getString("type"));int downloads=row.getInt("downloads");
            AndroidApi.validate(type,code);if(!ids.add(id)||!codes.add(type+":"+code)||downloads<0||authorName.isEmpty()||!validName(authorName)||!validName(name))throw new IllegalArgumentException("Invalid android script record");
            var positive=voters(row,"positive",author);var negative=voters(row,"negative",author);if(!Collections.disjoint(positive,negative))throw new IllegalArgumentException("Conflicting android script votes");
            data.scripts.add(new Script(id,author,authorName,name,code,type,downloads,positive,negative));
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag){
        var rows=new ListTag();for(var script:scripts){var row=new CompoundTag();row.putUUID("id",script.id);row.putUUID("author",script.author);row.putString("author_name",script.authorName);row.putString("name",script.name);row.put("code",AndroidApi.writeProgram(script.code));row.putString("type",script.type.toString());row.putInt("downloads",script.downloads);var positive=new ListTag();script.positive.forEach(id->positive.add(NbtUtils.createUUID(id)));row.put("positive",positive);var negative=new ListTag();script.negative.forEach(id->negative.add(NbtUtils.createUUID(id)));row.put("negative",negative);rows.add(row);}tag.put("scripts",rows);return tag;
    }
    @Override public void save(java.io.File file){NativeData.save(this,file);}
}

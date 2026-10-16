package io.github.slimefun4.forge;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import net.minecraftforge.server.permission.nodes.*;
import io.github.slimefun4.forge.mixin.PermissionNodeAccess;

/** Server-start policy snapshots; provider grants are evaluated on every use. */
public final class ItemAccess {
    private static final ForgeConfigSpec CONFIG;
    private static Set<String> disabledWorlds=Set.of();
    private static Map<String,Set<String>> itemWorlds=Map.of();
    private static Map<String,String> permissions=Map.of();
    private static Map<String,List<String>> lore=Map.of();
    private static final Map<String,PermissionNode<Boolean>> nodes=new HashMap<>();
    static {
        var b=new ForgeConfigSpec.Builder();b.comment("Disabled dimension IDs. All access settings take effect on server restart.").worldRestart().defineListAllowEmpty("disabled-worlds",List.<String>of(),ItemAccess::dimension);
        b.push("items");for(String id:ids()){
            b.push(id);b.worldRestart().define("permission","none");
            b.worldRestart().defineListAllowEmpty("lore",List.of("&rYou do not have the permission","&rto access this item."),v->v instanceof String);
            b.worldRestart().defineListAllowEmpty("disabled-worlds",List.<String>of(),ItemAccess::dimension);b.pop();
        }b.pop();CONFIG=b.build();
    }
    static List<String> ids(){return java.util.stream.Stream.of(Catalog.ITEMS.stream().map(Catalog.Entry::id),GuideContent.MANUAL.stream(),Catalog.VANILLA.stream().map(Catalog.Vanilla::id)).flatMap(s->s).toList();}
    private static boolean dimension(Object value){return value instanceof String s&&ResourceLocation.tryParse(s)!=null;}
    static void register(){
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER,CONFIG,"slimefun_forge/slimefun-access-server.toml");
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,ItemAccess::gather);
    }
    private static void load(){
        var config=ConfigTracker.INSTANCE.fileMap().get("slimefun_forge/slimefun-access-server.toml");
        try{
            // Forge's startup watcher reloads its mutable map in place; read one validated snapshot after startup.
            var snapshot=com.electronwill.nightconfig.toml.TomlFormat.instance().createParser().parse(java.nio.file.Files.readString(config.getFullPath()));
            if(!CONFIG.isCorrect(snapshot))throw new IllegalStateException("Invalid Slimefun access settings");
            disabledWorlds=dimensions(snapshot.get("disabled-worlds"));var worlds=new HashMap<String,Set<String>>();var names=new HashMap<String,String>();var lines=new HashMap<String,List<String>>();
            for(String id:ids()){
                String permission=snapshot.get(List.of("items",id,"permission"));if(!permission.equals("none"))names.put(id,permission);
                Set<String> disabled=dimensions(snapshot.get(List.of("items",id,"disabled-worlds")));if(!disabled.isEmpty())worlds.put(id,disabled);
                lines.put(id,List.copyOf(snapshot.get(List.of("items",id,"lore"))));
            }
            for(var entry:AddonSettings.items().entrySet())if(!entry.getValue().permission().equals("none"))names.put(entry.getKey(),entry.getValue().permission());
            itemWorlds=Map.copyOf(worlds);permissions=Map.copyOf(names);lore=Map.copyOf(lines);
        }catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
    }
    private static Set<String> dimensions(List<String> values){return values.stream().map(v->new ResourceLocation(v).toString()).collect(java.util.stream.Collectors.toUnmodifiableSet());}
    @SuppressWarnings("unchecked")
    private static void gather(PermissionGatherEvent.Nodes event){
        load();
        nodes.clear();for(String name:new HashSet<>(permissions.values())){
            var existing=event.getNodes().stream().filter(n->n.getNodeName().equals(name)).findFirst().orElse(null);
            if(existing!=null&&existing.getType()!=PermissionTypes.BOOLEAN)throw new IllegalStateException("Slimefun item permission is not Boolean: "+name);
            var node=existing==null?PermissionNodeAccess.create(name,PermissionTypes.BOOLEAN,(p,uuid,context)->p!=null&&p.hasPermissions(2)):(PermissionNode<Boolean>)existing;
            if(existing==null)event.addNodes(node);nodes.put(name,node);
        }
        for(var node:event.getNodes())if(node.getType()==PermissionTypes.BOOLEAN)nodes.putIfAbsent(node.getNodeName(),(PermissionNode<Boolean>)node);
    }
    public static String id(ItemStack stack){var key=BuiltInRegistries.ITEM.getKey(stack.getItem());return key.getNamespace().equals(SlimefunForge.ID)?key.getPath():AddonContent.ENTRIES.containsKey(key)?key.toString():null;}
    public static boolean worldEnabled(Level level){return !disabledWorlds.contains(level.dimension().location().toString());}
    public static boolean enabled(Level level,String id){return MachineClock.enabled(id)&&(level==null||worldEnabled(level)&&!itemWorlds.getOrDefault(id,Set.of()).contains(level.dimension().location().toString())&&!AddonSettings.item(id).worlds().contains(level.dimension().location().toString()));}
    public static boolean enabled(Level level,ItemStack stack){String id=id(stack);return id==null||enabled(level,id);}
    static boolean permissionKnown(String name){return nodes.containsKey(name);}
    public static boolean permitted(ServerPlayer player,String id){String name=id.contains(":")&&AddonContent.ENTRIES.containsKey(new ResourceLocation(id))?AddonSettings.item(id).permission():permissions.getOrDefault(id,"none");return name.equals("none")||PermissionAPI.getPermission(player,Objects.requireNonNull(nodes.get(name),"Unregistered item permission "+name));}
    public static boolean canUse(ServerPlayer player,String id){return Catalog.vanilla(id)!=null&&!MachineClock.enabled(id)||enabled(player.level(),id)&&permitted(player,id)&&ResearchData.get(player).knows(player,ResearchData.research(id));}
    static boolean automation(net.minecraft.server.level.ServerLevel level,java.util.UUID owner,ItemStack stack){
        if(owner==null||!worldEnabled(level))return false;String id=id(stack);if(id==null)return true;if(!enabled(level,id))return false;
        String permission=id.contains(":")&&AddonContent.ENTRIES.containsKey(new ResourceLocation(id))?AddonSettings.item(id).permission():permissions.getOrDefault(id,"none");
        if(!permission.equals("none")){var node=Objects.requireNonNull(nodes.get(permission),"Unregistered item permission "+permission);var online=level.getServer().getPlayerList().getPlayer(owner);if(!(online==null?PermissionAPI.getOfflinePermission(owner,node):PermissionAPI.getPermission(online,node)))return false;}
        var research=ResearchData.research(id);return !ResearchSettings.enabled(research)||ResearchData.get(level.getServer()).completed(owner,research);
    }
    static Component[] lore(String id){return lore.getOrDefault(id,AddonSettings.item(id).lore()).stream().map(Component::literal).toArray(Component[]::new);}
    public static void denied(ServerPlayer player,ItemStack stack){String id=id(stack);if(id!=null)denied(player,id);}
    public static void denied(ServerPlayer player,String id){
        String key=!MachineClock.enabled(id)?"disabled_item":!enabled(player.level(),id)?"disabled_world":!permitted(player,id)?"no_permission":"locked";
        Messages.chat(player,Component.translatable("message.slimefun."+key,GuideContent.name(id)));
    }
    private ItemAccess(){}
}

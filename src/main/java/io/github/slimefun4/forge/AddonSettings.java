package io.github.slimefun4.forge;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/** One core server config for addon policies. Parse and validate the whole snapshot before publishing it. */
final class AddonSettings {
    record ItemPolicy(boolean enabled,String permission,Set<String> worlds,List<String> lore,boolean enchant,boolean disenchant) {}
    record ResearchPolicy(boolean enabled,Integer cost) {}
    private record Snapshot(Set<String> disabled,Map<String,ItemPolicy> items,Map<String,ResearchPolicy> researches) {}
    private static final ItemPolicy DEFAULT=new ItemPolicy(true,"none",Set.of(),List.of("You do not have permission to access this item."),true,true);
    private static Snapshot current=new Snapshot(Set.of(),Map.of(),Map.of());
    static final ForgeConfigSpec CONFIG;
    static {
        var b=new ForgeConfigSpec.Builder();
        // Validators throw on malformed policies; silently correcting them would remove access restrictions.
        b.comment("Installed addon mod IDs disabled globally. Reloadable; existing reserved tasks keep their snapshot.")
            .define("disabled-addons",List.of(),value->{if(value==null)return false;disabled(value);return true;});
        b.comment("TOML array of tables: id, enabled, permission, disabled-worlds, lore, allow-enchanting, allow-disenchanting.")
            .define("items",List.of(),value->{if(value==null)return false;items(value);return true;});
        b.comment("TOML array of tables: id, enabled, cost. Omitted properties use registered defaults.")
            .define("researches",List.of(),value->{if(value==null)return false;researches(value);return true;});
        CONFIG=b.build();
    }
    static void register(IEventBus bus){
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER,CONFIG,"slimefun_forge/slimefun-addons-server.toml");
        bus.addListener((ModConfigEvent.Loading event)->{if(event.getConfig().getSpec()==CONFIG)apply(event.getConfig().getConfigData());});
    }
    /** Only the validated server file watcher publishes reloads; Forge also emits Reloading for client sync/startup. */
    static void reloaded(MinecraftServer server,UnmodifiableConfig config){
        if(!server.isSameThread())throw new IllegalStateException("Addon policy reload requires the server thread");
        apply(config);
        MachineRecipe.invalidate();for(var level:server.getAllLevels())EnergyNetwork.recipesReloaded(level);
        for(var player:server.getPlayerList().getPlayers())AddonGuides.refresh(player);
    }
    static void validate(UnmodifiableConfig config){
        validate(parse(config));
    }
    private static void validate(Snapshot snapshot){
        for(var policy:snapshot.items().values())if(!policy.permission().equals("none")&&!ItemAccess.permissionKnown(policy.permission()))throw bad("New permission node requires server restart: "+policy.permission());
    }
    private static Snapshot parse(UnmodifiableConfig config){
        for(String key:config.valueMap().keySet())if(!Set.of("disabled-addons","items","researches").contains(key))throw bad("Unknown root key "+key);
        return new Snapshot(disabled(config.getOrElse("disabled-addons",List.of())),items(config.getOrElse("items",List.of())),researches(config.getOrElse("researches",List.of())));
    }
    private static void apply(UnmodifiableConfig config){current=parse(config);}
    private static IllegalArgumentException bad(String message){return new IllegalArgumentException("slimefun-addons-server.toml: "+message);}
    private static List<?> list(Object value){if(!(value instanceof List<?> values))throw bad("Expected a list, got "+value);return values;}
    private static Set<String> disabled(Object value){
        var result=new HashSet<String>();for(Object entry:list(value)){
            if(!(entry instanceof String id)||java.util.stream.Stream.of(AddonContent.CATEGORIES,AddonContent.RESEARCHES,AddonContent.ENTRIES)
                .flatMap(map->map.keySet().stream()).noneMatch(k->k.getNamespace().equals(id)))throw bad("Unknown addon "+entry);
            if(!result.add(id))throw bad("Duplicate disabled addon "+id);
        }return Set.copyOf(result);
    }
    private static UnmodifiableConfig table(Object value,Set<String> keys){
        if(!(value instanceof UnmodifiableConfig config))throw bad("Expected a TOML table, got "+value);
        for(String key:config.valueMap().keySet())if(!keys.contains(key))throw bad("Unknown policy property "+key);
        return config;
    }
    private static String id(UnmodifiableConfig config,Map<ResourceLocation,?> registered){
        Object value=config.get("id");if(!(value instanceof String text))throw bad("Policy requires string id");
        var id=ResourceLocation.tryParse(text);if(id==null||!text.equals(id.toString())||!registered.containsKey(id))throw bad("Unknown full addon ID "+text);
        return text;
    }
    private static boolean bool(UnmodifiableConfig config,String key,boolean fallback){Object value=config.getOrElse(key,fallback);if(!(value instanceof Boolean result))throw bad("Expected boolean "+key);return result;}
    private static List<String> strings(UnmodifiableConfig config,String key,List<String> fallback){
        var values=new ArrayList<String>();for(Object value:list(config.getOrElse(key,fallback))){if(!(value instanceof String text))throw bad("Expected string in "+key);values.add(text);}return List.copyOf(values);
    }
    private static Map<String,ItemPolicy> items(Object value){
        var result=new HashMap<String,ItemPolicy>();for(Object entry:list(value)){
            var config=table(entry,Set.of("id","enabled","permission","disabled-worlds","lore","allow-enchanting","allow-disenchanting"));String id=id(config,AddonContent.ENTRIES);
            if(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation(id)) instanceof io.github.slimefun4.forge.api.ItemApi.FixedSettings fixed&&(config.contains("allow-enchanting")||config.contains("allow-disenchanting")))throw bad("Fixed item settings for "+id+": "+fixed.fixedSettingsReason());
            Object name=config.getOrElse("permission","none");if(!(name instanceof String permission)||permission.isBlank())throw bad("Invalid permission for "+id);
            var worlds=new HashSet<String>();for(String world:strings(config,"disabled-worlds",List.of())){if(ResourceLocation.tryParse(world)==null)throw bad("Invalid dimension "+world+" for "+id);worlds.add(new ResourceLocation(world).toString());}
            var policy=new ItemPolicy(bool(config,"enabled",true),permission,Set.copyOf(worlds),strings(config,"lore",DEFAULT.lore()),bool(config,"allow-enchanting",true),bool(config,"allow-disenchanting",true));
            if(result.putIfAbsent(id,policy)!=null)throw bad("Duplicate item policy "+id);
        }return Map.copyOf(result);
    }
    private static Map<String,ResearchPolicy> researches(Object value){
        var result=new HashMap<String,ResearchPolicy>();for(Object entry:list(value)){
            var config=table(entry,Set.of("id","enabled","cost"));String id=id(config,AddonContent.RESEARCHES);Object valueCost=config.get("cost");
            if(valueCost!=null&&(!(valueCost instanceof Integer||valueCost instanceof Long)||((Number)valueCost).longValue()<0||((Number)valueCost).longValue()>Integer.MAX_VALUE))throw bad("Invalid non-negative integer cost for "+id);
            var policy=new ResearchPolicy(bool(config,"enabled",true),valueCost==null?null:((Number)valueCost).intValue());
            if(result.putIfAbsent(id,policy)!=null)throw bad("Duplicate research policy "+id);
        }return Map.copyOf(result);
    }
    static ItemPolicy item(String id){return current.items().getOrDefault(id,DEFAULT);}
    static Map<String,ItemPolicy> items(){return current.items();}
    static boolean ownerEnabled(String id){return !id.contains(":")||!current.disabled().contains(new ResourceLocation(id).getNamespace());}
    static boolean enabled(String id){return item(id).enabled()&&ownerEnabled(id);}
    static boolean researchEnabled(String id){var value=current.researches().get(id);return value==null||value.enabled();}
    static int cost(Catalog.Research research){var value=current.researches().get(research.id());return value==null||value.cost()==null?research.cost():value.cost();}
    private AddonSettings(){}
}

package io.github.slimefun4.forge;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.common.ForgeConfigSpec;

/** RC-37 item settings; the upstream grappling delay is passed directly as ticks. */
public final class MagicSettings {
    public static final ForgeConfigSpec CONFIG;
    public static final ForgeConfigSpec.IntValue WIND_POWER,SCROLL_RADIUS,DESPAWN_TICKS;
    public static final ForgeConfigSpec.BooleanValue CONSUME_HOOK;
    public static final ForgeConfigSpec.IntValue VAMPIRE_CHANCE,EXPLOSION_RADIUS;
    public static final Map<String,ForgeConfigSpec.IntValue> BEHEADING_CHANCES;
    public static final ForgeConfigSpec.BooleanValue ENCHANT_BOOKS,HOPPER_SILENT,HOPPER_REDSTONE;
    public static final Map<Enchantment,List<ForgeConfigSpec.BooleanValue>> ENCHANTMENT_LEVELS;
    public static final ForgeConfigSpec.DoubleValue HOPPER_RADIUS,MAGNET_RADIUS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> UNPLACEABLE_BLOCKS;
    static {
        var builder=new ForgeConfigSpec.Builder();
        builder.push("staff_elemental_wind");WIND_POWER=builder.defineInRange("power",4,1,Integer.MAX_VALUE);builder.pop();
        builder.push("scroll_of_dimensional_teleposition");SCROLL_RADIUS=builder.defineInRange("radius",10,1,Integer.MAX_VALUE);builder.pop();
        builder.push("grappling_hook");CONSUME_HOOK=builder.define("consume-on-use",true);
        DESPAWN_TICKS=builder.comment("RC-37 calls this despawn-seconds but schedules the value as game ticks; 0 means next tick.").defineInRange("despawn-seconds",60,0,Integer.MAX_VALUE);builder.pop();
        builder.push("blade_of_vampires");VAMPIRE_CHANCE=builder.defineInRange("chance",45,0,100);builder.pop();
        builder.push("explosive_bow");EXPLOSION_RADIUS=builder.defineInRange("explosion-range",3,1,Integer.MAX_VALUE);builder.pop();
        builder.push("sword_of_beheading");var chances=new LinkedHashMap<String,ForgeConfigSpec.IntValue>();
        for(String type:List.of("ZOMBIE","SKELETON","WITHER_SKELETON","CREEPER","PIGLIN","PLAYER"))chances.put(type,builder.defineInRange("chance."+type,type.equals("WITHER_SKELETON")?25:type.equals("PLAYER")?70:40,0,100));
        BEHEADING_CHANCES=Map.copyOf(chances);builder.pop();
        builder.push("magician_talisman");ENCHANT_BOOKS=builder.define("allow-enchantment-books",false);var levels=new LinkedHashMap<Enchantment,List<ForgeConfigSpec.BooleanValue>>();
        for(var enchant:BuiltInRegistries.ENCHANTMENT){var values=new ArrayList<ForgeConfigSpec.BooleanValue>();var key=BuiltInRegistries.ENCHANTMENT.getKey(enchant);for(int n=1;n<=enchant.getMaxLevel();n++)values.add(builder.define("allow-enchantments."+key.getNamespace()+"."+key.getPath()+".level."+n,true));levels.put(enchant,List.copyOf(values));}
        ENCHANTMENT_LEVELS=Map.copyOf(levels);builder.pop();
        builder.push("infused_hopper");HOPPER_RADIUS=builder.defineInRange("radius",3.5,.1,Double.MAX_VALUE);HOPPER_SILENT=builder.define("silent",false);HOPPER_REDSTONE=builder.define("toggleable-with-redstone",false);builder.pop();
        builder.push("infused_magnet");MAGNET_RADIUS=builder.defineInRange("pickup-radius",6.0,.1,Double.MAX_VALUE);builder.pop();
        builder.push("block_placer");UNPLACEABLE_BLOCKS=builder.defineListAllowEmpty("unplaceable-blocks",List.of("BEDROCK","END_PORTAL_FRAME","COMMAND_BLOCK","BARRIER","REPEATING_COMMAND_BLOCK","CHAIN_COMMAND_BLOCK","STRUCTURE_VOID","STRUCTURE_BLOCK","JIGSAW","NETHER_PORTAL","END_PORTAL","END_GATEWAY"),value->value instanceof String s&&ResourceLocation.tryParse(s.toLowerCase(Locale.ROOT))!=null&&BuiltInRegistries.BLOCK.containsKey(new ResourceLocation(s.toLowerCase(Locale.ROOT))));builder.pop();
        CONFIG=builder.build();
    }
    private MagicSettings(){}
}

package io.github.slimefun4.forge;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Immutable content only: never holds players, worlds or mutable item stacks. */
public final class Catalog {
    // RC-37 implementations whose own or inherited handler is a BlockTicker.
    private static final java.util.Set<String> TICKING=java.util.Set.of("EnhancedFurnace","AnimalGrowthAccelerator","AutoBreeder","CropGrowthAccelerator","EnergyRegulator","ExpCollector","FluidPump","IronGolemAssembler","TreeGrowthAccelerator","WitherAssembler","AutoAnvil","AutoBrewer","AutoDisenchanter","AutoDrier","AutoEnchanter","BookBinder","CarbonPress","ChargingBench","ElectricDustWasher","ElectricFurnace","ElectricGoldPan","ElectricIngotFactory","ElectricIngotPulverizer","ElectricOreGrinder","ElectricPress","ElectricSmeltery","ElectrifiedCrucible","FoodComposter","FoodFabricator","Freezer","HeatedPressureChamber","OilPump","ProduceCollector","Refinery","GPSTransmitter","ButcherAndroid","FarmerAndroid","FishermanAndroid","MinerAndroid","ProgrammableAndroid","WoodcutterAndroid","RainbowBlock","GEOMiner","TeleporterPylon","InfusedHopper","CargoManager","TrashCan","ArmorAutoCrafter","EnhancedAutoCrafter","VanillaAutoCrafter");
    public record Entry(String id, String name, String material, String implementation, String group,
                        String behavior, int rows, int capacity, int consumption, int speed, int efficiency, int fortune,
                        int production, int nightProduction, int runeColor, int radiation, int tier,
                        int juiceColor, String juiceEffect, int juiceDuration, List<Gift> gifts,
                        int armorColor, double thrust, java.util.Map<String,Integer> enchantments, List<Effect> effects, Talisman talisman) {
        public boolean machine() { return "machine".equals(behavior); }
        public boolean storage() { return "storage".equals(behavior); }
        public boolean ticking() { return TICKING.contains(implementation); }
    }
    public record Research(String id, String name, int cost, List<String> items) {}
    public record Vanilla(String id,String material,String group) {}
    public record Gift(String item,int count) {}
    public record Effect(String effect,int duration,int amplifier) {}
    public record Talisman(boolean consumable,boolean cancels,int chance) {}
    public static final List<Entry> ITEMS = read("catalog", Entry[].class);
    public static final List<Research> RESEARCH = read("researches", Research[].class);
    public static final List<Vanilla> VANILLA = read("vanilla_items",Vanilla[].class);
    private static final java.util.Map<String,Vanilla> VANILLA_BY_ID=VANILLA.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Vanilla::id,e->e));
    public static Vanilla vanilla(String id){return VANILLA_BY_ID.get(id);}
    public static final List<String> HEAD_SKINS=read("cleared_heads",String[].class);
    private static final java.util.Map<String,Entry> BY_ID=ITEMS.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Entry::id,e->e));
    public static Entry entry(String id) { return BY_ID.get(id); }
    public static net.minecraft.world.item.ItemStack stack(net.minecraft.world.item.Item item,int count) {
        var stack=new net.minecraft.world.item.ItemStack(item,count);
        var key=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
        var entry=key.getNamespace().equals(SlimefunForge.ID)?BY_ID.get(key.getPath()):null;
        if(entry!=null&&entry.enchantments()!=null)entry.enchantments().forEach((id,level)->{
            var enchant=net.minecraft.core.registries.BuiltInRegistries.ENCHANTMENT.get(new net.minecraft.resources.ResourceLocation("minecraft",id));
            if(enchant==null)throw new IllegalStateException("Unknown enchantment "+id);stack.enchant(enchant,level);
        });
        return stack;
    }
    private static <T> List<T> read(String name, Class<T[]> type) {
        try (var stream = Objects.requireNonNull(Catalog.class.getResourceAsStream("/slimefun/" + name + ".json"));
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return List.copyOf(Arrays.asList(new Gson().fromJson(reader, type)));
        } catch (Exception ex) { throw new IllegalStateException("Invalid Slimefun catalog: " + name, ex); }
    }
    private Catalog() {}
}

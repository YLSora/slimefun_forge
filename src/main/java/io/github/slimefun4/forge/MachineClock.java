package io.github.slimefun4.forge;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/** RC-37 reads the machine interval once when its server ticker starts. */
public final class MachineClock {
    private static final ForgeConfigSpec CONFIG;
    private static final ForgeConfigSpec.IntValue DELAY;
    private static final ForgeConfigSpec.BooleanValue ENABLED;
    private static final Map<String,ForgeConfigSpec.BooleanValue> ITEMS=new LinkedHashMap<>();
    private static Set<String> disabled=Set.of();
    private static int interval=10;
    private static boolean enabled=true;
    static {
        var builder=new ForgeConfigSpec.Builder();DELAY=builder.comment("Game ticks between Slimefun block callbacks. Restart the server to apply changes; per-cycle costs remain unchanged.").worldRestart().defineInRange("custom-ticker-delay",10,1,Integer.MAX_VALUE);ENABLED=builder.comment("Enable items with an RC-37 BlockTicker. Restart to apply. Existing saved callbacks and explicit administrator give are retained.").worldRestart().define("enable-tickers",true);
        builder.push("items");for(String id:ItemAccess.ids()){var entry=Catalog.entry(id);if(entry!=null&&entry.implementation().equals("HiddenItem"))continue;builder.push(id);ITEMS.put(id,builder.worldRestart().define("enabled",true));builder.pop();}builder.pop();CONFIG=builder.build();
    }
    static void register(IEventBus bus){ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER,CONFIG,"slimefun_forge/slimefun-ticker-server.toml");bus.addListener((ModConfigEvent.Loading event)->{if(event.getConfig().getSpec()==CONFIG){interval=DELAY.get();enabled=ENABLED.get();disabled=ITEMS.entrySet().stream().filter(e->!e.getValue().get()).map(Map.Entry::getKey).collect(java.util.stream.Collectors.toUnmodifiableSet());MachineRecipe.invalidate();}});}
    public static boolean enabled(String id){var entry=Catalog.entry(id);return AddonSettings.enabled(id)&&!disabled.contains(id)&&(enabled||entry==null||!entry.ticking());}
    public static boolean enabled(net.minecraft.world.item.ItemStack stack){var id=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());return enabled(id.getNamespace().equals(SlimefunForge.ID)?id.getPath():id.toString());}
    public static boolean callbacksAllowed(BlockState state){
        var id=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());var entry=id.getNamespace().equals(SlimefunForge.ID)?Catalog.entry(id.getPath()):null;
        // The global switch returns before RC-37 removes per-item handlers; energy providers have no BlockTicker.
        return entry==null||!entry.ticking()||!enabled||!disabled.contains(entry.id());
    }
    public static int interval(){return interval;}
    public static int delay(Level level){return interval-(int)Math.floorMod(level.getGameTime(),interval);}
    public static boolean due(Level level){return Math.floorMod(level.getGameTime(),interval)==0;}
    public static boolean ready(ServerLevel level,BlockPos pos,BlockState state){if(!callbacksAllowed(state))return false;if(due(level))return true;level.scheduleTick(pos,state.getBlock(),delay(level));return false;}
    static String seconds(long cycles){return java.math.BigDecimal.valueOf(cycles).multiply(java.math.BigDecimal.valueOf(interval)).divide(java.math.BigDecimal.valueOf(20)).stripTrailingZeros().toPlainString();}
    private MachineClock(){}
}

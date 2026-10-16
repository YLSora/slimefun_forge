package io.github.slimefun4.forge;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

/** RC-37's shared nine-dust distribution and legacy output-selection options. */
final class OreWashing {
    private static final String[] DUSTS={"iron_dust","gold_dust","copper_dust","tin_dust","zinc_dust","aluminum_dust","magnesium_dust","lead_dust","silver_dust"};
    private static final ForgeConfigSpec CONFIG;
    private static final ForgeConfigSpec.BooleanValue LEGACY_ORE,LEGACY_DUST;
    static {
        var builder=new ForgeConfigSpec.Builder();
        LEGACY_ORE=builder.comment("Allow manual ore washers to use fully occupied outputs when the selected dust and stone chunk can stack.").define("legacy-ore-washer",false);
        LEGACY_DUST=builder.comment("Allow electric dust washers to select dust with both output slots occupied.").define("legacy-dust-washer",false);
        CONFIG=builder.build();
    }
    private OreWashing() {}
    static void register(IEventBus bus) {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER,CONFIG,"slimefun_forge/slimefun-dust-washer-server.toml");
        bus.addListener((ModConfigEvent.Reloading event)-> {
            if(event.getConfig().getSpec()!=CONFIG) return;
            var server=ServerLifecycleHooks.getCurrentServer();
            if(server!=null) server.execute(()-> { for(var level:server.getAllLevels()) EnergyNetwork.recipesReloaded(level); });
        });
    }
    static boolean legacyOre() { return LEGACY_ORE.get(); }
    static boolean legacyDust() { return LEGACY_DUST.get(); }
    static ItemStack randomDust() { return new ItemStack(SlimefunForge.item(DUSTS[ThreadLocalRandom.current().nextInt(DUSTS.length)])); }
    static java.util.List<ItemStack> outputs(){return java.util.Arrays.stream(DUSTS).map(id->new ItemStack(SlimefunForge.item(id))).toList();}
    static boolean canProduce(Predicate<ItemStack> fits) {
        for(var id:DUSTS) if(fits.test(new ItemStack(SlimefunForge.item(id)))) return true;
        return false;
    }
}

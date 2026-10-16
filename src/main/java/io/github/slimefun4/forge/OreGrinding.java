package io.github.slimefun4.forge;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

/** RC-37 PostSetup ordering and the ore crusher's shared yield setting. */
final class OreGrinding {
    private static final ForgeConfigSpec CONFIG;
    private static final ForgeConfigSpec.BooleanValue LEGACY,DOUBLE_ORES;
    static {
        var builder=new ForgeConfigSpec.Builder();
        LEGACY=builder.comment("Keep grindstone recipes before ore crusher recipes. False prefers larger input stacks.").define("legacy-ore-grinder",true);
        DOUBLE_ORES=builder.comment("Double the output of marked mineral ore recipes in both manual and electric grinders.").define("double-ores",true);
        CONFIG=builder.build();
    }
    private OreGrinding() {}
    static boolean legacy() { return LEGACY.get(); }
    static boolean doubleOres() { return DOUBLE_ORES.get(); }
    static void register(IEventBus bus) {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER,CONFIG,"slimefun_forge/slimefun-ore-grinder-server.toml");
        bus.addListener((ModConfigEvent.Reloading event)-> {
            if(event.getConfig().getSpec()!=CONFIG) return;
            var server=ServerLifecycleHooks.getCurrentServer();
            if(server!=null) server.execute(()-> { MachineRecipe.invalidate(); for(var level:server.getAllLevels()) EnergyNetwork.recipesReloaded(level); });
        });
    }
}

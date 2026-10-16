package io.github.slimefun4.forge;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.*;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

/** RC-37 gives each freezer its own ice-compression ratio. */
final class Freezing {
    private static final ForgeConfigSpec CONFIG;
    private static final Map<String,ForgeConfigSpec.BooleanValue> RATIOS=new LinkedHashMap<>();
    static {
        var builder=new ForgeConfigSpec.Builder();
        for(var id:List.of("freezer","freezer_2","freezer_3")) {
            builder.push(id); RATIOS.put(id,builder.comment("Use nine ice or packed ice per compression instead of one.").define("use-vanilla-ratios",false)); builder.pop();
        }
        CONFIG=builder.build();
    }
    private Freezing() {}
    static int amount(String machine,ItemStack input) {
        return (input.is(Items.ICE) || input.is(Items.PACKED_ICE)) && RATIOS.get(machine).get()?9:input.getCount();
    }
    static void register(IEventBus bus) {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER,CONFIG,"slimefun_forge/slimefun-freezer-server.toml");
        bus.addListener((ModConfigEvent.Reloading event)-> {
            if(event.getConfig().getSpec()!=CONFIG) return;
            var server=ServerLifecycleHooks.getCurrentServer();
            if(server!=null) server.execute(()-> { for(var level:server.getAllLevels()) EnergyNetwork.recipesReloaded(level); });
        });
    }
}

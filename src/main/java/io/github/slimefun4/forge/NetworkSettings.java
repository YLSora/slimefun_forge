package io.github.slimefun4.forge;

import net.minecraftforge.common.ForgeConfigSpec;

public final class NetworkSettings {
    public static final ForgeConfigSpec CONFIG;
    public static final ForgeConfigSpec.IntValue MAX_STEPS,CARGO_DELAY;
    public static final ForgeConfigSpec.BooleanValue VISUALIZER,DELETE_EXCESS;
    static {
        var builder=new ForgeConfigSpec.Builder();
        MAX_STEPS=builder.comment("Positions classified per network callback, including empty positions; not a node-count limit.").defineInRange("max-size",200,1,Integer.MAX_VALUE);
        CARGO_DELAY=builder.comment("Skip this many cargo manager callbacks between transfers.").defineInRange("cargo-ticker-delay",0,0,Integer.MAX_VALUE);
        VISUALIZER=builder.define("enable-visualizer",true);
        DELETE_EXCESS=builder.comment("Discard only items that cannot be returned after a failed transfer. Full destinations retain source items.").define("delete-excess-items",false);
        CONFIG=builder.build();
    }
    private NetworkSettings(){}
}

package io.github.slimefun4.forge;

import java.util.*;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/** Original research identities remain registered items even when their research gate is disabled. */
public final class ResearchSettings {
    public static final ForgeConfigSpec CONFIG;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    private static final Map<String,ForgeConfigSpec.BooleanValue> REGISTERED=new HashMap<>();
    private static final Map<String,ForgeConfigSpec.IntValue> COSTS=new HashMap<>();
    static {
        var builder=new ForgeConfigSpec.Builder();ENABLED=builder.define("enable-researching",true);builder.push("slimefun");
        for(var research:Catalog.RESEARCH){builder.push(research.id());REGISTERED.put(research.id(),builder.define("enabled",true));COSTS.put(research.id(),builder.defineInRange("cost",research.cost(),0,Integer.MAX_VALUE));builder.pop();}
        builder.pop();CONFIG=builder.build();
    }
    static void register(){ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER,CONFIG,"slimefun_forge/slimefun-researches-server.toml");}
    public static boolean registered(Catalog.Research research){return research!=null&&(REGISTERED.containsKey(research.id())?REGISTERED.get(research.id()).get():AddonContent.RESEARCHES.containsKey(new net.minecraft.resources.ResourceLocation(research.id()))&&AddonSettings.researchEnabled(research.id()));}
    public static boolean enabled(Catalog.Research research){return ENABLED.get()&&registered(research);}
    public static int cost(Catalog.Research research){return COSTS.containsKey(research.id())?COSTS.get(research.id()).get():AddonSettings.cost(research);}
    private ResearchSettings() {}
}

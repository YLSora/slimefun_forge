package io.github.slimefun4.forge;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import net.minecraft.world.item.*;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

/** RC-37's shared manual/electric gold-pan weights; zero-weight entries never produce items. */
public final class GoldPanDrops {
    public static final ForgeConfigSpec CONFIG;
    private static final String[][] IDS={{"flint","clay_ball","sifted_ore","iron_nugget"},{"quartz","gold_nugget","nether_wart","blaze_powder","glowstone_dust","ghast_tear"}};
    private static final ForgeConfigSpec.IntValue[][] WEIGHTS=new ForgeConfigSpec.IntValue[2][];
    private static final ForgeConfigSpec.BooleanValue[] OVERRIDE=new ForgeConfigSpec.BooleanValue[3];
    static {
        var builder=new ForgeConfigSpec.Builder(); int[][] defaults={{40,20,35,5},{50,25,10,8,5,2}};
        for(int group=0;group<2;group++) {
            builder.push(group==0?"gravel":"nether"); WEIGHTS[group]=new ForgeConfigSpec.IntValue[IDS[group].length];
            for(int n=0;n<IDS[group].length;n++) WEIGHTS[group][n]=builder.comment("Relative RC-37 weight. Zero disables this output.").defineInRange(IDS[group][n],defaults[group][n],0,Integer.MAX_VALUE);
            builder.pop();
        }
        for(int tier=0;tier<OVERRIDE.length;tier++) {
            builder.push("electric_gold_pan"+(tier==0?"":"_"+(tier+1)));
            OVERRIDE[tier]=builder.comment("Allow new operations with both output slots occupied; this can force selected outputs.").define("override-output-limit",false); builder.pop();
        }
        CONFIG=builder.build();
    }
    private GoldPanDrops() {}
    public static void register(IEventBus bus) {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER,CONFIG,"slimefun_forge/slimefun-gold-pan-server.toml");
        bus.addListener((ModConfigEvent.Reloading event)-> {
            if(event.getConfig().getSpec()!=CONFIG) return;
            var server=ServerLifecycleHooks.getCurrentServer();
            if(server!=null) server.execute(()-> { for(var level:server.getAllLevels()) EnergyNetwork.recipesReloaded(level); });
        });
    }
    public static boolean outputLimitOverridden(String machine) { return OVERRIDE[switch(machine) { case "electric_gold_pan_3" -> 2; case "electric_gold_pan_2" -> 1; default -> 0; }].get(); }
    public static int inputGroup(ItemStack input) {
        if(input.isEmpty()) return -1;
        if(input.is(Items.GRAVEL) && MachineRecipe.same(input,new ItemStack(Items.GRAVEL))) return 0;
        if((input.is(Items.SOUL_SAND)||input.is(Items.SOUL_SOIL)) && MachineRecipe.same(input,new ItemStack(input.getItem()))) return 1;
        return -1;
    }
    private static ItemStack output(int group,int index) {
        String id=IDS[group][index];
        var item=id.equals("sifted_ore")?SlimefunForge.item(id):net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation("minecraft",id));
        return new ItemStack(item);
    }
    record Drop(ItemStack output,int weight) {}
    static java.util.List<Drop> drops(int group){
        var result=new java.util.ArrayList<Drop>();
        for(int n=0;n<WEIGHTS[group].length;n++)if(WEIGHTS[group][n].get()>0)result.add(new Drop(output(group,n),WEIGHTS[group][n].get()));
        return java.util.List.copyOf(result);
    }
    public static boolean canProduce(int group,Predicate<ItemStack> fits) {
        for(int n=0;n<WEIGHTS[group].length;n++) if(WEIGHTS[group][n].get()>0 && fits.test(output(group,n))) return true;
        return false;
    }
    public static ItemStack pick(int group) {
        int[] values=new int[WEIGHTS[group].length]; long total=0;
        for(int n=0;n<values.length;n++) { values[n]=WEIGHTS[group][n].get(); total+=values[n]; }
        if(total==0) return ItemStack.EMPTY;
        // Native WeightedRandomList sums ints; RC-37 accepts each weight up to Integer.MAX_VALUE.
        long roll=ThreadLocalRandom.current().nextLong(total);
        for(int n=0;n<values.length;n++) { if(roll<values[n]) return output(group,n); roll-=values[n]; }
        throw new IllegalStateException("Gold pan weight sum changed");
    }
}

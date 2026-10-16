package io.github.slimefun4.forge;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.item.*;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

/** Native inputs, outputs and entity predicates for the five-second collector operation. */
public record AnimalProduce(ItemStack input,ItemStack output,Predicate<LivingEntity> predicate) {
    static final Set<AnimalProduce> RECIPES=new LinkedHashSet<>();
    static final ForgeConfigSpec.IntValue RANGE;
    private static final ForgeConfigSpec CONFIG;
    static {
        var builder=new ForgeConfigSpec.Builder();RANGE=builder.defineInRange("range",2,1,32);CONFIG=builder.build();
        RECIPES.add(new AnimalProduce(new ItemStack(Items.BUCKET),new ItemStack(Items.MILK_BUCKET),e->(e instanceof Cow || e instanceof Goat)&&!e.isBaby()));
        RECIPES.add(new AnimalProduce(new ItemStack(Items.BOWL),new ItemStack(Items.MUSHROOM_STEW),e->e instanceof MushroomCow&&!e.isBaby()));
    }
    public AnimalProduce {
        Objects.requireNonNull(input);Objects.requireNonNull(output);Objects.requireNonNull(predicate);
        if(input.isEmpty()||output.isEmpty()||output.getCount()>output.getMaxStackSize())throw new IllegalArgumentException("Invalid animal produce stacks");
        input=input.copy();output=output.copy();
    }
    @Override public ItemStack input(){return input.copy();}
    @Override public ItemStack output(){return output.copy();}
    static void register(IEventBus bus) {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER,CONFIG,"slimefun_forge/slimefun-produce-server.toml");
        bus.addListener((ModConfigEvent.Reloading event)->{
            if(event.getConfig().getSpec()!=CONFIG)return;
            var server=ServerLifecycleHooks.getCurrentServer();
            if(server!=null)server.execute(()->{for(var level:server.getAllLevels())EnergyNetwork.recipesReloaded(level);});
        });
    }
}

package io.github.slimefun4.forge.mixin;

import io.github.slimefun4.forge.EnhancedFurnaceBlock;
import io.github.slimefun4.forge.SlimefunForge;
import io.github.slimefun4.forge.MachineClock;
import io.github.slimefun4.forge.ItemAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class EnhancedFurnaceMixin {
    @Shadow int cookingProgress;
    @Shadow int cookingTotalTime;
    @Unique private static final TagKey<Item> slimefun$LUCK=TagKey.create(Registries.ITEM,SlimefunForge.key("enhanced_furnace_luck_materials"));
    @Inject(method="serverTick",at=@At("HEAD"))
    private static void slimefun$speed(Level level,BlockPos pos,BlockState state,AbstractFurnaceBlockEntity furnace,CallbackInfo ci) {
        if(state.getBlock() instanceof EnhancedFurnaceBlock block&&MachineClock.callbacksAllowed(state)&&MachineClock.due(level)) {
            var access=(EnhancedFurnaceMixin)(Object)furnace;
            if(access.cookingProgress>0&&block.entry.speed()>1) {
                access.cookingProgress=Math.min(access.cookingTotalTime-1,access.cookingProgress+(block.entry.speed()-1)*10);
                furnace.setChanged();
            }
        }
    }
    @Inject(method="getBurnDuration",at=@At("RETURN"),cancellable=true)
    private void slimefun$fuel(ItemStack fuel,CallbackInfoReturnable<Integer> ci) {
        if(((BlockEntity)(Object)this).getBlockState().getBlock() instanceof EnhancedFurnaceBlock block&&ItemAccess.enabled(((BlockEntity)(Object)this).getLevel(),block.entry.id())&&block.entry.efficiency()>1)
            ci.setReturnValue((int)Math.min(32766L,(long)ci.getReturnValueI()*(block.entry.efficiency()-1)));
    }
    @Redirect(method="burn",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/crafting/Recipe;assemble(Lnet/minecraft/world/Container;Lnet/minecraft/core/RegistryAccess;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack slimefun$fortune(Recipe<Container> recipe,Container inventory,RegistryAccess registries) {
        var result=recipe.assemble(inventory,registries);
        var entity=(BlockEntity)(Object)this;
        if(entity.getBlockState().getBlock() instanceof EnhancedFurnaceBlock block&&ItemAccess.enabled(entity.getLevel(),block.entry.id())&&!result.isEmpty()) {
            int amount=inventory.getItem(0).is(slimefun$LUCK)?1+entity.getLevel().random.nextInt(block.entry.fortune()+1):1;
            return new ItemStack(result.getItem(),Math.min(amount,Math.min(inventory.getMaxStackSize(),result.getMaxStackSize())-inventory.getItem(2).getCount()));
        }
        return result;
    }
}

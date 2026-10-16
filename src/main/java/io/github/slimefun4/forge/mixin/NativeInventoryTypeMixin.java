package io.github.slimefun4.forge.mixin;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native containers retain their own inventory, menu, capability and saved identity. */
@Mixin(BlockEntityType.class)
public abstract class NativeInventoryTypeMixin {
    @Inject(method="isValid",at=@At("HEAD"),cancellable=true)
    private void slimefun$nativeInventory(BlockState state,CallbackInfoReturnable<Boolean> ci){
        if((Object)this==BlockEntityType.HOPPER&&state.getBlock() instanceof io.github.slimefun4.forge.InfusedHopperBlock
            ||(Object)this==BlockEntityType.FURNACE&&state.getBlock() instanceof io.github.slimefun4.forge.EnhancedFurnaceBlock
            ||(Object)this==BlockEntityType.DISPENSER&&state.getBlock() instanceof io.github.slimefun4.forge.BlockPlacerBlock)ci.setReturnValue(true);
    }
}

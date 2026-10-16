package io.github.slimefun4.forge.mixin;

import io.github.slimefun4.forge.AutoCrafterBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntity.class)
public abstract class ContainerChangedMixin {
    @Inject(method="setChanged(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V",at=@At("TAIL"))
    private static void slimefun$inventoryChanged(Level level,BlockPos pos,BlockState state,CallbackInfo ci) { AutoCrafterBlock.inventoryChanged(level,pos,state); }
}

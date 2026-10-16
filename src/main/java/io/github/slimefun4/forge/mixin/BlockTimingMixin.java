package io.github.slimefun4.forge.mixin;

import io.github.slimefun4.forge.Timings;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(ServerLevel.class)
public abstract class BlockTimingMixin {
    @Redirect(method="tickBlock",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/state/BlockState;tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"))
    private void slimefun$time(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){try(var sample=Timings.start(level,pos,state)){state.tick(level,pos,random);}}
}

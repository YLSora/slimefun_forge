package io.github.slimefun4.forge.mixin;

import io.github.slimefun4.forge.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FlowingFluid.class)
public abstract class FluidPhysicsMixin {
    private static boolean slimefun$protectedHead(BlockState state){return state.getBlock() instanceof AndroidBlock||state.getBlock() instanceof HeadBlock&&!(state.getBlock() instanceof ClearedHeadBlock)&&state.hasProperty(HeadBlock.POSE);}
    @Inject(method="canHoldFluid",at=@At("HEAD"),cancellable=true)
    private void slimefun$protectHead(BlockGetter level,BlockPos pos,BlockState state,Fluid fluid,CallbackInfoReturnable<Boolean> callback){
        if(slimefun$protectedHead(state))callback.setReturnValue(true);
    }
    // RC-37 cancels BlockFromToEvent after native path selection, so downward rejection must not divert flow sideways.
    @Inject(method="spread",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/material/FlowingFluid;spreadTo(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Lnet/minecraft/world/level/material/FluidState;)V"),cancellable=true)
    private void slimefun$stopDownward(Level level,BlockPos pos,FluidState state,CallbackInfo callback){
        if(slimefun$protectedHead(level.getBlockState(pos.below())))callback.cancel();
    }
    @Inject(method="spreadTo",at=@At("HEAD"),cancellable=true)
    private void slimefun$stopIntoHead(LevelAccessor level,BlockPos pos,BlockState state,Direction direction,FluidState fluid,CallbackInfo callback){
        if(slimefun$protectedHead(state))callback.cancel();
    }
    // An unfinished RC-37 fill keeps its visible level until its next fifty-tick step.
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void slimefun$crucibleFill(Level level,BlockPos pos,FluidState state,CallbackInfo callback) {
        if(CrucibleLiquids.holds(level,pos)) callback.cancel();
    }
}

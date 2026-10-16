package io.github.slimefun4.forge.mixin;

import io.github.slimefun4.forge.OutputChestBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets the vanilla half use its ordinary placement, shape and menu rules with an output half. */
@Mixin(ChestBlock.class)
public abstract class OutputChestMixin {
    @Redirect(method={"candidatePartnerFacing","updateShape"},at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/world/level/block/Block;)Z"))
    private boolean slimefun$pair(BlockState state,Block block) {
        return state.is(block) || OutputChestBlock.canPair(block,state.getBlock());
    }
    @Inject(method="combine",at=@At("HEAD"),cancellable=true)
    private void slimefun$combine(BlockState state,Level level,BlockPos pos,boolean ignoreBlocked,
        CallbackInfoReturnable<DoubleBlockCombiner.NeighborCombineResult<? extends ChestBlockEntity>> callback) {
        if(state.is(Blocks.CHEST) && state.getValue(ChestBlock.TYPE)!=ChestType.SINGLE) {
            var partner=pos.relative(ChestBlock.getConnectedDirection(state));
            if(!level.hasChunkAt(partner) || level.getBlockState(partner).getBlock() instanceof OutputChestBlock)
                callback.setReturnValue(OutputChestBlock.combineLoaded(state,level,pos,ignoreBlocked));
        }
    }
}

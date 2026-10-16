package io.github.slimefun4.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class EnhancedFurnaceBlock extends FurnaceBlock {
    public final Catalog.Entry entry;
    public EnhancedFurnaceBlock(Catalog.Entry entry) { super(Properties.copy(Blocks.FURNACE)); this.entry=entry; }
    @Override public <T extends net.minecraft.world.level.block.entity.BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level,BlockState state,net.minecraft.world.level.block.entity.BlockEntityType<T> type){
        return Timings.ticker(super.getTicker(level,state,type));
    }
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if(player instanceof ServerPlayer server&&!ItemAccess.canUse(server,entry.id())) {
            ItemAccess.denied(server,entry.id());return InteractionResult.FAIL;
        }
        return super.use(state,level,pos,player,hand,hit);
    }
}

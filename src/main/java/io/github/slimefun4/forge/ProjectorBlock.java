package io.github.slimefun4.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public final class ProjectorBlock extends BaseEntityBlock {
    public ProjectorBlock() { super(Properties.of().strength(2).noOcclusion()); }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new ProjectorEntity(pos,state); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) { return Block.box(4,4,4,12,12,12); }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack) {
        if(!level.isClientSide && placer!=null && level.getBlockEntity(pos) instanceof ProjectorEntity projector) projector.setOwner(placer.getUUID());
    }
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if(player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof ProjectorEntity projector && projector.ownedBy(player)) {
            var stack=new ItemStack(asItem());
            if(!ResearchData.canUse(server,stack)) { ItemAccess.denied(server,stack); return InteractionResult.FAIL; }
            GuideContent.open(server,projector);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}

package io.github.slimefun4.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

public final class GeoMinerBlock extends HeadBlock.Entity {
    public static final IntegerProperty STATUS=IntegerProperty.create("status",0,6);
    public GeoMinerBlock() { super(HeadBlock.properties()); registerDefaultState(defaultBlockState().setValue(STATUS,0)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { super.createBlockStateDefinition(builder);builder.add(STATUS); }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new GeoMinerEntity(pos,state); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        if(context.getPlayer() instanceof ServerPlayer player && !ResearchData.canUse(player,context.getItemInHand())) { ItemAccess.denied(player,"geo_miner"); player.inventoryMenu.sendAllDataToRemote(); return null; }
        return super.getStateForPlacement(context);
    }
    @Override public void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random) { if(MachineClock.ready(level,pos,state)&&level.getBlockEntity(pos) instanceof GeoMinerEntity machine) machine.work(); }
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if(player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof GeoMinerEntity machine) {
            if(!level.mayInteract(player,pos)) return InteractionResult.FAIL;
            if(!ResearchData.canUse(server,new ItemStack(asItem()))) { ItemAccess.denied(server,"geo_miner"); return InteractionResult.FAIL; }
            GuideContent.open(server,machine);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void onRemove(BlockState old,Level level,BlockPos pos,BlockState next,boolean moving) {
        if(!old.is(next.getBlock())) { if(level.getBlockEntity(pos) instanceof GeoMinerEntity machine) machine.dismantle(); super.onRemove(old,level,pos,next,moving); }
    }
}

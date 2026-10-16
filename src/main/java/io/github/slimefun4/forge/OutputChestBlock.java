package io.github.slimefun4.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;

/** Vanilla chest storage, including an ordinary chest as the other half. */
public final class OutputChestBlock extends ChestBlock {
    private static final net.minecraft.tags.TagKey<Block> OUTPUTS=net.minecraft.tags.BlockTags.create(SlimefunForge.key("output_containers"));
    public OutputChestBlock() { super(Properties.copy(Blocks.CHEST), () -> SlimefunForge.OUTPUT_CHEST_ENTITY.get()); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new OutputChestEntity(pos,state); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        if(context.getPlayer() instanceof ServerPlayer player && !ResearchData.canUse(player,context.getItemInHand())) {
            ItemAccess.denied(player,context.getItemInHand());
            player.inventoryMenu.sendAllDataToRemote(); // Restore the item consumed by client placement prediction.
            return null;
        }
        return super.getStateForPlacement(context);
    }
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if(player instanceof ServerPlayer server && !ResearchData.canUse(server,new ItemStack(asItem()))) {
            ItemAccess.denied(server,new ItemStack(asItem()));
            return InteractionResult.FAIL;
        }
        return super.use(state,level,pos,player,hand,hit);
    }
    public static boolean canPair(Block first,Block second) {
        return (first==Blocks.CHEST || first instanceof OutputChestBlock)
            && (second==Blocks.CHEST || second instanceof OutputChestBlock);
    }
    @Override public DoubleBlockCombiner.NeighborCombineResult<? extends ChestBlockEntity> combine(BlockState state,Level level,BlockPos pos,boolean ignoreBlocked) {
        return combineLoaded(state,level,pos,ignoreBlocked);
    }
    public static DoubleBlockCombiner.NeighborCombineResult<ChestBlockEntity> combineLoaded(BlockState state,Level level,BlockPos pos,boolean ignoreBlocked) {
        if(!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof ChestBlockEntity first)
            || (!ignoreBlocked && isChestBlockedAt(level,pos))) return DoubleBlockCombiner.Combiner::acceptNone;
        var type=state.getValue(TYPE);
        if(type!=ChestType.SINGLE) {
            var otherPos=pos.relative(getConnectedDirection(state));
            if(level.hasChunkAt(otherPos)) {
                var otherState=level.getBlockState(otherPos);
                if(canPair(state.getBlock(),otherState.getBlock()) && otherState.getValue(TYPE)!=ChestType.SINGLE
                    && otherState.getValue(TYPE)!=type && otherState.getValue(FACING)==state.getValue(FACING)
                    && level.getBlockEntity(otherPos) instanceof ChestBlockEntity second) {
                    if(!ignoreBlocked && isChestBlockedAt(level,otherPos)) return DoubleBlockCombiner.Combiner::acceptNone;
                    return type==ChestType.RIGHT ? new DoubleBlockCombiner.NeighborCombineResult.Double<>(first,second)
                        : new DoubleBlockCombiner.NeighborCombineResult.Double<>(second,first);
                }
            }
        }
        return new DoubleBlockCombiner.NeighborCombineResult.Single<>(first);
    }
    public static Container outputAt(Level level,BlockPos pos) {
        if(!level.hasChunkAt(pos)) return null;
        var state=level.getBlockState(pos);
        if(!ItemAccess.enabled(level,new ItemStack(state.getBlock())))return null;
        if(state.getBlock() instanceof OutputChestBlock chest)return ChestBlock.getContainer(chest,state,level,pos,true);
        return state.is(OUTPUTS)&&level.getBlockEntity(pos) instanceof Container container?container:null;
    }
}

package io.github.slimefun4.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Redstone scheduling and transfer are inherited unchanged from the vanilla dropper. */
public final class IgnitionChamberBlock extends DropperBlock {
    public IgnitionChamberBlock() { super(Properties.copy(Blocks.DROPPER)); }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new IgnitionChamberEntity(pos,state); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        if(context.getPlayer() instanceof ServerPlayer player && !ResearchData.canUse(player,context.getItemInHand())) {
            ItemAccess.denied(player,context.getItemInHand());
            player.inventoryMenu.sendAllDataToRemote(); // Restore the item consumed by client placement prediction.
            return null;
        }
        return super.getStateForPlacement(context);
    }
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if(player instanceof ServerPlayer server) {
            var stack=new ItemStack(asItem());
            if(!ResearchData.canUse(server,stack)) { ItemAccess.denied(server,stack); return InteractionResult.FAIL; }
            if(level.getBlockEntity(pos) instanceof IgnitionChamberEntity chamber) {
                player.openMenu(chamber);
                player.awardStat(Stats.INSPECT_DROPPER);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}

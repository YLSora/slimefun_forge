package io.github.slimefun4.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** A vanilla dispenser inventory whose redstone pulses never eject its contents. */
public final class AndroidInterfaceBlock extends DispenserBlock {
    final boolean fuel;
    public AndroidInterfaceBlock(boolean fuel){super(Properties.copy(Blocks.DISPENSER));this.fuel=fuel;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new AndroidInterfaceEntity(pos,state);}
    @Override public void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        if(context.getPlayer() instanceof ServerPlayer player&&!ResearchData.canUse(player,context.getItemInHand())){ItemAccess.denied(player,context.getItemInHand());player.inventoryMenu.sendAllDataToRemote();return null;}
        return super.getStateForPlacement(context);
    }
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(player instanceof ServerPlayer server){var stack=new ItemStack(this);if(!ResearchData.canUse(server,stack)){ItemAccess.denied(server,stack);return InteractionResult.FAIL;}if(level.getBlockEntity(pos) instanceof AndroidInterfaceEntity entity)server.openMenu(entity);}
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}

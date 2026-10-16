package io.github.slimefun4.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class GpsTeleporterBlock extends Block implements EntityBlock {
    public GpsTeleporterBlock(){super(Properties.copy(Blocks.IRON_BLOCK));}
    static boolean researched(BlockPlaceContext context){
        if(context.getPlayer() instanceof ServerPlayer p&&!ResearchData.canUse(p,context.getItemInHand())){ItemAccess.denied(p,context.getItemInHand());p.inventoryMenu.sendAllDataToRemote();return false;}return true;
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return researched(context)?super.getStateForPlacement(context):null;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new GpsTeleporterEntity(pos,state);}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){if(!level.isClientSide&&placer!=null&&level.getBlockEntity(pos) instanceof GpsTeleporterEntity entity)entity.setOwner(placer.getUUID());}
}

package io.github.slimefun4.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class GpsTransmitterBlock extends HeadBlock.Entity {
    final Catalog.Entry definition;
    public GpsTransmitterBlock(Catalog.Entry definition) { super(HeadBlock.properties()); this.definition=definition; }
    int strength(int y) {
        return switch(definition.id()) {
            case "gps_transmitter" -> y;
            case "gps_transmitter_2" -> 4*y+100;
            case "gps_transmitter_3" -> 16*y+500;
            case "gps_transmitter_4" -> 64*y+2100;
            default -> throw new IllegalStateException("Unknown GPS transmitter: "+definition.id());
        };
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new GpsTransmitterEntity(pos,state); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        if(context.getPlayer() instanceof ServerPlayer player && !ResearchData.canUse(player,context.getItemInHand())) {
            ItemAccess.denied(player,context.getItemInHand()); player.inventoryMenu.sendAllDataToRemote(); return null;
        }
        return super.getStateForPlacement(context);
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack item) {
        if(!level.isClientSide && placer instanceof ServerPlayer player && level.getBlockEntity(pos) instanceof GpsTransmitterEntity transmitter) transmitter.setOwner(player.getUUID());
    }
    @Override public void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random) {
        if(!MachineClock.ready(level,pos,state))return;
        if(level.getBlockEntity(pos) instanceof GpsTransmitterEntity transmitter) transmitter.work();
    }
}

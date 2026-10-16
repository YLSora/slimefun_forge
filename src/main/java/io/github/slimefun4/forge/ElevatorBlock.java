package io.github.slimefun4.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.phys.BlockHitResult;

public final class ElevatorBlock extends PressurePlateBlock implements EntityBlock {
    public ElevatorBlock(){super(Sensitivity.MOBS,Properties.copy(Blocks.STONE_PRESSURE_PLATE),BlockSetType.STONE);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new ElevatorEntity(pos,state);}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){if(!level.isClientSide&&placer!=null&&level.getBlockEntity(pos) instanceof ElevatorEntity floor)floor.setOwner(placer.getUUID());}
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(player instanceof ServerPlayer server&&level.getBlockEntity(pos) instanceof ElevatorEntity floor&&floor.accessible(server)&&floor.ownedBy(server))ElevatorMenu.open(server,floor,true);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void entityInside(BlockState state,Level level,BlockPos pos,Entity entity){
        if(entity instanceof ServerPlayer player&&!player.isSpectator()&&TOUCH_AABB.move(pos).intersects(player.getBoundingBox())
            &&level.getBlockEntity(pos) instanceof ElevatorEntity floor&&floor.accessible(player)&&ElevatorMenu.enter(player,pos))ElevatorMenu.open(player,floor,false);
        super.entityInside(state,level,pos,entity);
    }
}

package io.github.slimefun4.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;

public final class GpsActivationPlate extends PressurePlateBlock implements EntityBlock {
    private final boolean personal;
    public GpsActivationPlate(boolean personal){super(Sensitivity.MOBS,Properties.copy(Blocks.STONE_PRESSURE_PLATE),BlockSetType.STONE);this.personal=personal;}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return GpsTeleporterBlock.researched(context)?super.getStateForPlacement(context):null;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new GpsTeleporterEntity(pos,state);}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){if(!level.isClientSide&&placer!=null&&level.getBlockEntity(pos) instanceof GpsTeleporterEntity entity)entity.setOwner(placer.getUUID());}
    public boolean accessible(ServerPlayer p,GpsTeleporterEntity entity){return (!personal||p.getUUID().equals(entity.owner()))&&p.level().mayInteract(p,entity.getBlockPos())&&ResearchData.canUse(p,new ItemStack(this));}
    @Override public void entityInside(BlockState state,Level level,BlockPos pos,Entity entity){
        if(!state.getValue(POWERED)&&entity instanceof ServerPlayer p&&!p.isSpectator()&&TOUCH_AABB.move(pos).intersects(p.getBoundingBox()))GpsTeleportMenu.open(p,pos);
        super.entityInside(state,level,pos,entity);
    }
}

package io.github.slimefun4.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

public final class InfusedHopperBlock extends HopperBlock {
    @Override public <T extends net.minecraft.world.level.block.entity.BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level,BlockState state,net.minecraft.world.level.block.entity.BlockEntityType<T> type){
        return Timings.ticker(super.getTicker(level,state,type));
    }
    public InfusedHopperBlock(){super(Properties.copy(Blocks.HOPPER));}
    @Override public void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moving){super.onPlace(state,level,pos,old,moving);if(!level.isClientSide&&!old.is(this))level.scheduleTick(pos,this,MachineClock.delay(level));}
    @Override public void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random) {
        if(!MachineClock.ready(level,pos,state))return;
        if(MagicSettings.HOPPER_REDSTONE.get()&&!state.getValue(ENABLED)){level.scheduleTick(pos,this,MachineClock.delay(level));return;}
        var at=new Vec3(pos.getX()+.5,pos.getY()+1.2,pos.getZ()+.5);boolean moved=false;
        for(var item:level.getEntitiesOfClass(ItemEntity.class,new AABB(at,at).inflate(MagicSettings.HOPPER_RADIUS.get()),e->e.isAlive()&&!e.hasPickUpDelay()&&!e.getPersistentData().contains("slimefun:no_pickup")&&e.position().distanceToSqr(at)>.25)){item.setDeltaMovement(0,.1,0);item.teleportTo(at.x,at.y,at.z);moved=true;}
        if(moved&&!MagicSettings.HOPPER_SILENT.get())level.playSound(null,pos,SoundEvents.ENDERMAN_TELEPORT,SoundSource.BLOCKS,.5F,1);
        level.scheduleTick(pos,this,MachineClock.delay(level));
    }
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(player instanceof ServerPlayer p&&!ResearchData.canUse(p,new ItemStack(asItem()))){ItemAccess.denied(p,new ItemStack(asItem()));return InteractionResult.FAIL;}
        return super.use(state,level,pos,player,hand,hit);
    }
}

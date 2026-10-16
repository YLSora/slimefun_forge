package io.github.slimefun4.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public final class GpsPylonBlock extends GlassBlock {
    public static final BooleanProperty PURPLE=BooleanProperty.create("purple");
    public GpsPylonBlock(){super(Properties.copy(Blocks.PURPLE_STAINED_GLASS));registerDefaultState(stateDefinition.any().setValue(PURPLE,true));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(PURPLE);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return GpsTeleporterBlock.researched(context)?super.getStateForPlacement(context):null;}
    private void schedule(Level level,BlockPos pos){if(level instanceof ServerLevel server&&!level.getBlockTicks().hasScheduledTick(pos,this))level.scheduleTick(pos,this,MachineClock.delay(server.getServer().overworld()));}
    @Override public void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moving){super.onPlace(state,level,pos,old,moving);schedule(level,pos);}
    @Override public void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){
        if(!level.getBlockState(pos).is(this)||!MachineClock.callbacksAllowed(state))return;
        if(!MachineClock.due(level.getServer().overworld())){schedule(level,pos);return;}
        boolean purple=(level.getServer().overworld().getGameTime()/MachineClock.interval()&1)!=0;
        if(state.getValue(PURPLE)!=purple)level.setBlock(pos,state.setValue(PURPLE,purple),Block.UPDATE_CLIENTS);
        schedule(level,pos);
    }
}

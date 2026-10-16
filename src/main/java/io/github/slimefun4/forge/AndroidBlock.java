package io.github.slimefun4.forge;

import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import net.minecraftforge.server.permission.nodes.*;

@Mod.EventBusSubscriber(modid=SlimefunForge.ID)
public final class AndroidBlock extends BaseEntityBlock {
    @Override public boolean canBeReplaced(BlockState state,net.minecraft.world.level.material.Fluid fluid){return false;}
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final PermissionNode<Boolean> BYPASS=new PermissionNode<>(SlimefunForge.ID,"android.bypass",PermissionTypes.BOOLEAN,(player,id,context)->player!=null&&player.server.getPlayerList().isOp(player.getGameProfile()));
    final int tier;
    enum Type { BASIC,MINER,WOODCUTTER,FARMER,ADVANCED_FARMER,FISHERMAN,FIGHTER }
    final Type type;
    public AndroidBlock(Catalog.Entry entry){super(HeadBlock.properties());tier=entry.tier();if(tier<1||tier>3)throw new IllegalArgumentException("Invalid android tier");type=switch(entry.implementation()){case "ProgrammableAndroid"->Type.BASIC;case "MinerAndroid"->Type.MINER;case "WoodcutterAndroid"->Type.WOODCUTTER;case "FarmerAndroid"->tier==1?Type.FARMER:Type.ADVANCED_FARMER;case "FishermanAndroid"->Type.FISHERMAN;case "ButcherAndroid"->Type.FIGHTER;default->throw new IllegalArgumentException("Unknown android type");};registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));}
    @SubscribeEvent public static void permissions(PermissionGatherEvent.Nodes event){event.addNodes(BYPASS);}
    static boolean mayAccess(ServerPlayer player,io.github.slimefun4.forge.api.AndroidApi.Robot android){return io.github.slimefun4.forge.api.AndroidApi.mayAccess(player,android);}
    @SubscribeEvent public static void breaking(BlockEvent.BreakEvent event){if(event.getPlayer() instanceof ServerPlayer player&&event.getLevel().getBlockEntity(event.getPos()) instanceof io.github.slimefun4.forge.api.AndroidApi.Robot android&&!mayAccess(player,android))event.setCanceled(true);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING);}
    @Override public BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override public BlockState mirror(BlockState state,Mirror mirror){return rotate(state,mirror.getRotation(state.getValue(FACING)));}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new AndroidEntity(pos,state);}
    @Override public RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return Block.box(4,0,4,12,8,12);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        if(context.getPlayer() instanceof ServerPlayer player&&!ResearchData.canUse(player,context.getItemInHand())){ItemAccess.denied(player,context.getItemInHand());player.inventoryMenu.sendAllDataToRemote();return null;}
        return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){if(placer instanceof ServerPlayer player&&level.getBlockEntity(pos) instanceof AndroidEntity android)android.setOwner(player.getUUID());}
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(player instanceof ServerPlayer server&&level.getBlockEntity(pos) instanceof AndroidEntity android){
            if(!ResearchData.canUse(server,new ItemStack(this))){ItemAccess.denied(server,new ItemStack(this));return InteractionResult.CONSUME;}
            if(!mayAccess(server,android))return InteractionResult.CONSUME;GuideContent.open(server,android);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){
        if(!MachineClock.callbacksAllowed(state))return;
        if(level.getBlockEntity(pos) instanceof AndroidEntity android){
            // A saved tick can resume off the shared ten-tick boundary after chunk loading.
            if(!MachineClock.due(level))android.wake();else android.work();
        }
    }
    @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){
        if(!state.is(next.getBlock())&&level.getBlockEntity(pos) instanceof AndroidEntity android&&!android.moving())Containers.dropContents(level,pos,android.inventory);
        super.onRemove(state,level,pos,next,moving);
    }
}

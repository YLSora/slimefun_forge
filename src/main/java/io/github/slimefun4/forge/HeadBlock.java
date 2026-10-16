package io.github.slimefun4.forge;

import net.minecraft.core.*;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.*;

/** One native machine identity across the 16 standing and four wall skull poses. */
public abstract class HeadBlock extends Block {
    public static final IntegerProperty POSE=IntegerProperty.create("head_pose",0,19);
    private static final Direction[] WALLS={Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST};
    public static int wallPose(Direction face){for(int i=0;i<4;i++)if(WALLS[i]==face)return 16+i;throw new IllegalArgumentException("Head wall face must be horizontal");}
    public static Direction wallFacing(BlockState state){return WALLS[state.getValue(POSE)-16];}
    protected HeadBlock(Properties properties){super(properties);if(hasHead())registerDefaultState(defaultBlockState().setValue(POSE,0));}
    protected boolean hasHead(){return true;}
    public static Properties properties(){return Properties.copy(Blocks.PLAYER_HEAD).noOcclusion();}
    @Override public boolean canBeReplaced(BlockState state,net.minecraft.world.level.material.Fluid fluid){return !hasHead()&&super.canBeReplaced(state,fluid);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){if(hasHead())builder.add(POSE);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        var state=super.getStateForPlacement(context);if(!hasHead()||state==null)return state;
        var face=context.getClickedFace();
        if(face==Direction.DOWN)face=java.util.Arrays.stream(context.getNearestLookingDirections()).filter(d->d.getAxis().isHorizontal()).findFirst().orElse(Direction.NORTH).getOpposite();
        if(face==Direction.UP)return state.setValue(POSE,Blocks.PLAYER_HEAD.getStateForPlacement(context).getValue(SkullBlock.ROTATION));
        return state.setValue(POSE,wallPose(face));
    }
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){
        if(!hasHead())return super.getShape(state,level,pos,context);
        int pose=state.getValue(POSE);var nativeState=pose<16?Blocks.PLAYER_HEAD.defaultBlockState():Blocks.PLAYER_WALL_HEAD.defaultBlockState().setValue(WallSkullBlock.FACING,WALLS[pose-16]);
        return nativeState.getShape(level,pos,context);
    }
    @Override public BlockState rotate(BlockState state,Rotation rotation){
        if(!hasHead())return super.rotate(state,rotation);int pose=state.getValue(POSE);
        return state.setValue(POSE,pose<16?rotation.rotate(pose,16):16+java.util.List.of(WALLS).indexOf(rotation.rotate(WALLS[pose-16])));
    }
    @Override public BlockState mirror(BlockState state,Mirror mirror){
        if(!hasHead())return super.mirror(state,mirror);int pose=state.getValue(POSE);
        return pose<16?state.setValue(POSE,mirror.mirror(pose,16)):rotate(state,mirror.getRotation(WALLS[pose-16]));
    }
    public abstract static class Entity extends HeadBlock implements EntityBlock {
        protected Entity(Properties properties){super(properties);}
        @Override public boolean triggerEvent(BlockState state,Level level,BlockPos pos,int id,int value){super.triggerEvent(state,level,pos,id,value);var entity=level.getBlockEntity(pos);return entity!=null&&entity.triggerEvent(id,value);}
        @Override public MenuProvider getMenuProvider(BlockState state,Level level,BlockPos pos){BlockEntity entity=level.getBlockEntity(pos);return entity instanceof MenuProvider menu?menu:null;}
    }
}

package io.github.slimefun4.forge;

import java.util.stream.IntStream;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

public final class RainbowBlocks {
    public static final IntegerProperty COLOR=IntegerProperty.create("color",0,15);
    public static Block create(Catalog.Entry entry) {
        var vanilla=BuiltInRegistries.BLOCK.get(new ResourceLocation("minecraft",entry.material()));
        int[] colors=entry.id().endsWith("_xmas")?new int[]{14,13}:entry.id().endsWith("_valentine")?new int[]{2,6}:entry.id().endsWith("_halloween")?new int[]{1,15}:IntStream.range(0,16).toArray();
        if(vanilla instanceof StainedGlassPaneBlock)return new Pane(vanilla,colors);
        if(vanilla instanceof StainedGlassBlock)return new Glass(vanilla,colors);
        if(vanilla instanceof GlazedTerracottaBlock)return new Glazed(vanilla,colors);
        return new Solid(vanilla,colors,entry.material().endsWith("_wool"));
    }
    private static void schedule(Level level,BlockPos pos,BlockState state){if(!level.isClientSide)level.scheduleTick(pos,state.getBlock(),MachineClock.delay(level));}
    private static void cycle(BlockState state,ServerLevel level,BlockPos pos,int[] colors){
        if(!MachineClock.ready(level,pos,state))return;
        var next=state.setValue(COLOR,colors[(int)Math.floorMod(level.getGameTime()/MachineClock.interval(),colors.length)]);
        if(next.hasProperty(GlazedTerracottaBlock.FACING))next=next.setValue(GlazedTerracottaBlock.FACING,Direction.NORTH);
        if(!next.equals(state))level.setBlock(pos,next,Block.UPDATE_CLIENTS);schedule(level,pos,next);
    }
    private static float[] beam(BlockState state){return DyeColor.byId(state.getValue(COLOR)).getTextureDiffuseColors();}
    private static final class Solid extends Block {
        private final int[] colors;private final boolean wool;
        Solid(Block base,int[] colors,boolean wool){super(Properties.copy(base));this.colors=colors;this.wool=wool;registerDefaultState(defaultBlockState().setValue(COLOR,0));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){super.createBlockStateDefinition(builder);builder.add(COLOR);}
        @Override public void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moving){super.onPlace(state,level,pos,old,moving);if(!old.is(this))schedule(level,pos,state);}
        @Override public void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){cycle(state,level,pos,colors);}
        @Override public int getFlammability(BlockState state,BlockGetter level,BlockPos pos,Direction face){return wool?60:0;}
        @Override public int getFireSpreadSpeed(BlockState state,BlockGetter level,BlockPos pos,Direction face){return wool?30:0;}
    }
    private static final class Glass extends StainedGlassBlock {
        private final int[] colors;
        Glass(Block base,int[] colors){super(DyeColor.WHITE,Properties.copy(base));this.colors=colors;registerDefaultState(defaultBlockState().setValue(COLOR,0));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){super.createBlockStateDefinition(builder);builder.add(COLOR);}
        @Override public void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moving){super.onPlace(state,level,pos,old,moving);if(!old.is(this))schedule(level,pos,state);}
        @Override public void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){cycle(state,level,pos,colors);}
        @Override public float[] getBeaconColorMultiplier(BlockState state,LevelReader level,BlockPos pos,BlockPos beacon){return beam(state);}
    }
    private static final class Pane extends StainedGlassPaneBlock {
        private final int[] colors;
        Pane(Block base,int[] colors){super(DyeColor.WHITE,Properties.copy(base));this.colors=colors;registerDefaultState(defaultBlockState().setValue(COLOR,0));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){super.createBlockStateDefinition(builder);builder.add(COLOR);}
        @Override public void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moving){super.onPlace(state,level,pos,old,moving);if(!old.is(this))schedule(level,pos,state);}
        @Override public void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){cycle(state,level,pos,colors);}
        @Override public float[] getBeaconColorMultiplier(BlockState state,LevelReader level,BlockPos pos,BlockPos beacon){return beam(state);}
    }
    private static final class Glazed extends GlazedTerracottaBlock {
        private final int[] colors;
        Glazed(Block base,int[] colors){super(Properties.copy(base));this.colors=colors;registerDefaultState(defaultBlockState().setValue(COLOR,0));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){super.createBlockStateDefinition(builder);builder.add(COLOR);}
        @Override public void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moving){super.onPlace(state,level,pos,old,moving);if(!old.is(this))schedule(level,pos,state);}
        @Override public void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){cycle(state,level,pos,colors);}
    }
    private RainbowBlocks(){}
}

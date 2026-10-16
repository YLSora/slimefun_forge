package io.github.slimefun4.forge;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Fluid;

/** Only appearance survives a debug clear; native block states need no machine entity or ticker. */
public final class ClearedHeadBlock extends HeadBlock {
    public static final Property<String> SKIN=new Property<>("skin",String.class){
        @Override public Collection<String> getPossibleValues(){return Catalog.HEAD_SKINS;}
        @Override public String getName(String value){return value;}
        @Override public Optional<String> getValue(String value){return Catalog.HEAD_SKINS.contains(value)?Optional.of(value):Optional.empty();}
    };
    public ClearedHeadBlock(){super(HeadBlock.properties());registerDefaultState(defaultBlockState().setValue(SKIN,"missing"));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){super.createBlockStateDefinition(builder);builder.add(SKIN);}
    @Override public boolean canBeReplaced(BlockState state,Fluid fluid){return true;}
    public static BlockState copy(BlockState original){
        String skin=BuiltInRegistries.BLOCK.getKey(original.getBlock()).getPath();
        int pose=original.hasProperty(POSE)?original.getValue(POSE):switch(original.getValue(AndroidBlock.FACING)){case NORTH->0;case EAST->4;case SOUTH->8;case WEST->12;default->throw new IllegalArgumentException("Vertical android");};
        return SlimefunForge.CLEARED_HEAD.get().defaultBlockState().setValue(SKIN,skin).setValue(POSE,pose);
    }
    @Override public ItemStack getCloneItemStack(BlockGetter level,BlockPos pos,BlockState state){var stack=new ItemStack(asItem());stack.getOrCreateTagElement("BlockStateTag").putString("skin",state.getValue(SKIN));return stack;}
}

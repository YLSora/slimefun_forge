package io.github.slimefun4.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class OutputChestEntity extends ChestBlockEntity {
    public OutputChestEntity(BlockPos pos,BlockState state) { super(SlimefunForge.OUTPUT_CHEST_ENTITY.get(),pos,state); }
    @Override protected Component getDefaultName() { return Component.translatable("block.slimefun.output_chest"); }
}

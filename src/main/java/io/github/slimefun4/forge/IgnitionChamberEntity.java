package io.github.slimefun4.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class IgnitionChamberEntity extends DispenserBlockEntity {
    public IgnitionChamberEntity(BlockPos pos,BlockState state) { super(SlimefunForge.IGNITION_CHAMBER_ENTITY.get(),pos,state); }
    @Override protected Component getDefaultName() { return Component.translatable("block.slimefun.ignition_chamber"); }
}

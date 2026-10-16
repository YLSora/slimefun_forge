package io.github.slimefun4.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Cancelable;

/** Fired on the Forge bus after owner protection and before a miner commits its drops. */
@Cancelable
public final class AndroidMineEvent extends BlockEvent {
    private final io.github.slimefun4.forge.api.AndroidApi.Robot android;
    public AndroidMineEvent(io.github.slimefun4.forge.api.AndroidApi.Robot android,BlockPos pos,BlockState state){super(android.getLevel(),pos,state);this.android=android;}
    public io.github.slimefun4.forge.api.AndroidApi.Robot getAndroid(){return android;}
}

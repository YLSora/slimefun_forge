package io.github.slimefun4.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.Event;

/** Non-cancelable server-thread notification immediately before a reactor becomes lava. */
public final class ReactorExplodeEvent extends Event {
    private final ServerLevel level;
    private final net.minecraft.world.level.block.entity.BlockEntity reactor;
    public ReactorExplodeEvent(ServerLevel level,net.minecraft.world.level.block.entity.BlockEntity reactor) {this.level=java.util.Objects.requireNonNull(level);this.reactor=java.util.Objects.requireNonNull(reactor);}
    public ServerLevel getLevel() {return level;}
    public net.minecraft.world.level.block.entity.BlockEntity getReactor() {return reactor;}
    public BlockPos getPosition() {return reactor.getBlockPos();}
}

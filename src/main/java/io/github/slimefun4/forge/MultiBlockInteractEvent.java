package io.github.slimefun4.forge;

import java.util.Objects;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Cancelable;

/** Server-thread veto after recognition, before a native multiblock changes inventory or starts work. */
@Cancelable
public final class MultiBlockInteractEvent extends PlayerEvent {
    private final ResourceLocation structure;
    private final BlockPos position,clicked;
    private final Direction face;
    public MultiBlockInteractEvent(ServerPlayer player,ResourceLocation structure,BlockPos position,BlockPos clicked,Direction face){super(player);this.structure=Objects.requireNonNull(structure);this.position=position.immutable();this.clicked=clicked.immutable();this.face=Objects.requireNonNull(face);}
    public ResourceLocation getStructure(){return structure;}
    public BlockPos getPosition(){return position;}
    public BlockPos getClicked(){return clicked;}
    public Direction getFace(){return face;}
}

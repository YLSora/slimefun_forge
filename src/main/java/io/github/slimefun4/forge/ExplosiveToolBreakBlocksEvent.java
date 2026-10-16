package io.github.slimefun4.forge;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Cancelable;

@Cancelable
public final class ExplosiveToolBreakBlocksEvent extends PlayerEvent {
    private final BlockPos origin;private final Set<BlockPos> allowed;private List<BlockPos> blocks;private final ItemStack tool;
    public ExplosiveToolBreakBlocksEvent(ServerPlayer player,BlockPos origin,List<BlockPos> blocks,ItemStack tool){super(player);this.origin=origin.immutable();this.blocks=blocks.stream().map(BlockPos::immutable).distinct().toList();this.allowed=Set.copyOf(this.blocks);this.tool=tool.copy();}
    public BlockPos getOrigin(){return origin;}
    public List<BlockPos> getAdditionalBlocks(){return blocks;}
    /** Replacement may remove/reorder original targets, never add distant or unvalidated blocks. */
    public void setAdditionalBlocks(Collection<BlockPos> value){blocks=EventRules.targets(value.stream().map(BlockPos::immutable).toList(),allowed);}
    public ItemStack getTool(){return tool.copy();}
}

package io.github.slimefun4.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Cancelable;

@Cancelable
public final class BlockPlacerPlaceEvent extends PlayerEvent {
    private final BlockPos source,target;
    private ItemStack item;
    private boolean immutable;
    public BlockPlacerPlaceEvent(ServerPlayer player,BlockPos source,BlockPos target,ItemStack item){super(player);this.source=source.immutable();this.target=target.immutable();setItem(item);}
    public BlockPos getSource(){return source;}
    public BlockPos getTarget(){return target;}
    public ItemStack getItem(){return item.copy();}
    public void setItem(ItemStack stack){checkMutable();if(stack==null||stack.getCount()!=1||!(stack.getItem() instanceof net.minecraft.world.item.BlockItem))throw new IllegalArgumentException("Placer replacement must be one block item");item=stack.copy();}
    public void setImmutable(){immutable=true;}
    public boolean isImmutable(){return immutable;}
    @Override public void setCanceled(boolean value){checkMutable();super.setCanceled(value);}
    private void checkMutable(){if(immutable)throw new IllegalStateException("Block placer event is immutable");}
}

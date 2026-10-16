package io.github.slimefun4.forge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Cancelable;

@Cancelable
public final class TalismanActivateEvent extends PlayerEvent {
    private final Item talisman;
    private final ItemStack item;
    private boolean preventsConsumption;
    public TalismanActivateEvent(ServerPlayer player,Item talisman,ItemStack item){super(player);if(!(talisman instanceof io.github.slimefun4.forge.api.MagicApi.Talisman)||!item.is(talisman))throw new IllegalArgumentException("Invalid native talisman");this.talisman=talisman;this.item=item.copy();}
    public Item getTalisman(){return talisman;}
    public ItemStack getItem(){return item.copy();}
    public boolean preventsConsumption(){return preventsConsumption;}
    public void setPreventsConsumption(boolean value){preventsConsumption=value;}
}

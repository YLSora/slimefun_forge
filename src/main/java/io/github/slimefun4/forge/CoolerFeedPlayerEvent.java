package io.github.slimefun4.forge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Cancelable;

@Cancelable
public final class CoolerFeedPlayerEvent extends PlayerEvent {
    private final ItemStack cooler;
    private ItemStack consumed;
    public CoolerFeedPlayerEvent(ServerPlayer player,ItemStack cooler,ItemStack consumed) {
        super(player);this.cooler=cooler.copy();setConsumedItem(consumed.copyWithCount(1));
    }
    public ItemStack getCooler() { return cooler.copy(); }
    public ItemStack getConsumedItem() { return consumed.copy(); }
    public void setConsumedItem(ItemStack stack) {
        if(stack==null||stack.isEmpty()||stack.getCount()!=1||!(cooler.getItem() instanceof io.github.slimefun4.forge.api.BackpackApi.Cooling cooling)||!cooling.canFeed(stack.copy()))throw new IllegalArgumentException("Consumed item must be one drink accepted by this cooler");
        consumed=stack.copy();
    }
}

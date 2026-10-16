package io.github.slimefun4.forge;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/** Server-thread pre-reservation event; listeners edit the live input stacks. */
@Cancelable
public final class AutoEnchanterProcessEvent extends Event {
    private final net.minecraft.world.level.block.entity.BlockEntity machine;
    private final ItemStack item,enchantedBook;
    public AutoEnchanterProcessEvent(net.minecraft.world.level.block.entity.BlockEntity machine,ItemStack item,ItemStack enchantedBook){this.machine=machine;this.item=item;this.enchantedBook=enchantedBook;}
    public net.minecraft.world.level.block.entity.BlockEntity getMachine(){return machine;}
    public ItemStack getItem(){return item;}
    public ItemStack getEnchantedBook(){return enchantedBook;}
}


package io.github.slimefun4.forge;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;

/** Industrial contents share a display name but retain distinct registry identities. */
public final class OrganicMaterialItem extends Item {
    public OrganicMaterialItem() { super(new Properties()); }
    static boolean isFertilizer(ItemStack stack) { return CargoFilter.inventoryMatches(stack,new ItemStack(SlimefunForge.item("fertilizer_wheat")),false); }
    static boolean isFood(ItemStack stack) { return CargoFilter.inventoryMatches(stack,new ItemStack(SlimefunForge.item("organic_food_wheat")),false); }
    @Override public Component getName(ItemStack stack) { return super.getName(stack).copy().withStyle(ChatFormatting.GREEN); }
}

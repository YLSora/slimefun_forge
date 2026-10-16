package io.github.slimefun4.forge.mixin;

import io.github.slimefun4.forge.SlimefunForge;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.FireworkStarRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FireworkStarRecipe.class)
public abstract class FireworkStarRecipeMixin {
    /** Only the recipe's read view changes; vanilla validates and consumes the original slots. */
    @Redirect(method={"matches(Lnet/minecraft/world/inventory/CraftingContainer;Lnet/minecraft/world/level/Level;)Z",
        "assemble(Lnet/minecraft/world/inventory/CraftingContainer;Lnet/minecraft/core/RegistryAccess;)Lnet/minecraft/world/item/ItemStack;"},
        at=@At(value="INVOKE",target="Lnet/minecraft/world/inventory/CraftingContainer;getItem(I)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack slimefun$fireworkMaterial(CraftingContainer inventory,int slot) {
        ItemStack stack=inventory.getItem(slot);
        if(stack.is(SlimefunForge.item("synthetic_diamond"))) return new ItemStack(Items.DIAMOND,stack.getCount());
        if(stack.is(SlimefunForge.item("synthetic_sapphire"))) return new ItemStack(Items.PLAYER_HEAD,stack.getCount());
        return stack;
    }
}

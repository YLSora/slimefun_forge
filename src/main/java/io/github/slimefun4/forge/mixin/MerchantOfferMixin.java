package io.github.slimefun4.forge.mixin;

import io.github.slimefun4.forge.SlimefunForge;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MerchantOffer.class)
public abstract class MerchantOfferMixin {
    /** Preserve vanilla required NBT, prices, second payment and transaction bookkeeping. */
    @Redirect(method="isRequiredItem",at=@At(value="INVOKE",
        target="Lnet/minecraft/world/item/ItemStack;isSameItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean slimefun$emeraldPayment(ItemStack supplied,ItemStack required) {
        return ItemStack.isSameItem(supplied,required)
            || required.is(Items.EMERALD)&&supplied.is(SlimefunForge.item("synthetic_emerald"));
    }
}

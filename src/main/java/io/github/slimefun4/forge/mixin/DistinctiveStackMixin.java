package io.github.slimefun4.forge.mixin;

import io.github.slimefun4.forge.AddonItems;
import io.github.slimefun4.forge.api.ItemApi;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A custom identity can narrow native merging, never merge away different NBT/capabilities. */
@Mixin(ItemStack.class)
abstract class DistinctiveStackMixin {
    @Inject(method="isSameItemSameTags",at=@At("RETURN"),cancellable=true)
    private static void slimefun$identity(ItemStack first,ItemStack second,CallbackInfoReturnable<Boolean> result){
        if(result.getReturnValue()&&!first.isEmpty()&&!AddonItems.distinctive(first,second,ItemApi.Match.STACK))result.setReturnValue(false);
    }
}

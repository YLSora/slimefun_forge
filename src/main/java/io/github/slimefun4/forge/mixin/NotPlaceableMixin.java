package io.github.slimefun4.forge.mixin;

import io.github.slimefun4.forge.api.ItemApi;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
abstract class NotPlaceableMixin {
    @Inject(method="place",at=@At("HEAD"),cancellable=true)
    private void slimefun$placement(BlockPlaceContext context,CallbackInfoReturnable<InteractionResult> callback){if((Object)this instanceof ItemApi.NotPlaceable)callback.setReturnValue(InteractionResult.FAIL);}
}

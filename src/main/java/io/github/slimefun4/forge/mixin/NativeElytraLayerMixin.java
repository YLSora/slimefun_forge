package io.github.slimefun4.forge.mixin;

import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ElytraLayer.class)
public abstract class NativeElytraLayerMixin {
    @Inject(method="shouldRender",remap=false,at=@At("RETURN"),cancellable=true)
    private void slimefun$nativeElytra(ItemStack stack,LivingEntity entity,CallbackInfoReturnable<Boolean> cir) {
        if(stack.getItem() instanceof ElytraItem&&BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("slimefun"))cir.setReturnValue(true);
    }
}

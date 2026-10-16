package io.github.slimefun4.forge.mixin;

import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStack.class)
public abstract class TalismanDurabilityMixin {
    @Inject(method="hurtAndBreak",at=@At(value="INVOKE",target="Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V"))
    private <T extends LivingEntity> void slimefun$repair(int amount,T entity,Consumer<T> onBreak,CallbackInfo ci){if(entity instanceof ServerPlayer player)io.github.slimefun4.forge.TalismanItem.broken(player,(ItemStack)(Object)this);}
}

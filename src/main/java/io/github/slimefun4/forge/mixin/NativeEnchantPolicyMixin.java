package io.github.slimefun4.forge.mixin;

import io.github.slimefun4.forge.NativeItemRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.EnchantmentMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnchantmentMenu.class)
abstract class NativeEnchantPolicyMixin {
    @Inject(method="clickMenuButton",at=@At("HEAD"),cancellable=true)
    private void slimefun$enchant(Player player,int choice,CallbackInfoReturnable<Boolean> callback){
        if(player instanceof ServerPlayer server&&!NativeItemRules.allowEnchant((EnchantmentMenu)(Object)this,server))callback.setReturnValue(false);
    }
}

package io.github.slimefun4.forge.mixin;

import io.github.slimefun4.forge.NativeItemRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
abstract class NativeResultPolicyMixin {
    @Inject(method="clicked",at=@At("HEAD"),cancellable=true)
    private void slimefun$result(int slot,int button,ClickType type,Player player,CallbackInfo callback){
        var menu=(AbstractContainerMenu)(Object)this;
        if(player instanceof ServerPlayer server&&!NativeItemRules.allowClick(menu,slot,type,server)){menu.sendAllDataToRemote();callback.cancel();}
    }
}

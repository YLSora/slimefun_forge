package io.github.slimefun4.forge.mixin;

import io.github.slimefun4.forge.NativeItemRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native shift crafting calls quickMoveStack repeatedly; recheck every individual transfer. */
@Mixin({CraftingMenu.class,InventoryMenu.class,ItemCombinerMenu.class,GrindstoneMenu.class,CartographyTableMenu.class,StonecutterMenu.class,LoomMenu.class,AbstractFurnaceMenu.class})
abstract class NativeQuickMovePolicyMixin {
    @Inject(method="quickMoveStack",at=@At("HEAD"),cancellable=true)
    private void slimefun$transfer(Player player,int slot,CallbackInfoReturnable<ItemStack> callback){
        var menu=(AbstractContainerMenu)(Object)this;
        if(player instanceof ServerPlayer server&&!NativeItemRules.allowClick(menu,slot,ClickType.QUICK_MOVE,server))callback.setReturnValue(ItemStack.EMPTY);
    }
}

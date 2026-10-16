package io.github.slimefun4.forge.mixin;

import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnchantmentMenu.class)
public abstract class TalismanEnchantMixin {
    @Unique private ServerPlayer slimefun$enchanter;
    @Inject(method="clickMenuButton",at=@At("HEAD"))
    private void slimefun$begin(Player player,int button,CallbackInfoReturnable<Boolean> ci){slimefun$enchanter=player instanceof ServerPlayer server?server:null;}
    @Inject(method="clickMenuButton",at=@At("RETURN"))
    private void slimefun$end(Player player,int button,CallbackInfoReturnable<Boolean> ci){slimefun$enchanter=null;}
    @Inject(method="getEnchantmentList",at=@At("RETURN"),cancellable=true)
    private void slimefun$enchant(ItemStack stack,int button,int cost,CallbackInfoReturnable<List<EnchantmentInstance>> ci){
        var player=slimefun$enchanter;slimefun$enchanter=null;if(player==null)return;var before=stack.copy();
        var result=io.github.slimefun4.forge.TalismanItem.enchant(player,stack,ci.getReturnValue());
        ci.setReturnValue(player.containerMenu==(Object)this&&player.containerMenu.getSlot(0).getItem()==stack&&ItemStack.matches(stack,before)?result:List.of());
    }
}

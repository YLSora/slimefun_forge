package io.github.slimefun4.forge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.*;

/** RC-37 DamageableItem breaks an already exhausted item on the following damage attempt. */
public final class EquipmentDamage {
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void creativeUse(PlayerInteractEvent.RightClickItem event) {
        if(!(event.getEntity() instanceof ServerPlayer player)||!player.isCreative())return;
        var stack=event.getItemStack();String kind=SpecialTools.kind(stack);
        if(!kind.equals("seismic_axe")&&!kind.equals("pickaxe_of_the_seeker")&&!kind.equals("staff_elemental_storm"))return;
        // These RC items spend durability/uses in creative; finish before vanilla restores the stack.
        var result=stack.use(player.level(),player,event.getHand());event.setCanceled(true);event.setCancellationResult(result.getResult());
        player.inventoryMenu.sendAllDataToRemote();
    }
    public static void damage(ServerPlayer player,ItemStack stack,EquipmentSlot slot,boolean armor) {
        if(stack.isEmpty()||!stack.isDamageableItem())return;
        int unbreaking=stack.getEnchantmentLevel(Enchantments.UNBREAKING);
        if(unbreaking>0&&player.getRandom().nextDouble()>=(armor?.6+.4/(unbreaking+1):1.0/(unbreaking+1)))return;
        if(stack.getDamageValue()>=stack.getMaxDamage()){player.broadcastBreakEvent(slot);stack.setCount(0);}
        else stack.setDamageValue(stack.getDamageValue()+1);
    }
    private EquipmentDamage(){}
}

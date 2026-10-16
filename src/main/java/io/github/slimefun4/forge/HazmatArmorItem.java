package io.github.slimefun4.forge;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;

public final class HazmatArmorItem extends DyeableArmorItem implements io.github.slimefun4.forge.api.ItemApi.ProtectiveArmor {
    public HazmatArmorItem(String material) {
        super(ArmorMaterials.LEATHER,Type.valueOf(material.substring("leather_".length()).toUpperCase(java.util.Locale.ROOT)),new Properties());
    }
    @Override public int getColor(ItemStack stack) { return hasCustomColor(stack)?super.getColor(stack):getType()==Type.BOOTS?0:0xffa500; }
    @Override public net.minecraft.world.InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level,Player player,net.minecraft.world.InteractionHand hand) {
        if(player instanceof net.minecraft.server.level.ServerPlayer p&&!ResearchData.canUse(p,p.getItemInHand(hand))){ItemAccess.denied(p,p.getItemInHand(hand));p.inventoryMenu.sendAllDataToRemote();return net.minecraft.world.InteractionResultHolder.fail(p.getItemInHand(hand));}
        return super.use(level,player,hand);
    }
    @Override public io.github.slimefun4.forge.api.ItemApi.ArmorProtection protection(ItemStack stack){return new io.github.slimefun4.forge.api.ItemApi.ArmorProtection(java.util.Set.of(io.github.slimefun4.forge.api.ItemApi.Protection.RADIATION,io.github.slimefun4.forge.api.ItemApi.Protection.BEES),SlimefunForge.key("hazmat"),true);}
}

package io.github.slimefun4.forge.client;

import io.github.slimefun4.forge.api.Rechargeable;

import io.github.slimefun4.forge.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=SlimefunForge.ID,value=Dist.CLIENT)
public final class ItemTooltips {
    @SubscribeEvent public static void tooltip(ItemTooltipEvent event){
        var stack=event.getItemStack();
        if(stack.getItem() instanceof Rechargeable battery && !(stack.getItem() instanceof PortableTeleporterItem))event.getToolTip().add(Component.translatable("tooltip.slimefun.item_charge",battery.getItemCharge(stack),battery.getMaxItemCharge(stack)).withStyle(net.minecraft.ChatFormatting.YELLOW));
        if(!BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals(SlimefunForge.ID))return;
        String locale=Minecraft.getInstance().options.languageCode;var lines=event.getToolTip();
        if(!stack.hasTag()||!stack.getTag().getBoolean("slimefun_preview_lore"))lines.addAll(1,ItemText.lore(stack,locale));
        for(int i=0;i<lines.size();i++)lines.set(i,ItemText.localize(locale,lines.get(i)));
    }
}

package io.github.slimefun4.forge.client;

import io.github.slimefun4.forge.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=SlimefunForge.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class AncientPedestalRenderer {
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(SlimefunForge.ANCIENT_ALTAR_ENTITY.get(),context->{
            var vanilla=new EnchantTableRenderer(context);
            return (altar,partialTick,pose,buffers,light,overlay)->vanilla.render(altar.bookAnimation(),partialTick,pose,buffers,light,overlay);
        });
    }
    @SubscribeEvent public static void colors(RegisterColorHandlersEvent.Item event) {
        for(var entry:Catalog.ITEMS) if(SlimefunForge.item(entry.id()) instanceof net.minecraft.world.item.DyeableLeatherItem dyeable)
            event.register((stack,index)->index==0?dyeable.getColor(stack):0xffffff,SlimefunForge.item(entry.id()));
        for(var entry:Catalog.ITEMS) if(entry.behavior().equals("juice"))
            event.register((stack,index)->index==0?(stack.hasTag()&&stack.getTag().contains("CustomPotionColor",99)?stack.getTag().getInt("CustomPotionColor"):entry.juiceColor()):0xffffff,SlimefunForge.item(entry.id()));
        for(var entry:Catalog.ITEMS) if("ElementalRune".equals(entry.implementation())||entry.behavior().equals("functional_rune"))
            event.register((stack,index)->index==1?entry.runeColor():0xffffff,SlimefunForge.item(entry.id()));
        for(var entry:Catalog.ITEMS) if(entry.behavior().equals("hazmat"))
            event.register((stack,index)->index==0?((HazmatArmorItem)stack.getItem()).getColor(stack):0xffffff,SlimefunForge.item(entry.id()));
    }
}

package io.github.slimefun4.forge.client;

import io.github.slimefun4.forge.*;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid=SlimefunForge.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class ClearedHeadModels {
    @SubscribeEvent public static void setup(FMLClientSetupEvent event){event.enqueueWork(()->ItemProperties.register(SlimefunForge.CLEARED_HEAD_ITEM.get(),SlimefunForge.key("skin"),(stack,level,entity,seed)->{var tag=stack.getTagElement("BlockStateTag");return tag==null?0:Catalog.HEAD_SKINS.indexOf(tag.getString("skin"))+1;}));}
}

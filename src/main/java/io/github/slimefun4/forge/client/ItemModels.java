package io.github.slimefun4.forge.client;

import io.github.slimefun4.forge.SlimefunForge;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid=SlimefunForge.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class ItemModels {
    @SubscribeEvent public static void glass(net.minecraftforge.client.event.RegisterNamedRenderTypesEvent event){
        // Match vanilla glass's item shader instead of Forge's layered item shader.
        event.register("glass",net.minecraft.client.renderer.RenderType.translucent(),net.minecraft.client.renderer.Sheets.translucentCullBlockSheet());
    }
    @SubscribeEvent public static void register(FMLClientSetupEvent event){
        event.enqueueWork(()->{
            var angle=new ResourceLocation("angle");ItemProperties.register(SlimefunForge.item("portable_teleporter"),angle,ItemProperties.getProperty(Items.COMPASS,angle));
            for(var entry:io.github.slimefun4.forge.Catalog.ITEMS) {
                var item=SlimefunForge.item(entry.id());
                if(item instanceof net.minecraft.world.item.BowItem)for(var name:java.util.List.of("pull","pulling")){var key=new ResourceLocation(name);ItemProperties.register(item,key,ItemProperties.getProperty(Items.BOW,key));}
                if(item instanceof net.minecraft.world.item.TridentItem){var key=new ResourceLocation("throwing");ItemProperties.register(item,key,ItemProperties.getProperty(Items.TRIDENT,key));}
                if(item instanceof net.minecraft.world.item.ElytraItem){var key=new ResourceLocation("broken");ItemProperties.register(item,key,ItemProperties.getProperty(Items.ELYTRA,key));}
            }
        });
    }
}

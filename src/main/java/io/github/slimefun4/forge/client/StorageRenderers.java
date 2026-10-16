package io.github.slimefun4.forge.client;

import io.github.slimefun4.forge.SlimefunForge;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=SlimefunForge.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class StorageRenderers {
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(SlimefunForge.OUTPUT_CHEST_ENTITY.get(),ChestRenderer::new);
    }
}

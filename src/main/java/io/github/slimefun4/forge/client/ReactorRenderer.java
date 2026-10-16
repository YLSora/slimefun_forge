package io.github.slimefun4.forge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.slimefun4.forge.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=SlimefunForge.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class ReactorRenderer implements BlockEntityRenderer<ReactorEntity> {
    private static final Component[] LABELS=java.util.stream.IntStream.rangeClosed(1,50).mapToObj(i->Component.literal("❄ ").withStyle(net.minecraft.ChatFormatting.AQUA).append(Component.literal(i==50?"100%":i*2+".0%").withStyle(net.minecraft.ChatFormatting.GRAY))).toArray(Component[]::new);
    public ReactorRenderer(BlockEntityRendererProvider.Context context) {}
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {event.registerBlockEntityRenderer(SlimefunForge.REACTOR_ENTITY.get(),ReactorRenderer::new);}
    @Override public void render(ReactorEntity reactor,float partialTick,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        int coolant=reactor.getBlockState().getValue(ReactorBlock.COOLANT);
        if(coolant>0)ProjectorRenderer.draw(LABELS[coolant-1],1.5,pose,buffers);
    }
}

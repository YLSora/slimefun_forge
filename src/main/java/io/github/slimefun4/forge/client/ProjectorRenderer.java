package io.github.slimefun4.forge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.slimefun4.forge.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=SlimefunForge.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class ProjectorRenderer implements BlockEntityRenderer<ProjectorEntity> {
    public ProjectorRenderer(BlockEntityRendererProvider.Context context) {}
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(SlimefunForge.PROJECTOR_ENTITY.get(),ProjectorRenderer::new);
        event.registerBlockEntityRenderer(SlimefunForge.MACHINE_ENTITY.get(),context->(machine,partialTick,pose,buffers,light,overlay)->{
            if(machine.regulator()) draw(machine.networkStatus(),1.25,pose,buffers);
        });
        event.registerBlockEntityRenderer(SlimefunForge.CARGO_ENTITY.get(),context->(node,partialTick,pose,buffers,light,overlay)->{
            if(node.manager())draw(node.status(),1.25,pose,buffers);
        });
        event.registerBlockEntityRenderer(SlimefunForge.GPS_TRANSMITTER_ENTITY.get(),context->(transmitter,partialTick,pose,buffers,light,overlay)->draw(transmitter.status(),1.25,pose,buffers));
    }
    @Override public void render(ProjectorEntity projector,float partialTick,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        draw(projector.text(),1+projector.offset(),pose,buffers);
    }
    public static void draw(net.minecraft.network.chat.Component text,double height,PoseStack pose,MultiBufferSource buffers) {
        var minecraft=Minecraft.getInstance(); var font=minecraft.font;
        pose.pushPose(); pose.translate(0.5,height,0.5);
        pose.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation()); pose.scale(-0.025F,-0.025F,0.025F);
        font.drawInBatch(text,-font.width(text)/2F,0,0xffffffff,false,pose.last().pose(),buffers,Font.DisplayMode.NORMAL,0x40000000,LightTexture.FULL_BRIGHT);
        pose.popPose();
    }
}

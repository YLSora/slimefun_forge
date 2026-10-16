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
public final class GeoMinerRenderer implements BlockEntityRenderer<GeoMinerEntity> {
    private static final Component[] LABELS={Component.translatable("message.slimefun.geo_miner_idle").withStyle(net.minecraft.ChatFormatting.GRAY),Component.translatable("message.slimefun.geo_miner_scan").withStyle(net.minecraft.ChatFormatting.DARK_RED),Component.translatable("message.slimefun.geo_miner_finished").withStyle(net.minecraft.ChatFormatting.GRAY),
        mining(GeoResources.URANIUM),mining(GeoResources.NETHER_ICE),mining(GeoResources.SALT)};
    private static Component mining(GeoResources resource){return Component.translatable("message.slimefun.geo_miner_mining",(resource==GeoResources.URANIUM?Component.translatable("geo.slimefun.mining_uranium"):resource.name()).copy().withStyle(net.minecraft.ChatFormatting.WHITE)).withStyle(net.minecraft.ChatFormatting.GRAY);}
    public GeoMinerRenderer(BlockEntityRendererProvider.Context context) {}
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) { event.registerBlockEntityRenderer(SlimefunForge.GEO_MINER_ENTITY.get(),GeoMinerRenderer::new); }
    @Override public void render(GeoMinerEntity miner,float partialTick,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        int status=miner.getBlockState().getValue(GeoMinerBlock.STATUS);var name=miner.miningName();
        ProjectorRenderer.draw(status<6?LABELS[status]:Component.translatable("message.slimefun.geo_miner_mining",name==null?Component.literal("…"):name).withStyle(net.minecraft.ChatFormatting.GRAY),1.5,pose,buffers);
    }
}

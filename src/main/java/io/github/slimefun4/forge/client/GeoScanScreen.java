package io.github.slimefun4.forge.client;

import io.github.slimefun4.forge.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Native inventory presentation of the server's read-only scan slots. */
@Mod.EventBusSubscriber(modid=SlimefunForge.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class GeoScanScreen extends AbstractContainerScreen<GeoScanMenu> {
    private static final ResourceLocation TEXTURE=new ResourceLocation("minecraft","textures/gui/container/generic_54.png");
    public GeoScanScreen(GeoScanMenu menu,Inventory inventory,Component title) {
        super(menu,inventory,title);imageHeight=222;inventoryLabelY=imageHeight-94;
    }
    @SubscribeEvent public static void register(FMLClientSetupEvent event) {
        event.enqueueWork(()->MenuScreens.register(SlimefunForge.GEO_SCAN_MENU.get(),GeoScanScreen::new));
    }
    @Override protected void renderBg(GuiGraphics graphics,float partial,int mouseX,int mouseY) {
        graphics.blit(TEXTURE,leftPos,topPos,0,0,imageWidth,125);
        graphics.blit(TEXTURE,leftPos,topPos+125,0,126,imageWidth,96);
    }
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial) {
        renderBackground(graphics);super.render(graphics,mouseX,mouseY,partial);renderTooltip(graphics,mouseX,mouseY);
    }
}

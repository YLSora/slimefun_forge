package io.github.slimefun4.forge.client;

import io.github.slimefun4.forge.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid=SlimefunForge.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class ProcessingScreen extends AbstractContainerScreen<ProcessingMenu> {
    public ProcessingScreen(ProcessingMenu menu,Inventory inventory,Component title) {
        super(menu,inventory,title);imageHeight=114+menu.getRowCount()*18;inventoryLabelY=imageHeight-94;
    }
    @SubscribeEvent public static void register(FMLClientSetupEvent event) {
        event.enqueueWork(()->MenuScreens.register(SlimefunForge.PROCESSING_MENU.get(),ProcessingScreen::new));
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY) {
        var texture=net.minecraft.resources.ResourceLocation.parse("minecraft:textures/gui/container/generic_54.png");
        int height=menu.getRowCount()*18+17;g.blit(texture,leftPos,topPos,0,0,imageWidth,height);g.blit(texture,leftPos,topPos+height,0,126,imageWidth,96);
    }
    @Override protected void renderLabels(GuiGraphics g,int mouseX,int mouseY) {
        g.enableScissor(leftPos+8,topPos, leftPos+imageWidth-8,topPos+imageHeight);
        super.renderLabels(g,mouseX,mouseY);
        g.disableScissor();
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial) { renderBackground(g); super.render(g,mouseX,mouseY,partial); renderTooltip(g,mouseX,mouseY);if(font.width(title)>imageWidth-16&&mouseX>=leftPos+8&&mouseX<leftPos+imageWidth-8&&mouseY>=topPos+titleLabelY&&mouseY<topPos+titleLabelY+font.lineHeight)g.renderTooltip(font,title,mouseX,mouseY); }
}

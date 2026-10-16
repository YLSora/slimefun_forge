package io.github.slimefun4.forge;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

public final class OutputChestItem extends ResearchBlockItem {
    public OutputChestItem(Block block) { super(block); }

    @Override public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() { return Renderer.INSTANCE; }
        });
    }

    private static final class Renderer extends BlockEntityWithoutLevelRenderer {
        private static final Renderer INSTANCE = new Renderer();
        private final ItemStack chest = new ItemStack(Items.CHEST);

        private Renderer() {
            super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        }

        @Override public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
                                          MultiBufferSource buffers, int light, int overlay) {
            // Vanilla's item renderer only recognizes the vanilla chest block.
            Minecraft.getInstance().getItemRenderer().getBlockEntityRenderer()
                .renderByItem(chest, context, pose, buffers, light, overlay);
        }
    }
}

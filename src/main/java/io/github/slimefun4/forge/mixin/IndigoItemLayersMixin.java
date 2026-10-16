package io.github.slimefun4.forge.mixin;

import io.github.slimefun4.forge.SlimefunForge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="net.fabricmc.fabric.impl.client.indigo.renderer.render.ItemRenderContext",remap=false)
public abstract class IndigoItemLayersMixin {
    @Shadow private ItemStack itemStack;
    @Shadow private boolean isDefaultTranslucent;

    @Inject(method="computeOutputInfo",at=@At("TAIL"))
    private void slimefun$modelLayers(CallbackInfo ci) {
        if(itemStack.getItem() instanceof BlockItem item&&BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(SlimefunForge.ID)) {
            var state=item.getBlock().defaultBlockState();
            var model=Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
            // Indigo's vanilla layer table cannot see Forge model JSON render types.
            isDefaultTranslucent=model.getRenderTypes(state,RandomSource.create(42),ModelData.EMPTY).contains(RenderType.translucent());
        }
    }
}

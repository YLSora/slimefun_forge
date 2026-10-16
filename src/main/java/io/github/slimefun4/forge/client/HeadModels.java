package io.github.slimefun4.forge.client;

import io.github.slimefun4.forge.SlimefunForge;
import java.util.function.Function;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.*;
import net.minecraftforge.client.model.geometry.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=SlimefunForge.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class HeadModels extends SimpleUnbakedGeometry<HeadModels> {
    private final ElementsModel elements;
    private HeadModels(ElementsModel elements){this.elements=elements;}
    @SubscribeEvent public static void register(ModelEvent.RegisterGeometryLoaders event){
        IGeometryLoader<HeadModels> loader=(json,context)->new HeadModels(ElementsModel.Loader.INSTANCE.read(json,context));
        event.register("head",loader);
    }
    @Override protected void addQuads(IGeometryBakingContext context,IModelBuilder<?> builder,ModelBaker baker,Function<Material,TextureAtlasSprite> sprites,ModelState state,ResourceLocation location){
        var model=elements.bake(context,baker,sprites,state,ItemOverrides.EMPTY,location);
        for(var quad:model.getQuads(null,null,RandomSource.create(0))){
            int[] front=quad.getVertices().clone(),back=new int[front.length];int stride=front.length/4;
            var sprite=quad.getSprite();
            // Native skull UVs use the 64px skin grid; undo block baking's two UV insets.
            for(int vertex=0;vertex<4;vertex++){
                int offset=vertex*stride;
                float u=sprite.getUOffset(Float.intBitsToFloat(front[offset+4]));
                float v=sprite.getVOffset(Float.intBitsToFloat(front[offset+5]));
                front[offset+4]=Float.floatToRawIntBits(sprite.getU(Math.round(u*4)/4.0));
                front[offset+5]=Float.floatToRawIntBits(sprite.getV(Math.round(v*4)/4.0));
            }
            builder.addUnculledFace(new BakedQuad(front,quad.getTintIndex(),quad.getDirection(),sprite,quad.isShade(),quad.hasAmbientOcclusion()));
            // Chunk layers cull backfaces globally; reverse winding to match vanilla skulls' two-sided skin.
            for(int vertex=0;vertex<4;vertex++)System.arraycopy(front,vertex*stride,back,(3-vertex)*stride,stride);
            var facing=quad.getDirection().getOpposite();
            // Indigo uses the declared face instead of winding when deriving axis-aligned normals.
            ForgeHooksClient.fillNormal(back,facing,true);
            builder.addUnculledFace(new BakedQuad(back,quad.getTintIndex(),facing,quad.getSprite(),quad.isShade(),quad.hasAmbientOcclusion()));
        }
    }
}

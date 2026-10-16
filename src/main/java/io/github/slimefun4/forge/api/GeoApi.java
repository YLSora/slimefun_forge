package io.github.slimefun4.forge.api;

import io.github.slimefun4.forge.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.DimensionType;

/** Frozen namespaced GEO definitions; reserves stay in the core's per-dimension SavedData. */
public final class GeoApi {
    public record Resource(ResourceLocation id,Component name,ResourceLocation output,boolean mineable,int deviation,
            Map<ResourceKey<DimensionType>,Integer> environments,Map<ResourceKey<Biome>,Integer> biomes,Map<TagKey<Biome>,Integer> tags) {
        public Resource {
            Objects.requireNonNull(id);name=Objects.requireNonNull(name).copy();Objects.requireNonNull(output);
            if(deviation<0)throw new IllegalArgumentException("Negative GEO deviation");
            environments=Map.copyOf(environments);biomes=Map.copyOf(biomes);tags=Map.copyOf(tags);
            java.util.stream.Stream.of(environments.values(),biomes.values(),tags.values()).flatMap(Collection::stream).forEach(base->NetworkRules.reserve(base,deviation,0));
        }
        @Override public Component name(){return name.copy();}
    }
    public static void register(Resource resource){GeoResources.register(resource);}
    public static Map<ResourceLocation,GeoResources> resources(){return GeoResources.resources();}
    public static OptionalInt supply(ServerLevel level,ChunkPos chunk,ResourceLocation resource){NetworkApi.check(level);return GeoResourceData.get(level).supply(chunk,GeoResources.require(resource));}
    public static Map<GeoResources,Integer> scan(ServerLevel level,BlockPos scanner){return GeoResourceData.get(level).scan(level,scanner);}
    /** Set a registered reserve without generating unrelated resources. Missing records remain unscanned. */
    public static void supply(ServerLevel level,ChunkPos chunk,ResourceLocation resource,int amount){GeoResourceData.get(level).set(level,chunk,GeoResources.require(resource),amount);}
    public static boolean extract(ServerLevel level,BlockPos pos,ResourceLocation resource){return GeoResourceData.get(level).extractOne(level,pos,GeoResources.require(resource));}
    private GeoApi(){}
}

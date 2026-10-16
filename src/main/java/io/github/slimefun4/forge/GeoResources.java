package io.github.slimefun4.forge;

import com.google.gson.Gson;
import io.github.slimefun4.forge.api.GeoApi;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.dimension.*;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Complete IDs replace the fixed enum. Core and addon resources share every live consumer. */
@Mod.EventBusSubscriber(modid="slimefun")
public final class GeoResources {
    private static final Map<ResourceLocation,GeoResources> RESOURCES=new LinkedHashMap<>();
    public static final GeoResources OIL=core("oil","bucket_of_oil",false,8,10,0,"oil_v1.18"),
        URANIUM=core("uranium","small_uranium",true,2,4,0,"uranium_v1.18"),
        SALT=core("salt","salt",true,18,6,8,"salt_v1.18"),NETHER_ICE=core("nether_ice","nether_ice",true,6,0,32,"nether_ice_v1.16");
    public final ResourceLocation id;
    private final GeoApi.Resource definition;private final boolean core;
    private GeoResources(GeoApi.Resource definition,boolean core){this.id=definition.id();this.definition=definition;this.core=core;}
    private record BiomeSupply(int value,List<String> biomes){}
    private static GeoResources core(String path,String output,boolean mineable,int deviation,int overworld,int nether,String table){
        var biomes=new LinkedHashMap<ResourceKey<net.minecraft.world.level.biome.Biome>,Integer>();
        try(var input=Objects.requireNonNull(GeoResources.class.getResourceAsStream("/slimefun/geo/"+table+".json"));var reader=new InputStreamReader(input,StandardCharsets.UTF_8)){
            for(var group:new Gson().fromJson(reader,BiomeSupply[].class))for(var biome:group.biomes())biomes.put(ResourceKey.create(Registries.BIOME,new ResourceLocation(biome)),group.value());
        }catch(Exception error){throw new IllegalStateException("Invalid GEO biome table "+table,error);}
        var resource=new GeoResources(new GeoApi.Resource(SlimefunForge.key(path),Component.translatable("geo.slimefun."+path),SlimefunForge.key(output),mineable,deviation,Map.of(BuiltinDimensionTypes.OVERWORLD,overworld,BuiltinDimensionTypes.OVERWORLD_CAVES,overworld,BuiltinDimensionTypes.NETHER,nether),biomes,Map.of()),true);
        RESOURCES.put(resource.id,resource);return resource;
    }
    public static synchronized void register(GeoApi.Resource definition){AddonContent.open();AddonContent.owned(definition.id());if(RESOURCES.putIfAbsent(definition.id(),new GeoResources(definition,false))!=null)throw new IllegalArgumentException("Duplicate GEO resource "+definition.id());}
    public static Map<ResourceLocation,GeoResources> resources(){return Collections.unmodifiableMap(new LinkedHashMap<>(RESOURCES));}
    public static GeoResources require(ResourceLocation id){var resource=RESOURCES.get(id);if(resource==null)throw new IllegalArgumentException("Unknown GEO resource "+id);return resource;}
    public static List<GeoResources> mineableResources(){var result=new ArrayList<>(List.of(URANIUM,NETHER_ICE,SALT));RESOURCES.values().stream().filter(r->!r.core&&r.mineable()).forEach(result::add);return List.copyOf(result);}
    public boolean mineable(){return definition.mineable();}
    public Component name(){return definition.name();}
    public ItemStack output(){return new ItemStack(ForgeRegistries.ITEMS.getValue(definition.output()));}
    static void freeze(){for(var resource:RESOURCES.values())AddonContent.item(resource.definition.output());}
    @SubscribeEvent public static void tags(TagsUpdatedEvent event){
        var access=event.getRegistryAccess();var biomes=access.registryOrThrow(Registries.BIOME);var dimensions=access.registryOrThrow(Registries.DIMENSION_TYPE);
        for(var resource:RESOURCES.values()){
            for(var key:resource.definition.biomes().keySet())if(!biomes.containsKey(key.location()))throw new IllegalArgumentException("Unknown GEO biome "+key.location()+" for "+resource.id);
            for(var key:resource.definition.environments().keySet())if(!dimensions.containsKey(key.location()))throw new IllegalArgumentException("Unknown GEO dimension type "+key.location()+" for "+resource.id);
            for(var tag:resource.definition.tags().keySet())if(biomes.getTag(tag).isEmpty())throw new IllegalArgumentException("Missing GEO biome tag "+tag.location()+" for "+resource.id);
        }
    }
    public int defaultSupply(ServerLevel level,BlockPos sample){
        var type=level.dimensionTypeRegistration().unwrapKey().orElseThrow();int base=definition.environments().getOrDefault(type,0);if(core&&base==0)return 0;
        var biome=level.getBiome(sample);var exact=biome.unwrapKey().map(definition.biomes()::get).orElse(null);if(exact!=null)return exact;
        for(var entry:definition.tags().entrySet().stream().sorted(Comparator.comparing(e->e.getKey().location())).toList())if(biome.is(entry.getKey()))return entry.getValue();return base;
    }
    public int generate(ServerLevel level,BlockPos sample){int base=defaultSupply(level,sample),deviation=definition.deviation();return NetworkRules.reserve(base,deviation,base==0||deviation==0?0:level.random.nextInt(deviation));}
}

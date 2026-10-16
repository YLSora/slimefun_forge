package io.github.slimefun4.forge;

import java.util.*;
import io.github.slimefun4.forge.api.SlimefunAddonApi.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

/** Internal adapter between the supported addon API and the original catalog. Not an extension point. */
public final class AddonContent {
    static final Map<ResourceLocation,Category> CATEGORIES=new LinkedHashMap<>();
    static final Map<ResourceLocation,Research> RESEARCHES=new LinkedHashMap<>();
    static final Map<ResourceLocation,Entry> ENTRIES=new LinkedHashMap<>();
    private static boolean frozen;
    private static Content content;
    static void open(){if(frozen)throw new IllegalStateException("Slimefun addon definitions are frozen after common setup");}
    static void owned(ResourceLocation id){
        Objects.requireNonNull(id,"Addon definition ID");
        if(Set.of("minecraft","slimefun").contains(id.getNamespace())||ModList.get().getModContainerById(id.getNamespace()).isEmpty())throw new IllegalArgumentException("Invalid addon owner for "+id);
    }
    private static void title(String title){if(title==null||title.isBlank())throw new IllegalArgumentException("Addon translation key is blank");}
    private static <T> void put(Map<ResourceLocation,T> map,ResourceLocation id,T value){if(map.putIfAbsent(id,value)!=null)throw new IllegalArgumentException("Duplicate Slimefun addon ID "+id);}
    public static synchronized void register(Category value){open();Objects.requireNonNull(value);owned(value.id());title(value.title());Objects.requireNonNull(value.icon(),"Category icon "+value.id());put(CATEGORIES,value.id(),value);}
    public static synchronized void register(Research value){open();Objects.requireNonNull(value);owned(value.id());title(value.title());if(value.cost()<0)throw new IllegalArgumentException("Negative research cost for "+value.id());put(RESEARCHES,value.id(),value);}
    public static synchronized void register(Entry value){open();Objects.requireNonNull(value);owned(value.item());Objects.requireNonNull(value.category(),"Entry category "+value.item());put(ENTRIES,value.item(),value);}
    public static synchronized Content content(){if(!frozen)throw new IllegalStateException("Slimefun addon definitions are not finalized yet");return content;}
    static void item(ResourceLocation id){if(!ForgeRegistries.ITEMS.containsKey(id)||ForgeRegistries.ITEMS.getValue(id)==Items.AIR)throw new IllegalArgumentException("Missing addon item "+id);}
    static synchronized void freeze(){
        open();
        for(var category:CATEGORIES.values())item(category.icon());
        for(var entry:ENTRIES.values()){
            item(entry.item());
            if(!CATEGORIES.containsKey(entry.category()))throw new IllegalArgumentException("Missing category "+entry.category()+" for "+entry.item());
            if(!AddonGuides.shared(entry.category(),entry.item()))throw new IllegalArgumentException("Private addon category "+entry.category()+" for "+entry.item());
            if(entry.research()!=null&&!RESEARCHES.containsKey(entry.research()))throw new IllegalArgumentException("Missing research "+entry.research()+" for "+entry.item());
        }
        ResearchData.addons();
        AddonItems.validate();
        AddonMachines.freeze();
        AddonAndroids.freeze();
        GeoResources.freeze();
        AddonGuides.freeze();
        GuideContent.addons();
        content=new Content(CATEGORIES,RESEARCHES,ENTRIES);
        frozen=true;
        net.minecraftforge.fml.ModLoader.get().postEvent(new io.github.slimefun4.forge.api.AddonRegistryFinalizedEvent(content));
    }
    static String researchKey(Catalog.Research research){var value=RESEARCHES.get(new ResourceLocation(research.id()));return value==null?"research.slimefun."+research.id():value.title();}
    static String commandId(Catalog.Research research){return research.id().contains(":")?research.id():"slimefun:"+research.id();}
    private AddonContent(){}
}

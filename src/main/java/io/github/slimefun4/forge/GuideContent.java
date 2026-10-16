package io.github.slimefun4.forge;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;

/** Immutable guide metadata; live recipes and research stay on the owning server. */
final class GuideContent {
    static List<String> LOCALES=List.of(read("/slimefun/guide_locales.json",String[].class));
    record Group(String id,String key,String icon,int tier,List<String> parents,int month) {}
    static List<Group> GROUPS=List.of(read("/slimefun/guide_groups.json",Group[].class));
    static final com.google.gson.JsonObject WIKI=read("/slimefun/guide_wiki.json",com.google.gson.JsonObject.class);
    static final com.google.gson.JsonObject RECIPES=read("/slimefun/guide_recipes.json",com.google.gson.JsonObject.class);
    static final List<String> MANUAL=List.of("enhanced_crafting_table","grind_stone","armor_forge","ore_crusher","compressor","smeltery","ore_washer","pressure_chamber","magic_workbench","industrial_miner","advanced_industrial_miner","juicer","makeshift_smeltery","table_saw","automated_panning_machine");
    static List<String> ENTRIES=java.util.stream.Stream.of(Catalog.ITEMS.stream().filter(e->!Set.of("RestoredBackpack","HiddenItem").contains(e.implementation())).map(Catalog.Entry::id),MANUAL.stream(),Catalog.VANILLA.stream().map(Catalog.Vanilla::id)).flatMap(s->s).toList();
    static void addons(){
        GROUPS=java.util.stream.Stream.concat(GROUPS.stream(),AddonContent.CATEGORIES.values().stream().map(c->new Group(c.id().toString(),c.title(),c.icon().toString(),0,List.of(),0))).sorted(Comparator.comparingInt(AddonGuides::order)).toList();
        ENTRIES=java.util.stream.Stream.concat(ENTRIES.stream(),AddonContent.ENTRIES.keySet().stream().map(Object::toString)).toList();
        LANGUAGES.clear();
    }
    private static final Map<String,Map<String,String>> LANGUAGES=new HashMap<>();
    static <T> T read(String path,Class<T> type) {
        try(var input=Objects.requireNonNull(GuideContent.class.getResourceAsStream(path));var reader=new InputStreamReader(input,StandardCharsets.UTF_8)){return new Gson().fromJson(reader,type);}
        catch(Exception e){throw new IllegalStateException("Invalid guide resource "+path,e);}
    }
    static Group group(String id){return GROUPS.stream().filter(g->g.id().equals(id)).findFirst().orElseThrow();}
    static String groupOf(String id){var addon=AddonContent.ENTRIES.get(new ResourceLocation(id));if(addon!=null)return addon.category().toString();var vanilla=Catalog.vanilla(id);if(vanilla!=null)return vanilla.group();var entry=Catalog.entry(id);return entry==null?"basicMachines":entry.group();}
    static boolean visible(Group group){return group.month()==0||java.time.LocalDate.now().getMonthValue()==group.month();}
    static boolean accessible(ServerPlayer player,Group group){
        return group.parents().stream().allMatch(parent->ENTRIES.stream().filter(MachineClock::enabled).filter(id->groupOf(id).equals(parent)).allMatch(id->ResearchData.get(player).knows(player,ResearchData.research(id))));
    }
    static Component name(String id){
        if(id.contains(":")||Catalog.vanilla(id)!=null)return icon(id).getHoverName();
        return Component.translatable("item.slimefun."+id);
    }
    static Component category(Group group){return Component.translatable(group.id().contains(":")?group.key():"category.slimefun."+group.key());}
    static ItemStack icon(String id){
        var vanilla=Catalog.vanilla(id);if(vanilla!=null)return new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("minecraft",vanilla.material())));
        if(id.contains(":"))return new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(id)));
        if(id.equals("ender_talisman"))return new ItemStack(Items.EMERALD);
        if(!MANUAL.contains(id))return Catalog.stack(SlimefunForge.item(id),1);
        Item item=switch(id){
            case "grind_stone","ore_washer"->Items.OAK_FENCE;case "armor_forge"->Items.ANVIL;
            case "ore_crusher"->Items.IRON_BARS;case "compressor"->Items.PISTON;case "smeltery"->Items.NETHER_BRICKS;
            case "makeshift_smeltery"->Items.BRICKS;case "table_saw"->Items.STONECUTTER;case "automated_panning_machine"->Items.OAK_TRAPDOOR;
            case "juicer"->Items.GLASS;case "pressure_chamber"->Items.CAULDRON;case "industrial_miner","advanced_industrial_miner"->Items.BLAST_FURNACE;default->Items.CRAFTING_TABLE;
        };
        return new ItemStack(item).setHoverName(name(id));
    }
    @SuppressWarnings("unchecked")
    static Map<String,String> language(String locale){
        var cached=LANGUAGES.get(locale);if(cached!=null)return cached;
        if(!LOCALES.contains(locale))locale="en_us";
        final String selected=locale;
        return LANGUAGES.computeIfAbsent(locale,key->{
            var result=new HashMap<String,String>((Map<String,String>)read("/assets/slimefun/lang/en_us.json",Map.class));
            if(!selected.equals("en_us")&&GuideContent.class.getResource("/assets/slimefun/lang/"+selected+".json")!=null)result.putAll((Map<String,String>)read("/assets/slimefun/lang/"+selected+".json",Map.class));
            var namespaces=AddonContent.CATEGORIES.keySet().stream().map(ResourceLocation::getNamespace).collect(java.util.stream.Collectors.toSet());
            namespaces.addAll(AddonContent.ENTRIES.keySet().stream().map(ResourceLocation::getNamespace).toList());
            namespaces.addAll(AddonContent.RESEARCHES.keySet().stream().map(ResourceLocation::getNamespace).toList());
            namespaces.addAll(AddonGuides.LANGUAGE_OWNERS);
            for(String namespace:namespaces)for(String language:selected.equals("en_us")?List.of("en_us"):List.of("en_us",selected)){
                result.putAll(translations(namespace,language));
            }
            return Map.copyOf(result);
        });
    }
    static Map<String,String> translations(String namespace,String language){
        var path=net.minecraftforge.fml.ModList.get().getModFileById(namespace).getFile().findResource("assets",namespace,"lang",language+".json");
        if(!java.nio.file.Files.exists(path))return Map.of();
        try(var reader=java.nio.file.Files.newBufferedReader(path,StandardCharsets.UTF_8)){
            var result=new LinkedHashMap<String,String>();var entries=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            for(var entry:entries.entrySet()){
                String[] parts=entry.getKey().split("\\.",3);if(parts.length<2||!parts[1].equals(namespace))continue;
                if(!entry.getValue().isJsonPrimitive()||!entry.getValue().getAsJsonPrimitive().isString())throw new IllegalArgumentException("Translation is not a string: "+entry.getKey());
                result.put(entry.getKey(),entry.getValue().getAsString());
            }
            return Map.copyOf(result);
        }catch(Exception e){throw new IllegalStateException("Invalid addon language "+path,e);}
    }
    static boolean matches(Map<String,String> language,String id,String query){
        var entry=Catalog.entry(id);var research=ResearchData.research(id);
        String translated=research==null?"":language.getOrDefault(AddonContent.researchKey(research),research.name());
        String itemKey=id.contains(":")?icon(id).getItem().getDescriptionId():"item.slimefun."+id;
        return (id+" "+(entry==null?name(id).getString():entry.name())+" "+language.getOrDefault(itemKey,"")+" "+translated).toLowerCase(Locale.ROOT).contains(query);
    }
    static Component localize(ServerPlayer player,Component value){
        return localize(Guide.locale(player),value);
    }
    static boolean open(ServerPlayer player,net.minecraft.world.MenuProvider provider){return player.openMenu(new net.minecraft.world.SimpleMenuProvider(provider::createMenu,localize(player,provider.getDisplayName()))).isPresent();}
    static ItemStack localize(ServerPlayer player,ItemStack value){
        if(value.isEmpty())return ItemStack.EMPTY;
        var result=value.copy().setHoverName(localize(player,value.getHoverName()).copy().withStyle(s->s.withItalic(false)));var data=result.getTagElement("display");
        if(data!=null&&data.contains("Lore")){var lines=data.getList("Lore",8);for(int i=0;i<lines.size();i++)lines.set(i,net.minecraft.nbt.StringTag.valueOf(Component.Serializer.toJson(localize(player,Component.Serializer.fromJson(lines.getString(i))).copy().withStyle(s->s.withItalic(false)))));}
        if(!result.getOrCreateTag().getBoolean("slimefun_preview_lore")){
            var lines=new net.minecraft.nbt.ListTag();for(var line:ItemText.lore(value,Guide.locale(player)))lines.add(net.minecraft.nbt.StringTag.valueOf(Component.Serializer.toJson(line)));
            if(data!=null&&data.contains("Lore"))lines.addAll(data.getList("Lore",8));
            if(!lines.isEmpty())result.getOrCreateTagElement("display").put("Lore",lines);
            result.getOrCreateTag().putBoolean("slimefun_preview_lore",true);
        }
        return result;
    }
    static Component localize(String locale,Component value){
        var baseStyle=value.getStyle().applyTo(net.minecraft.network.chat.Style.EMPTY.withItalic(false));var result=value.plainCopy().setStyle(baseStyle);
        String format=null;Object[] args=new Object[0];
        if(value.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tr){
            var translations=language(locale);format=translations.getOrDefault(tr.getKey(),language("en_us").get(tr.getKey()));
            args=Arrays.stream(tr.getArgs()).map(a->a instanceof Component c?localize(locale,c):a).toArray();
            if(format==null)result=Component.translatableWithFallback(tr.getKey(),tr.getFallback(),args).setStyle(baseStyle);
        }else if(value.getContents() instanceof net.minecraft.network.chat.contents.LiteralContents literal && Messages.COLORS.matcher(literal.text()).find())format=literal.text().replace("%","%%");
        if(format!=null){
                // Imported RC-37 formats use positional arguments so colors may split a translated sentence.
                result=Component.empty().setStyle(baseStyle);
                var codes=Messages.COLORS.matcher(format);var style=baseStyle;int start=0;
                while(codes.find()){
                    for(var part:Component.translatableWithFallback("",format.substring(start,codes.start()),args).setStyle(style).toFlatList())result.append(part);
                    style=Messages.color(style,codes.group());start=codes.end();
                }
                for(var part:Component.translatableWithFallback("",format.substring(start),args).setStyle(style).toFlatList())result.append(part);
        }
        for(var sibling:value.getSiblings())result.append(localize(locale,sibling));
        return result;
    }
    private GuideContent() {}
}

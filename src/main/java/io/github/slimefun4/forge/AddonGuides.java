package io.github.slimefun4.forge;

import io.github.slimefun4.forge.api.GuideApi.*;
import java.time.Month;
import java.util.*;
import java.util.function.Function;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Internal bridge. Definitions are fixed; callbacks return a fresh view of current server state. */
public final class AddonGuides {
    private static final CategoryOptions DEFAULT=new CategoryOptions(null,0);
    private static final Map<ResourceLocation,CategoryOptions> CATEGORIES=new LinkedHashMap<>();
    private static final Map<ResourceLocation,ItemOptions> ITEMS=new LinkedHashMap<>();
    private static final ItemOptions DEFAULT_ITEM=new ItemOptions(false,null);
    static final Map<ResourceLocation,Setting> SETTINGS=new LinkedHashMap<>();
    private static final Map<ResourceLocation,RecipeType> TYPES=new LinkedHashMap<>();
    private static final Map<ResourceLocation,Function<Context,List<DisplayRecipe>>> RECIPES=new LinkedHashMap<>();
    private static final Map<String,Language> LANGUAGES=new LinkedHashMap<>();
    static final Set<String> LANGUAGE_OWNERS=new LinkedHashSet<>();
    private static Map<String,List<String>> paths=Map.of();
    private static <T> void register(Map<ResourceLocation,T> map,ResourceLocation id,T value){
        AddonContent.open();AddonContent.owned(id);Objects.requireNonNull(value);
        if(map.putIfAbsent(id,value)!=null)throw new IllegalArgumentException("Duplicate guide definition "+id);
    }
    public static synchronized void category(ResourceLocation id,CategoryOptions options){register(CATEGORIES,id,options);}
    public static synchronized void item(ResourceLocation id,ItemOptions options){register(ITEMS,id,options);}
    public static ItemOptions itemOptions(ResourceLocation id){AddonContent.content();if(!AddonContent.ENTRIES.containsKey(id))throw new IllegalArgumentException("Unknown addon guide entry "+id);return ITEMS.getOrDefault(id,DEFAULT_ITEM);}
    static boolean itemVisible(String id){return !ITEMS.getOrDefault(new ResourceLocation(id),DEFAULT_ITEM).hidden();}
    static String wiki(String id){return ITEMS.getOrDefault(new ResourceLocation(id),DEFAULT_ITEM).wiki();}
    public static synchronized void setting(ResourceLocation id,Setting setting){register(SETTINGS,id,setting);}
    public static synchronized void recipeType(RecipeType type){Objects.requireNonNull(type);if(type.title()==null||type.title().isBlank())throw new IllegalArgumentException("Blank recipe title "+type.id());Objects.requireNonNull(type.icon());register(TYPES,type.id(),type);}
    public static synchronized void recipes(ResourceLocation item,Function<Context,List<DisplayRecipe>> provider){register(RECIPES,item,provider);}
    public static synchronized void language(String owner,Language language){
        AddonContent.open();AddonContent.owned(new ResourceLocation(owner,"language"));Objects.requireNonNull(language);
        var old=LANGUAGES.putIfAbsent(language.code(),language);
        if(old!=null&&!old.equals(language))throw new IllegalArgumentException("Conflicting guide language "+language.code());
        LANGUAGE_OWNERS.add(owner);
    }
    static CategoryOptions options(String id){return id.contains(":")?CATEGORIES.getOrDefault(new ResourceLocation(id),DEFAULT):DEFAULT;}
    static boolean shared(ResourceLocation category,ResourceLocation entry){return GuideHierarchy.shared(category.getNamespace(),entry.getNamespace(),options(category.toString()).sharedWith());}
    static void freeze(){
        for(var entry:CATEGORIES.entrySet()){
            var id=entry.getKey();var rules=entry.getValue();
            if(!AddonContent.CATEGORIES.containsKey(id))throw new IllegalArgumentException("Missing guide category "+id);
            if(rules.parent()!=null&&(!AddonContent.CATEGORIES.containsKey(rules.parent())||!shared(rules.parent(),id)))throw new IllegalArgumentException("Missing or private guide parent "+rules.parent()+" for "+id);
            for(var research:rules.researches())if(ResearchData.all().stream().noneMatch(r->AddonContent.commandId(r).equals(research.toString())))throw new IllegalArgumentException("Unknown category research "+research+" for "+id);
            for(String owner:rules.sharedWith())AddonContent.owned(new ResourceLocation(owner,"shared_category"));
        }
        var parents=new LinkedHashMap<String,String>();for(var id:AddonContent.CATEGORIES.keySet()){var parent=options(id.toString()).parent();parents.put(id.toString(),parent==null?"":parent.toString());}
        paths=GuideHierarchy.paths(parents);
        for(var id:RECIPES.keySet())if(!AddonContent.ENTRIES.containsKey(id))throw new IllegalArgumentException("Missing display entry "+id);
        for(var id:ITEMS.keySet())if(!AddonContent.ENTRIES.containsKey(id))throw new IllegalArgumentException("Missing guide item options entry "+id);
        for(var type:TYPES.values()){AddonContent.item(type.icon());if(type.machine()!=null)AddonContent.item(type.machine());}
        var locales=new LinkedHashSet<>(GuideContent.LOCALES);locales.addAll(LANGUAGES.keySet());GuideContent.LOCALES=List.copyOf(locales);
    }
    static String parent(String id){var parent=options(id).parent();return parent==null?null:parent.toString();}
    static int order(GuideContent.Group group){return group.id().contains(":")?options(group.id()).order():group.tier();}
    static Context context(ServerPlayer player,boolean cheat){return new Context(player,cheat?Mode.CREATIVE:Mode.SURVIVAL);}
    static boolean visible(Context context,String id){return allowed(context,id,true);}
    static boolean accessible(Context context,String id){return allowed(context,id,false);}
    private static boolean allowed(Context context,String id,boolean visibility){
        for(String current:paths.getOrDefault(id,List.of(id))){
            var group=GuideContent.group(current);
            if(!GuideContent.visible(group))return false;
            if(!AddonSettings.ownerEnabled(current))return false;
            var options=options(current);
            if(!options.months().isEmpty()&&!options.months().contains(Month.from(java.time.LocalDate.now())))return false;
            if(visibility&&!options.behavior().visible(context))return false;
            if(visibility)continue;
            if(!options.behavior().accessible(context))return false;
            if(context.mode()==Mode.CREATIVE)continue;
            if(!GuideContent.accessible(context.player(),group))return false;
            for(var required:options.researches()){
                var research=ResearchData.all().stream().filter(r->AddonContent.commandId(r).equals(required.toString())).findFirst().orElseThrow();
                if(!ResearchData.get(context.player()).knows(context.player(),research))return false;
            }
        }
        return true;
    }
    static List<String> entries(Context context,String category){
        var registered=AddonContent.ENTRIES.values().stream().filter(e->e.category().toString().equals(category)).map(e->e.item()).toList();
        if(!category.contains(":"))return GuideContent.ENTRIES.stream().filter(id->GuideContent.groupOf(id).equals(category)).toList();
        var values=List.copyOf(options(category).behavior().entries(context,registered));
        var unique=new LinkedHashSet<ResourceLocation>();for(var id:values)if(!registered.contains(id)||!unique.add(id))throw new IllegalArgumentException("Invalid dynamic guide entry "+id+" in "+category);
        return values.stream().map(Object::toString).filter(AddonGuides::itemVisible).toList();
    }
    static List<DisplayRecipe> descriptions(Context context,String item){
        var provider=RECIPES.get(new ResourceLocation(item));if(provider==null)return List.of();
        var recipes=List.copyOf(provider.apply(context));
        for(var recipe:recipes)if(!TYPES.containsKey(recipe.type()))throw new IllegalArgumentException("Unknown display recipe type "+recipe.type()+" for "+item);
        return recipes;
    }
    static RecipeType type(ResourceLocation id){return Objects.requireNonNull(TYPES.get(id),"Display type "+id);}
    public static boolean canOpen(Context context,ResourceLocation category){
        check(context.player());if(!AddonContent.content().categories().containsKey(category))throw new IllegalArgumentException("Unknown addon category "+category);
        return Guide.allowed(context.player(),context.mode())&&visible(context,category.toString())&&accessible(context,category.toString());
    }
    public static List<ResourceLocation> queryEntries(Context context,ResourceLocation category){
        if(!canOpen(context,category))return List.of();
        return entries(context,category.toString()).stream().filter(MachineClock::enabled).filter(id->ItemAccess.enabled(context.player().level(),id)).map(ResourceLocation::new).toList();
    }
    public static Map<ResourceLocation,RecipeType> recipeTypes(){AddonContent.content();return Map.copyOf(TYPES);}
    public static List<DisplayRecipe> queryRecipes(Context context,ResourceLocation item){
        check(context.player());if(!AddonContent.content().entries().containsKey(item))throw new IllegalArgumentException("Unknown addon entry "+item);
        var entry=AddonContent.ENTRIES.get(item);if(!queryEntries(context,entry.category()).contains(item))return List.of();
        return descriptions(context,item.toString());
    }
    public static Map<String,Language> languages(){
        AddonContent.content();var result=new LinkedHashMap<String,Language>();
        for(String code:GuideContent.LOCALES){var locale=Locale.forLanguageTag(code.replace('_','-'));result.put(code,new Language(code,locale.getDisplayName(locale),Set.of("ar","fa","he","ur").contains(locale.getLanguage())));}
        result.putAll(LANGUAGES);return Collections.unmodifiableMap(result);
    }
    public static TranslationCoverage translationCoverage(String owner,String language){
        AddonContent.content();if(!GuideContent.LOCALES.contains(language)||net.minecraftforge.fml.ModList.get().getModContainerById(owner).isEmpty())throw new IllegalArgumentException("Unknown language/owner "+owner+"/"+language);
        var english=GuideContent.translations(owner,"en_us");var selected=GuideContent.translations(owner,language);
        return new TranslationCoverage((int)english.keySet().stream().filter(selected::containsKey).count(),english.size());
    }
    static void check(ServerPlayer player){if(!player.server.isSameThread())throw new IllegalStateException("Guide operation requires the owning server thread");}
    public static String locale(ServerPlayer player){check(player);return Guide.locale(player);}
    public static void selectLanguage(ServerPlayer player,String code){
        check(player);if(!player.server.isDedicatedServer()||!Guide.TRANSLATIONS.get())throw new IllegalStateException("Guide language follows server/client policy");
        if(!code.isEmpty()&&!GuideContent.LOCALES.contains(code))throw new IllegalArgumentException("Unknown guide language "+code);
        Guide.observeLanguage(player);if(code.isEmpty())Guide.preferences(player).remove("language");else Guide.preferences(player).putString("language",code);Guide.observeLanguage(player);refresh(player);
    }
    public static Component localize(ServerPlayer player,Component value){check(player);return GuideContent.localize(player,value);}
    public static ItemStack withLore(ItemStack value,List<Component> lore){
        var copy=value.copy();var lines=new ListTag();var display=copy.getOrCreateTagElement("display");if(display.contains("Lore"))lines.addAll(display.getList("Lore",8));
        for(var line:lore)lines.add(StringTag.valueOf(Component.Serializer.toJson(line)));display.put("Lore",lines);return copy;
    }
    public static boolean isGuide(ItemStack stack){return stack.is(SlimefunForge.GUIDE.get())||stack.is(SlimefunForge.CREATIVE_GUIDE.get());}
    public static void open(ServerPlayer player,Mode mode,ResourceLocation category,ResourceLocation item,String query,int page){check(player);GuideMenu.navigate(player,mode,category,item,query,page);}
    public static void settings(ServerPlayer player){check(player);GuideSettings.open(player,GuideSettings.heldBook(player));}
    public static void back(ServerPlayer player){check(player);if(player.containerMenu instanceof GuideMenu menu){menu.back();menu.refresh();}}
    public static void refresh(ServerPlayer player){check(player);if(player.containerMenu instanceof GuideMenu menu)menu.refresh();else if(player.containerMenu instanceof GuideSettings menu)menu.refresh();}
    private AddonGuides(){}
}

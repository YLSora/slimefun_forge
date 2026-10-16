package io.github.slimefun4.forge.api;

import io.github.slimefun4.forge.AddonGuides;
import java.time.Month;
import java.util.*;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;

/** Guide definitions register in the addon constructor on both physical sides and freeze at common setup.
 * Live callbacks run synchronously on the player's server thread. Never retain a Context or player.
 */
public final class GuideApi {
    /** Hidden entries remain native items but cannot be listed, searched or opened in this guide.
     * Wiki is optional, absolute HTTP(S), and opens a chat link only after the player clicks its button.
     */
    public record ItemOptions(boolean hidden,String wiki) {
        public ItemOptions { wiki=io.github.slimefun4.forge.GuideLinks.wiki(wiki); }
    }
    public enum Mode { SURVIVAL, CREATIVE }
    public record Context(ServerPlayer player,Mode mode) {
        public Context { Objects.requireNonNull(player);Objects.requireNonNull(mode); }
    }
    /** Empty months means all year. Research IDs can reference core or addon research.
     * Only sharedWith owners may attach entries or child categories across addon namespaces.
     */
    public record CategoryOptions(ResourceLocation parent,int order,Set<ResourceLocation> researches,
                                  Set<Month> months,Set<String> sharedWith,CategoryBehavior behavior) {
        public CategoryOptions { researches=Set.copyOf(researches);months=Set.copyOf(months);sharedWith=Set.copyOf(sharedWith);Objects.requireNonNull(behavior); }
        public CategoryOptions(ResourceLocation parent,int order){this(parent,order,Set.of(),Set.of(),Set.of(),new CategoryBehavior(){});}
    }
    public interface CategoryBehavior {
        default boolean visible(Context context){return true;}
        default boolean accessible(Context context){return true;}
        /** Return a fresh selection of registered entries belonging to this category, in display order. */
        default List<ResourceLocation> entries(Context context,List<ResourceLocation> registered){return registered;}
        /** Null uses the core list. Custom menus must validate their native transactions and expose back. */
        default MenuProvider page(Context context,Runnable back){return null;}
    }
    /** Button is 0 (left) or 1 (right); shift is native QUICK_MOVE, never a client supplied action ID. */
    public record Click(int button,boolean shift) {}
    public interface Setting {
        default boolean visible(Context context){return true;}
        ItemStack icon(Context context);
        Component selection(Context context);
        void click(Context context,Click click);
    }
    /** Display identity is separate from an executable native RecipeType. Machine is an optional item ID. */
    public record RecipeType(ResourceLocation id,String title,ResourceLocation icon,ResourceLocation machine) {}
    /** Read-only description: never registered in RecipeManager. At most nine inputs and nine outputs. */
    public record DisplayRecipe(ResourceLocation type,Component section,List<ItemStack> inputs,List<ItemStack> outputs,List<Component> notes) {
        public DisplayRecipe {
            Objects.requireNonNull(type);section=Objects.requireNonNull(section).copy();
            if(inputs.size()>9||outputs.isEmpty()||outputs.size()>9||outputs.stream().anyMatch(ItemStack::isEmpty))throw new IllegalArgumentException("Guide description requires 0..9 inputs and 1..9 nonempty outputs");
            inputs=inputs.stream().map(ItemStack::copy).toList();outputs=outputs.stream().map(ItemStack::copy).toList();notes=notes.stream().map(Component::copy).map(c->(Component)c).toList();
        }
        @Override public Component section(){return section.copy();}
        @Override public List<ItemStack> inputs(){return inputs.stream().map(ItemStack::copy).toList();}
        @Override public List<ItemStack> outputs(){return outputs.stream().map(ItemStack::copy).toList();}
        @Override public List<Component> notes(){return notes.stream().map(Component::copy).map(c->(Component)c).toList();}
    }
    public record Language(String code,String name,boolean rightToLeft) {
        public Language { if(code==null||!code.matches("[a-z]{2,8}_[a-z0-9]{2,8}")||name==null||name.isBlank())throw new IllegalArgumentException("Invalid guide language "+code); }
    }
    /** Counts actual translated English keys, before English fallback; zero total means no English dictionary. */
    public record TranslationCoverage(int translated,int total) {}
    public static void registerCategory(ResourceLocation id,CategoryOptions options){AddonGuides.category(id,options);}
    public static void registerItem(ResourceLocation item,ItemOptions options){AddonGuides.item(item,options);}
    /** Frozen metadata query, including hidden items; requires a registered addon entry. */
    public static ItemOptions itemOptions(ResourceLocation item){return AddonGuides.itemOptions(item);}
    public static void registerSetting(ResourceLocation id,Setting setting){AddonGuides.setting(id,setting);}
    public static void registerRecipeType(RecipeType type){AddonGuides.recipeType(type);}
    public static void registerRecipes(ResourceLocation item,Function<Context,List<DisplayRecipe>> provider){AddonGuides.recipes(item,provider);}
    /** Reads assets/owner/lang/code.json, accepts only keys whose second segment is owner. */
    public static void registerLanguage(String owner,Language language){AddonGuides.language(owner,language);}
    /** Current dynamic members, after ancestor, mode and visibility checks. Unknown addon IDs fail. */
    public static List<ResourceLocation> entries(Context context,ResourceLocation category){return AddonGuides.queryEntries(context,category);}
    public static boolean canOpen(Context context,ResourceLocation category){return AddonGuides.canOpen(context,category);}
    public static Map<ResourceLocation,RecipeType> recipeTypes(){return AddonGuides.recipeTypes();}
    public static List<DisplayRecipe> recipes(Context context,ResourceLocation item){return AddonGuides.queryRecipes(context,item);}
    public static Map<String,Language> languages(){return AddonGuides.languages();}
    public static TranslationCoverage translationCoverage(String owner,String language){return AddonGuides.translationCoverage(owner,language);}
    public static String language(ServerPlayer player){return AddonGuides.locale(player);}
    /** Dedicated servers use the preference; integrated servers follow the native client language. */
    public static void language(ServerPlayer player,String code){AddonGuides.selectLanguage(player,code);}
    public static Component localize(ServerPlayer player,Component text){return AddonGuides.localize(player,text);}
    public static ItemStack withLore(ItemStack stack,List<Component> lore){return AddonGuides.withLore(stack,lore);}
    public static boolean isGuide(ItemStack stack){return AddonGuides.isGuide(stack);}
    /** Zero-based page; CREATIVE mode always rechecks the native cheat permission. */
    public static void home(ServerPlayer player,Mode mode){AddonGuides.open(player,mode,null,null,null,0);}
    public static void category(ServerPlayer player,Mode mode,ResourceLocation category,int page){AddonGuides.open(player,mode,category,null,null,page);}
    public static void item(ServerPlayer player,Mode mode,ResourceLocation item){AddonGuides.open(player,mode,null,item,null,0);}
    public static void search(ServerPlayer player,Mode mode,String query){AddonGuides.open(player,mode,null,null,query,0);}
    public static void settings(ServerPlayer player){AddonGuides.settings(player);}
    public static void back(ServerPlayer player){AddonGuides.back(player);}
    public static void refresh(ServerPlayer player){AddonGuides.refresh(player);}
    private GuideApi(){}
}

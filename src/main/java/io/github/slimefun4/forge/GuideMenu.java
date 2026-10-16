package io.github.slimefun4.forge;

import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import io.github.slimefun4.forge.api.GuideApi;

/** RC-37 chest guide layout, using native container packets and server-owned navigation. */
public final class GuideMenu extends ChestMenu {
    private static final int[] GRID={3,4,5,12,13,14,21,22,23};
    private record View(String group,String query,String selected,int page,int recipePage) {}
    private final ServerPlayer owner;
    private final String locale;
    private final String title;
    private final SimpleContainer display;
    private final Map<Integer,Runnable> actions=new HashMap<>();
    private final Map<Integer,String> listedItems=new HashMap<>();
    private final Deque<View> history=new ArrayDeque<>();
    List<net.minecraft.nbt.CompoundTag> profileHistory(){return history.stream().map(view->{var tag=new net.minecraft.nbt.CompoundTag();if(view.group()!=null)tag.putString("category",view.group());if(view.query()!=null)tag.putString("query",view.query());if(view.selected()!=null)tag.putString("item",view.selected());tag.putInt("page",view.page());tag.putInt("recipe_page",view.recipePage());return tag;}).toList();}
    private String group,query,selected;
    private int page,recipePage;
    private boolean cheat,alternatives;
    private int choice;
    private long lastCycle;

    public static void open(ServerPlayer player){navigate(player,GuideApi.Mode.SURVIVAL,null,null,null,0);}
    public static void openCreative(ServerPlayer player){navigate(player,GuideApi.Mode.CREATIVE,null,null,null,0);}
    public static void search(ServerPlayer player,String query){search(player,query,null);}
    static void search(ServerPlayer player,String query,GuideMenu previous){
        if(query.length()>256)return;
        openView(player,previous==null?GuideApi.Mode.SURVIVAL:previous.mode(),new View(null,GuideInput.normalize(query),null,0,0),previous);
    }
    static void navigate(ServerPlayer player,GuideApi.Mode mode,ResourceLocation category,ResourceLocation item,String query,int page){
        AddonGuides.check(player);Objects.requireNonNull(mode);if(page<0)throw new IllegalArgumentException("Negative guide page");
        String group=null;
        if(category!=null)group=GuideContent.GROUPS.stream().map(GuideContent.Group::id).filter(id->id.equals(category.toString())||category.getNamespace().equals(SlimefunForge.ID)&&id.replaceAll("([a-z])([A-Z])","$1_$2").toLowerCase(Locale.ROOT).equals(category.getPath())).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown guide category "+category));
        String selected=item==null?null:item.getNamespace().equals(SlimefunForge.ID)?item.getPath():item.toString();
        if(selected!=null&&!GuideContent.ENTRIES.contains(selected)&&!selected.startsWith("minecraft:"))throw new IllegalArgumentException("Unknown guide entry "+item);
        if(item!=null&&!GuideContent.MANUAL.contains(selected)&&(!BuiltInRegistries.ITEM.containsKey(item)||BuiltInRegistries.ITEM.get(item)==Items.AIR))throw new IllegalArgumentException("Unknown guide item "+item);
        var previous=player.containerMenu instanceof GuideMenu menu&&menu.mode()==mode?menu:null;
        if(category==null&&item==null&&query==null)previous=null;
        openView(player,mode,new View(group,query==null?null:GuideInput.normalize(query),selected,page,0),previous);
    }
    private static void openView(ServerPlayer player,GuideApi.Mode mode,View view,GuideMenu previous){
        var context=new GuideApi.Context(player,mode);
        if(!Guide.allowed(player,mode))return;
        if(view.group()!=null&&(!AddonGuides.visible(context,view.group())||!AddonGuides.accessible(context,view.group())))return;
        if(view.selected()!=null&&!available(context,view.selected()))return;
        if(view.group()!=null&&custom(player,mode,view.group(),previous))return;
        var name=view.group()!=null?GuideContent.category(GuideContent.group(view.group())):Guide.text(player,view.query()==null?"title":"search_title",view.query()==null?"":view.query());
        Guide.openPage(player,mode,new SimpleMenuProvider((id,inv,p)->new GuideMenu(id,inv,player,view,previous,6,false,mode),name),()->(view.group()==null||AddonGuides.visible(context,view.group())&&AddonGuides.accessible(context,view.group()))&&(view.selected()==null||available(context,view.selected())));
    }
    private static boolean custom(ServerPlayer player,GuideApi.Mode mode,String category,GuideMenu previous){
        var context=new GuideApi.Context(player,mode);var window=new AbstractContainerMenu[1];
        Runnable back=()->{
            AddonGuides.check(player);if(window[0]==null||player.containerMenu!=window[0]||!Guide.allowed(player,mode))return;
            window[0]=null;
            if(previous==null)openView(player,mode,new View(null,null,null,0,0),null);
            else previous.resume();
        };
        var provider=AddonGuides.options(category).behavior().page(context,back);if(provider==null)return false;
        window[0]=Guide.openPage(player,mode,provider,()->AddonGuides.visible(context,category)&&AddonGuides.accessible(context,category));return true;
    }
    private GuideMenu(int id,Inventory inventory,ServerPlayer player,View initial,GuideMenu previous,int rows,boolean resize,GuideApi.Mode mode){
        super(rows==3?MenuType.GENERIC_9x3:rows==4?MenuType.GENERIC_9x4:MenuType.GENERIC_9x6,id,inventory,new SimpleContainer(54),rows);owner=player;locale=Guide.locale(player);display=(SimpleContainer)getContainer();cheat=mode==GuideApi.Mode.CREATIVE;restore(initial);
        if(previous!=null){history.addAll(previous.history);if(resize){choice=previous.choice;lastCycle=previous.lastCycle;}else history.push(previous.view());}
        title=title().getString();render();
    }
    private GuideApi.Mode mode(){return cheat?GuideApi.Mode.CREATIVE:GuideApi.Mode.SURVIVAL;}
    private GuideApi.Context context(){return new GuideApi.Context(owner,mode());}
    private static boolean available(GuideApi.Context context,String id){
        if(!AddonGuides.itemVisible(id)||!MachineClock.enabled(id)||!ItemAccess.enabled(context.player().level(),id))return false;
        if(id.startsWith("minecraft:")&&!GuideContent.ENTRIES.contains(id))return Guide.VANILLA.get();
        String category=GuideContent.groupOf(id);
        return AddonGuides.accessible(context,category)&&(Guide.HIDDEN_SEARCH.get()||AddonGuides.visible(context,category))&&AddonGuides.entries(context,category).contains(id);
    }
    void resume(){Guide.openPage(owner,mode(),new SimpleMenuProvider((id,inv,p)->new GuideMenu(id,inv,owner,view(),this,6,true,mode()),title()));}
    private void category(String id){if(!AddonGuides.visible(context(),id)||!AddonGuides.accessible(context(),id))return;if(custom(owner,mode(),id,this))return;history.push(view());group=id;query=selected=null;page=recipePage=0;}
    private Component tr(String key,Object...args){return Guide.text(owner,key,args);}
    private Component title(){return selected!=null?tr("title"):group!=null?GuideContent.localize(owner,GuideContent.category(GuideContent.group(group))):query!=null?tr("search_title",query):tr("title");}
    private void backButton(){button(0,Items.ENCHANTED_BOOK,"back",this::back);}
    private ItemStack locked(String id){return label(new ItemStack(Items.BARRIER),GuideContent.name(id),tr("locked").copy().withStyle(ChatFormatting.DARK_RED,ChatFormatting.BOLD),Component.empty(),tr("unlock_click").copy().withStyle(ChatFormatting.GREEN),Component.empty(),tr("unlock_cost",ResearchSettings.cost(ResearchData.research(id))));}
    private ItemStack label(ItemStack stack,Component name,Component...lore){
        var result=stack.copy().setHoverName(name);
        if(lore.length>0){var lines=new ListTag();for(var line:lore)lines.add(StringTag.valueOf(Component.Serializer.toJson(line)));result.getOrCreateTagElement("display").put("Lore",lines);}
        return result;
    }
    private void put(int slot,Item item,Component name,Runnable action,Component...lore){put(slot,label(new ItemStack(item),name,lore),action);}
    private void button(int slot,Item item,String key,Runnable action,Object...args){put(slot,MenuItems.label(owner,item,"guide."+key,args),action);}
    private void put(int slot,ItemStack item,Runnable action){
        display.setItem(slot,GuideContent.localize(owner,item));if(action!=null)actions.put(slot,action);
    }
    private View view(){return new View(group,query,selected,page,recipePage);}
    private void restore(View view){group=view.group();query=view.query();selected=view.selected();page=view.page();recipePage=view.recipePage();}
    void back(){if(history.isEmpty())home();else restore(history.pop());}
    private void home(){int rootPage=0;for(var old:history)if(old.group()==null&&old.query()==null&&old.selected()==null)rootPage=old.page();history.clear();group=query=selected=null;page=rootPage;recipePage=0;}
    private void item(String id){if(!available(context(),id)||id.startsWith("minecraft:")&&nativeRecipes(id).isEmpty())return;history.push(view());selected=id;recipePage=0;page=0;}
    private boolean known(String id){return ResearchData.get(owner).knows(owner,ResearchData.research(id));}

    public void refresh(){
        if(owner.containerMenu!=this)return;
        render();
        int rows=rowsForDisplay();
        if(rows!=getRowCount()||!locale.equals(Guide.locale(owner))||!title.equals(title().getString()))Guide.openPage(owner,mode(),new SimpleMenuProvider((id,inv,p)->new GuideMenu(id,inv,owner,view(),this,rows,true,mode()),title()));
        else super.broadcastChanges();
    }
    private int rowsForDisplay(){for(int slot=53;slot>=27;slot--)if(!display.getItem(slot).isEmpty())return slot>=36?6:4;return 3;}
    private void render(){
        if(group!=null&&(!AddonGuides.visible(context(),group)||!AddonGuides.accessible(context(),group))||selected!=null&&!available(context(),selected))home();
        display.clearContent();actions.clear();listedItems.clear();alternatives=false;
        if(selected==null){
            for(int i=0;i<9;i++)put(i,MenuItems.blank(Items.GRAY_STAINED_GLASS_PANE),null);
            for(int i=45;i<54;i++)put(i,MenuItems.blank(Items.GRAY_STAINED_GLASS_PANE),null);
            if(!cheat)button(1,Items.COMPARATOR,"settings",()->GuideSettings.open(owner,GuideSettings.heldBook(owner)));button(7,Items.NAME_TAG,"search",()->Guide.prompt(owner,this));
            if(group!=null||query!=null)backButton();
            renderList();
        }else renderRecipe();
    }
    private void pages(int size,int previous,int next){
        int pages=Math.max(1,(size+35)/36);page=Math.max(0,Math.min(page,pages-1));
        put(previous,MenuItems.guidePage(owner,false,page,pages),()->page=Math.max(0,page-1));
        put(next,MenuItems.guidePage(owner,true,page,pages),()->page=Math.min(pages-1,page+1));
    }
    private Component categoryName(GuideContent.Group entry){return GuideContent.category(entry).copy().withStyle(entry.month()==0?ChatFormatting.YELLOW:ChatFormatting.GOLD);}
    private void renderList(){
        var groups=query!=null?List.<GuideContent.Group>of():GuideContent.GROUPS.stream().filter(g->Objects.equals(AddonGuides.parent(g.id()),group)).filter(g->AddonGuides.visible(context(),g.id()))
            .filter(g->g.id().contains(":")||GuideContent.ENTRIES.stream().filter(MachineClock::enabled).anyMatch(id->GuideContent.groupOf(id).equals(g.id()))).toList();
        var language=GuideContent.language(Guide.locale(owner));
        var searchable=new HashSet<String>();
        if(query!=null)for(var category:GuideContent.GROUPS)if(AddonGuides.accessible(context(),category.id())&&(Guide.HIDDEN_SEARCH.get()||AddonGuides.visible(context(),category.id())))searchable.addAll(AddonGuides.entries(context(),category.id()));
        var candidates=group!=null?AddonGuides.entries(context(),group):query!=null?GuideContent.ENTRIES:List.<String>of();
        var entries=candidates.stream().filter(MachineClock::enabled).filter(id->ItemAccess.enabled(owner.level(),id)).filter(id->query==null||searchable.contains(id)&&GuideContent.matches(language,id,query)).toList();
        int size=groups.size()+entries.size();pages(size,46,52);
        if(size==0)put(22,Items.BARRIER,tr("empty"),null);
        for(int i=0;i<36&&page*36+i<size;i++){
            int index=page*36+i;
            if(index<groups.size()){
                var entry=groups.get(index);boolean accessible=AddonGuides.accessible(context(),entry.id());
                Component name=categoryName(entry);ItemStack icon;
                if(accessible)icon=MenuItems.label(owner,GuideContent.icon(entry.icon()),"guide.category",name);
                else{
                    var lore=new ArrayList<Component>();lore.add(Component.empty());
                    for(var line:Guide.lines(owner,"parents"))lore.add(line.copy().withStyle(ChatFormatting.WHITE));
                    lore.add(Component.empty());for(String parent:entry.parents())lore.add(categoryName(GuideContent.group(parent)));
                    for(var research:AddonGuides.options(entry.id()).researches())lore.add(Component.translatable(io.github.slimefun4.forge.api.ResearchApi.get(owner.server,owner.getUUID(),research).title()).withStyle(ChatFormatting.YELLOW));
                    name=tr("locked").copy().withStyle(ChatFormatting.DARK_RED).append(Component.literal(" - ").withStyle(ChatFormatting.GRAY)).append(name);
                    icon=label(new ItemStack(Items.BARRIER),name,lore.toArray(Component[]::new));
                }
                put(9+i,icon,()->category(entry.id()));continue;
            }
            String id=entries.get(index-groups.size());var research=ResearchData.research(id);boolean locked=!cheat&&query==null&&!known(id);
            listedItems.put(9+i,id);
            boolean denied=!cheat&&query==null&&!ItemAccess.permitted(owner,id);
            var icon=denied?label(new ItemStack(Items.BARRIER),GuideContent.name(id),ItemAccess.lore(id)):locked?locked(id):GuideContent.icon(id);
            if(query!=null){
                var category=GuideContent.category(GuideContent.group(GuideContent.groupOf(id)));
                icon=label(icon,icon.getHoverName(),Component.empty(),Component.literal("⇨ ").withStyle(ChatFormatting.DARK_GRAY).append(category.copy().withStyle(ChatFormatting.WHITE)));
                icon.getOrCreateTag().putBoolean("slimefun_preview_lore",true);
                icon.hideTooltipPart(ItemStack.TooltipPart.MODIFIERS);icon.hideTooltipPart(ItemStack.TooltipPart.ENCHANTMENTS);icon.hideTooltipPart(ItemStack.TooltipPart.ADDITIONAL);
            }
            put(9+i,icon,()->{if(!available(context(),id))return;if(cheat)give(id,false);else if(query==null&&(!ItemAccess.enabled(owner.level(),id)||!ItemAccess.permitted(owner,id)))return;else if(query==null&&!known(id))GuideResearch.unlock(owner,research,GuideContent.icon(id));else item(id);});
        }
    }
    private static String id(ItemStack stack){var key=BuiltInRegistries.ITEM.getKey(stack.getItem());return key.getNamespace().equals(SlimefunForge.ID)?key.getPath():key.toString();}
    private List<? extends Recipe<?>> recipes(){
        if(GuideContent.MANUAL.contains(selected))return List.of();
        if(AddonContent.ENTRIES.containsKey(new ResourceLocation(selected)))return owner.level().getRecipeManager().getRecipes().stream().filter(r->!r.getResultItem(owner.level().registryAccess()).isEmpty()&&id(r.getResultItem(owner.level().registryAccess())).equals(selected)).sorted(Comparator.comparing(Recipe::getId)).toList();
        if(!selected.startsWith("minecraft:")){var recipe=GuideRecipes.construction(selected);return recipe==null?List.of():List.of(recipe);}
        return nativeRecipes(selected);
    }
    private List<? extends Recipe<?>> nativeRecipes(String selected){
        if(!Guide.VANILLA.get())return List.of();
        return owner.level().getRecipeManager().getRecipes().stream().filter(r->!(r instanceof MachineRecipe)&&!r.getResultItem(owner.level().registryAccess()).isEmpty()
            &&id(r.getResultItem(owner.level().registryAccess())).equals(selected)).sorted(Comparator.comparing(Recipe::getId)).toList();
    }
    private void ingredient(int index,ItemStack input){ingredientAt(GRID[index],input,true);}
    private void ingredientAt(int slot,ItemStack input,boolean mask){
        if(input.isEmpty())return;String target=id(input);
        if(mask&&!ResearchData.canUse(owner,input)){
            put(slot,label(new ItemStack(Items.BARRIER),GuideContent.name(target),tr("locked").copy().withStyle(ChatFormatting.DARK_RED,ChatFormatting.BOLD),Component.empty(),ItemAccess.permitted(owner,target)?tr("unlock_category",GuideContent.category(GuideContent.group(GuideContent.groupOf(target)))):Component.literal("No Permission").withStyle(ChatFormatting.WHITE)),null);
            return;
        }
        put(slot,input.copy(),()->{if(!mask||ResearchData.canUse(owner,input))item(target);});
    }
    private void station(String station){
        boolean available=GuideContent.MANUAL.contains(station)||Catalog.entry(station)!=null;
        var icon=available?GuideContent.icon(station):new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(station)));
        if(icon.isEmpty())icon=new ItemStack(Items.CRAFTING_TABLE);
        var name=available?GuideContent.name(station):icon.getHoverName();
        var key="guide.station."+station;
        put(10,GuideContent.language(Guide.locale(owner)).containsKey("menu.slimefun."+key)?MenuItems.label(owner,icon,key,name):label(icon,name),null);
    }
    private void renderRecipe(){
        backButton();
        String wiki=AddonGuides.wiki(selected);
        if(wiki==null&&GuideContent.WIKI.has(selected))wiki="https://github.com/Slimefun/Slimefun4/wiki/"+GuideContent.WIKI.get(selected).getAsString();
        final String url=wiki;
        if(url!=null)button(8,Items.KNOWLEDGE_BOOK,"item_wiki",()->{
            owner.sendSystemMessage(Component.literal(url).withStyle(style->style.withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.OPEN_URL,url))));
        });
        var recipes=recipes();var descriptions=AddonGuides.descriptions(context(),selected);int recipeCount=recipes.size()+descriptions.size();
        recipePage=Math.max(0,Math.min(recipePage,Math.max(0,recipeCount-1)));
        if(GuideContent.MANUAL.contains(selected)){
            var shape=structure(selected);for(int i=0;i<shape.length;i++)if(!shape[i].isEmpty()){
                if(shape[i].equals("fire"))put(GRID[i],Items.FLINT_AND_STEEL,tr("ignite"),null);
                else if(shape[i].equals("oak_fence")||shape[i].equals("oak_trapdoor")){
                    var tag=shape[i].equals("oak_fence")?net.minecraft.tags.BlockTags.WOODEN_FENCES:net.minecraft.tags.BlockTags.WOODEN_TRAPDOORS;
                    var choices=java.util.stream.StreamSupport.stream(BuiltInRegistries.BLOCK.getTagOrEmpty(tag).spliterator(),false).map(holder->holder.value().asItem()).filter(item->item!=Items.AIR).sorted(Comparator.comparing(BuiltInRegistries.ITEM::getKey)).toList();
                    alternatives|=choices.size()>1;if(!choices.isEmpty())ingredient(i,new ItemStack(choices.get(Math.floorMod(choice,choices.size()))));
                }else ingredient(i,new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("minecraft",shape[i]))));
            }
            Component note=tr(selected.endsWith("smeltery")?"ignite":selected.equals("pressure_chamber")?"pressure":selected.equals("magic_workbench")?"horizontal":"side");
            if(selected.contains("industrial_miner")){int size=selected.startsWith("advanced")?11:7;note=tr("mining",size,size);}
            put(10,AddonGuides.withLore(MenuItems.label(owner,Items.BRICKS,"guide.multiblock"),List.of(note.copy().withStyle(ChatFormatting.GRAY))),null);put(16,GuideContent.icon(selected),null);
        }else if(recipePage>=recipes.size()&&!descriptions.isEmpty()){
            var recipe=descriptions.get(recipePage-recipes.size());var type=AddonGuides.type(recipe.type());
            var inputs=recipe.inputs();for(int i=0;i<inputs.size();i++)ingredient(i,inputs.get(i));
            var icon=GuideContent.icon((type.machine()==null?type.icon():type.machine()).toString());
            put(10,label(icon,Component.translatable(type.title()),recipe.notes().toArray(Component[]::new)),null);
            put(1,Items.PAPER,recipe.section(),null);var outputs=recipe.outputs();put(16,outputs.get(0),null);
            for(int i=1;i<outputs.size();i++)put(35+i,outputs.get(i),null);
        }else if(!recipes.isEmpty()){
            var recipe=recipes.get(recipePage);
            if(recipe instanceof MachineRecipe machine){
                for(int i=0;i<machine.inputs.size()&&i<9;i++)ingredient(i,machine.inputs.get(i));station(machine.station);put(16,machine.output.copy(),null);
                if(machine.station.equals("ancient_altar"))put(10,label(display.getItem(10),display.getItem(10).getHoverName(),tr("altar")),null);
            }else{
                var inputs=recipe.getIngredients();
                for(int i=0;i<inputs.size()&&i<9;i++){
                    var choices=inputs.get(i).getItems();if(choices.length==0)continue;
                    int slot=inputs.size()==1?4:recipe instanceof ShapedRecipe shaped?(i/shaped.getWidth())*3+i%shaped.getWidth():i;
                    alternatives|=choices.length>1;ingredientAt(GRID[slot],choices[Math.floorMod(choice,choices.length)],false);
                }
                var station=recipe.getToastSymbol();put(10,station,null);put(16,recipe.getResultItem(owner.level().registryAccess()).copy(),null);
            }
        }else{
            put(16,GuideContent.icon(selected),null);
            source();
        }
        if(recipeCount>1){
            for(int slot=27;slot<36;slot++)put(slot,Items.GRAY_STAINED_GLASS_PANE,Component.empty(),null);
            put(28,MenuItems.guidePage(owner,false,recipePage,recipeCount),()->recipePage=Math.max(0,recipePage-1));
            put(34,MenuItems.guidePage(owner,true,recipePage,recipeCount),()->recipePage=Math.min(recipeCount-1,recipePage+1));
        }
        if(!selected.startsWith("minecraft:")&&descriptions.isEmpty())renderUsages();
    }
    private void renderUsages(){
        var recipes=GuideRecipes.display(owner.level(),selected).stream().filter(value->AddonGuides.itemVisible(id(value.item()))).toList();if(recipes.isEmpty())return;
        int pages=(recipes.size()+17)/18;page=Math.min(page,pages-1);
        for(int i=27;i<36;i++)put(i,Items.GRAY_STAINED_GLASS_PANE,tr(GuideRecipes.section(selected)).copy().withStyle(ChatFormatting.WHITE),null);
        put(28,MenuItems.guidePage(owner,false,page,pages),()->page=Math.max(0,page-1));put(34,MenuItems.guidePage(owner,true,page,pages),()->page=Math.min(pages-1,page+1));
        for(int i=0;i<18&&page*18+i<recipes.size();i++){
            var value=recipes.get(page*18+i);var stack=value.item();
            put((i%2==0?36:45)+i/2,label(stack,stack.getHoverName(),value.lore()),()->item(id(stack)));
        }
    }
    private void source(){
        String source=GuideRecipes.source(selected);if(source!=null)station(source);else put(10,Items.PAPER,tr("acquisition"),null);
        Component note=switch(selected){
            case "basic_circuit_board" -> tr("source_circuit");
            case "sifted_ore" -> GuideRecipes.panChance(0,SlimefunForge.item(selected))==0?tr("source_disabled"):tr("source_sifted",String.format(Locale.ROOT,"%.2f",GuideRecipes.panChance(0,SlimefunForge.item(selected))));
            case "bucket_of_oil" -> tr("source_oil");
            case "nether_ice" -> tr("source_ice");
            case "strange_nether_goo" -> StrangeNetherGoo.BARTER_CHANCE.get()<1||StrangeNetherGoo.BARTER_CHANCE.get()>=100?tr("source_disabled"):tr("goo",StrangeNetherGoo.BARTER_CHANCE.get());
            case "broken_spawner" -> tr("source_spawner");
            default -> tr("no_recipe");
        };
        put(10,label(display.getItem(10),display.getItem(10).getHoverName(),note),null);
    }
    private static String[] structure(String id){
        String shape=switch(id){
            case "enhanced_crafting_table"->",,,,crafting_table,,,dispenser,";
            case "grind_stone"->",,,,oak_fence,,,dispenser,";
            case "armor_forge"->",,,,anvil,,,dispenser,";
            case "ore_crusher"->",,,,nether_brick_fence,,iron_bars,dispenser,iron_bars";
            case "compressor"->",,,,nether_brick_fence,,piston,dispenser,piston";
            case "smeltery"->",nether_brick_fence,,nether_bricks,dispenser,nether_bricks,,fire,";
            case "makeshift_smeltery"->",oak_fence,,bricks,dispenser,bricks,,fire,";
            case "ore_washer"->",dispenser,,,oak_fence,,,cauldron,";
            case "juicer"->",glass,,,nether_brick_fence,,,dispenser,";
            case "pressure_chamber"->"smooth_stone_slab,dispenser,smooth_stone_slab,piston,glass,piston,piston,cauldron,piston";
            case "magic_workbench"->",,,,,,bookshelf,crafting_table,dispenser";
            case "industrial_miner"->",,,piston,chest,piston,iron_block,blast_furnace,iron_block";
            case "advanced_industrial_miner"->",,,piston,chest,piston,diamond_block,blast_furnace,diamond_block";
            case "table_saw"->",,,smooth_stone_slab,stonecutter,smooth_stone_slab,,iron_block,";
            case "automated_panning_machine"->",,,,oak_trapdoor,,,cauldron,";
            default->throw new IllegalArgumentException(id);
        };
        return shape.split(",",-1);
    }
    @Override public void clicked(int slot,int button,ClickType type,Player player){
        if(player!=owner||player.containerMenu!=this||!stillValid(player)||button!=0)return;
        if(group!=null&&(!AddonGuides.visible(context(),group)||!AddonGuides.accessible(context(),group))||selected!=null&&!available(context(),selected)){home();refresh();return;}
        if(type==ClickType.QUICK_MOVE&&slot==0){home();refresh();return;}
        if(type==ClickType.QUICK_MOVE&&cheat&&listedItems.containsKey(slot)){give(listedItems.get(slot),true);return;}
        if(type!=ClickType.PICKUP)return;
        var action=actions.get(slot);if(action==null)return;action.run();
        if(owner.containerMenu==this)refresh();
    }
    private void give(String id,boolean full){
        if(!Guide.canCheat(owner)||!available(context(),id)){Messages.chat(owner,tr("no_permission"));return;}
        if(GuideContent.MANUAL.contains(id)){Messages.chat(owner,tr("no_multiblocks"));return;}
        if(Catalog.entry(id)==null&&Catalog.vanilla(id)==null&&!AddonContent.ENTRIES.containsKey(new net.minecraft.resources.ResourceLocation(id)))return;
        var stack=GuideContent.icon(id);if(full)stack.setCount(stack.getMaxStackSize());
        var rest=net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(new net.minecraftforge.items.wrapper.PlayerMainInvWrapper(owner.getInventory()),stack,false);
        if(!rest.isEmpty())Messages.chat(owner,tr("bag_full"));owner.inventoryMenu.broadcastChanges();
    }
    @Override public void broadcastChanges(){
        if(owner!=null&&owner.containerMenu==this&&!locale.equals(Guide.locale(owner))){refresh();return;}
        if(owner!=null&&alternatives&&owner.server.overworld().getGameTime()-lastCycle>=20){lastCycle=owner.server.overworld().getGameTime();choice++;refresh();return;}
        super.broadcastChanges();
    }
    @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
    @Override public boolean stillValid(Player player){return player==owner&&Guide.allowed(owner,mode());}
}

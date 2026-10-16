package io.github.slimefun4.forge;

import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraftforge.fml.ModList;

/** Native chest settings. Preferences belong to the player; cheat mode belongs to the held book. */
public final class GuideSettings extends ChestMenu {
    private final ServerPlayer owner;
    private final String locale;
    private final String title;
    private final ItemStack book;
    private final SimpleContainer display;
    private final Map<Integer,Runnable> actions=new HashMap<>();
    private final Map<Integer,net.minecraft.resources.ResourceLocation> addonActions=new HashMap<>();
    private final GuideMenu guide;
    private String section="";
    private int page;
    public static void open(ServerPlayer player,ItemStack book){
        var guide=player.containerMenu instanceof GuideMenu menu?menu:null;
        Guide.openPage(player,io.github.slimefun4.forge.api.GuideApi.Mode.SURVIVAL,new SimpleMenuProvider((id,inv,p)->new GuideSettings(id,inv,player,book,null,guide),Guide.text(player,"settings")));
    }
    static ItemStack heldBook(ServerPlayer player){for(var hand:InteractionHand.values())if(AddonGuides.isGuide(player.getItemInHand(hand)))return player.getItemInHand(hand);return ItemStack.EMPTY;}
    private GuideSettings(int id,Inventory inv,ServerPlayer owner,ItemStack book,GuideSettings previous,GuideMenu guide){super(MenuType.GENERIC_9x6,id,inv,new SimpleContainer(54),6);this.owner=owner;this.guide=guide;locale=Guide.locale(owner);this.book=book.copy();display=(SimpleContainer)getContainer();if(previous!=null){section=previous.section;page=previous.page;}title=title().getString();render();}
    private Component tr(String key,Object...args){return Guide.text(owner,key,args);}
    private boolean selectableLanguage(){return owner.server.isDedicatedServer()&&Guide.TRANSLATIONS.get();}
    private Component title(){return tr(section.equals("languages")?"languages":section.equals("mods")?"mods":section.equals("credits")?"credits":section.equals("addons")?"addon_settings":"settings");}
    private static Component languageName(String code){var language=AddonGuides.languages().get(code);return Component.literal(language.name()+" ("+code+")");}
    private void put(int slot,Item item,Component name,Runnable action,Component...lore){
        var stack=new ItemStack(item).setHoverName(name);var lines=new ListTag();for(var line:lore)lines.add(StringTag.valueOf(Component.Serializer.toJson(line)));stack.getOrCreateTagElement("display").put("Lore",lines);
        display.setItem(slot,GuideContent.localize(owner,stack));if(action!=null)actions.put(slot,action);
    }
    private void button(int slot,Item item,String key,Runnable action,Object...args){display.setItem(slot,MenuItems.label(owner,item,"guide."+key,args));if(action!=null)actions.put(slot,action);}
    private void pages(int pages){
        page=Math.max(0,Math.min(page,pages-1));
        display.setItem(46,MenuItems.guidePage(owner,false,page,pages));actions.put(46,()->page=Math.max(0,page-1));
        display.setItem(52,MenuItems.guidePage(owner,true,page,pages));actions.put(52,()->page=Math.min(pages-1,page+1));
    }
    private void link(int slot,Item icon,String title,String url){button(slot,icon,title,()->{owner.closeContainer();owner.sendSystemMessage(Component.literal(url).withStyle(s->s.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL,url))));});}
    public void refresh(){
        if(owner.containerMenu!=this)return;
        if(!selectableLanguage()&&section.equals("languages"))section="";
        if(!locale.equals(Guide.locale(owner))||!title.equals(title().getString())){Guide.openPage(owner,io.github.slimefun4.forge.api.GuideApi.Mode.SURVIVAL,new SimpleMenuProvider((id,inv,p)->new GuideSettings(id,inv,owner,book,this,guide),title()));return;}
        render();
    }
    private void render(){
        if(!selectableLanguage()&&section.equals("languages"))section="";
        display.clearContent();actions.clear();addonActions.clear();
        for(int i=0;i<54;i++)if(i<9||i>44||i%9==0||i%9==8)display.setItem(i,MenuItems.blank(Items.GRAY_STAINED_GLASS_PANE));
        if(section.equals("languages")){
            button(1,Items.ENCHANTED_BOOK,"back_settings",()->section="");
            var choices=new ArrayList<String>();choices.add("");choices.addAll(GuideContent.LOCALES);
            int pages=(choices.size()+35)/36;
            pages(pages);
            for(int i=0;i<36&&page*36+i<choices.size();i++){String locale=choices.get(page*36+i);put(9+i,Items.PAPER,(locale.isEmpty()?tr("automatic"):languageName(locale)).copy().withStyle(locale.isEmpty()?ChatFormatting.GRAY:ChatFormatting.GREEN),()->{if(!selectableLanguage())return;section="";AddonGuides.selectLanguage(owner,locale);Messages.chat(owner,tr("language_updated",languageName(Guide.locale(owner))));},Component.empty(),tr(locale.isEmpty()?"language_default_hint":"language_select_hint"));}
        }else if(section.equals("addons")){
            button(0,Items.ENCHANTED_BOOK,"back_settings",()->section="");
            var context=AddonGuides.context(owner,false);
            var settings=AddonGuides.SETTINGS.entrySet().stream().filter(e->AddonSettings.ownerEnabled(e.getKey().toString())&&e.getValue().visible(context)).toList();
            int pages=Math.max(1,(settings.size()+35)/36);page=Math.max(0,Math.min(page,pages-1));
            pages(pages);
            for(int i=0;i<36&&page*36+i<settings.size();i++){
                var entry=settings.get(page*36+i);var setting=entry.getValue();
                var icon=AddonGuides.withLore(setting.icon(context),List.of(setting.selection(context),Component.literal(entry.getKey().getNamespace())));
                display.setItem(9+i,GuideContent.localize(owner,icon));addonActions.put(9+i,entry.getKey());
            }
        }else if(section.equals("credits")){
            button(0,Items.ENCHANTED_BOOK,"back_settings",()->section="");var credits=ProjectInfo.CONTRIBUTORS;int pages=Math.max(1,(credits.size()+35)/36);pages(pages);
            for(int i=0;i<36&&page*36+i<credits.size();i++){var author=credits.get(page*36+i);var color=switch(author.role()){case "developer"->ChatFormatting.GOLD;case "translator"->ChatFormatting.BLUE;case "artist"->ChatFormatting.RED;default->ChatFormatting.DARK_AQUA;};put(9+i,Items.PAPER,Component.literal(author.name()).withStyle(ChatFormatting.YELLOW),null,tr("credit_"+author.role()).copy().withStyle(color));}
        }else if(section.equals("mods")){
            button(0,Items.ENCHANTED_BOOK,"back_settings",()->section="");var mods=ModList.get().getMods();int pages=Math.max(1,(mods.size()+35)/36);pages(pages);
            for(int i=0;i<36&&page*36+i<mods.size();i++){var mod=mods.get(page*36+i);put(9+i,Items.BOOK,Component.literal(mod.getDisplayName()).withStyle(ChatFormatting.YELLOW),null,Component.literal(mod.getModId()+" "+mod.getVersion()).withStyle(ChatFormatting.GRAY));}
        }else{
            button(0,Items.ENCHANTED_BOOK,"back_guide",()->{if(guide!=null)guide.resume();else if(book.is(SlimefunForge.CREATIVE_GUIDE.get()))GuideMenu.openCreative(owner);else GuideMenu.open(owner);});
            button(2,Items.PLAYER_HEAD,"credits",()->{section="credits";page=0;},ProjectInfo.CONTRIBUTORS.size());
            var credit=AddonGuides.withLore(display.getItem(2),List.of(Component.empty(),Component.literal("Forge: MinazukiSora").withStyle(ChatFormatting.DARK_AQUA),tr("original_authors").copy().withStyle(ChatFormatting.GOLD),tr("chinese_authors").copy().withStyle(ChatFormatting.BLUE)));display.setItem(2,GuideContent.localize(owner,credit));
            display.setItem(4,MenuItems.label(owner,Items.WRITABLE_BOOK,"guide.versions",owner.server.getServerVersion(),net.minecraftforge.versions.forge.ForgeVersion.getVersion(),ModList.get().getModContainerById(SlimefunForge.ID).orElseThrow().getModInfo().getVersion().toString()));
            link(6,Items.COMPARATOR,"source","https://github.com/Slimefun/Slimefun4/tree/RC-37");link(8,Items.KNOWLEDGE_BOOK,"wiki","https://github.com/Slimefun/Slimefun4/wiki");
            int slot=19;
            if(Guide.FIREWORKS.get())button(slot++,Items.FIREWORK_ROCKET,Guide.option(owner,"fireworks")?"fireworks_enabled":"fireworks_disabled",()->Guide.preferences(owner).putBoolean("fireworks",!Guide.option(owner,"fireworks")));
            if(!Guide.NO_ANIMATION.get()){String key=Guide.option(owner,"animation")?"animation_enabled":"animation_disabled";var lines=Guide.lines(owner,key);var name=lines.remove(0);lines.add(Component.empty());lines.add(Component.literal("⇨ ").withStyle(ChatFormatting.GRAY).append(tr(key+"_click")));put(slot++,Items.MAP,name,()->Guide.preferences(owner).putBoolean("animation",!Guide.option(owner,"animation")),lines.toArray(Component[]::new));}
            if(selectableLanguage())button(slot,Items.BOOK,"language",()->{if(selectableLanguage()){section="languages";page=0;}},languageName(Guide.locale(owner)),ProjectInfo.CONTRIBUTORS.size());
            else if(!owner.server.isDedicatedServer())put(slot,Items.BOOK,tr("language_client").copy().withStyle(ChatFormatting.GRAY),null,languageName(Guide.locale(owner)).copy().withStyle(ChatFormatting.GREEN));
            put(47,Items.BOOKSHELF,tr("mods").copy().withStyle(ChatFormatting.DARK_AQUA),()->{section="mods";page=0;});
            if(!AddonGuides.SETTINGS.isEmpty())put(51,Items.COMPARATOR,tr("addon_settings").copy().withStyle(ChatFormatting.YELLOW),()->{section="addons";page=0;});
            put(49,Items.EXPERIENCE_BOTTLE,tr("rank",Guide.rank(owner)).copy().withStyle(ChatFormatting.YELLOW),null,tr("progress",ResearchData.get(owner).completed(owner),ResearchData.total(),ResearchData.get(owner).completed(owner)*100/Math.max(1,ResearchData.total())).copy().withStyle(ChatFormatting.GRAY));
        }
        broadcastChanges();
    }
    @Override public void clicked(int slot,int button,ClickType type,Player player){
        if(player!=owner||player.containerMenu!=this||!stillValid(player)||button<0||button>1||type!=ClickType.PICKUP&&type!=ClickType.QUICK_MOVE)return;
        var id=addonActions.get(slot);
        if(id!=null){var setting=AddonGuides.SETTINGS.get(id);var context=AddonGuides.context(owner,false);if(AddonSettings.ownerEnabled(id.toString())&&setting.visible(context))setting.click(context,new io.github.slimefun4.forge.api.GuideApi.Click(button,type==ClickType.QUICK_MOVE));}
        else {if(button!=0||type!=ClickType.PICKUP)return;var action=actions.get(slot);if(action!=null)action.run();}
        if(owner.containerMenu==this)refresh();
    }
    @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
    @Override public void broadcastChanges(){if(owner!=null&&owner.containerMenu==this&&!locale.equals(Guide.locale(owner))){refresh();return;}super.broadcastChanges();}
    @Override public boolean stillValid(Player player){return player==owner&&Guide.allowed(owner,io.github.slimefun4.forge.api.GuideApi.Mode.SURVIVAL);}
}

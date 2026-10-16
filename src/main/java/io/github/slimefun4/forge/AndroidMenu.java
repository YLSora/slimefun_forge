package io.github.slimefun4.forge;

import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import io.github.slimefun4.forge.MenuItems.Head;
import io.github.slimefun4.forge.api.AndroidApi;
import net.minecraft.resources.ResourceLocation;

/** Vanilla container packets carry both the inventory and bounded script-editor requests. */
public final class AndroidMenu extends ChestMenu {
    private enum Page { INVENTORY,HOME,SCRIPT,CHOOSE,LIBRARY }
    private static final int[] OUTPUT={20,21,22,29,30,31};
    private final io.github.slimefun4.forge.api.AndroidApi.Robot android;
    private final ServerPlayer viewer;
    private final SimpleContainer icons;
    private final int size;
    private Page page=Page.INVENTORY;
    private List<ResourceLocation> baseline;
    private List<ResourceLocation> draft=List.of();
    private int editIndex;
    private int libraryPage,libraryPages;
    private List<AndroidScripts.Script> displayed=List.of();
    public AndroidMenu(int id,Inventory inventory,io.github.slimefun4.forge.api.AndroidApi.Robot android){this(id,inventory,android,null,new SimpleContainer(54));}
    private AndroidMenu(int id,Inventory inventory,io.github.slimefun4.forge.api.AndroidApi.Robot android,AndroidMenu previous,SimpleContainer icons){
        super(List.of(MenuType.GENERIC_9x1,MenuType.GENERIC_9x2,MenuType.GENERIC_9x3,MenuType.GENERIC_9x4,MenuType.GENERIC_9x5,MenuType.GENERIC_9x6).get(icons.getContainerSize()/9-1),id,inventory,icons,icons.getContainerSize()/9);
        this.android=android;viewer=(ServerPlayer)inventory.player;this.icons=icons;size=icons.getContainerSize();
        if(previous!=null){page=previous.page;baseline=previous.baseline;draft=previous.draft;editIndex=previous.editIndex;libraryPage=previous.libraryPage;}
        populate();
    }
    private Component text(String key,Object...args){return GuideContent.localize(viewer,Component.translatable("menu.slimefun.android."+key,args));}
    private ItemStack icon(Head head,String key,Component...lore){var stack=MenuItems.label(viewer,MenuItems.head(head),"android."+key);var lines=new ListTag();for(var line:lore)lines.add(StringTag.valueOf(Component.Serializer.toJson(line)));if(!lines.isEmpty())stack.getOrCreateTagElement("display").put("Lore",lines);return stack;}
    private Head instruction(String value){return switch(value){
        case "START"->Head.SCRIPT_START;case "REPEAT"->Head.SCRIPT_REPEAT;case "WAIT"->Head.SCRIPT_WAIT;
        case "GO_FORWARD"->Head.SCRIPT_FORWARD;case "GO_UP"->Head.SCRIPT_UP;case "GO_DOWN"->Head.SCRIPT_DOWN;
        case "TURN_LEFT"->Head.SCRIPT_LEFT;case "TURN_RIGHT"->Head.SCRIPT_RIGHT;
        case "DIG_UP","MOVE_AND_DIG_UP"->Head.SCRIPT_DIG_UP;case "DIG_FORWARD","MOVE_AND_DIG_FORWARD"->Head.SCRIPT_DIG_FORWARD;case "DIG_DOWN","MOVE_AND_DIG_DOWN"->Head.SCRIPT_DIG_DOWN;
        case "ATTACK_MOBS_ANIMALS","ATTACK_MOBS","ATTACK_ANIMALS","ATTACK_ANIMALS_ADULT"->Head.SCRIPT_ATTACK;
        case "CHOP_TREE"->Head.SCRIPT_CHOP_TREE;case "CATCH_FISH"->Head.SCRIPT_FISH;
        case "FARM_FORWARD","FARM_EXOTIC_FORWARD"->Head.SCRIPT_FARM_FORWARD;case "FARM_DOWN","FARM_EXOTIC_DOWN"->Head.SCRIPT_FARM_DOWN;
        case "INTERFACE_ITEMS"->Head.SCRIPT_PUSH_ITEMS;case "INTERFACE_FUEL"->Head.SCRIPT_PULL_FUEL;default->throw new IllegalArgumentException("Unknown instruction: "+value);
    };}
    private ItemStack instructionIcon(ResourceLocation id,Component...lore){
        if(id.getNamespace().equals("slimefun"))return icon(instruction(id.getPath().toUpperCase(Locale.ROOT)),"instruction."+id.getPath().toUpperCase(Locale.ROOT),lore);
        var action=AndroidApi.action(id);var stack=action.icon().get().copy().setHoverName(action.title());var lines=new ListTag();for(var line:lore)lines.add(StringTag.valueOf(Component.Serializer.toJson(line)));stack.getOrCreateTagElement("display").put("Lore",lines);return stack;
    }
    private Component title(){return page==Page.INVENTORY?GuideContent.localize(viewer,android.getDisplayName()):page==Page.LIBRARY?text("library_title"):text("editor_title").copy().withStyle(ChatFormatting.DARK_AQUA);}
    private void refresh(){
        int cells=switch(page){case INVENTORY,LIBRARY->54;case HOME->9;case SCRIPT->Math.min(54,draft.size()+1);case CHOOSE->10+android.choices().size();};
        viewer.openMenu(new net.minecraft.world.SimpleMenuProvider((id,inventory,player)->new AndroidMenu(id,inventory,android,this,new SimpleContainer((cells+8)/9*9)),title()));
    }
    private void populate(){
        icons.clearContent();
        for(int i=0;i<size;i++){var old=slots.get(i);var slot=new Slot(icons,i,old.x,old.y){public boolean mayPlace(ItemStack item){return false;}public boolean mayPickup(Player player){return false;}};slot.index=i;slots.set(i,slot);}
        if(page==Page.INVENTORY){
            for(int i=0;i<54;i++)icons.setItem(i,MenuItems.blank(Items.GRAY_STAINED_GLASS_PANE));
            MenuItems.fill(icons,Items.ORANGE_STAINED_GLASS_PANE,10,11,12,13,14,19,23,28,32,37,38,39,40,41);
            for(int i=0;i<7;i++){int at=i<6?OUTPUT[i]:43;var old=slots.get(at);final boolean fuel=i==6;var slot=new Slot(android.inventory(),i,old.x,old.y){public boolean mayPlace(ItemStack item){return fuel;}public boolean mayPickup(Player player){return stillValid(player);}};slot.index=at;slots.set(at,slot);}
            icons.setItem(15,icon(Head.SCRIPT_START,"start"));icons.setItem(17,icon(Head.SCRIPT_PAUSE,"pause"));icons.setItem(16,icon(Head.ENERGY_REGULATOR,"editor"));icons.setItem(34,icon(Head.GENERATOR,"fuel_"+android.tier()));
        }else if(page==Page.HOME){icons.setItem(1,icon(Head.SCRIPT_FORWARD,"edit"));icons.setItem(3,icon(Head.SCRIPT_NEW,"new"));icons.setItem(5,icon(Head.SCRIPT_DOWN,"library"));icons.setItem(8,icon(Head.SCRIPT_LEFT,"back"));}
        else if(page==Page.LIBRARY){
            var all=AndroidScripts.get(viewer.server).list(android.type());libraryPages=all.size()/45+1;libraryPage=Math.floorMod(libraryPage,libraryPages);int start=libraryPage*45;displayed=all.subList(start,Math.min(start+45,all.size()));
            for(int i=0;i<displayed.size();i++)icons.setItem(i,scriptIcon(displayed.get(i)));
            for(int i=45;i<54;i++)icons.setItem(i,MenuItems.blank(Items.GRAY_STAINED_GLASS_PANE));
            for(int slot:new int[]{46,50}){
                boolean active=slot==46?libraryPage>0:libraryPage+1<libraryPages;
                var button=MenuItems.label(viewer,active?Items.LIME_STAINED_GLASS_PANE:Items.BLACK_STAINED_GLASS_PANE,slot==46?"android.library_previous":"android.library_next",libraryPage+1,libraryPages);
                if(active)button.setHoverName(Component.literal(button.getHoverName().getString()).withStyle(style->style.withColor(ChatFormatting.WHITE).withItalic(false)));
                icons.setItem(slot,button);
            }
            icons.setItem(48,icon(Head.SCRIPT_UP,"library_upload"));icons.setItem(53,icon(Head.SCRIPT_LEFT,"back"));
        }
        else if(page==Page.SCRIPT){
            icons.setItem(0,icon(Head.SCRIPT_START,"instruction.START",Component.empty(),text("return_hint")));for(int i=1;i<draft.size()-1;i++)icons.setItem(i,instructionIcon(draft.get(i),Component.empty(),text("edit_hint"),text("delete_hint"),text("duplicate_hint")));
            int last=draft.size()-1;if(draft.size()<54)icons.setItem(last++,icon(Head.SCRIPT_NEW,"add"));icons.setItem(last,icon(Head.SCRIPT_REPEAT,"instruction.REPEAT",Component.empty(),text("return_hint")));
        }else{
            MenuItems.fill(icons,Items.GRAY_STAINED_GLASS_PANE,0,1,2,3,4,5,6,7,8);
            icons.setItem(9,icon(Head.SCRIPT_PAUSE,"delete"));int i=10;for(var instruction:android.choices())icons.setItem(i++,instructionIcon(instruction));
        }
        broadcastChanges();
    }
    private void script(List<ResourceLocation> value){draft=new ArrayList<>(value);baseline=android.script();page=Page.SCRIPT;refresh();}
    private void commit(List<ResourceLocation> next){android.setScript(next);script(android.script());}
    void openLibrary(int number){page=Page.LIBRARY;libraryPage=number;refresh();viewer.playNotifySound(SoundEvents.NOTE_BLOCK_HAT.value(),SoundSource.PLAYERS,.7F,.7F);}
    private ItemStack scriptIcon(AndroidScripts.Script script){
        var item=new ItemStack(android.getBlockState().getBlock()).setHoverName(Component.literal(script.name).withStyle(ChatFormatting.AQUA));var lore=new ListTag();
        var lines=new ArrayList<Component>(List.of(Component.translatable("menu.slimefun.android.library_author",script.authorName(viewer.server)),Component.translatable("menu.slimefun.android.library_downloads",script.downloads),Component.translatable("menu.slimefun.android.library_rating",Component.literal(Float.toString(script.rating())).withStyle(ChatFormatting.DARK_RED)),Component.translatable("menu.slimefun.android.library_votes",script.positive.size(),script.negative.size()),Component.translatable("menu.slimefun.android.library_download"),Component.translatable("menu.slimefun.android.library_override")));
        if(script.canRate(viewer.getUUID()))lines.add(Component.translatable("menu.slimefun.android.library_rate"));for(var line:lines)lore.add(StringTag.valueOf(Component.Serializer.toJson(GuideContent.localize(viewer,line))));item.getOrCreateTagElement("display").put("Lore",lore);return item;
    }
    @Override public boolean stillValid(Player player){return player==viewer&&player.isAlive()&&AndroidApi.valid(android)&&android.getLevel()==player.level()&&player.distanceToSqr(android.getBlockPos().getCenter())<=64&&AndroidApi.mayAccess(viewer,android)&&ResearchData.canUse(viewer,new ItemStack(android.getBlockState().getBlock()));}
    @Override public void clicked(int slot,int button,ClickType type,Player player){
        if(player.containerMenu!=this||!stillValid(player))return;
        if(slot>=size||slot<0){super.clicked(slot,button,type,player);return;}
        if(page==Page.INVENTORY&&(slot==43||Arrays.stream(OUTPUT).anyMatch(n->n==slot))){super.clicked(slot,button,type,player);return;}
        if((type!=ClickType.PICKUP&&type!=ClickType.QUICK_MOVE)||(button!=0&&button!=1))return;
        if(page==Page.INVENTORY){if(type!=ClickType.PICKUP)return;if(slot==15){android.pause(false);viewer.closeContainer();Messages.chat(viewer,Component.translatable("message.slimefun.android_started"));}else if(slot==17){android.pause(true);Messages.chat(viewer,Component.translatable("message.slimefun.android_stopped"));}else if(slot==16){android.pause(true);Messages.chat(viewer,Component.translatable("message.slimefun.android_stopped"));page=Page.HOME;refresh();}return;}
        if(page==Page.HOME){if(type!=ClickType.PICKUP)return;if(slot==1)script(android.script());else if(slot==3)script(List.of(AndroidApi.START,AndroidApi.REPEAT));else if(slot==5)openLibrary(0);else if(slot==8){page=Page.INVENTORY;refresh();}return;}
        if(page==Page.LIBRARY){
            var data=AndroidScripts.get(viewer.server);
            if(slot<displayed.size()){
                var selected=displayed.get(slot);if(type==ClickType.QUICK_MOVE){data.rate(viewer,selected,button==0);openLibrary(libraryPage);}
                else if(button==0&&data.download(android,selected)){page=Page.HOME;refresh();}
            }else if(type==ClickType.PICKUP){
                if(slot==48)AndroidScripts.prompt(viewer,android,this,libraryPage);else if(slot==53){page=Page.HOME;refresh();}else if(libraryPages>1&&(slot==46||slot==50))openLibrary(libraryPage+(slot==46?-1:1));
            }
            return;
        }
        if(!android.script().equals(baseline)){script(android.script());return;}
        if(page==Page.SCRIPT){
            int last=draft.size()-1;
            if(slot==0||slot==last+(draft.size()<54?1:0)){page=Page.INVENTORY;refresh();return;}
            if(slot==last&&draft.size()<54&&type==ClickType.PICKUP){editIndex=slot;page=Page.CHOOSE;refresh();return;}
            if(slot>0&&slot<last){
                if(button==1){var next=new ArrayList<>(draft);if(type==ClickType.QUICK_MOVE){if(next.size()>=54)return;next.add(slot,next.get(slot));}else next.remove(slot);commit(next);}
                else if(type==ClickType.PICKUP){editIndex=slot;page=Page.CHOOSE;refresh();}
            }
        }else if(type==ClickType.PICKUP){
            var next=new ArrayList<>(draft);if(slot==9){if(editIndex<next.size()-1)next.remove(editIndex);commit(next);return;}
            var choices=android.choices();int selected=slot-10;if(selected<0||selected>=choices.size())return;
            if(editIndex==next.size()-1){if(next.size()>=54)return;next.add(editIndex,choices.get(selected));}else next.set(editIndex,choices.get(selected));commit(next);
        }
    }
    @Override public ItemStack quickMoveStack(Player player,int slot){
        if(player.containerMenu!=this||!stillValid(player)||slot<0||slot>=slots.size())return ItemStack.EMPTY;var current=slots.get(slot);var stack=current.getItem();if(stack.isEmpty())return ItemStack.EMPTY;var before=stack.copy();
        if(slot<size){if(page!=Page.INVENTORY||!current.mayPickup(player)||!moveItemStackTo(stack,size,slots.size(),true))return ItemStack.EMPTY;}
        else if(page!=Page.INVENTORY||!moveItemStackTo(stack,43,44,false))return ItemStack.EMPTY;
        if(stack.isEmpty())current.set(ItemStack.EMPTY);else current.setChanged();current.onTake(player,stack);return before;
    }
}

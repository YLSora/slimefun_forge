package io.github.slimefun4.forge;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;

/** RC-37 display items; the actual machine inventory never receives presentation NBT. */
final class MenuItems {
    // head_models.py binds these display-only models to the existing memory-core item.
    enum Head { SCRIPT_START, SCRIPT_REPEAT, SCRIPT_WAIT, SCRIPT_FORWARD, SCRIPT_UP, SCRIPT_DOWN, SCRIPT_LEFT, SCRIPT_RIGHT, SCRIPT_DIG_UP, SCRIPT_DIG_FORWARD, SCRIPT_DIG_DOWN, SCRIPT_ATTACK, SCRIPT_CHOP_TREE, SCRIPT_FISH, SCRIPT_FARM_FORWARD, SCRIPT_FARM_DOWN, SCRIPT_PUSH_ITEMS, SCRIPT_PULL_FUEL, SCRIPT_NEW, SCRIPT_PAUSE, ENERGY_REGULATOR, GENERATOR, EXCLAMATION_MARK, MINECRAFT_CHUNK }
    static ItemStack head(Head icon){var stack=new ItemStack(SlimefunForge.item("android_memory_core"));stack.getOrCreateTag().putInt("CustomModelData",icon.ordinal()+1);return stack;}
    static ItemStack blank(Item item){return new ItemStack(item).setHoverName(Component.literal(" ").withStyle(s->s.withItalic(false)));}
    static void fill(Container display,Item item,int... slots){for(int slot:slots)display.setItem(slot,blank(item));}
    static void processing(Container display,boolean smeltery){
        for(int i=0;i<45;i++)display.setItem(i,blank(Items.GRAY_STAINED_GLASS_PANE));
        fill(display,Items.CYAN_STAINED_GLASS_PANE,9,10,11,12,18,21,27,28,29,30);
        if(smeltery)fill(display,Items.CYAN_STAINED_GLASS_PANE,0,1,2,3,36,37,38,39);
        fill(display,Items.ORANGE_STAINED_GLASS_PANE,14,15,16,17,23,26,32,33,34,35);
        fill(display,Items.BLACK_STAINED_GLASS_PANE,22);
        for(int i:smeltery?new int[]{10,11,19,20,24,25,28,29}:new int[]{19,20,24,25})display.setItem(i,ItemStack.EMPTY);
    }
    static ItemStack label(ServerPlayer player,ItemStack icon,String key,Object... args){
        var stack=icon.copy();stack.setHoverName(GuideContent.localize(player,Component.translatable("menu.slimefun."+key,args)));
        var lines=new ListTag();var language=GuideContent.language(Guide.locale(player));
        for(int i=0;language.containsKey("menu.slimefun."+key+".lore."+i);i++)lines.add(StringTag.valueOf(Component.Serializer.toJson(GuideContent.localize(player,Component.translatable("menu.slimefun."+key+".lore."+i,args)))));
        stack.getOrCreateTagElement("display").put("Lore",lines);stack.getOrCreateTag().putBoolean("slimefun_preview_lore",true);return stack;
    }
    static ItemStack label(ServerPlayer player,Item icon,String key,Object... args){return label(player,new ItemStack(icon),key,args);}
    static ItemStack guidePage(ServerPlayer player,boolean next,int page,int pages){
        boolean active=next?page+1<pages:page>0;
        var stack=label(player,active?Items.LIME_STAINED_GLASS_PANE:Items.BLACK_STAINED_GLASS_PANE,"guide."+(next?"next":"previous"),page+1,pages);
        return stack.setHoverName(stack.getHoverName().copy().withStyle(active?ChatFormatting.WHITE:ChatFormatting.DARK_GRAY));
    }
    static Item progressIcon(String id){return switch(Catalog.entry(id).implementation()){
        case "AutoAnvil","ElectricIngotPulverizer","ElectricOreGrinder"->Items.IRON_PICKAXE;
        case "AutoBrewer"->Items.FISHING_ROD;case "AutoDisenchanter"->Items.DIAMOND_CHESTPLATE;
        case "AutoEnchanter"->Items.GOLDEN_CHESTPLATE;case "BookBinder"->Items.IRON_CHESTPLATE;
        case "CarbonPress","GEOMiner"->Items.DIAMOND_PICKAXE;case "ChargingBench","Freezer"->Items.GOLDEN_PICKAXE;
        case "ElectricDustWasher"->Items.GOLDEN_SHOVEL;case "ElectricGoldPan","OilPump"->Items.DIAMOND_SHOVEL;
        case "ElectricPress"->Items.IRON_HOE;case "FoodComposter","FoodFabricator"->Items.GOLDEN_HOE;
        case "ProduceCollector"->Items.SHEARS;default->Items.FLINT_AND_STEEL;
    };}
    static ItemStack progress(Item icon,int elapsed,int total){
        if(total<=0)return blank(Items.BLACK_STAINED_GLASS_PANE);
        int remaining=total-elapsed;float percent=Math.round(((elapsed*100F/total)*100F)/100F);int bars=(int)percent/5;
        ChatFormatting color=percent<16?ChatFormatting.DARK_RED:percent<32?ChatFormatting.RED:percent<48?ChatFormatting.GOLD:percent<64?ChatFormatting.YELLOW:percent<80?ChatFormatting.DARK_GREEN:ChatFormatting.GREEN;
        var bar=Component.literal(":".repeat(bars)).withStyle(color).append(Component.literal(":".repeat(20-bars)+" - "+percent+"%").withStyle(ChatFormatting.GRAY));
        int seconds=remaining/2;var time=Component.literal((seconds>=60?seconds/60+"m ":"")+seconds%60+"s").withStyle(ChatFormatting.GRAY);
        var stack=blank(icon);stack.setDamageValue(icon.getMaxDamage()/total*remaining);stack.hideTooltipPart(ItemStack.TooltipPart.MODIFIERS);stack.getOrCreateTag().putBoolean("slimefun_preview_lore",true);
        var lore=new ListTag();for(var line:new Component[]{bar,Component.empty(),time})lore.add(StringTag.valueOf(Component.Serializer.toJson(line.copy().withStyle(s->s.withItalic(false)))));stack.getOrCreateTagElement("display").put("Lore",lore);return stack;
    }
    private MenuItems(){}
}

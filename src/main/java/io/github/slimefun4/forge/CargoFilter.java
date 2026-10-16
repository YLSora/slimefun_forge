package io.github.slimefun4.forge;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.PotionUtils;

/** RC-37 filter identity is deliberately less strict than native stack merging. */
final class CargoFilter {
    private static final List<String> SOULBOUND=ItemText.glyphs(Component.literal("§7Soulbound"));
    static boolean matches(ItemStack item,ItemStack template,boolean checkLore){
        if(item.isEmpty()||template.isEmpty()||item.getItem()!=template.getItem())return false;
        if(item.getItem() instanceof io.github.slimefun4.forge.api.BackpackApi.Bag)return Objects.equals(item.getTagElement("slimefun_backpack"),template.getTagElement("slimefun_backpack"))&&sameLore(item,template)&&AddonItems.distinctive(item,template,io.github.slimefun4.forge.api.ItemApi.Match.FILTER);
        if(item.getItem() instanceof io.github.slimefun4.forge.api.ItemApi.Distinctive||AddonContent.ENTRIES.containsKey(BuiltInRegistries.ITEM.getKey(item.getItem())))return AddonItems.matches(item,template,io.github.slimefun4.forge.api.ItemApi.Match.FILTER);
        if(BuiltInRegistries.ITEM.getKey(item.getItem()).getNamespace().equals(SlimefunForge.ID))
            return true;
        return sameMetadata(item,template,checkLore);
    }
    static boolean inventoryMatches(ItemStack item,ItemStack template,boolean checkLore){
        if(item.isEmpty()||template.isEmpty())return false;
        var source=originalMetadata(item,checkLore);var current=originalMetadata(template,checkLore);
        return source.getItem()==current.getItem()&&sameMetadata(source,current,checkLore);
    }
    private static ItemStack originalMetadata(ItemStack stack,boolean checkLore){
        var id=BuiltInRegistries.ITEM.getKey(stack.getItem());var entry=id.getNamespace().equals(SlimefunForge.ID)?Catalog.entry(id.getPath()):null;if(entry==null)return stack;
        // Native Bukkit inventory stacks compare material/name/lore, without the cargo wrapper's ID shortcut.
        var result=new ItemStack(BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation("minecraft",entry.material())),stack.getCount());if(stack.hasTag())result.setTag(stack.getTag().copy());
        if(!stack.hasCustomHoverName())result.setHoverName(ItemText.localize("en_us",stack.getItem().getDescription()));
        var display=stack.getTagElement("display");if(checkLore&&(display==null||!display.contains("Lore"))){var lines=new ListTag();for(var line:ItemText.lore(stack,"en_us"))lines.add(StringTag.valueOf(Component.Serializer.toJson(line)));if(!lines.isEmpty())result.getOrCreateTagElement("display").put("Lore",lines);}
        return result;
    }
    private static boolean sameMetadata(ItemStack item,ItemStack template,boolean checkLore){
        if(hasMeta(item)!=hasMeta(template))return false;
        if(!Objects.equals(formatted(display(item,"Name")),formatted(display(template,"Name")))||!Objects.equals(tag(item,"CustomModelData"),tag(template,"CustomModelData")))return false;
        return (!checkLore||sameLore(item,template))&&(!(item.getItem() instanceof PotionItem)||PotionUtils.getPotion(item)==PotionUtils.getPotion(template));
    }
    private static Tag tag(ItemStack item,String name){return item.getTag()==null?null:item.getTag().get(name);}
    private static Tag display(ItemStack item,String name){var display=item.getTagElement("display");var value=display==null?null:display.get(name);return name.equals("Lore")&&value instanceof ListTag list&&list.isEmpty()?null:value;}
    private static boolean hasMeta(ItemStack item){
        var tag=item.getTag();if(tag==null)return false;
        for(String key:tag.getAllKeys()){
            if((key.equals("Damage")||key.equals("RepairCost")||key.equals("HideFlags")||key.equals("Unbreakable"))&&tag.getInt(key)==0)continue;
            if(Set.of("Enchantments","StoredEnchantments","AttributeModifiers","CustomPotionEffects","CanDestroy","CanPlaceOn").contains(key)&&tag.get(key) instanceof ListTag list&&list.isEmpty())continue;
            if(key.equals("display")){var display=tag.getCompound(key).copy();if(display.get("Lore") instanceof ListTag list&&list.isEmpty())display.remove("Lore");if(display.isEmpty())continue;}
            return true;
        }
        return false;
    }
    private static boolean sameLore(ItemStack a,ItemStack b){
        var first=display(a,"Lore");var second=display(b,"Lore");
        if(first==null||second==null)return first==second;
        if(!(first instanceof ListTag left)||!(second instanceof ListTag right))return first.equals(second);
        return lines(left).equals(lines(right));
    }
    private static List<String> formatted(Tag value){
        if(value==null)return null;
        try{var text=Component.Serializer.fromJson(value.getAsString());if(text!=null)return ItemText.glyphs(text);}catch(RuntimeException malformed){}
        return List.of("raw:"+value);
    }
    private static List<List<String>> lines(ListTag lore){
        var result=new ArrayList<List<String>>();
        for(int i=0;i<lore.size();i++){
            var line=formatted(lore.get(i));if(line.equals(SOULBOUND))continue;
            result.add(line);
        }
        return result;
    }
    private CargoFilter() {}
}

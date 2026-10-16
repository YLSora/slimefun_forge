package io.github.slimefun4.forge;

import java.util.*;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Display-only upstream lore; real item NBT remains gameplay data. */
public final class ItemText {
    private static final JsonObject COUNTS=GuideContent.read("/slimefun/item_lore.json",JsonObject.class);
    public static Component localize(String locale,Component component){return GuideContent.localize(locale,component);}
    static List<String> glyphs(Component component) {
        var result=new ArrayList<String>();component.visit((style,text)->{net.minecraft.util.StringDecomposer.iterateFormatted(text,style,(index,s,codepoint)->{result.add(codepoint+":"+s.getColor()+":"+s.isBold()+s.isItalic()+s.isUnderlined()+s.isStrikethrough()+s.isObfuscated());return true;});return Optional.empty();},net.minecraft.network.chat.Style.EMPTY);return result;
    }
    public static List<Component> lore(ItemStack stack,String locale){
        var id=BuiltInRegistries.ITEM.getKey(stack.getItem());var lines=new ArrayList<Component>();
        if(!id.getNamespace().equals(SlimefunForge.ID)||!COUNTS.has(id.getPath()))return lines;
        String language=locale.equals("zh_cn")?locale:"en_us";
        int count=COUNTS.getAsJsonObject(id.getPath()).get(language).getAsInt();
        for(int i=0;i<count;i++)lines.add(localize(language,Component.translatable("lore.slimefun."+id.getPath()+"."+i)));
        return lines;
    }
    private ItemText(){}
}

package io.github.slimefun4.forge;

import java.util.Map;
import java.util.Set;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import static java.util.Map.entry;
import static net.minecraft.world.item.alchemy.Potions.*;

/** RC-37's restricted dynamic recipes; vanilla mixing alone has different fermentation rules. */
final class Brewing {
    private static final Set<Item> AWKWARD_INGREDIENTS=Set.of(Items.SUGAR,Items.RABBIT_FOOT,Items.BLAZE_POWDER,Items.GLISTERING_MELON_SLICE,Items.SPIDER_EYE,Items.GHAST_TEAR,Items.MAGMA_CREAM,Items.PUFFERFISH,Items.GOLDEN_CARROT,Items.TURTLE_HELMET,Items.PHANTOM_MEMBRANE);
    private static final Map<Potion,Potion> EXTENDED=Map.ofEntries(
        entry(NIGHT_VISION,LONG_NIGHT_VISION),entry(INVISIBILITY,LONG_INVISIBILITY),entry(LEAPING,LONG_LEAPING),entry(FIRE_RESISTANCE,LONG_FIRE_RESISTANCE),entry(SWIFTNESS,LONG_SWIFTNESS),entry(SLOWNESS,LONG_SLOWNESS),entry(WATER_BREATHING,LONG_WATER_BREATHING),entry(POISON,LONG_POISON),entry(REGENERATION,LONG_REGENERATION),entry(STRENGTH,LONG_STRENGTH),entry(WEAKNESS,LONG_WEAKNESS),entry(TURTLE_MASTER,LONG_TURTLE_MASTER),entry(SLOW_FALLING,LONG_SLOW_FALLING));
    private static final Map<Potion,Potion> UPGRADED=Map.ofEntries(
        entry(LEAPING,STRONG_LEAPING),entry(SWIFTNESS,STRONG_SWIFTNESS),entry(SLOWNESS,STRONG_SLOWNESS),entry(HEALING,STRONG_HEALING),entry(HARMING,STRONG_HARMING),entry(POISON,STRONG_POISON),entry(REGENERATION,STRONG_REGENERATION),entry(STRENGTH,STRONG_STRENGTH),entry(TURTLE_MASTER,STRONG_TURTLE_MASTER));
    private static final Map<Potion,Potion> FERMENTED=Map.ofEntries(
        entry(SWIFTNESS,SLOWNESS),entry(LONG_SWIFTNESS,LONG_SLOWNESS),entry(STRONG_SWIFTNESS,STRONG_SLOWNESS),
        entry(LEAPING,SLOWNESS),entry(LONG_LEAPING,LONG_SLOWNESS),entry(STRONG_LEAPING,STRONG_SLOWNESS),
        entry(HEALING,HARMING),entry(STRONG_HEALING,STRONG_HARMING),entry(POISON,HARMING),entry(STRONG_POISON,STRONG_HARMING),
        entry(NIGHT_VISION,INVISIBILITY),entry(LONG_NIGHT_VISION,LONG_INVISIBILITY));
    private static boolean potion(ItemStack stack){return stack.is(Items.POTION)||stack.is(Items.SPLASH_POTION)||stack.is(Items.LINGERING_POTION);}
    static ItemStack brew(ItemStack first,ItemStack second) {
        var source=potion(first)?first:second;var ingredient=potion(first)?second:first;
        if(!potion(source)||ingredient.isEmpty())return ItemStack.EMPTY;
        // A fresh native turtle helmet carries Damage:0; only metadata beyond its default is disallowed.
        if(ingredient.hasTag()&&!ItemStack.isSameItemSameTags(ingredient,new ItemStack(ingredient.getItem())))return ItemStack.EMPTY;
        var type=PotionUtils.getPotion(source);Potion result=null;var container=source.getItem();
        if(type==WATER) {
            if(ingredient.is(Items.FERMENTED_SPIDER_EYE))result=WEAKNESS;
            else if(ingredient.is(Items.NETHER_WART))result=AWKWARD;
            else if(source.is(Items.POTION)&&ingredient.is(Items.GUNPOWDER)){result=WATER;container=Items.SPLASH_POTION;}
            else if(source.is(Items.SPLASH_POTION)&&ingredient.is(Items.DRAGON_BREATH)){result=WATER;container=Items.LINGERING_POTION;}
        } else if(ingredient.is(Items.FERMENTED_SPIDER_EYE)) {
            // RC-37 cannot construct extended harming; reject long poison without consuming it.
            result=FERMENTED.get(type);
        } else if(ingredient.is(Items.REDSTONE))result=EXTENDED.containsValue(type)?type:EXTENDED.get(type);
        else if(ingredient.is(Items.GLOWSTONE_DUST))result=UPGRADED.containsValue(type)?type:UPGRADED.get(type);
        else if(type==AWKWARD&&AWKWARD_INGREDIENTS.contains(ingredient.getItem()))result=PotionUtils.getPotion(PotionBrewing.mix(ingredient,source));
        if(result==null)return ItemStack.EMPTY;
        var output=new ItemStack(container);if(source.hasTag())output.setTag(source.getTag().copy());
        return PotionUtils.setPotion(output,result);
    }
}

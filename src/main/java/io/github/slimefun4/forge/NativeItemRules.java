package io.github.slimefun4.forge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/** Native result transactions are checked without removing previews, paid outputs or ingredients. */
public final class NativeItemRules {
    private static int result(AbstractContainerMenu menu){
        if(menu instanceof CraftingMenu||menu instanceof InventoryMenu)return 0;
        if(menu instanceof AnvilMenu||menu instanceof GrindstoneMenu||menu instanceof CartographyTableMenu||menu instanceof AbstractFurnaceMenu)return 2;
        if(menu instanceof SmithingMenu||menu instanceof LoomMenu)return 3;
        return menu instanceof StonecutterMenu?1:-1;
    }
    private static boolean usable(ServerPlayer player,ItemStack stack){
        if(ResearchData.canUse(player,stack))return true;ItemAccess.denied(player,stack);return false;
    }
    public static boolean allowClick(AbstractContainerMenu menu,int slot,ClickType type,ServerPlayer player){
        int output=result(menu);if(output<0||slot!=output&&type!=ClickType.PICKUP_ALL)return true;
        if(player.containerMenu!=menu||!menu.stillValid(player))return false;
        var result=menu.getSlot(output).getItem();if(result.isEmpty())return true;
        if(!usable(player,result))return false;
        int first=menu instanceof CraftingMenu||menu instanceof InventoryMenu?1:0;
        int end=menu instanceof CraftingMenu?10:menu instanceof InventoryMenu?5:output;
        boolean enchants=menu instanceof AnvilMenu&&!EnchantmentHelper.getEnchantments(result).equals(EnchantmentHelper.getEnchantments(menu.getSlot(0).getItem()));
        boolean disenchants=menu instanceof GrindstoneMenu;
        for(int i=first;i<end;i++){
            var input=menu.getSlot(i).getItem();if(!usable(player,input))return false;
            if(!input.isEmpty()&&(enchants&&!Enchanting.allowed(input,false)||disenchants&&!Enchanting.allowed(input,true)))return false;
        }
        return true;
    }
    public static boolean allowEnchant(EnchantmentMenu menu,ServerPlayer player){return player.containerMenu==menu&&menu.stillValid(player)&&usable(player,menu.getSlot(0).getItem())&&Enchanting.allowed(menu.getSlot(0).getItem(),false);}
    private NativeItemRules(){}
}

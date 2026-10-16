package io.github.slimefun4.forge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

public final class CoolerItem extends BackpackItem implements io.github.slimefun4.forge.api.BackpackApi.Cooling {
    private static final java.util.Set<ServerPlayer> FEEDING=java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    public CoolerItem() { super(3); }
    @Override public boolean accepts(ItemStack stack) { return stack.getItem() instanceof JuiceItem; }
    @Override public boolean canFeed(ItemStack drink){return drink.getItem() instanceof JuiceItem||drink.is(net.minecraft.world.item.Items.POTION)&&drink.hasCustomHoverName();}
    @Override public java.util.List<MobEffectInstance> effects(ItemStack drink){return drink.getItem() instanceof JuiceItem item&&(drink.getTag()==null||!drink.getTag().contains("Potion"))?item.effects(drink):net.minecraft.world.item.alchemy.PotionUtils.getCustomEffects(drink);}
    public static void request(ServerPlayer player) {
        for(int slot=0;slot<player.getInventory().getContainerSize();slot++) {
            var bag=player.getInventory().getItem(slot);
            if(!(bag.getItem() instanceof io.github.slimefun4.forge.api.BackpackApi.Cooling))continue;
            if(!ResearchData.canUse(player,bag)){ItemAccess.denied(player,bag);return;}
            FoodEffects.queueCooler(player,bag);
        }
    }
    private static boolean carried(ServerPlayer player,ItemStack bag) {
        if(!player.isAlive()||player.server.getPlayerList().getPlayer(player.getUUID())!=player||bag.getCount()!=1||!ResearchData.canUse(player,bag))return false;
        for(int slot=0;slot<player.getInventory().getContainerSize();slot++)if(player.getInventory().getItem(slot)==bag)return true;
        return false;
    }
    static void feed(ServerPlayer player,ItemStack bag) {
        if(!FEEDING.add(player))return;
        try{feedOnce(player,bag);}finally{FEEDING.remove(player);}
    }
    private static void feedOnce(ServerPlayer player,ItemStack bag) {
        if(!(bag.getItem() instanceof io.github.slimefun4.forge.api.BackpackApi.Cooling cooling)||!carried(player,bag))return;
        BagInventory contents=player.containerMenu instanceof BagMenu menu?menu.contents(bag):null;
        if(contents==null)contents=BackpackData.get(player.server).inventory(player,bag);
        if(contents==null)return;
        for(int slot=0;slot<contents.getContainerSize();slot++) {
            var drink=contents.getItem(slot);
            if(!cooling.canFeed(drink.copy()))continue;
            var before=drink.copy();var bagBefore=bag.copy();var menuBefore=player.containerMenu;var levelBefore=player.level();long revision=contents.revision();int food=player.getFoodData().getFoodLevel();float previousSaturation=player.getFoodData().getSaturationLevel();
            var event=new CoolerFeedPlayerEvent(player,bag,drink);
            if(MinecraftForge.EVENT_BUS.post(event))return;
            if(!carried(player,bag)||player.containerMenu!=menuBefore||player.level()!=levelBefore||!ItemStack.matches(before,contents.getItem(slot))||!ItemStack.matches(bagBefore,bag))return;
            var consumed=event.getConsumedItem();
            if(consumed.isEmpty()||consumed.getCount()!=1||!cooling.canFeed(consumed.copy()))return;
            var effects=cooling.effects(consumed.copy()).stream().map(MobEffectInstance::new).toList();float saturation=cooling.saturation(consumed.copy());if(!Float.isFinite(saturation)||saturation<0||saturation>20)throw new IllegalArgumentException("Invalid cooler saturation");
            var remainder=java.util.Objects.requireNonNull(cooling.remainder(consumed.copy())).copy();
            if(!remainder.isEmpty())EventRules.stack(remainder.getCount(),remainder.getMaxStackSize());
            var inventory=new net.minecraft.world.SimpleContainer(36);var expected=new net.minecraft.world.SimpleContainer(36);
            for(int i=0;i<36;i++){inventory.setItem(i,player.getInventory().getItem(i).copy());expected.setItem(i,player.getInventory().getItem(i).copy());}
            if(!remainder.isEmpty()&&!inventory.addItem(remainder).isEmpty())return;
            if(!carried(player,bag)||player.containerMenu!=menuBefore||player.level()!=levelBefore||player.getFoodData().getFoodLevel()!=food||player.getFoodData().getSaturationLevel()!=previousSaturation||contents.revision()!=revision||BackpackData.get(player.server).inventory(player,bag)!=contents||!ItemStack.matches(before,contents.getItem(slot))||!ItemStack.matches(bagBefore,bag))return;
            for(int i=0;i<36;i++)if(!ItemStack.matches(expected.getItem(i),player.getInventory().getItem(i)))return;
            contents.removeItem(slot,1);BackpackData.sync(bag,contents);
            if(!remainder.isEmpty())for(int i=0;i<36;i++)if(!ItemStack.matches(expected.getItem(i),inventory.getItem(i)))player.getInventory().setItem(i,inventory.getItem(i));
            for(var effect:effects)player.addEffect(new MobEffectInstance(effect));
            player.getFoodData().setSaturation(saturation);
            player.playNotifySound(SoundEvents.GENERIC_DRINK,SoundSource.PLAYERS,1,1);
            player.containerMenu.broadcastChanges();
            return;
        }
    }
}

package io.github.slimefun4.forge;

import java.util.List;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

public final class JuiceItem extends Item {
    private final MobEffect effect;
    private final int duration;
    public final int color;
    public JuiceItem(Catalog.Entry entry) {
        super(new Properties().stacksTo(1));
        effect=switch(entry.juiceEffect()) { case "saturation"->MobEffects.SATURATION; case "absorption"->MobEffects.ABSORPTION; default->throw new IllegalArgumentException(entry.id()); };
        duration=entry.juiceDuration(); color=entry.juiceColor();
        if(duration<=0)throw new IllegalArgumentException("Juice duration: "+entry.id());
    }
    public List<MobEffectInstance> effects(ItemStack stack) {
        return stack.hasTag() && (stack.getTag().contains("CustomPotionEffects") || stack.getTag().contains("Potion"))
            ? PotionUtils.getMobEffects(stack) : List.of(new MobEffectInstance(effect,duration,0));
    }
    @Override public int getUseDuration(ItemStack stack) { return 32; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.DRINK; }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        var stack=player.getItemInHand(hand);
        if(player instanceof ServerPlayer server&&!ResearchData.canUse(server,stack)) { ItemAccess.denied(server,stack);return InteractionResultHolder.fail(stack); }
        return ItemUtils.startUsingInstantly(level,player,hand);
    }
    @Override public ItemStack finishUsingItem(ItemStack stack,Level level,LivingEntity entity) {
        if(level.isClientSide || entity.isUsingItem()&&entity.getUseItemRemainingTicks()<0)return stack;
        if(entity instanceof ServerPlayer player&&!ResearchData.canUse(player,stack)) {
            ItemAccess.denied(player,stack);return stack;
        }
        // RC-37 clears the first similar hand before vanilla dispatches the remaining stack.
        entity.addEffect(new MobEffectInstance(effect,duration,0));
        if(entity instanceof Player player) {
            var held=CargoFilter.inventoryMatches(stack,player.getMainHandItem(),true)?player.getMainHandItem():CargoFilter.inventoryMatches(stack,player.getOffhandItem(),true)?player.getOffhandItem():ItemStack.EMPTY;
            if(held.getCount()==1)held.shrink(1);
            else if(!held.isEmpty()) {
                var bottle=new ItemStack(Items.GLASS_BOTTLE);
                for(var stored:player.getInventory().items)if(ItemStack.isSameItemSameTags(stored,bottle)){stored.shrink(1);break;}
            }
        }
        if(stack.isEmpty())return stack;
        if(entity instanceof ServerPlayer player)CriteriaTriggers.CONSUME_ITEM.trigger(player,stack);
        for(var instance:effects(stack)) {
            if(instance.getEffect().isInstantenous())instance.getEffect().applyInstantenousEffect(entity,entity,entity,instance.getAmplifier(),1);
            else entity.addEffect(new MobEffectInstance(instance));
        }
        if(entity instanceof Player player)player.awardStat(Stats.ITEM_USED.get(this));
        if(!(entity instanceof Player player)||!player.getAbilities().instabuild) {
            stack.shrink(1);var bottle=new ItemStack(Items.GLASS_BOTTLE);
            if(stack.isEmpty())return bottle;
            if(entity instanceof Player consumer&&!consumer.addItem(bottle))consumer.drop(bottle,false);
        }
        entity.gameEvent(GameEvent.DRINK);
        return stack;
    }
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag) {
        PotionUtils.addPotionTooltip(effects(stack),lines,1);
    }
}

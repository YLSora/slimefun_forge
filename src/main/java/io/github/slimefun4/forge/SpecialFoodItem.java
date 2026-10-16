package io.github.slimefun4.forge;

import java.util.Objects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

public final class SpecialFoodItem extends Item {
    private final String kind;
    public SpecialFoodItem(Catalog.Entry entry) {
        super(new Properties().food(Objects.requireNonNull(new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft",entry.material()))).getFoodProperties(null))));
        kind=entry.implementation();
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        var stack=player.getItemInHand(hand);
        if(player instanceof ServerPlayer server&&!ResearchData.canUse(server,stack)) { ItemAccess.denied(server,stack);return InteractionResultHolder.fail(stack); }
        return super.use(level,player,hand);
    }
    @Override public ItemStack finishUsingItem(ItemStack stack,Level level,LivingEntity entity) {
        if(entity.isUsingItem()&&entity.getUseItemRemainingTicks()<0)return stack;
        if(entity instanceof ServerPlayer player) {
            if(!ResearchData.canUse(player,stack)){ItemAccess.denied(player,stack);return stack;}
            switch(kind) {
                case "FortuneCookie" -> Messages.send(player,Component.translatable("message.slimefun.fortune_cookie."+player.getRandom().nextInt(14)).withStyle(ChatFormatting.GRAY),false,false);
                case "DietCookie" -> {
                    Messages.chat(player,Component.translatable("message.slimefun.diet_cookie").withStyle(ChatFormatting.YELLOW));
                    player.playNotifySound(SoundEvents.GENERIC_EAT,SoundSource.PLAYERS,1,1);
                    player.removeEffect(MobEffects.LEVITATION);
                    player.addEffect(new MobEffectInstance(MobEffects.LEVITATION,60,1));
                }
                case "MonsterJerky" -> FoodEffects.queueJerky(player);
            }
        }
        return super.finishUsingItem(stack,level,entity);
    }
}

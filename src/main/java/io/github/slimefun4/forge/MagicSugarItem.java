package io.github.slimefun4.forge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

public final class MagicSugarItem extends Item {
    public MagicSugarItem() { super(new Properties()); }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        var stack=player.getItemInHand(hand);
        if(player instanceof ServerPlayer server) {
            if(!ResearchData.canUse(server,stack)){ItemAccess.denied(server,stack);return InteractionResultHolder.fail(stack);}
            if(!player.isCreative())stack.shrink(1);
            server.playNotifySound(SoundEvents.GENERIC_EAT,SoundSource.PLAYERS,1,1);
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,600,3));
            // The following block may open a menu that has no offhand slot.
            player.inventoryMenu.broadcastChanges();
        }
        return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
    }
}

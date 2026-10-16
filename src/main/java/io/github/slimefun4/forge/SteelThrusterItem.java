package io.github.slimefun4.forge;

import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;

public final class SteelThrusterItem extends Item {
    public SteelThrusterItem(){super(new Properties().stacksTo(16));}
    @Override public InteractionResult onItemUseFirst(ItemStack stack,UseOnContext context){return InteractionResult.SUCCESS;}
    @Override public InteractionResult interactLivingEntity(ItemStack stack,Player player,LivingEntity target,InteractionHand hand){return target instanceof net.minecraft.world.entity.animal.Cow?InteractionResult.SUCCESS:InteractionResult.PASS;}
}

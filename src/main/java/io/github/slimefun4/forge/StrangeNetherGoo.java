package io.github.slimefun4.forge;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraftforge.common.ForgeConfigSpec;

/** RC-37 barter drop and its entity interaction, with no vanilla dye fallback. */
public final class StrangeNetherGoo extends Item {
    public static final ForgeConfigSpec CONFIG;
    public static final ForgeConfigSpec.IntValue BARTER_CHANCE;
    private static final org.slf4j.Logger LOGGER=com.mojang.logging.LogUtils.getLogger();
    static {
        var builder=new ForgeConfigSpec.Builder();
        BARTER_CHANCE=builder.comment("RC-37: 1-99 percent. 0 and 100 warn and do not replace drops.").defineInRange("barter-chance",7,0,100);
        CONFIG=builder.build();
    }
    public StrangeNetherGoo() { super(new Properties()); }
    public static ItemStack replaceDrop(LivingEntity source,ItemStack original) {
        if(!(source instanceof Piglin) || source.level().isClientSide || original.isEmpty()) return original;
        int chance=BARTER_CHANCE.get();
        if(chance<1 || chance>=100) {
            LOGGER.warn("The Piglin Bartering chance must be between 1-99% on item: STRANGE_NETHER_GOO"); return original;
        }
        return chance>ThreadLocalRandom.current().nextInt(100)?new ItemStack(SlimefunForge.item("strange_nether_goo")):original;
    }
    @Override public InteractionResult interactLivingEntity(ItemStack stack,Player player,LivingEntity target,InteractionHand hand) {
        if(!(target instanceof Sheep sheep)) return InteractionResult.PASS;
        if(player instanceof ServerPlayer server) {
            if(!ResearchData.canUse(server,stack)) { ItemAccess.denied(server,stack); return InteractionResult.FAIL; }
            if(sheep.hasCustomName()) return InteractionResult.FAIL;
            if(!player.getAbilities().instabuild) stack.shrink(1);
            sheep.addEffect(new MobEffectInstance(MobEffects.POISON,60,2));
            sheep.setColor(DyeColor.PURPLE);
            sheep.setCustomName(Component.translatable("entity.slimefun.tainted_sheep").withStyle(ChatFormatting.DARK_PURPLE));
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }
    @Override public InteractionResult onItemUseFirst(ItemStack stack,UseOnContext context) {
        if(!context.getLevel().getBlockState(context.getClickedPos()).is(BlockTags.ALL_SIGNS)) return InteractionResult.PASS;
        if(context.getPlayer() instanceof ServerPlayer server && !ResearchData.canUse(server,stack)) ItemAccess.denied(server,stack);
        return InteractionResult.SUCCESS;
    }
}

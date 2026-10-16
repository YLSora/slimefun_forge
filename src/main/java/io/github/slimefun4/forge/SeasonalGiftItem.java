package io.github.slimefun4.forge;

import java.util.List;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

public final class SeasonalGiftItem extends Item {
    private static final int[] COLORS={0x00ffff,0,0x0000ff,0xff00ff,0x808080,0x008000,0x00ff00,0x800000,0x000080,0x808000,0xffa500,0x800080,0xff0000,0xc0c0c0,0x008080,0xffffff,0xffff00};
    private final boolean christmas;
    private final List<Catalog.Gift> gifts;
    public SeasonalGiftItem(Catalog.Entry entry) { super(new Properties());christmas=entry.implementation().equals("ChristmasPresent");gifts=List.copyOf(entry.gifts());if(gifts.isEmpty())throw new IllegalArgumentException(entry.id()); }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        var stack=player.getItemInHand(hand);
        if(player instanceof ServerPlayer server&&!ResearchData.canUse(server,stack)){ItemAccess.denied(server,stack);return InteractionResultHolder.fail(stack);}
        return new InteractionResultHolder<>(christmas?InteractionResult.SUCCESS:open(level,player,stack,player.position()),stack);
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        var player=context.getPlayer();if(player==null)return InteractionResult.PASS;
        var pos=context.getClickedPos().relative(context.getClickedFace());
        return open(context.getLevel(),player,context.getItemInHand(),christmas?Vec3.atLowerCornerOf(pos):player.position());
    }
    private InteractionResult open(Level level,Player player,ItemStack stack,Vec3 at) {
        if(player instanceof ServerPlayer server) {
            if(!ResearchData.canUse(server,stack)){ItemAccess.denied(server,stack);return InteractionResult.FAIL;}
            var expected=stack.copy();var origin=player.position();
            var gift=gifts.get(level.random.nextInt(gifts.size()));
            var item=ForgeRegistries.ITEMS.getValue(new ResourceLocation(gift.item()));
            if(item==null||item==Items.AIR)throw new IllegalStateException("Unregistered seasonal gift: "+gift.item());
            var drop=io.github.slimefun4.forge.api.ItemSpawnApi.spawn(server.serverLevel(),server,christmas?io.github.slimefun4.forge.api.ItemSpawnApi.Reason.CHRISTMAS_PRESENT_OPENED:io.github.slimefun4.forge.api.ItemSpawnApi.Reason.EASTER_EGG_OPENED,at,new ItemStack(item,gift.count()),()->server.isAlive()&&server.level()==level&&server.position().equals(origin)&&ItemStack.matches(stack,expected)&&(server.getMainHandItem()==stack||server.getOffhandItem()==stack)&&ResearchData.canUse(server,stack));
            if(drop==null)return InteractionResult.FAIL;
            if(!player.isCreative())stack.shrink(1);
            int amount=christmas?3:2;
            for(int i=0;i<amount;i++) {
                var rocket=new ItemStack(Items.FIREWORK_ROCKET);
                rocket.setHoverName(Component.translatable("item.slimefun.research_firework"));
                var effect=new CompoundTag();effect.putBoolean("Flicker",level.random.nextBoolean());effect.putBoolean("Trail",level.random.nextBoolean());
                effect.putByte("Type",(byte)level.random.nextInt(2));effect.putIntArray("Colors",new int[]{COLORS[level.random.nextInt(COLORS.length)]});
                var effects=new ListTag();effects.add(effect);var fireworks=rocket.getOrCreateTagElement("Fireworks");fireworks.put("Explosions",effects);fireworks.putByte("Flight",(byte)(level.random.nextInt(2)+1));
                var entity=new FireworkRocketEntity(level,player.getX()+level.random.nextInt(amount*2)-amount,player.getY(),player.getZ()+level.random.nextInt(amount*2)-amount,rocket);
                entity.getPersistentData().putBoolean("slimefun_celebration",true);level.addFreshEntity(entity);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @SubscribeEvent public static void fireworkDamage(LivingAttackEvent event) {
        if(event.getSource().getDirectEntity() instanceof FireworkRocketEntity rocket&&rocket.getPersistentData().getBoolean("slimefun_celebration"))event.setCanceled(true);
    }
}

package io.github.slimefun4.forge;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.*;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.level.BlockEvent;

public class EquipmentArmorItem extends ArmorItem {
    private static final java.util.Map<ServerPlayer,Integer> LANDED=new java.util.WeakHashMap<>();
    public static void glideChanged(ServerPlayer player,boolean flying){if(!flying&&player.isFallFlying())LANDED.put(player,player.server.getTickCount());else if(flying)LANDED.remove(player);}
    private static boolean impact(ServerPlayer player){var tick=LANDED.get(player);return player.isFallFlying()||tick!=null&&player.server.getTickCount()==tick;}
    protected final Catalog.Entry entry;
    protected EquipmentArmorItem(Catalog.Entry entry,ArmorItem vanilla){super(vanilla.getMaterial(),vanilla.getType(),new Properties());this.entry=entry;}
    public static Item create(Catalog.Entry entry) {
        var vanilla=(ArmorItem)BuiltInRegistries.ITEM.get(new ResourceLocation("minecraft",entry.material()));
        return vanilla instanceof DyeableLeatherItem?new Colored(entry,vanilla):new EquipmentArmorItem(entry,vanilla);
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        if(player instanceof ServerPlayer p&&!ResearchData.canUse(p,p.getItemInHand(hand))){ItemAccess.denied(p,p.getItemInHand(hand));p.inventoryMenu.sendAllDataToRemote();return InteractionResultHolder.fail(p.getItemInHand(hand));}
        return super.use(level,player,hand);
    }
    @Override public void onArmorTick(ItemStack stack,Level level,Player player) {
        if(!(player instanceof ServerPlayer p)||!p.isAlive()||!ResearchData.canUse(p,stack))return;
        int rainbowInterval=Radiation.rainbowInterval();
        if(entry.implementation().equals("RainbowArmorPiece")&&rainbowInterval>0&&p.server.getTickCount()%rainbowInterval==0&&this instanceof DyeableLeatherItem dyeable) {
            int[] colors={0xB02E26,0xF9801D,0xFED83D,0x80C71F,0x3AB3DA,0x8932B8,0xC74EBD};int color=colors[Math.floorMod(p.server.getTickCount()/rainbowInterval,colors.length)];
            if(dyeable.getColor(stack)!=color)dyeable.setColor(stack,color);
        }
        if(Radiation.armorTick(p)&&entry.effects()!=null)for(var effect:entry.effects()) {
            var type=BuiltInRegistries.MOB_EFFECT.get(new ResourceLocation("minecraft",effect.effect()));
            if(type==null)throw new IllegalStateException("Unknown armor effect "+effect.effect());
            p.removeEffect(type);p.addEffect(new MobEffectInstance(type,effect.duration(),effect.amplifier()));
        }
    }
    private static final class Colored extends EquipmentArmorItem implements DyeableLeatherItem {
        Colored(Catalog.Entry entry,ArmorItem vanilla){super(entry,vanilla);}
        @Override public int getColor(ItemStack stack){return hasCustomColor(stack)?DyeableLeatherItem.super.getColor(stack):entry.armorColor();}
    }
    @SubscribeEvent public static void pearl(EntityTeleportEvent.EnderPearl event) {
        var stack=event.getPlayer().getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET);
        if(stack.getItem() instanceof EquipmentArmorItem item&&item.entry.id().equals("ender_boots")&&ResearchData.canUse(event.getPlayer(),stack))event.setAttackDamage(0);
    }
    @SubscribeEvent public static void trample(BlockEvent.FarmlandTrampleEvent event) {
        if(event.getEntity() instanceof ServerPlayer p&&wears(p,EquipmentSlot.FEET,"farmer_shoes"))event.setCanceled(true);
    }
    private static boolean wears(ServerPlayer p,EquipmentSlot slot,String id){var stack=p.getItemBySlot(slot);return stack.getItem() instanceof EquipmentArmorItem item&&item.entry.id().equals(id)&&ResearchData.canUse(p,stack);}
    @SubscribeEvent public static void damage(LivingAttackEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer p))return;
        boolean fall=event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FALL);
        if((fall||event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FLY_INTO_WALL))&&impact(p)&&wears(p,EquipmentSlot.HEAD,"elytra_cap")) {
            event.setCanceled(true);p.playNotifySound(SoundEvents.STONE_HIT,SoundSource.PLAYERS,1,1);if(!p.isCreative())EquipmentDamage.damage(p,p.getItemBySlot(EquipmentSlot.HEAD),EquipmentSlot.HEAD,true);return;
        }
        var stack=p.getItemBySlot(EquipmentSlot.FEET);if(!fall||!(stack.getItem() instanceof EquipmentArmorItem boots)||!ResearchData.canUse(p,stack))return;
        if(boots.entry.implementation().equals("LongFallBoots")) {
            event.setCanceled(true);p.serverLevel().playSound(null,p.blockPosition(),boots.entry.id().equals("bee_boots")?SoundEvents.HONEY_BLOCK_FALL:SoundEvents.SLIME_BLOCK_FALL,SoundSource.PLAYERS,1,1);
        } else if(boots.entry.id().equals("boots_of_the_stomper")) {
            event.setCanceled(true);p.playNotifySound(SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR,SoundSource.PLAYERS,1,2);FlightGadgets.velocity(p,new Vec3(0,.7,0));
            for(var target:p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,p.getBoundingBox().inflate(4),e->e!=p&&e.isAlive()&&e.isPushable()&&!e.isNoGravity())) {
                Vec3 delta=target.position().subtract(p.position());Vec3 motion=delta.lengthSqr()<.05?new Vec3(0,1,0):delta.normalize().scale(1.4);
                if(target instanceof ServerPlayer other)FlightGadgets.velocity(other,motion);else{target.setDeltaMovement(motion);target.hurtMarked=true;}
                if(!(target instanceof Player other)||p.server.isPvpAllowed()&&p.canHarmPlayer(other))target.hurt(p.damageSources().playerAttack(p),event.getAmount()/2);
            }
            for(var pos:net.minecraft.core.BlockPos.betweenClosed(p.blockPosition().below().offset(-1,0,-1),p.blockPosition().below().offset(1,0,1)))if(p.level().hasChunkAt(pos))p.level().levelEvent(2001,pos,net.minecraft.world.level.block.Block.getId(p.level().getBlockState(pos)));
        }
    }
}

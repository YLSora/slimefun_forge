package io.github.slimefun4.forge;

import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.common.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/** Persistent exposure belongs to the player; post-death grace belongs to one running server. */
final class Radiation {
    private static final String EXPOSURE="slimefun_radiation";
    private static final ForgeConfigSpec CONFIG;
    private static final ForgeConfigSpec.BooleanValue ARMOR_ENABLED,RADIATION_ENABLED;
    private static final ForgeConfigSpec.IntValue ARMOR_INTERVAL,RAINBOW_INTERVAL,RADIATION_INTERVAL,GRACE_SECONDS;
    private static final Map<MinecraftServer,Map<UUID,Long>> GRACE=new IdentityHashMap<>();
    static {
        var builder=new ForgeConfigSpec.Builder();
        ARMOR_ENABLED=builder.define("enable-armor-effects",true);
        ARMOR_INTERVAL=builder.comment("Seconds between potion refreshes; RC-37 solar helmets use this same value in game ticks.").defineInRange("armor-update-interval",10,1,Integer.MAX_VALUE/20-1);
        RAINBOW_INTERVAL=builder.comment("Seconds between rainbow armor color changes.").defineInRange("rainbow-armor-update-interval",3,1,Integer.MAX_VALUE/20);
        RADIATION_ENABLED=builder.define("enable-radiation",true);
        RADIATION_INTERVAL=builder.defineInRange("radiation-update-interval",1,1,Integer.MAX_VALUE/20-1);
        GRACE_SECONDS=builder.defineInRange("radiation-grace-period",15,0,Integer.MAX_VALUE);
        CONFIG=builder.build();
    }
    static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER,CONFIG,"slimefun_forge/slimefun-radiation-server.toml");
        MinecraftForge.EVENT_BUS.register(Radiation.class);
    }
    static void clear(LivingEntity entity) { entity.getPersistentData().remove(EXPOSURE); }
    static boolean armorTick(ServerPlayer player){return ARMOR_ENABLED.get()&&player.tickCount%(ARMOR_INTERVAL.get()*20)==0;}
    static boolean solarTick(ServerPlayer player){return ARMOR_ENABLED.get()&&player.tickCount%ARMOR_INTERVAL.get()==0;}
    static int rainbowInterval(){return ARMOR_ENABLED.get()?RAINBOW_INTERVAL.get()*20:0;}
    static int exposure(ServerPlayer player) { return Math.max(0,Math.min(100,player.getPersistentData().getInt(EXPOSURE))); }
    static void setExposure(ServerPlayer player,int value) {
        if(value==0) clear(player); else player.getPersistentData().putInt(EXPOSURE,value);
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || !player.isAlive() || player.tickCount<=0 || !ARMOR_ENABLED.get()) return;
        if(armorTick(player)) {
            armorEffect(player,EquipmentSlot.HEAD,MobEffects.WATER_BREATHING);
            armorEffect(player,EquipmentSlot.CHEST,MobEffects.FIRE_RESISTANCE);
        }
        int interval=RADIATION_INTERVAL.get()*20;
        if(!RADIATION_ENABLED.get() || player.tickCount%interval!=0) return;
        var grace=GRACE.get(player.server);
        if(grace!=null) {
            var until=grace.get(player.getUUID());
            if(until!=null) { if(until>=System.currentTimeMillis()) return; grace.remove(player.getUUID()); }
        }
        int before=exposure(player);
        if(player.isCreative() || player.isSpectator() || AddonItems.protectedFrom(player,io.github.slimefun4.forge.api.ItemApi.Protection.RADIATION)) { setExposure(player,Math.max(0,before-1)); return; }
        int total=0;
        for(int i=0;i<player.getInventory().getContainerSize();i++) {
            var stack=player.getInventory().getItem(i);
            if(!stack.isEmpty() && stack.getItem() instanceof io.github.slimefun4.forge.api.ItemApi.Radioactive item&&ItemAccess.enabled(player.level(),stack))total=ItemRules.exposure(total,item.radiation(stack.copy()),stack.getCount());
        }
        int after=total>0?ItemRules.exposure(before,total,1):Math.max(0,before-1);
        setExposure(player,after);
        if(before==0 && after>0) Messages.chat(player,Component.translatable("message.slimefun.radiation"));
        if(after>=10) player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,interval+20,3));
        if(after>=25) player.addEffect(new MobEffectInstance(MobEffects.WITHER,interval+20,after>=75?3:0));
        if(after>=50) player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,interval+20,4));
        if(after>=100) player.addEffect(new MobEffectInstance(MobEffects.HARM,interval+20,49));
        if(before>0 || after>0) Messages.send(player,Component.translatable("message.slimefun.radiation_level",after),true,false);
    }
    private static void armorEffect(ServerPlayer player,EquipmentSlot slot,MobEffect effect) {
        var stack=player.getItemBySlot(slot);
        if(stack.getItem() instanceof HazmatArmorItem armor && armor.getEquipmentSlot()==slot && ResearchData.canUse(player,stack)) {
            player.removeEffect(effect); player.addEffect(new MobEffectInstance(effect,300,1));
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void death(LivingDeathEvent event) {
        if(event.getEntity() instanceof ServerPlayer player) {
            clear(player);
            GRACE.computeIfAbsent(player.server,key->new HashMap<>()).put(player.getUUID(),System.currentTimeMillis()+GRACE_SECONDS.get()*1000L);
        }
    }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        if(event.getEntity() instanceof ServerPlayer player && event.getOriginal() instanceof ServerPlayer old)
            setExposure(player,event.isWasDeath()?0:exposure(old));
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { GRACE.remove(event.getServer()); }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void bee(LivingHurtEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer player) || !(event.getSource().getDirectEntity() instanceof Bee) || !AddonItems.protectedFrom(player,io.github.slimefun4.forge.api.ItemApi.Protection.BEES)) return;
        event.setAmount(0);
        for(var stack:player.getArmorSlots()) {
            if(!stack.isDamageableItem())continue;
            int unbreaking=EnchantmentHelper.getItemEnchantmentLevel(Enchantments.UNBREAKING,stack);
            // Dough 0130f8d9ce intentionally differs from vanilla armor wear and breaks strictly above max.
            if(unbreaking>0 && player.getRandom().nextDouble()*100<=60+40/(unbreaking+1)) continue;
            int damage=stack.getDamageValue()+1;
            if(damage>stack.getMaxDamage()) { var slot=LivingEntity.getEquipmentSlotForItem(stack); stack.shrink(1); player.broadcastBreakEvent(slot); }
            else stack.setDamageValue(damage);
        }
    }
}

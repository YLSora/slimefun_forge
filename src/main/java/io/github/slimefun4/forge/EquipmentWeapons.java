package io.github.slimefun4.forge;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.eventbus.api.*;

public final class EquipmentWeapons {
    private static final String BOW="slimefun_bow";
    private static final Set<ServerPlayer> QUAKING=Collections.newSetFromMap(new IdentityHashMap<>());
    @SubscribeEvent public static void restoreVisual(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof FallingBlockEntity falling && falling.getPersistentData().getBoolean("slimefun_seismic_visual")) falling.disableDrop();
    }
    public static Item create(Catalog.Entry entry) {
        return switch(entry.material()) {
            case "stick" -> new Item(new Item.Properties());
            case "iron_sword" -> new SwordItem(Tiers.IRON,3,-2.4F,new Item.Properties());
            case "golden_sword" -> new SwordItem(Tiers.GOLD,3,-2.4F,new Item.Properties());
            case "iron_axe" -> new Seismic();
            case "bow" -> new Bow(entry.id());
            default -> throw new IllegalArgumentException("Unknown weapon "+entry.id());
        };
    }
    public static final class Bow extends BowItem {
        private final String kind;
        Bow(String kind){super(new Properties().durability(384));this.kind=kind;}
        @Override public AbstractArrow customArrow(AbstractArrow arrow){arrow.getPersistentData().putString(BOW,kind);return arrow;}
        @Override public void releaseUsing(ItemStack stack,Level level,LivingEntity entity,int time){if(entity instanceof ServerPlayer p&&!ResearchData.canUse(p,stack))return;super.releaseUsing(stack,level,entity,time);}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public static void hit(LivingHurtEvent event) {
        var target=event.getEntity();if(!(target.level() instanceof ServerLevel level))return;
        if(!event.isCanceled()&&event.getSource().is(DamageTypes.PLAYER_ATTACK)&&event.getSource().getDirectEntity() instanceof ServerPlayer p) {
            var stack=p.getMainHandItem();
            if(SpecialTools.kind(stack).equals("blade_of_vampires")&&ResearchData.canUse(p,stack)&&p.getRandom().nextInt(100)<MagicSettings.VAMPIRE_CHANCE.get()){p.setHealth(Math.min(p.getMaxHealth(),p.getHealth()+4));p.playNotifySound(SoundEvents.ARROW_HIT_PLAYER,SoundSource.PLAYERS,.7F,.7F);}
        }
        if(!event.getSource().is(DamageTypes.ARROW)||!(event.getSource().getDirectEntity() instanceof AbstractArrow arrow))return;
        var data=arrow.getPersistentData();String kind=data.getString(BOW);data.remove(BOW);
        if(event.isCanceled()||data.contains(BOW+"_expires")&&level.getGameTime()>=data.getLong(BOW+"_expires"))return;
        if(kind.equals("icy_bow")){frozen(target);return;}
        if(!kind.equals("explosive_bow"))return;
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION,target.getX(),target.getY(),target.getZ(),1,0,0,0,0);
        level.playSound(null,target.blockPosition(),SoundEvents.GENERIC_EXPLODE,SoundSource.PLAYERS,1,1);
        int radius=MagicSettings.EXPLOSION_RADIUS.get();
        for(var other:level.getEntitiesOfClass(LivingEntity.class,new AABB(target.position(),target.position()).inflate(radius),e->e!=target&&e.isAlive()&&!(e instanceof ArmorStand))) {
            Vec3 distance=other.position().subtract(target.position()).add(0,.75,0);
            float amount=event.getAmount()*(float)(1-distance.lengthSqr()/(2.0*radius*radius));
            if(amount<=0||!canDamage(arrow.getOwner(),other))continue;
            if(other.hurt(target.damageSources().explosion(arrow,arrow.getOwner()),amount))motion(other,other.getDeltaMovement().add(new Vec3(distance.x,.75,distance.z).normalize().scale(2)));
        }
    }
    @SubscribeEvent public static void impact(net.minecraftforge.event.entity.ProjectileImpactEvent event){if(event.getProjectile() instanceof AbstractArrow arrow&&!arrow.level().isClientSide&&arrow.getPersistentData().contains(BOW)&&!arrow.getPersistentData().contains(BOW+"_expires"))arrow.getPersistentData().putLong(BOW+"_expires",arrow.level().getGameTime()+4);}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void shield(ShieldBlockEvent event){if(event.getBlockedDamage()>=event.getOriginalBlockedDamage()&&event.getDamageSource().getDirectEntity() instanceof AbstractArrow arrow)arrow.getPersistentData().remove(BOW);}
    private static void frozen(LivingEntity target) {
        if(target instanceof Player)target.setTicksFrozen(60);
        target.level().levelEvent(2001,target.blockPosition(),Block.getId(Blocks.ICE.defaultBlockState()));target.level().levelEvent(2001,BlockPos.containing(target.getEyePosition()),Block.getId(Blocks.ICE.defaultBlockState()));
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,40,10));target.addEffect(new MobEffectInstance(MobEffects.JUMP,40,-10));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void heads(LivingDropsEvent event) {
        if(!(event.getSource().getEntity() instanceof ServerPlayer p))return;
        var stack=p.getMainHandItem();if(!SpecialTools.kind(stack).equals("sword_of_beheading")||!ResearchData.canUse(p,stack))return;
        var victim=event.getEntity();var type=victim.getType();Item item;
        if(type==EntityType.ZOMBIE)item=Items.ZOMBIE_HEAD;
        else if(type==EntityType.SKELETON)item=Items.SKELETON_SKULL;
        else if(type==EntityType.CREEPER)item=Items.CREEPER_HEAD;
        else if(type==EntityType.PIGLIN)item=Items.PIGLIN_HEAD;
        else if(type==EntityType.WITHER_SKELETON)item=Items.WITHER_SKELETON_SKULL;
        else if(victim instanceof Player)item=Items.PLAYER_HEAD;
        else return;
        int chance=MagicSettings.BEHEADING_CHANCES.get(victim instanceof Player?"PLAYER":EntityType.getKey(type).getPath().toUpperCase(Locale.ROOT)).get();
        if(p.getRandom().nextInt(100)>=chance)return;
        var head=new ItemStack(item);if(victim instanceof Player owner)head.getOrCreateTag().put("SkullOwner",NbtUtils.writeGameProfile(new CompoundTag(),owner.getGameProfile()));
        event.getDrops().add(new ItemEntity(victim.level(),victim.getX(),victim.getY(),victim.getZ(),head));
    }
    private static boolean canDamage(Entity owner,LivingEntity target){return !(target instanceof Player other)||owner instanceof ServerPlayer p&&p.server.isPvpAllowed()&&p.canHarmPlayer(other);}
    private static void motion(LivingEntity entity,Vec3 velocity){if(entity instanceof ServerPlayer p)FlightGadgets.velocity(p,velocity);else{entity.setDeltaMovement(velocity);entity.hurtMarked=true;}}
    private static final class Seismic extends AxeItem {
        Seismic(){super(Tiers.IRON,6,-3.1F,new Properties());}
        @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){activate(player,hand);return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide);}
        @Override public InteractionResult onItemUseFirst(ItemStack stack,UseOnContext context){activate(context.getPlayer(),context.getHand());return InteractionResult.PASS;}
        private void activate(Player player,InteractionHand hand) {
            if(!(player instanceof ServerPlayer p))return;var tool=p.getItemInHand(hand);if(tool.getItem()!=this||!ResearchData.canUse(p,tool)||!QUAKING.add(p))return;
            try {
            var level=p.serverLevel();var blocks=new ArrayList<BlockPos>();Vec3 start=p.getEyePosition(),end=start.add(p.getLookAngle().scale(10));
            BlockGetter.traverseBlocks(start,end,blocks,(list,pos)->{if(!SpecialTools.available(level,pos))return list;list.add(pos.immutable());return level.getBlockState(pos).isAir()?null:list;},list->list);
            var pushed=new HashSet<UUID>();
            for(int i=2;i<blocks.size();i++) {
                if(!p.isAlive()||p.serverLevel()!=level||p.getItemInHand(hand)!=tool||!ResearchData.canUse(p,tool))break;
                var ground=blocks.get(i);while(ground.getY()>level.getMinBuildHeight()+1&&level.getBlockState(ground).isAir())ground=ground.below();
                var state=level.getBlockState(ground);if(state.isAir())continue;level.levelEvent(2001,ground,Block.getId(state));
                if(level.getBlockState(ground.above()).isAir()) {
                    var visual=new FallingBlockEntity(EntityType.FALLING_BLOCK,level);var tag=visual.saveWithoutId(new CompoundTag());tag.put("BlockState",NbtUtils.writeBlockState(state));tag.putBoolean("DropItem",false);tag.putBoolean("HurtEntities",false);tag.putInt("Time",1);visual.load(tag);visual.getPersistentData().putBoolean("slimefun_seismic_visual",true);visual.disableDrop();
                    visual.setPos(ground.getX()+.5,ground.getY()+1,ground.getZ()+.5);visual.setStartPos(ground.above());visual.setDeltaMovement(0,.4+i*.01,0);level.addFreshEntity(visual);
                }
                var origin=Vec3.atLowerCornerOf(ground);
                for(var target:level.getEntitiesOfClass(LivingEntity.class,new AABB(origin,origin).inflate(1.5),e->e!=p&&e.isAlive()&&!(e instanceof ArmorStand))) {
                    if(target.chunkPosition().x!=(ground.getX()>>4)||target.chunkPosition().z!=(ground.getZ()>>4)||target.position().distanceToSqr(origin)>=2.25||target.distanceToSqr(p)<=.04||!pushed.add(target.getUUID())||!canDamage(p,target))continue;
                    var push=target.position().subtract(p.position()).normalize().scale(1.2);if(target.hurt(p.damageSources().playerAttack(p),6))motion(target,new Vec3(push.x,.9,push.z));
                }
            }
            for(int i=0;i<4;i++)EquipmentDamage.damage(p,tool,hand==InteractionHand.MAIN_HAND?EquipmentSlot.MAINHAND:EquipmentSlot.OFFHAND,false);
            p.inventoryMenu.broadcastChanges();
            } finally {QUAKING.remove(p);}
        }
    }
    private EquipmentWeapons(){}
}

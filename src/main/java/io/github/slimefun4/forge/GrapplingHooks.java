package io.github.slimefun4.forge;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class GrapplingHooks {
    private static final Map<ServerPlayer,Hook> ACTIVE=new WeakHashMap<>();
    private static final Set<ServerPlayer> LAUNCHING=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final class Hook {
        final Arrow arrow;final Bat bat;final boolean refund;final long expires;long impact=Long.MAX_VALUE,release=Long.MAX_VALUE;boolean finished;
        Hook(Arrow arrow,Bat bat,boolean refund,long expires){this.arrow=arrow;this.bat=bat;this.refund=refund;this.expires=expires;}
        void remove(){arrow.discard();bat.discard();}
    }
    static void launch(ServerPlayer p,InteractionHand hand) {
        if(ACTIVE.containsKey(p)||p.getOffhandItem().getItem() instanceof BowItem||!LAUNCHING.add(p))return;
        try {
        var stack=p.getItemInHand(hand);boolean refund=stack.is(SlimefunForge.item("grappling_hook"))&&!p.isCreative()&&MagicSettings.CONSUME_HOOK.get();
        var expected=stack.copy();var level=p.serverLevel();
        var direction=p.getLookAngle().scale(2);var at=p.getEyePosition().add(direction);var arrow=new Arrow(p.level(),at.x,at.y,at.z);arrow.setOwner(p);arrow.setDeltaMovement(direction);arrow.pickup=AbstractArrow.Pickup.DISALLOWED;
        var bat=new Bat(EntityType.BAT,p.level());bat.setPos(p.position());bat.setInvulnerable(true);bat.setNoAi(true);bat.setSilent(true);bat.setInvisible(true);
        // Spawn callbacks may reject, replace the held stack, or reenter; pay only after both entities commit.
        if(!level.addFreshEntity(arrow)||!level.addFreshEntity(bat)||!arrow.isAlive()||!bat.isAlive()||!p.isAlive()||p.hasDisconnected()||p.serverLevel()!=level||p.getItemInHand(hand)!=stack||!ItemStack.matches(stack,expected)||!ResearchData.canUse(p,stack)){arrow.discard();bat.discard();return;}
        if(refund)stack.shrink(1);
        bat.setLeashedTo(arrow,true);ACTIVE.put(p,new Hook(arrow,bat,refund,(long)p.server.getTickCount()+MagicSettings.DESPAWN_TICKS.get()));
        } finally {LAUNCHING.remove(p);}
    }
    @SubscribeEvent public static void impact(ProjectileImpactEvent event) {
        if(event.getProjectile() instanceof Arrow arrow&&arrow.getOwner() instanceof ServerPlayer p){var hook=ACTIVE.get(p);if(hook!=null&&hook.arrow==arrow&&!hook.finished)hook.impact=Math.min(hook.impact,p.server.getTickCount()+2);}
    }
    @SubscribeEvent public static void hitEntity(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        if(event.getSource().getDirectEntity() instanceof Arrow arrow&&arrow.getOwner() instanceof ServerPlayer p){var hook=ACTIVE.get(p);if(hook!=null&&hook.arrow==arrow&&!hook.finished)pull(p,hook);}
    }
    @SubscribeEvent public static void portal(EntityTravelToDimensionEvent event) {
        if(event.getEntity() instanceof Arrow arrow&&arrow.getOwner() instanceof ServerPlayer p){var hook=ACTIVE.get(p);if(hook!=null&&hook.arrow==arrow){event.setCanceled(true);hook.impact=p.server.getTickCount();}}
    }
    private static void pull(ServerPlayer p,Hook hook) {
        Vec3 target=hook.arrow.position();
        if(hook.refund){var drop=new ItemEntity(p.level(),target.x,target.y,target.z,new ItemStack(SlimefunForge.item("grappling_hook")));drop.setPickUpDelay(16);p.serverLevel().addFreshEntity(drop);}
        Vec3 velocity=new Vec3(0,.2,0);
        if(p.position().distanceTo(target)<3){if(target.y<=p.getY())velocity=target.subtract(p.position());}
        else {
            p.teleportTo(p.getX(),p.getY()+.5,p.getZ());p.connection.resetPosition();
            var delta=target.subtract(p.position());double distance=delta.length();
            if(distance>0)velocity=new Vec3((1+.08*distance)*delta.x/distance,(1+.04*distance)*delta.y/distance+.04*distance,(1+.08*distance)*delta.z/distance);
        }
        FlightGadgets.velocity(p,velocity);hook.finished=true;hook.release=p.server.getTickCount()+20;hook.remove();
    }
    // Run after ordinary END-phase interaction dispatch so zero-tick hooks expire in that tick.
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOW) public static void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;long now=event.getServer().getTickCount();var it=ACTIVE.entrySet().iterator();
        while(it.hasNext()) {
            var entry=it.next();var p=entry.getKey();var hook=entry.getValue();if(p.server!=event.getServer())continue;
            if(p.hasDisconnected()||!p.isAlive()||p.level()!=hook.arrow.level()||!p.level().hasChunkAt(hook.arrow.blockPosition())){hook.remove();it.remove();continue;}
            if(hook.finished){if(now>=hook.release)it.remove();continue;}
            if(now>=hook.impact&&hook.arrow.isAlive())pull(p,hook);
            else if(now>=hook.expires||!hook.arrow.isAlive()){hook.remove();hook.finished=true;hook.release=now+20;}
        }
    }
    private static void clear(net.minecraft.world.entity.player.Player p){var hook=ACTIVE.remove(p);if(hook!=null)hook.remove();}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){clear(event.getEntity());}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event){clear(event.getEntity());}
    @SubscribeEvent public static void stop(ServerStoppedEvent event){var it=ACTIVE.entrySet().iterator();while(it.hasNext()){var e=it.next();if(e.getKey().server==event.getServer()){e.getValue().remove();it.remove();}}}
    private GrapplingHooks(){}
}

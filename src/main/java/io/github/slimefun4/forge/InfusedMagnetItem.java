package io.github.slimefun4.forge;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class InfusedMagnetItem extends Item {
    private record Task(int start,net.minecraft.server.level.ServerLevel level,double radius){}
    private static final Map<ServerPlayer,Task> ACTIVE=new WeakHashMap<>();
    public InfusedMagnetItem(){super(new Properties());}
    public static void crouch(ServerPlayer player,boolean crouching) {
        ACTIVE.remove(player);if(!crouching||!player.isAlive()||player.isSpectator())return;
        for(int i=0;i<player.getInventory().getContainerSize();i++){var stack=player.getInventory().getItem(i);if(stack.getItem() instanceof InfusedMagnetItem&&ResearchData.canUse(player,stack)){ACTIVE.put(player,new Task(player.server.getTickCount()+1,player.serverLevel(),MagicSettings.MAGNET_RADIUS.get()));break;}}
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!(event.player instanceof ServerPlayer p))return;var task=ACTIVE.get(p);if(task==null)return;
        if(!p.isAlive()||!p.isShiftKeyDown()||p.isSpectator()||p.serverLevel()!=task.level()){ACTIVE.remove(p);return;}
        int age=p.server.getTickCount()-task.start();if(age<0||age%8!=0)return;boolean moved=false;
        for(var item:p.serverLevel().getEntitiesOfClass(ItemEntity.class,p.getBoundingBox().inflate(task.radius()),e->e.isAlive()&&!e.hasPickUpDelay()&&!e.getPersistentData().contains("slimefun:no_pickup")&&e.distanceToSqr(p)>.3)){item.teleportTo(p.getX(),p.getY(),p.getZ());moved=true;}
        if(moved)p.playNotifySound(SoundEvents.ENDERMAN_TELEPORT,SoundSource.PLAYERS,.25F,.9F);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){ACTIVE.remove(event.getEntity());}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event){ACTIVE.remove(event.getEntity());}
    @SubscribeEvent public static void stop(ServerStoppedEvent event){ACTIVE.keySet().removeIf(p->p.server==event.getServer());}
}

package io.github.slimefun4.forge;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;
import io.github.slimefun4.forge.api.ResearchUnlockEvent;

/** Paid learning survives logout and normal restart in the native player save. */
final class GuideResearch {
    private record Learning(Catalog.Research research,long due) {}
    private static final Map<ServerPlayer,Learning> ACTIVE=new IdentityHashMap<>();
    private static final Set<ServerPlayer> REQUESTING=Collections.newSetFromMap(new IdentityHashMap<>());
    private record Deferred(ServerPlayer player,Catalog.Research research,long due,boolean progress,boolean paid) {}
    private static final List<Deferred> DEFERRED=new ArrayList<>();
    static void share(ServerPlayer player,Catalog.Research research){
        if(!MinecraftForge.EVENT_BUS.post(new ResearchUnlockEvent(player,new net.minecraft.resources.ResourceLocation(AddonContent.commandId(research)))))DEFERRED.add(new Deferred(player,research,(long)player.server.getTickCount()+1,false,false));
    }
    static void cancel(ServerPlayer player){ACTIVE.remove(player);DEFERRED.removeIf(d->d.player()==player);var settings=Guide.preferences(player);settings.remove("learning");settings.remove("learning_due");}
    static boolean cancel(ServerPlayer player,Catalog.Research research){
        boolean changed=DEFERRED.removeIf(d->d.player()==player&&d.research().id().equals(research.id()));
        var active=ACTIVE.get(player);if(active!=null&&active.research().id().equals(research.id())){ACTIVE.remove(player);changed=true;}
        var settings=Guide.preferences(player);if(settings.getString("learning").equals(research.id())){settings.remove("learning");settings.remove("learning_due");changed=true;}
        return changed;
    }
    static boolean unlock(ServerPlayer player,Catalog.Research research,ItemStack source){
        var data=ResearchData.get(player);if(!ResearchSettings.enabled(research)||data.completed(player,research)||ACTIVE.containsKey(player)||DEFERRED.stream().anyMatch(d->d.player()==player&&d.paid())||!REQUESTING.add(player))return false;
        try{
        var pre=new io.github.slimefun4.forge.api.PlayerPreResearchEvent(player,new net.minecraft.resources.ResourceLocation(AddonContent.commandId(research)),net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(source.getItem()));
        if(MinecraftForge.EVENT_BUS.post(pre)||data.completed(player,research))return false;
        String id=ItemAccess.id(source);if(id==null||!ItemAccess.enabled(player.level(),id)||!ItemAccess.permitted(player,id)||!ResearchSettings.enabled(research))return false;
        if(!data.pay(player,research)){Messages.chat(player,Component.translatable("message.slimefun.not_enough_xp"));return false;}
        boolean instant=player.isCreative()&&Guide.FREE_CREATIVE.get()||Guide.NO_ANIMATION.get()||!Guide.option(player,"animation");
        if(instant){if(MinecraftForge.EVENT_BUS.post(new ResearchUnlockEvent(player,new net.minecraft.resources.ResourceLocation(AddonContent.commandId(research)))))return false;DEFERRED.add(new Deferred(player,research,(long)player.server.getTickCount()+1,false,true));return true;}
        // The original queues the initial animation even when the unlock event is cancelled.
        DEFERRED.add(new Deferred(player,research,(long)player.server.getTickCount()+5,true,false));
        if(MinecraftForge.EVENT_BUS.post(new ResearchUnlockEvent(player,new net.minecraft.resources.ResourceLocation(AddonContent.commandId(research)))))return false;
        long due=player.server.overworld().getGameTime()+100;
        var settings=Guide.preferences(player);settings.putString("learning",research.id());settings.putLong("learning_due",due);
        ACTIVE.put(player,new Learning(research,due));Messages.chat(player,Guide.text(player,"learning_start",Component.translatable(AddonContent.researchKey(research))));
        return true;
        }finally{REQUESTING.remove(player);}
    }
    private static void progress(ServerPlayer player,Catalog.Research research,int percent){
        player.playNotifySound(SoundEvents.BAT_TAKEOFF,SoundSource.PLAYERS,.7F,1);
        Messages.chat(player,Guide.text(player,"learning",Component.translatable(AddonContent.researchKey(research)),percent+"%"));
    }
    static void finish(ServerPlayer player,Catalog.Research research,boolean given){
        if(Guide.preferences(player).getString("learning").equals(research.id()))cancel(player);
        if(ResearchData.get(player).completed(player,research))return;complete(player,research,given);
    }
    private static void complete(ServerPlayer player,Catalog.Research research,boolean given){
        ResearchData.get(player).grant(player,research);
        Messages.chat(player,Guide.text(player,"unlocked",Component.translatable(AddonContent.researchKey(research))));
        if(given)Messages.chat(player,Component.translatable("command.slimefun.researched",player.getName(),Component.translatable(AddonContent.researchKey(research))));
        if(Guide.FIREWORKS.get()&&Guide.option(player,"fireworks")){
            var random=player.getRandom();var rocket=new ItemStack(Items.FIREWORK_ROCKET);var explosion=new CompoundTag();
            explosion.putByte("Type",(byte)random.nextInt(2));explosion.putBoolean("Flicker",random.nextBoolean());explosion.putBoolean("Trail",random.nextBoolean());
            explosion.putIntArray("Colors",new int[]{DyeColor.values()[random.nextInt(DyeColor.values().length)].getFireworkColor()});
            var effects=new ListTag();effects.add(explosion);var tag=rocket.getOrCreateTagElement("Fireworks");tag.putByte("Flight",(byte)0);tag.put("Explosions",effects);
            var entity=new FireworkRocketEntity(player.level(),player.getX()+random.nextInt(2)-1,player.getY(),player.getZ()+random.nextInt(2)-1,rocket);
            entity.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            // A native client explosion has zero flight and no server entity capable of applying damage.
            for(var viewer:player.serverLevel().players())if(viewer.distanceToSqr(entity.position())<=4096){
                viewer.connection.send(entity.getAddEntityPacket());
                viewer.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket(entity.getId(),entity.getEntityData().getNonDefaultValues()));
                viewer.connection.send(new net.minecraft.network.protocol.game.ClientboundEntityEventPacket(entity,(byte)17));
                viewer.connection.send(new net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket(entity.getId()));
            }
        }
        if(player.containerMenu instanceof GuideMenu menu)menu.refresh();
    }
    private static void resume(ServerPlayer player){
        var settings=Guide.preferences(player);String id=settings.getString("learning");if(id.isEmpty())return;
        var research=ResearchData.all().stream().filter(r->r.id().equals(id)).findFirst().orElseThrow(()->new IllegalStateException("Unknown paid research "+id));
        ACTIVE.put(player,new Learning(research,settings.getLong("learning_due")));
    }
    @SubscribeEvent public static void joined(PlayerEvent.PlayerLoggedInEvent event){if(event.getEntity() instanceof ServerPlayer player)resume(player);}
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event){if(event.getEntity() instanceof ServerPlayer player)resume(player);}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity() instanceof ServerPlayer player)ACTIVE.remove(player);}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){ACTIVE.keySet().removeIf(p->p.server==event.getServer());DEFERRED.removeIf(s->s.player().server==event.getServer());}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase==TickEvent.Phase.START&&!DEFERRED.isEmpty()){
            var ready=new ArrayList<Deferred>();
            for(var iterator=DEFERRED.iterator();iterator.hasNext();){var shared=iterator.next();if(shared.player().server==event.getServer()&&shared.due()<=event.getServer().getTickCount()){ready.add(shared);iterator.remove();}}
            // Each accepted use completes separately, as in the original instant research tasks.
            for(var shared:ready)if(shared.progress())progress(shared.player(),shared.research(),0);else complete(shared.player(),shared.research(),false);
        }
        if(event.phase!=TickEvent.Phase.END)return;
        for(var iterator=ACTIVE.entrySet().iterator();iterator.hasNext();){
            var entry=iterator.next();var player=entry.getKey();if(player.server!=event.getServer())continue;
            if(player.isRemoved()||!player.isAlive()){iterator.remove();continue;}
            var learning=entry.getValue();long left=learning.due()-player.server.overworld().getGameTime();
            if(left<=0){iterator.remove();var settings=Guide.preferences(player);settings.remove("learning");settings.remove("learning_due");complete(player,learning.research(),false);}
            else if(left==80||left==60||left==40||left==20)progress(player,learning.research(),switch((int)left){case 80->23;case 60->44;case 40->57;default->92;});
        }
    }
    private GuideResearch() {}
}

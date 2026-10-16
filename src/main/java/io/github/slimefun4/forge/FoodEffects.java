package io.github.slimefun4.forge;

import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Only food effects explicitly waiting for the next server tick are visited. */
public final class FoodEffects {
    private record Pending(int tick,ServerPlayer player,ItemStack cooler,boolean jerky) {}
    private static final Map<MinecraftServer,List<Pending>> PENDING=new IdentityHashMap<>();
    static void queueCooler(ServerPlayer player,ItemStack cooler) { queue(player,cooler,false); }
    static void queueJerky(ServerPlayer player) { queue(player,ItemStack.EMPTY,true); }
    private static void queue(ServerPlayer player,ItemStack cooler,boolean jerky) {
        var server=player.server;
        PENDING.computeIfAbsent(server,s->new ArrayList<>()).add(new Pending(server.getTickCount()+1,player,cooler,jerky));
    }
    @SubscribeEvent(receiveCanceled=true) public static void starvation(LivingAttackEvent event) {
        if(event.getEntity() instanceof ServerPlayer player&&event.getSource().is(DamageTypes.STARVE))CoolerItem.request(player);
    }
    @SubscribeEvent(receiveCanceled=true) public static void foodChange(StaffFoodEvent event) {
        if(event.getEntity() instanceof ServerPlayer player&&event.getFood()<player.getFoodData().getFoodLevel())CoolerItem.request(player);
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.START)return;
        var server=event.getServer();var tasks=PENDING.remove(server);if(tasks==null)return;
        for(var task:tasks) {
            if(task.tick>server.getTickCount()){PENDING.computeIfAbsent(server,s->new ArrayList<>()).add(task);continue;}
            var player=task.player;
            if(!player.isAlive()||server.getPlayerList().getPlayer(player.getUUID())!=player)continue;
            if(!task.jerky)CoolerItem.feed(player,task.cooler);
            else { player.removeEffect(MobEffects.HUNGER);player.addEffect(new MobEffectInstance(MobEffects.SATURATION,5,0)); }
        }
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { PENDING.remove(event.getServer()); }
    private FoodEffects() {}
}

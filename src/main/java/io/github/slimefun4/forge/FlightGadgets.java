package io.github.slimefun4.forge;

import java.util.*;
import io.github.slimefun4.forge.api.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Only a crouch transition starts a task; empty batteries stay stopped until the next transition. */
public final class FlightGadgets {
    private record Flight(net.minecraft.world.item.ItemStack stack,EquipmentSlot slot,long start,net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {}
    private static final Map<ServerPlayer,List<Flight>> ACTIVE=new WeakHashMap<>();
    public static void crouch(ServerPlayer player,boolean crouching) {
        ACTIVE.remove(player);if(!crouching||!player.isAlive()||player.isSpectator())return;
        var tasks=new ArrayList<Flight>();
        for(var slot:List.of(EquipmentSlot.CHEST,EquipmentSlot.FEET)) {
            var stack=player.getItemBySlot(slot);
            if(stack.getItem() instanceof EquipmentApi.FlightGear gear&&gear.flight(stack.copy()).slot()==slot&&ResearchData.canUse(player,stack))
                tasks.add(new Flight(stack,slot,player.server.getTickCount()+1,player.level().dimension()));
        }
        if(!tasks.isEmpty())ACTIVE.put(player,tasks);
    }
    public static void velocity(ServerPlayer player,Vec3 velocity) {
        player.setDeltaMovement(velocity);player.hurtMarked=true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }
    public static int chargeFromSun(ServerPlayer player,net.minecraft.world.item.ItemStack helmet,float amount){
        if(!player.server.isSameThread())throw new IllegalStateException("Solar charging requires server thread");
        if(!Float.isFinite(amount)||amount<=0)throw new IllegalArgumentException("Solar charge must be positive and finite");
        var level=player.serverLevel();
        if(!player.isAlive()||player.hasDisconnected()||player.isSpectator()||player.getItemBySlot(EquipmentSlot.HEAD)!=helmet||!Radiation.solarTick(player)||!ResearchData.canUse(player,helmet)||level.dimension()!=net.minecraft.world.level.Level.OVERWORLD||!EquipmentRules.sunlit(level.getDayTime())||level.getBrightness(net.minecraft.world.level.LightLayer.SKY,net.minecraft.core.BlockPos.containing(player.getEyePosition()))!=15)return 0;
        if(player.serverLevel()!=level||player.getItemBySlot(EquipmentSlot.HEAD)!=helmet||!player.isAlive()||player.hasDisconnected())return 0;
        int charged=0;
        for(var slot:EquipmentSlot.values())if(slot!=EquipmentSlot.HEAD){var target=player.getItemBySlot(slot);if(ItemApi.enabled(level,target)&&target.getItem() instanceof Rechargeable battery&&battery.addItemCharge(target,amount))charged++;}
        return charged;
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        for(var entry:new ArrayList<>(ACTIVE.entrySet())) {
            var player=entry.getKey();if(player.server!=event.getServer())continue;
            if(!player.isAlive()||player.isSpectator()||player.hasDisconnected()||!player.isShiftKeyDown()){ACTIVE.remove(player);continue;}
            var tasks=entry.getValue();tasks.removeIf(task->{
                var stack=player.getItemBySlot(task.slot());
                if(ACTIVE.get(player)!=tasks||player.level().dimension()!=task.dimension()||stack!=task.stack()||stack.isEmpty()||!(stack.getItem() instanceof EquipmentApi.FlightGear gear)||!ResearchData.canUse(player,stack))return true;
                var before=stack.copy();var flight=Objects.requireNonNull(gear.flight(before.copy()));
                if(flight.slot()!=task.slot()||player.getItemBySlot(task.slot())!=stack||!net.minecraft.world.item.ItemStack.matches(before,stack)||ACTIVE.get(player)!=tasks||player.level().dimension()!=task.dimension())return true;
                boolean boots=task.slot()==EquipmentSlot.FEET;long age=player.server.getTickCount()-task.start();
                if(age<0||age%(boots?2:3)!=0)return false;
                Vec3 motion;
                if(flight.mode()!=EquipmentApi.FlightMode.PARACHUTE) {
                    if(!(stack.getItem() instanceof Rechargeable battery))throw new IllegalArgumentException("Jet flight gear must implement Rechargeable: "+stack.getItem());
                    if(!battery.removeItemCharge(stack,boots?.075F:.08F))return true;
                    var look=player.getLookAngle();double thrust=flight.thrust();
                    double accuracy=Math.round((thrust-.7)*100)/100.0,offset=player.getRandom().nextBoolean()?accuracy:-accuracy;
                    motion=boots?new Vec3(look.x*thrust+offset,.04,look.z*thrust-offset):look.scale(.2F).add(0,thrust,0);
                    player.serverLevel().playSound(null,player.blockPosition(),boots?SoundEvents.TNT_PRIMED:SoundEvents.GENERIC_EXPLODE,SoundSource.PLAYERS,.25F,1);
                    player.serverLevel().sendParticles(ParticleTypes.SMOKE,player.getX(),player.getY(),player.getZ(),1,0,0,0,0);
                } else motion=new Vec3(0,-.1,0);
                player.fallDistance=0;velocity(player,motion);return false;
            });
            if(tasks.isEmpty()&&ACTIVE.get(player)==tasks)ACTIVE.remove(player);
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){ACTIVE.remove(event.getEntity());}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event){ACTIVE.remove(event.getEntity());}
    @SubscribeEvent public static void stop(ServerStoppedEvent event){ACTIVE.keySet().removeIf(p->p.server==event.getServer());}
    private FlightGadgets(){}
}

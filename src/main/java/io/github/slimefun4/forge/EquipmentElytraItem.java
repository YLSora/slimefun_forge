package io.github.slimefun4.forge;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.effect.*;

public final class EquipmentElytraItem extends ElytraItem {
    private static final class Flight {final int start;final Level level;double lastY;Flight(ServerPlayer p){start=p.tickCount;level=p.level();lastY=p.getY();}}
    private final boolean bee;
    private static final Map<ServerPlayer,Flight> FLIGHTS=new WeakHashMap<>();
    public static void glideChanged(ServerPlayer player,boolean flying){
        FLIGHTS.remove(player);var stack=player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
        if(flying&&stack.getItem() instanceof EquipmentElytraItem item&&item.bee&&ResearchData.canUse(player,stack))FLIGHTS.put(player,new Flight(player));
    }
    public EquipmentElytraItem(Catalog.Entry entry){super(new Properties().durability(432));bee=entry.id().equals("bee_wings");}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        if(player instanceof ServerPlayer p&&!ResearchData.canUse(p,p.getItemInHand(hand))){ItemAccess.denied(p,p.getItemInHand(hand));return InteractionResultHolder.fail(p.getItemInHand(hand));}
        return super.use(level,player,hand);
    }
    @Override public boolean canElytraFly(ItemStack stack,LivingEntity entity){return (!(entity instanceof ServerPlayer p)||ResearchData.canUse(p,stack))&&super.canElytraFly(stack,entity);}
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END||!(event.player instanceof ServerPlayer p))return;
        var flight=FLIGHTS.get(p);if(flight==null)return;var level=p.level();var stack=p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
        if(!p.isAlive()||!p.isFallFlying()||flight.level!=level||!(stack.getItem() instanceof EquipmentElytraItem item)||!item.bee||!ResearchData.canUse(p,stack)||p.hasEffect(MobEffects.SLOW_FALLING)){FLIGHTS.remove(p);return;}
        if(p.tickCount-flight.start<3)return;
        if(p.getY()<flight.lastY) {
            var pos=p.blockPosition();int distance=pos.getY()-(level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,pos.getX(),pos.getZ())-1);
            boolean close=distance>=0&&distance<=4;
            if(distance<0)for(int i=1;i<=6;i++)if(level.getBlockState(pos.below(i)).isSolid()){close=true;break;}
            if(close){p.fallDistance=0;p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING,60,0));Messages.chat(p,net.minecraft.network.chat.Component.translatable("message.slimefun.bee_slow_fall"));FLIGHTS.remove(p);}
        }
        flight.lastY=p.getY();
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event){FLIGHTS.remove(event.getEntity());}
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void stop(net.minecraftforge.event.server.ServerStoppedEvent event){FLIGHTS.keySet().removeIf(p->p.server==event.getServer());}
}

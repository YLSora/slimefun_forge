package io.github.slimefun4.forge.api;

import io.github.slimefun4.forge.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/** Native transmitter lifecycle, permission-checked offline waypoint CRUD and the real GPS journey. */
public final class GpsApi {
    public interface Transmitter {
        UUID gpsOwner();int signalStrength();int signalComplexity();
        default net.minecraft.world.item.ItemStack gpsIcon(){return new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COMPASS);}
    }
    public record Signal(GlobalPos position,int strength,int complexity,net.minecraft.world.item.ItemStack icon){
        public Signal {Objects.requireNonNull(position);if(strength<0||complexity<0||icon.isEmpty())throw new IllegalArgumentException("Invalid GPS signal");icon=icon.copyWithCount(1);}
        @Override public net.minecraft.world.item.ItemStack icon(){return icon.copy();}
    }
    /** canPay must only inspect state. pay runs once, after the native teleport has succeeded. */
    public interface Payment {boolean canPay();void pay();}
    public static void update(BlockEntity transmitter,boolean active){GpsSignals.update(transmitter,active);}
    public static void unregister(BlockEntity transmitter){GpsSignals.update(transmitter,false);}
    public static int complexity(MinecraftServer server,UUID owner){if(!server.isSameThread())throw new IllegalStateException("GPS requires server thread");return GpsSignals.complexity(server,owner);}
    public static Map<GlobalPos,Integer> transmitters(MinecraftServer server,UUID owner){if(!server.isSameThread())throw new IllegalStateException("GPS requires server thread");return GpsSignals.transmitters(server,owner);}
    public static List<Signal> signals(MinecraftServer server,UUID owner){return GpsSignals.signals(server,owner);}
    public static boolean mayAccess(ServerPlayer actor,UUID owner){return actor.isAlive()&&!actor.isRemoved()&&(actor.getUUID().equals(owner)||GpsAccess.bypass(actor));}
    private static void access(ServerPlayer actor,UUID owner){NetworkApi.check(actor.serverLevel());if(!mayAccess(actor,owner))throw new SecurityException("GPS waypoint owner permission denied");}
    public static List<GpsWaypoints.Waypoint> points(ServerPlayer actor,UUID owner){access(actor,owner);return GpsWaypoints.get(actor.server).points(owner);}
    public static boolean create(ServerPlayer actor,UUID owner,String name,ResourceKey<Level> dimension,Vec3 position,float yaw){access(actor,owner);return GpsWaypoints.get(actor.server).create(actor,owner,name,dimension,position,yaw,false);}
    public static boolean update(ServerPlayer actor,UUID owner,String id,String name,ResourceKey<Level> dimension,Vec3 position,float yaw){access(actor,owner);return GpsWaypoints.get(actor.server).update(actor,owner,id,name,dimension,position,yaw);}
    public static boolean remove(ServerPlayer actor,UUID owner,String id){access(actor,owner);return GpsWaypoints.get(actor.server).remove(owner,id);}
    public static boolean teleport(ServerPlayer player,UUID owner,String id,Payment payment){access(player,owner);return GpsTeleportMenu.start(player,owner,id,Objects.requireNonNull(payment));}
    public static boolean cancel(ServerPlayer player){NetworkApi.check(player.serverLevel());return GpsTeleportMenu.cancel(player);}
    private GpsApi(){}
}

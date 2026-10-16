package io.github.slimefun4.forge;

import java.util.Objects;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Cancelable;

/** Fired on the server thread before committing a player's new waypoint. */
@Cancelable
public final class WaypointCreateEvent extends PlayerEvent {
    private String name;
    private ResourceKey<Level> dimension;
    private Vec3 position;
    private float yaw;
    private final boolean deathpoint;
    private final java.util.UUID owner;
    public WaypointCreateEvent(ServerPlayer player,java.util.UUID owner,String name,ResourceKey<Level> dimension,Vec3 position,float yaw,boolean deathpoint) {
        super(player);this.owner=Objects.requireNonNull(owner);this.deathpoint=deathpoint;setName(name);setLocation(dimension,position,yaw);
    }
    public java.util.UUID getOwner(){return owner;}
    public String getId(){return NetworkRules.waypointId(name);}
    public String getName(){return name;}
    public void setName(String name){this.name=Objects.requireNonNull(name);}
    public ResourceKey<Level> getDimension(){return dimension;}
    public Vec3 getPosition(){return position;}
    public float getYaw(){return yaw;}
    public boolean isDeathpoint(){return deathpoint;}
    public void setLocation(ResourceKey<Level> dimension,Vec3 position,float yaw){this.dimension=Objects.requireNonNull(dimension);this.position=Objects.requireNonNull(position);this.yaw=yaw;}
}

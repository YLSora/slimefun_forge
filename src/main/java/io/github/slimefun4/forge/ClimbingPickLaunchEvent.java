package io.github.slimefun4.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Cancelable;

@Cancelable
public final class ClimbingPickLaunchEvent extends PlayerEvent {
    private final ItemStack item;private final BlockPos position;private Vec3 velocity;
    public ClimbingPickLaunchEvent(ServerPlayer player,ItemStack item,BlockPos position,Vec3 velocity){super(player);this.item=item.copy();this.position=position.immutable();setVelocity(velocity);}
    public ItemStack getItem(){return item.copy();}
    public BlockPos getPosition(){return position;}
    public Vec3 getVelocity(){return velocity;}
    public void setVelocity(Vec3 value){java.util.Objects.requireNonNull(value);EventRules.velocity(value.x,value.y,value.z);velocity=value;}
}

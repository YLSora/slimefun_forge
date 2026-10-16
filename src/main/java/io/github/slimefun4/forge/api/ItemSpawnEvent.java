package io.github.slimefun4.forge.api;

import io.github.slimefun4.forge.EventRules;
import java.util.Objects;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.*;

/** Before business generation. A cancelled reserved output stays pending; it is never silently destroyed. */
@Cancelable
public final class ItemSpawnEvent extends Event {
    private final ServerLevel level;private final ServerPlayer player;private final ItemSpawnApi.Reason reason;
    private Vec3 position;private ItemStack item;
    public ItemSpawnEvent(ServerLevel level,ServerPlayer player,ItemSpawnApi.Reason reason,Vec3 position,ItemStack item){this.level=Objects.requireNonNull(level);this.player=player;this.reason=Objects.requireNonNull(reason);setPosition(position);setItem(item);}
    public ServerLevel getLevel(){return level;}
    /** Nullable for offline owners and autonomous machines. */
    public ServerPlayer getPlayer(){return player;}
    public ItemSpawnApi.Reason getReason(){return reason;}
    public Vec3 getPosition(){return position;}
    public void setPosition(Vec3 value){Objects.requireNonNull(value);EventRules.position(value.x,value.y,value.z);position=value;}
    public ItemStack getItem(){return item.copy();}
    public void setItem(ItemStack value){Objects.requireNonNull(value);EventRules.stack(value.getCount(),value.getMaxStackSize());if(value.isEmpty())throw new IllegalArgumentException("Empty generated item");item=value.copy();}
}

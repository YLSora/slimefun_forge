package io.github.slimefun4.forge;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/** Server-thread completion event. Cancellation consumes the ritual without a drop, as in RC-37. */
@Cancelable
public final class AncientAltarCraftEvent extends Event {
    private final ServerLevel level;
    private final BlockPos altar;
    private final UUID owner;
    private final String ownerName;
    private ItemStack output;
    public AncientAltarCraftEvent(ServerLevel level,BlockPos altar,UUID owner,String ownerName,ItemStack output) {
        this.level=level; this.altar=altar.immutable(); this.owner=owner; this.ownerName=ownerName; setOutput(output);
    }
    public ServerLevel getLevel() { return level; }
    public BlockPos getAltar() { return altar; }
    public UUID getOwner() { return owner; }
    public String getOwnerName() { return ownerName; }
    /** Null when the initiating player is offline; the saved identity remains available. */
    public ServerPlayer getPlayer() { return level.getServer().getPlayerList().getPlayer(owner); }
    public ItemStack getOutput() { return output.copy(); }
    public void setOutput(ItemStack stack) {
        if(stack==null || stack.isEmpty()||stack.getCount()>stack.getMaxStackSize()) throw new IllegalArgumentException("An Ancient Altar requires one valid output stack");
        output=stack.copy();
    }
}

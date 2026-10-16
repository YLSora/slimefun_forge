package io.github.slimefun4.forge;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.eventbus.api.Event;

/** Non-cancelable server-thread notification when a finished task ends, including dismantling. */
public final class MachineOperationFinishEvent extends Event {
    private final ServerLevel level;
    private final BlockEntity machine;
    private final BlockPos position;
    private final List<ItemStack> ingredients,results;
    private final int totalTicks;
    private final io.github.slimefun4.forge.api.MachineApi.Processor processor;
    private final io.github.slimefun4.forge.api.MachineApi.Operation operation;

    public MachineOperationFinishEvent(ServerLevel level,BlockEntity machine,io.github.slimefun4.forge.api.MachineApi.Processor processor,io.github.slimefun4.forge.api.MachineApi.Operation operation) {
        this.level=level; this.machine=machine; this.position=machine.getBlockPos().immutable();
        this.processor=java.util.Objects.requireNonNull(processor);this.operation=java.util.Objects.requireNonNull(operation);
        if(!operation.finished())throw new IllegalArgumentException("Unfinished operation notification");
        this.ingredients=operation.inputs(); this.results=operation.outputs(); this.totalTicks=operation.total();
    }
    private static List<ItemStack> copy(List<ItemStack> stacks) { return stacks.stream().map(ItemStack::copy).toList(); }
    public ServerLevel getLevel() { return level; }
    public BlockEntity getMachine() { return machine; }
    public BlockPos getPosition() { return position; }
    /** Live native processor; already cleared when notified. A listener must not end a newly started operation. */
    public io.github.slimefun4.forge.api.MachineApi.Processor getProcessor(){return processor;}
    /** Immutable typed snapshot including addon payload; this remains valid after the live processor changes. */
    public io.github.slimefun4.forge.api.MachineApi.Operation getOperation(){return operation;}
    /** Actual reserved stacks, including counts and tags; each call returns defensive copies. */
    public List<ItemStack> getIngredients() { return copy(ingredients); }
    /** Planned results; dismantling a finished task does not deliver them. */
    public List<ItemStack> getResults() { return copy(results); }
    /** Owner-defined work units; core machines use Slimefun cycles and a separate delivery cycle. */
    public int getTotalTicks() { return totalTicks; }
    public int getProgress() { return totalTicks; }
}

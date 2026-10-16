package io.github.slimefun4.forge;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Cancelable;

@Cancelable
public final class MultiBlockCraftEvent extends PlayerEvent {
    private final String station;
    private final BlockPos position;
    private final List<ItemStack> input;
    private ItemStack output;
    public MultiBlockCraftEvent(ServerPlayer player,String station,BlockPos position,List<ItemStack> input,ItemStack output) {
        super(player);this.station=station;this.position=position.immutable();this.input=input.stream().map(ItemStack::copy).toList();setOutput(output);
    }
    public String getStation() { return station; }
    public BlockPos getPosition() { return position; }
    public List<ItemStack> getInput() { return input.stream().map(ItemStack::copy).toList(); }
    public ItemStack getOutput() { return output.copy(); }
    public void setOutput(ItemStack stack) {if(stack==null||stack.isEmpty()||stack.getCount()>stack.getMaxStackSize())throw new IllegalArgumentException("Invalid multiblock output");output=stack.copy();}
}

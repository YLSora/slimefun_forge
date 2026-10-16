package io.github.slimefun4.forge;

import javax.annotation.Nullable;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.*;
import net.minecraftforge.items.wrapper.InvWrapper;

/** The only automated item interface to a reactor, exactly three blocks above it. */
public final class ReactorPortEntity extends BlockEntity implements MenuProvider {
    private boolean loading,dismantling;
    public final SimpleContainer inventory=new SimpleContainer(7) { @Override public void setChanged() {super.setChanged();if(!loading) {ReactorPortEntity.this.setChanged();wake();}} };
    private LazyOptional<IItemHandler> items=createItems();
    public ReactorPortEntity(BlockPos pos,BlockState state) {super(SlimefunForge.REACTOR_PORT_ENTITY.get(),pos,state);}
    public BlockEntity reactor() {return level!=null&&level.hasChunkAt(worldPosition.below(3))&&level.getBlockEntity(worldPosition.below(3)) instanceof io.github.slimefun4.forge.api.MachineApi.ReactorAccess r&&r.isReactor()?level.getBlockEntity(worldPosition.below(3)):null;}
    private void wake() {var r=reactor();if(r instanceof io.github.slimefun4.forge.api.MachineApi.ReactorAccess access)access.wake();}
    @Override public void onLoad() {super.onLoad();wake();}
    boolean coolant(ItemStack stack) {var r=reactor();return r instanceof io.github.slimefun4.forge.api.MachineApi.ReactorAccess access?access.acceptsCoolant(stack):stack.is(SlimefunForge.item("reactor_collant_cell"))||stack.is(SlimefunForge.item("nether_ice_coolant_cell"));}
    private LazyOptional<IItemHandler> createItems() {return LazyOptional.of(()->new InvWrapper(inventory) {
        @Override public boolean isItemValid(int slot,ItemStack stack) {return slot>=0 && slot<6 && (slot>=3)==coolant(stack);}
        @Override public ItemStack insertItem(int slot,ItemStack stack,boolean simulate) {return isItemValid(slot,stack)?super.insertItem(slot,stack,simulate):stack;}
        @Override public ItemStack extractItem(int slot,int amount,boolean simulate) {return slot==6?super.extractItem(slot,amount,simulate):ItemStack.EMPTY;}
    });}
    public void dismantle() {if(level!=null && !level.isClientSide && !dismantling) {dismantling=true;Containers.dropContents(level,worldPosition,inventory);inventory.clearContent();}}
    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap,@Nullable Direction side) {return !isRemoved() && cap==ForgeCapabilities.ITEM_HANDLER?items.cast():super.getCapability(cap,side);}
    @Override public void invalidateCaps() {super.invalidateCaps();items.invalidate();}
    @Override public void reviveCaps() {super.reviveCaps();items=createItems();wake();}
    @Override protected void saveAdditional(CompoundTag tag) {super.saveAdditional(tag);var stacks=NonNullList.withSize(7,ItemStack.EMPTY);for(int i=0;i<7;i++) stacks.set(i,inventory.getItem(i));ContainerHelper.saveAllItems(tag,stacks);}
    @Override public void load(CompoundTag tag) {super.load(tag);var stacks=NonNullList.withSize(7,ItemStack.EMPTY);ContainerHelper.loadAllItems(tag,stacks);loading=true;try {for(int i=0;i<7;i++) inventory.setItem(i,stacks.get(i));} finally {loading=false;}wake();}
    @Override public Component getDisplayName() {return Component.translatable("block.slimefun.reactor_access_port");}
    @Override public AbstractContainerMenu createMenu(int id,Inventory inv,Player p) {return new ReactorMenu(id,inv,this,inventory);}
}

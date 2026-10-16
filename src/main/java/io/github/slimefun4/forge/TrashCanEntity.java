package io.github.slimefun4.forge;

import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;

public final class TrashCanEntity extends BlockEntity implements MenuProvider {
    private boolean loading;
    public final SimpleContainer inventory=new SimpleContainer(7) {
        @Override public void setChanged() {
            super.setChanged();
            if(!loading) { TrashCanEntity.this.setChanged(); wake(); }
        }
    };
    private LazyOptional<IItemHandler> items=createItems();
    public TrashCanEntity(BlockPos pos,BlockState state) { super(SlimefunForge.TRASH_CAN_ENTITY.get(),pos,state); }
    private LazyOptional<IItemHandler> createItems() {
        return LazyOptional.of(()->new InvWrapper(inventory) {
            @Override public ItemStack getStackInSlot(int slot) { Objects.checkIndex(slot,7); return super.getStackInSlot(slot); }
            @Override public ItemStack insertItem(int slot,ItemStack stack,boolean simulate) {
                Objects.checkIndex(slot,7); return isRemoved()?stack:super.insertItem(slot,stack,simulate);
            }
            @Override public ItemStack extractItem(int slot,int amount,boolean simulate) { Objects.checkIndex(slot,7); return ItemStack.EMPTY; }
            @Override public void setStackInSlot(int slot,ItemStack stack) { Objects.checkIndex(slot,7); if(!isRemoved()) super.setStackInSlot(slot,stack); }
            @Override public boolean isItemValid(int slot,ItemStack stack) { Objects.checkIndex(slot,7); return !isRemoved(); }
        });
    }
    private void wake() {
        if(level!=null && !level.isClientSide && !isRemoved() && !inventory.isEmpty()
            && !level.getBlockTicks().hasScheduledTick(worldPosition,getBlockState().getBlock()))
            level.scheduleTick(worldPosition,getBlockState().getBlock(),MachineClock.delay(level));
    }
    public void empty() {
        if(inventory.isEmpty()) return;
        inventory.clearContent(); setChanged();
    }
    @Override public void onLoad() { super.onLoad(); wake(); }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> capability,@Nullable Direction side) {
        if(!isRemoved() && capability==ForgeCapabilities.ITEM_HANDLER) return items.cast();
        return super.getCapability(capability,side);
    }
    @Override public void invalidateCaps() { super.invalidateCaps(); items.invalidate(); }
    @Override public void reviveCaps() { super.reviveCaps(); items=createItems(); wake(); }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        var slots=NonNullList.withSize(7,ItemStack.EMPTY);
        for(int i=0;i<7;i++) slots.set(i,inventory.getItem(i));
        ContainerHelper.saveAllItems(tag,slots);
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag); loading=true;
        var slots=NonNullList.withSize(7,ItemStack.EMPTY); ContainerHelper.loadAllItems(tag,slots);
        for(int i=0;i<7;i++) inventory.setItem(i,slots.get(i));
        loading=false; wake();
    }
    @Override public Component getDisplayName() { return Component.translatable("block.slimefun.trash_can_block"); }
    @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player player) { return new TrashMenu(id,inventory,this); }
    private static final class TrashMenu extends ChestMenu {
        private final TrashCanEntity bin;
        private final Player owner;
        TrashMenu(int id,Inventory inventory,TrashCanEntity bin) {
            super(MenuType.GENERIC_9x3,id,inventory,new SimpleContainer(27),3); this.bin=bin; owner=inventory.player;
            for(int i=0;i<27;i++) {
                var old=slots.get(i); boolean input=i>=10 && i<=16;
                var replacement=new Slot(input?bin.inventory:getContainer(),input?i-10:i,old.x,old.y) {
                    @Override public boolean mayPlace(ItemStack stack) { return input; }
                    @Override public boolean mayPickup(Player player) { return input&&stillValid(player); }
                };
                replacement.index=i; slots.set(i,replacement);
                if(!input) replacement.set(new ItemStack(Items.RED_STAINED_GLASS_PANE).setHoverName(Component.literal(" ")));
            }
        }
        @Override public boolean stillValid(Player player) {
            return player==owner && player.isAlive() && !bin.isRemoved() && player.level()==bin.level
                && bin.level.getBlockEntity(bin.worldPosition)==bin && player.distanceToSqr(bin.worldPosition.getCenter())<=64&&player.level().mayInteract(player,bin.worldPosition)
                &&(!(player instanceof net.minecraft.server.level.ServerPlayer server)||ResearchData.canUse(server,new ItemStack(bin.getBlockState().getBlock())));
        }
        @Override public void clicked(int slot,int button,ClickType type,Player player) {
            if(player.containerMenu!=this||!stillValid(player) || (slot>=0 && slot<27 && (slot<10 || slot>16))) return;
            super.clicked(slot,button,type,player);
        }
        @Override public ItemStack quickMoveStack(Player player,int index) {
            if(player.containerMenu!=this||!stillValid(player) || index<0 || index>=slots.size() || (index<27 && (index<10 || index>16))) return ItemStack.EMPTY;
            var slot=slots.get(index); var stack=slot.getItem(); if(stack.isEmpty()) return ItemStack.EMPTY;
            var before=stack.copy();
            if(index<27) { if(!moveItemStackTo(stack,27,slots.size(),true)) return ItemStack.EMPTY; }
            else if(!moveItemStackTo(stack,10,17,false)) return ItemStack.EMPTY;
            if(stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
            return before;
        }
    }
}

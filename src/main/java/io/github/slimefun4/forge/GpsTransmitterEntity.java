package io.github.slimefun4.forge;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.*;

/** All RC-37 tiers share one paid, ten-tick signal lifecycle. */
public final class GpsTransmitterEntity extends BlockEntity implements io.github.slimefun4.forge.api.GpsApi.Transmitter {
    private UUID owner;
    private boolean active;
    private net.minecraft.network.chat.Component status=net.minecraft.network.chat.Component.translatable("menu.slimefun.gps.offline").withStyle(net.minecraft.ChatFormatting.RED);
    private final Catalog.Entry definition;
    private final Power energy;
    private LazyOptional<IEnergyStorage> power;
    public GpsTransmitterEntity(BlockPos pos,BlockState state) { super(SlimefunForge.GPS_TRANSMITTER_ENTITY.get(),pos,state); definition=((GpsTransmitterBlock)state.getBlock()).definition; energy=new Power(); power=LazyOptional.of(()->energy); }
    public UUID owner() { return owner; }
    @Override public UUID gpsOwner(){return owner;}
    @Override public net.minecraft.world.item.ItemStack gpsIcon(){return new net.minecraft.world.item.ItemStack(getBlockState().getBlock());}
    @Override public int signalStrength(){return ((GpsTransmitterBlock)getBlockState().getBlock()).strength(worldPosition.getY());}
    @Override public int signalComplexity(){return ((GpsTransmitterBlock)getBlockState().getBlock()).strength(Math.max(0,worldPosition.getY()));}
    public int energy() { return energy.getEnergyStored(); }
    public net.minecraft.network.chat.Component status(){return status;}
    private void active(boolean value){
        if(active==value)return;active=value;
        status=value?net.minecraft.network.chat.Component.translatable("menu.slimefun.gps.strength",((GpsTransmitterBlock)getBlockState().getBlock()).strength(worldPosition.getY())).withStyle(net.minecraft.ChatFormatting.GREEN):net.minecraft.network.chat.Component.translatable("menu.slimefun.gps.offline").withStyle(net.minecraft.ChatFormatting.RED);
        if(level!=null&&!level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);
    }
    @Override public CompoundTag getUpdateTag(){var tag=new CompoundTag();tag.putBoolean("active",active);return tag;}
    @Override public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket(){return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);}
    @Override public void handleUpdateTag(CompoundTag tag){active(tag.getBoolean("active"));}
    @Override public void onDataPacket(net.minecraft.network.Connection connection,net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet){if(packet.getTag()!=null)handleUpdateTag(packet.getTag());}
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(){return new net.minecraft.world.phys.AABB(worldPosition).expandTowards(0,1.25,0).inflate(4);}
    public void setOwner(UUID value) { active(false);GpsSignals.update(this,false); owner=value; setChanged(); wake(); }
    private void wake() {
        if(level!=null && !level.isClientSide && !isRemoved() && !level.getBlockTicks().hasScheduledTick(worldPosition,getBlockState().getBlock()))
            level.scheduleTick(worldPosition,getBlockState().getBlock(),MachineClock.delay(level));
    }
    public void work() {
        if(level==null || level.isClientSide || isRemoved()) return;
        boolean active=owner!=null && energy.spend();
        active(active);
        GpsSignals.update(this,active);
        if(active) { setChanged(); wake(); } // One final cycle revokes the last paid signal when empty.
    }
    private void detach() { active(false);GpsSignals.update(this,false); EnergyNetwork.remove(this); }
    @Override public void onLoad() { super.onLoad(); EnergyNetwork.add(this,"GPSTransmitter"); wake(); }
    @Override public void setRemoved() { detach(); super.setRemoved(); }
    @Override public void onChunkUnloaded() { detach(); super.onChunkUnloaded(); }
    @Override public void invalidateCaps() { detach(); super.invalidateCaps(); power.invalidate(); }
    @Override public void reviveCaps() { super.reviveCaps(); power=LazyOptional.of(()->energy); EnergyNetwork.add(this,"GPSTransmitter"); wake(); }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> capability,@Nullable Direction side) {
        return !isRemoved() && capability==ForgeCapabilities.ENERGY ? power.cast() : super.getCapability(capability,side);
    }
    @Override protected void saveAdditional(CompoundTag tag) { super.saveAdditional(tag); if(owner!=null) tag.putUUID("owner",owner); tag.putInt("energy",energy()); }
    @Override public void load(CompoundTag tag) {
        GpsSignals.update(this,false); super.load(tag); owner=tag.hasUUID("owner")?tag.getUUID("owner"):null;
        energy.restore(tag.getInt("energy")); wake();
    }
    private final class Power extends EnergyStorage {
        Power() { super(definition.capacity(),definition.capacity(),0); }
        boolean spend() { if(energy<definition.consumption()) return false; energy-=definition.consumption(); return true; }
        void restore(int value) { energy=Math.max(0,Math.min(capacity,value)); }
        @Override public int receiveEnergy(int amount,boolean simulate) {
            if(isRemoved() || amount<=0) return 0;
            int accepted=super.receiveEnergy(amount,simulate);
            if(!simulate && accepted>0) { setChanged(); wake(); }
            return accepted;
        }
    }
}

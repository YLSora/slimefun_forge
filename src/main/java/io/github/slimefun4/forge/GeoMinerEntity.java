package io.github.slimefun4.forge;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.*;
import net.minecraftforge.items.wrapper.InvWrapper;

/** A reserved GEO unit, fourteen paid cycles, and a free output transaction. */
public final class GeoMinerEntity extends BlockEntity implements MenuProvider,io.github.slimefun4.forge.api.MachineApi.ProcessHolder {
    public static List<GeoResources> resources(){return GeoResources.mineableResources();}
    public static final int CAPACITY=512,CONSUMPTION=24,CYCLES=14;
    private GeoResources pending;
    private final io.github.slimefun4.forge.api.MachineApi.Processor processor=AddonMachines.processor(this,this::currentOperation,()->endOperation((ServerLevel)level,true),()->endOperation((ServerLevel)level,false));
    @Override public io.github.slimefun4.forge.api.MachineApi.Processor processor(){return processor;}
    private io.github.slimefun4.forge.api.MachineApi.Operation currentOperation(){return pending==null?null:new io.github.slimefun4.forge.api.MachineApi.Operation(SlimefunForge.key("geo_mining"),new CompoundTag(),CYCLES,progress,List.of(),List.of(result(pending)));}
    private int energy,progress;
    private boolean loading,dismantling,working;
    private long operations,lookups;
    public final SimpleContainer inventory=new SimpleContainer(10) {
        @Override public void setChanged() { super.setChanged(); if(!loading) { GeoMinerEntity.this.setChanged(); wake(); } }
        @Override public boolean canPlaceItem(int slot,ItemStack stack) { return false; }
    };
    private LazyOptional<IEnergyStorage> power=LazyOptional.of(Power::new);
    private LazyOptional<IItemHandler> items=createItems();
    public GeoMinerEntity(BlockPos pos,BlockState state) { super(SlimefunForge.GEO_MINER_ENTITY.get(),pos,state); }
    public int energy() { return energy; }
    public int progress() { return progress; }
    public GeoResources pending() { return pending; }
    public long operations() { return operations; }
    public long lookups() { return lookups; }
    public static ItemStack result(GeoResources resource) {
        if(!resource.mineable())throw new IllegalArgumentException("Not a mineable GEO resource");return resource.output();
    }
    public void wake() {
        if(level!=null && !level.isClientSide && !isRemoved() && !dismantling && !level.getBlockTicks().hasScheduledTick(worldPosition,getBlockState().getBlock()))
            level.scheduleTick(worldPosition,getBlockState().getBlock(),MachineClock.delay(level));
    }
    @Override public void onLoad() {
        super.onLoad();
        if(level instanceof ServerLevel server && !isRemoved()) {
            var cleared=GeoResourceData.get(server).clearedMining.remove(worldPosition);
            if(cleared!=null) { pending=cleared.resource(); progress=cleared.progress(); setChanged(); }
        }
        EnergyNetwork.add(this,"GEOMiner"); wake();
    }
    @Override public void setRemoved() { EnergyNetwork.remove(this); super.setRemoved(); }
    @Override public void onChunkUnloaded() { EnergyNetwork.remove(this); super.onChunkUnloaded(); }
    private void status(int value) {
        if(getBlockState().getValue(GeoMinerBlock.STATUS)!=value) level.setBlock(worldPosition,getBlockState().setValue(GeoMinerBlock.STATUS,value),3);
    }
    private SimpleContainer output(GeoResources resource) {
        var plan=MachineRecipe.copy(inventory);
        return plan.addItem(result(resource)).isEmpty()?plan:null;
    }
    public void work() {
        if(!(level instanceof ServerLevel server) || isRemoved() || dismantling || working) return;
        working=true;
        try {
            if(pending!=null) {
                status(status(pending));
                if(progress<CYCLES) {
                    if(energy<CONSUMPTION) return;
                    energy-=CONSUMPTION; progress++; setChanged(); wake();
                } else {
                    var plan=output(pending); if(plan==null) return;
                    MachineRecipe.commit(inventory,plan); operations++; endOperation(server,true);
                }
                return;
            }
            lookups++;
            var data=GeoResourceData.get(server); var chunk=new net.minecraft.world.level.ChunkPos(worldPosition);
            for(var resource:resources()) {
                if(!ItemAccess.enabled(server,result(resource)))continue;
                var supply=data.supply(chunk,resource);
                if(supply.isEmpty()) { status(1); return; }
                if(supply.getAsInt()==0) continue;
                if(output(resource)==null) return;
                if(data.extractOne(server,worldPosition,resource)) { pending=resource; progress=0; status(status(resource));level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);setChanged();wake(); }
                return;
            }
            status(2);
        } finally { working=false; }
    }
    private static int status(GeoResources resource){return resource==GeoResources.URANIUM?3:resource==GeoResources.NETHER_ICE?4:resource==GeoResources.SALT?5:6;}
    @Override public CompoundTag getUpdateTag(){var tag=new CompoundTag();if(pending!=null)tag.putString("mining",Component.Serializer.toJson(pending.name()));return tag;}
    private Component mining;
    public Component miningName(){return level!=null&&level.isClientSide?mining:pending==null?null:pending.name();}
    @Override public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket(){return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);}
    @Override public void handleUpdateTag(CompoundTag tag){mining=tag.contains("mining",Tag.TAG_STRING)?Component.Serializer.fromJson(tag.getString("mining")):null;}
    @Override public void onDataPacket(net.minecraft.network.Connection connection,net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet){if(packet.getTag()!=null)handleUpdateTag(packet.getTag());}
    private void endOperation(ServerLevel server,boolean notify) {
        var reserved=pending; boolean completed=reserved!=null && progress==CYCLES;var snapshot=completed?currentOperation():null;
        pending=null; progress=0; setChanged(); wake();
        if(completed&&notify) net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new MachineOperationFinishEvent(server,this,processor,snapshot));
        else if(!completed&&reserved!=null) GeoResourceData.get(server).refundOne(server,worldPosition,reserved);
    }
    public void dismantle() {
        if(!(level instanceof ServerLevel server) || dismantling) return;
        dismantling=true; endOperation(server,true); Containers.dropContents(server,worldPosition,inventory); inventory.clearContent();
    }
    public void clear() {
        if(!(level instanceof ServerLevel server) || dismantling || isRemoved()) return;
        dismantling=true;
        if(pending!=null) GeoResourceData.get(server).clearedMining.put(worldPosition,new GeoResourceData.ClearedMining(pending,progress));
        pending=null; progress=0; inventory.clearContent();
    }
    private LazyOptional<IItemHandler> createItems() { return LazyOptional.of(()->new InvWrapper(inventory) {
        @Override public ItemStack insertItem(int slot,ItemStack stack,boolean simulate) { return stack; }
        @Override public boolean isItemValid(int slot,ItemStack stack) { return false; }
    }); }
    private final class Power implements IEnergyStorage {
        @Override public int receiveEnergy(int amount,boolean simulate) { int accepted=Math.max(0,Math.min(amount,CAPACITY-energy)); if(!simulate && accepted>0) { energy+=accepted; setChanged(); wake(); } return accepted; }
        @Override public int extractEnergy(int amount,boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return energy; }
        @Override public int getMaxEnergyStored() { return CAPACITY; }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return true; }
    }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> capability,@Nullable Direction side) {
        if(!isRemoved() && capability==ForgeCapabilities.ENERGY) return power.cast();
        if(!isRemoved() && capability==ForgeCapabilities.ITEM_HANDLER) return items.cast();
        return super.getCapability(capability,side);
    }
    @Override public void invalidateCaps() { super.invalidateCaps(); power.invalidate(); items.invalidate(); }
    @Override public void reviveCaps() { super.reviveCaps(); power=LazyOptional.of(Power::new); items=createItems(); wake(); }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag); var stacks=NonNullList.withSize(10,ItemStack.EMPTY);
        for(int i=0;i<10;i++) stacks.set(i,inventory.getItem(i)); ContainerHelper.saveAllItems(tag,stacks); tag.putInt("energy",energy);
        if(pending!=null) { var op=new CompoundTag(); op.putString("resource",pending.id.toString()); op.putInt("progress",progress); op.putInt("total",CYCLES); tag.put("operation",op); }
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag); var stacks=NonNullList.withSize(10,ItemStack.EMPTY); ContainerHelper.loadAllItems(tag,stacks);
        loading=true; try { for(int i=0;i<10;i++) inventory.setItem(i,stacks.get(i)); } finally { loading=false; }
        energy=Math.max(0,Math.min(CAPACITY,tag.getInt("energy"))); pending=null; progress=0;
        if(tag.contains("operation")) {
            var op=tag.getCompound("operation");var id=op.getString("resource");if(!id.contains(":"))throw new IllegalArgumentException("GEO operation requires a full resource ID");pending=GeoResources.require(new net.minecraft.resources.ResourceLocation(id));if(!pending.mineable())throw new IllegalArgumentException("Invalid reserved GEO resource");
            progress=op.getInt("progress");
            if(!op.contains("progress",Tag.TAG_INT) || progress<0 || progress>CYCLES || op.getInt("total")!=CYCLES) throw new IllegalArgumentException("Invalid GEO progress");
        }
        wake();
    }
    @Override public Component getDisplayName() { return Component.translatable("block.slimefun.geo_miner"); }
    @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player player) { return new GeoMinerMenu(id,inventory,this); }
}

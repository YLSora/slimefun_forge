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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;

/** Network-driven fuel operation. Input, cooling, output, and meltdown stay on the server thread. */
public final class ReactorEntity extends BlockEntity implements MenuProvider,io.github.slimefun4.forge.api.MachineApi.ProcessHolder,io.github.slimefun4.forge.api.MachineApi.ReactorAccess {
    private static final int[][] WATER={{0,-1},{1,-1},{1,0},{1,1},{0,1},{-1,1},{-1,0},{-1,-1}};
    public final Catalog.Entry definition;
    public final SimpleContainer inventory=new SimpleContainer(7) { @Override public void setChanged() { super.setChanged(); if(!loading) { ReactorEntity.this.setChanged(); wake(); } } };
    private ItemStack ingredient=ItemStack.EMPTY,fuelOutput=ItemStack.EMPTY;
    private final io.github.slimefun4.forge.api.MachineApi.Processor processor=AddonMachines.processor(this,this::currentOperation,()->endOperation((ServerLevel)level,true),()->endOperation((ServerLevel)level,false));
    @Override public io.github.slimefun4.forge.api.MachineApi.Processor processor(){return processor;}
    private io.github.slimefun4.forge.api.MachineApi.Operation currentOperation(){return ingredient.isEmpty()?null:new io.github.slimefun4.forge.api.MachineApi.Operation(SlimefunForge.key("reactor_fuel"),new CompoundTag(),total,progress,List.of(ingredient),result().isEmpty()?List.of():List.of(result()));}
    private int energy,progress,total;
    private boolean productionMode,waterFault,dirty=true,loading,working,dismantling;
    private long operations,lookups;
    private LazyOptional<IEnergyStorage> power=LazyOptional.of(Power::new);
    public ReactorEntity(BlockPos pos,BlockState state) { super(SlimefunForge.REACTOR_ENTITY.get(),pos,state); definition=((ReactorBlock)state.getBlock()).definition; }
    public boolean star() { return definition.implementation().equals("NetherStarReactor"); }
    public int production() { return definition.production(); }
    public int capacity() { return definition.capacity(); }
    public int energy() { return energy; }
    void networkEnergy(int value) { if(energy!=value) {energy=value;setChanged();wake();} }
    public int progress() { return progress; }
    public int total() { return total; }
    public long operations() { return operations; }
    public long lookups() { return lookups; }
    public ItemStack pending() { return ingredient.copy(); }
    public boolean productionMode() { return productionMode; }
    public Item coolant() { return SlimefunForge.item(star()?"nether_ice_coolant_cell":"reactor_collant_cell"); }
    public void setProductionMode(boolean value) { if(productionMode!=value) { productionMode=value; setChanged(); wake(); } }
    public void wake() { dirty=true; }
    @Override public boolean isReactor(){return true;}
    @Override public void onLoad() { super.onLoad(); EnergyNetwork.add(this,definition.implementation()); wake(); }
    @Override public void setRemoved() { EnergyNetwork.remove(this); super.setRemoved(); }
    @Override public void onChunkUnloaded() { EnergyNetwork.remove(this); super.onChunkUnloaded(); }
    public ReactorPortEntity port() { return level!=null && level.hasChunkAt(worldPosition.above(3)) && level.getBlockEntity(worldPosition.above(3)) instanceof ReactorPortEntity port?port:null; }
    private List<Item> fuels() { var values=new java.util.ArrayList<Item>(star()?List.of(Items.NETHER_STAR):List.of(SlimefunForge.item("uranium"),SlimefunForge.item("neptunium"),SlimefunForge.item("boosted_uranium")));values.addAll(AddonMachines.fuels(AddonMachines.key(definition.id())));return values; }
    private int duration(Item item) { return fuelCycles(definition,new ItemStack(item)); }
    static int fuelCycles(Catalog.Entry definition,ItemStack input){var fuel=AddonMachines.fuel(null,AddonMachines.key(definition.id()),input);return fuel!=null?fuel.cycles():fuelCycles(definition.implementation(),input.getItem());}
    static int fuelCycles(String kind,Item item){
        if(kind.equals("NetherStarReactor"))return item==Items.NETHER_STAR?3600:0;
        if(!kind.equals("NuclearReactor"))return 0;
        return item==SlimefunForge.item("uranium")?2400:item==SlimefunForge.item("neptunium")?1200:item==SlimefunForge.item("boosted_uranium")?3000:0;
    }
    private ItemStack result() { return fuelOutput.copy(); }
    private ItemStack reserveResult(ItemStack input){var fuel=AddonMachines.fuel((ServerLevel)level,AddonMachines.key(definition.id()),input);return fuel!=null?AddonMachines.fuelResult(AddonMachines.key(definition.id()),input):!star()&&input.is(SlimefunForge.item("uranium"))?new ItemStack(SlimefunForge.item("neptunium")):!star()&&input.is(SlimefunForge.item("neptunium"))?new ItemStack(SlimefunForge.item("plutonium")):ItemStack.EMPTY;}
    private boolean matches(ItemStack stack,Item item) { return !stack.isEmpty() && ItemAccess.enabled(level,stack) && MachineRecipe.same(stack,new ItemStack(item)); }
    @Override public boolean acceptsCoolant(ItemStack stack){return matches(stack,coolant())||AddonMachines.coolant(level instanceof ServerLevel server?server:null,AddonMachines.key(definition.id()),stack);}
    private void pull(ReactorPortEntity port,int slot,int start) {
        var offered=port.inventory.getItem(slot); if(offered.isEmpty()) return;
        var plan=new SimpleContainer(inventory.getItem(start).copy(),inventory.getItem(start+1).copy(),inventory.getItem(start+2).copy()); var remainder=plan.addItem(offered.copy());
        if(remainder.getCount()==offered.getCount()) return;
        for(int n=0;n<3;n++) inventory.setItem(start+n,plan.getItem(n)); port.inventory.setItem(slot,remainder);
    }
    private void flush(ReactorPortEntity port) {
        if(port==null || inventory.getItem(6).isEmpty()) return;
        var plan=new SimpleContainer(port.inventory.getItem(6).copy()); var remainder=plan.addItem(inventory.getItem(6).copy());
        if(remainder.getCount()==inventory.getItem(6).getCount()) return;
        port.inventory.setItem(6,plan.getItem(0)); inventory.setItem(6,remainder);
    }
    public void work() {
        if(!(level instanceof ServerLevel server) || isRemoved() || working || dismantling) return;
        if(!server.getServer().isSameThread()) throw new IllegalStateException("Reactor requires its server thread");
        if(ingredient.isEmpty() && !dirty && !waterFault) return;
        working=true;
        try {
            boolean queuedFault=waterFault; waterFault=false;
            int generated=cycle(server);
            if(queuedFault || generated<0) melt(server);
            else if(generated>0) { energy+=generated; setChanged(); }
            if(queuedFault || waterFault) setChanged();
        } finally { working=false; }
    }
    private int cycle(ServerLevel server) {
        var port=port();
        if(ingredient.isEmpty()) {
            if(!ItemAccess.enabled(server,definition.id()))return 0;
            if(!dirty) return 0; dirty=false; lookups++; int found=-1;
            for(var fuel:fuels()) { for(int slot=0;slot<6;slot++) if(matches(inventory.getItem(slot),fuel)) { found=slot; break; } if(found>=0) break; }
            // RC-37 selects before restocking; fuel just received from the port starts next cycle.
            if(port!=null) outer: for(int slot=0;slot<3;slot++) for(var fuel:fuels()) if(matches(port.inventory.getItem(slot),fuel)) {
                var plan=new SimpleContainer(inventory.getItem(0).copy(),inventory.getItem(1).copy(),inventory.getItem(2).copy());
                if(plan.addItem(new ItemStack(fuel)).isEmpty()) { pull(port,slot,0); break outer; }
            }
            if(found>=0) {
                var output=reserveResult(inventory.getItem(found));
                if(!output.isEmpty()&&(!ItemAccess.enabled(server,output)||!new SimpleContainer(inventory.getItem(6).copy()).addItem(output.copy()).isEmpty()))return 0;
                ingredient=inventory.removeItem(found,1).copy();progress=0;total=duration(ingredient.getItem());fuelOutput=output;setChanged();dirty=true;
            }
            return 0;
        }
        if(star()) for(var entity:server.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,new net.minecraft.world.phys.AABB(worldPosition.getX()-5,worldPosition.getY()-5,worldPosition.getZ()-5,worldPosition.getX()+5,worldPosition.getY()+5,worldPosition.getZ()+5),e->e.isAlive() && !e.isRemoved()))
            entity.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WITHER,60,1));
        if(progress==total) {
            if(!dirty) return 0; dirty=false; flush(port); var output=result(); var plan=new SimpleContainer(inventory.getItem(6).copy());
            if(!output.isEmpty() && !plan.addItem(output).isEmpty()) return 0;
            inventory.setItem(6,plan.getItem(0)); flush(port); operations++; endOperation(server,true); return 0;
        }
        boolean fits=capacity()-energy>=production(); if(!fits && !productionMode) return 0;
        for(var offset:WATER) if(!server.hasChunkAt(worldPosition.offset(offset[0],0,offset[1]))) return 0;
        progress++; dirty=true; setChanged();
        var offset=WATER[server.random.nextInt(WATER.length)]; waterFault=!server.getBlockState(worldPosition.offset(offset[0],0,offset[1])).is(Blocks.WATER);
        if(progress%50==0) {
            if(port!=null) for(int slot=3;slot<6;slot++) if(acceptsCoolant(port.inventory.getItem(slot))) pull(port,slot,3);
            int found=-1; for(int slot=3;slot<6;slot++) if(acceptsCoolant(inventory.getItem(slot))) {found=slot;break;}
            if(found<0) return -1; inventory.removeItem(found,1);
        }
        int remaining=50-progress%50; if(getBlockState().getValue(ReactorBlock.COOLANT)!=remaining) server.setBlock(worldPosition,getBlockState().setValue(ReactorBlock.COOLANT,remaining),3);
        return fits?production():0;
    }
    private void endOperation(ServerLevel server,boolean notify) {
        var event=notify&&!ingredient.isEmpty() && progress==total?new MachineOperationFinishEvent(server,this,processor,currentOperation()):null;
        ingredient=fuelOutput=ItemStack.EMPTY; progress=total=0; dirty=true; setChanged();
        if(event!=null) net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event);
    }
    private void melt(ServerLevel server) {
        dismantling=true;
        for(var p:server.players()) if(p.containerMenu instanceof ReactorMenu menu && menu.entity()==this) p.closeContainer();
        endOperation(server,true); inventory.clearContent();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new ReactorExplodeEvent(server,this));
        server.setBlockAndUpdate(worldPosition,Blocks.LAVA.defaultBlockState());
        server.explode(null,worldPosition.getX(),worldPosition.getY(),worldPosition.getZ(),0F,false,net.minecraft.world.level.Level.ExplosionInteraction.NONE);
    }
    public void dismantle() { if(!(level instanceof ServerLevel server) || dismantling) return; dismantling=true; Containers.dropContents(server,worldPosition,inventory); inventory.clearContent(); endOperation(server,true); }
    private final class Power implements IEnergyStorage {
        @Override public int receiveEnergy(int amount,boolean simulate) { return 0; }
        @Override public int extractEnergy(int amount,boolean simulate) { int value=Math.max(0,Math.min(amount,energy)); if(!simulate && value>0) {energy-=value;setChanged();wake();} return value; }
        @Override public int getEnergyStored() { return energy; }
        @Override public int getMaxEnergyStored() { return capacity(); }
        @Override public boolean canExtract() { return true; }
        @Override public boolean canReceive() { return false; }
    }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> capability,@Nullable Direction side) { return !isRemoved() && capability==ForgeCapabilities.ENERGY?power.cast():super.getCapability(capability,side); }
    @Override public void invalidateCaps() { super.invalidateCaps(); power.invalidate(); }
    @Override public void reviveCaps() { super.reviveCaps(); power=LazyOptional.of(Power::new); wake(); }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag); var stacks=NonNullList.withSize(7,ItemStack.EMPTY); for(int i=0;i<7;i++) stacks.set(i,inventory.getItem(i)); ContainerHelper.saveAllItems(tag,stacks); tag.putInt("energy",energy); tag.putBoolean("production",productionMode); tag.putBoolean("water_fault",waterFault);
        if(!ingredient.isEmpty()) { var op=new CompoundTag(); op.put("ingredient",ingredient.save(new CompoundTag()));op.put("output",fuelOutput.save(new CompoundTag()));op.putInt("progress",progress);op.putInt("total",total);tag.put("operation",op); }
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag); var stacks=NonNullList.withSize(7,ItemStack.EMPTY); ContainerHelper.loadAllItems(tag,stacks); loading=true; try {for(int i=0;i<7;i++) inventory.setItem(i,stacks.get(i));} finally {loading=false;}
        energy=Math.max(0,Math.min(capacity(),tag.getInt("energy")));productionMode=tag.getBoolean("production");waterFault=tag.getBoolean("water_fault");ingredient=fuelOutput=ItemStack.EMPTY;progress=total=0;
        if(tag.contains("operation")) {var op=tag.getCompound("operation");ingredient=ItemStack.of(op.getCompound("ingredient"));fuelOutput=ItemStack.of(op.getCompound("output"));progress=op.getInt("progress");total=op.getInt("total");if(!op.contains("output",Tag.TAG_COMPOUND)||!op.contains("total",Tag.TAG_INT)||ingredient.getCount()!=1||!op.contains("progress",Tag.TAG_INT)||total<=0||progress<0||progress>total||fuelOutput.getCount()>fuelOutput.getMaxStackSize())throw new IllegalArgumentException("Invalid reactor fuel operation");}
        wake();
    }
    @Override public Component getDisplayName() { return Component.translatable("block.slimefun."+definition.id()); }
    @Override public AbstractContainerMenu createMenu(int id,Inventory inv,Player player) { return new ReactorMenu(id,inv,this,inventory); }
}

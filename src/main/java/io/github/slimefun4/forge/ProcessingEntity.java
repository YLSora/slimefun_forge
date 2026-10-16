package io.github.slimefun4.forge;

import io.github.slimefun4.forge.api.Rechargeable;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.WeakHashMap;
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
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.*;
import net.minecraftforge.items.wrapper.InvWrapper;

/** Free input reservation, paid ten-tick progress, then one separately paid delivery. */
public final class ProcessingEntity extends BlockEntity implements MenuProvider,io.github.slimefun4.forge.api.MachineApi.ProcessHolder {
    private record ClearedKey(BlockPos pos,String id) {}
    private static final Map<ServerLevel,Map<ClearedKey,CompoundTag>> CLEARED=new WeakHashMap<>();
    public final Catalog.Entry definition;
    public final SimpleContainer inventory;
    private boolean loading,dismantling,findingRecipe,dirty=true;
    private int viewers;
    private int energy,progress,total;
    private ItemStack pending=ItemStack.EMPTY;
    private ItemStack remainder=ItemStack.EMPTY;
    private List<ItemStack> ingredients=List.of();
    private long lookups,operations;
    private final io.github.slimefun4.forge.api.MachineApi.Processor processor=AddonMachines.processor(this,this::currentOperation,()->endOperation((ServerLevel)level,true),()->endOperation((ServerLevel)level,false));
    @Override public io.github.slimefun4.forge.api.MachineApi.Processor processor(){return processor;}
    private io.github.slimefun4.forge.api.MachineApi.Operation currentOperation(){return pending.isEmpty()?null:new io.github.slimefun4.forge.api.MachineApi.Operation(SlimefunForge.key("processing"),new CompoundTag(),total,progress,ingredients,results());}
    private LazyOptional<IEnergyStorage> power=LazyOptional.of(Power::new);
    private LazyOptional<IItemHandler> items=createItems();
    public ProcessingEntity(BlockPos pos,BlockState state) {
        super(SlimefunForge.PROCESSING_ENTITY.get(),pos,state); definition=((ProcessingBlock)state.getBlock()).definition;
        inventory=new SimpleContainer(definition.implementation().equals("ElectricSmeltery")?8:4) {
            @Override public void setChanged() { super.setChanged(); if(!loading) { dirty=true; ProcessingEntity.this.setChanged(); wake(); } }
            @Override public void startOpen(Player player) { viewers++; recipesReloaded(); }
            @Override public void stopOpen(Player player) { viewers--; recipesReloaded(); }
        };
    }
    public int inputSlots() { return inventory.getContainerSize()-2; }
    public boolean oilPump() { return definition.implementation().equals("OilPump"); }
    public int energy() { return energy; }
    public int progress() { return progress; }
    public int total() { return total; }
    public ItemStack pending() { return pending.copy(); }
    public boolean hasViewers() { return viewers>0; }
    public long lookups() { return lookups; }
    public long operations() { return operations; }
    public void wake() {
        if(level!=null && !level.isClientSide && !isRemoved() && !dismantling && !level.getBlockTicks().hasScheduledTick(worldPosition,getBlockState().getBlock()))
            level.scheduleTick(worldPosition,getBlockState().getBlock(),MachineClock.delay(level));
    }
    public void recipesReloaded() { dirty=true; wake(); }
    @Override public void onLoad() {
        super.onLoad();
        if(level instanceof ServerLevel server && !isRemoved() && server.getBlockEntity(worldPosition)==this) {
            var operation=takeCleared();
            if(operation!=null) { var saved=saveWithoutMetadata(); saved.put("operation",operation); load(saved); setChanged(); }
            dirty=true; EnergyNetwork.add(this,definition.implementation()); wake();
        }
    }
    private CompoundTag takeCleared() {
        var operations=CLEARED.get(level); if(operations==null)return null;
        var operation=operations.remove(new ClearedKey(worldPosition,definition.id())); if(operations.isEmpty())CLEARED.remove(level); return operation;
    }
    static void unload(ServerLevel level) { CLEARED.remove(level); }
    /** RC-37 keeps cleared processor operations in memory, keyed by world, location and item. */
    public void clear() {
        if(!(level instanceof ServerLevel server) || isRemoved() || dismantling)return;
        takeCleared();
        if(!pending.isEmpty())CLEARED.computeIfAbsent(server,k->new HashMap<>()).put(new ClearedKey(worldPosition.immutable(),definition.id()),saveWithoutMetadata().getCompound("operation"));
        dismantling=true; inventory.clearContent();
    }
    @Override public void setRemoved() { EnergyNetwork.remove(this); super.setRemoved(); }
    @Override public void onChunkUnloaded() { EnergyNetwork.remove(this); super.onChunkUnloaded(); }
    private SimpleContainer copySlots(int offset) {
        var copy=new SimpleContainer(offset==0?inputSlots():2);
        for(int i=0;i<copy.getContainerSize();i++) copy.setItem(i,inventory.getItem(offset+i).copy()); return copy;
    }
    private SimpleContainer outputPlan(ItemStack... results) {
        var output=copySlots(inputSlots());
        for(var result:results) if(!output.addItem(result.copy()).isEmpty()) return null;
        return output;
    }
    private void commit(int offset,Container planned) {
        for(int i=0;i<planned.getContainerSize();i++) inventory.setItem(offset+i,planned.getItem(i));
    }
    private void reserve(List<ItemStack> consumed,ItemStack result,int cycles) {
        reserve(consumed,result,cycles,remainderFor(consumed));
    }
    private void reserve(List<ItemStack> consumed,ItemStack result,int cycles,ItemStack second) {
        ingredients=consumed.stream().map(ItemStack::copy).toList(); pending=result.copy(); remainder=second.copy(); total=cycles; progress=0; setChanged(); wake();
    }
    private ItemStack remainderFor(List<ItemStack> consumed) {
        if(definition.implementation().equals("Freezer") && consumed.size()==1 && (consumed.get(0).is(Items.WATER_BUCKET) || consumed.get(0).is(Items.LAVA_BUCKET))) return new ItemStack(Items.BUCKET);
        return ItemStack.EMPTY;
    }
    private void endOperation(ServerLevel server,boolean notify) {
        var completed=notify&&!pending.isEmpty() && progress>=total ? new MachineOperationFinishEvent(server,this,processor,currentOperation()) : null;
        pending=ItemStack.EMPTY; remainder=ItemStack.EMPTY; ingredients=List.of(); progress=0; total=0; dirty=true; setChanged(); wake();
        if(completed!=null) MinecraftForge.EVENT_BUS.post(completed);
    }
    private List<ItemStack> results() {
        if(remainder.isEmpty()) return List.of(pending);
        return Enchanting.machine(definition.implementation())?List.of(pending,remainder):List.of(remainder,pending);
    }
    /** Permanent removal, distinct from chunk unloading: only present inventory is dropped. */
    public void dismantle() {
        if(!(level instanceof ServerLevel server) || dismantling) return;
        takeCleared(); dismantling=true; Containers.dropContents(server,worldPosition,inventory); endOperation(server,true);
    }
    public void work() {
        if(!(level instanceof ServerLevel server) || isRemoved() || dismantling || findingRecipe) return;
        if(definition.implementation().equals("ChargingBench")){chargeItems();return;}
        if(!pending.isEmpty()) {
            if(energy<definition.consumption()) return;
            SimpleContainer planned=null;
            if(progress>=total) { planned=outputPlan(results().toArray(ItemStack[]::new)); if(planned==null) return; }
            energy-=definition.consumption();
            if(progress<total) progress++;
            else {
                commit(inputSlots(),planned); operations++; endOperation(server,true); return;
            }
            setChanged(); wake(); return;
        }
        if(!dirty) return;
        dirty=false; lookups++;
        if(Enchanting.machine(definition.implementation())) {
            findingRecipe=true;
            try {
                var plan=Enchanting.find(this);if(plan==null || dismantling || isRemoved() || outputPlan(plan.result(),plan.second())==null) return;
                inventory.removeItem(0,1);inventory.removeItem(1,1);reserve(plan.ingredients(),plan.result(),plan.cycles(),plan.second());
            } finally { findingRecipe=false; }
            return;
        } else if(definition.implementation().equals("ProduceCollector")) {
            findingRecipe=true;
            try {
                for(int slot=0;slot<inputSlots();slot++)for(var produce:AnimalProduce.RECIPES) {
                    var input=inventory.getItem(slot);
                    if(!ItemAccess.enabled(server,input)||!ItemAccess.enabled(server,produce.output())||!MachineRecipe.same(input,produce.input()) || outputPlan(produce.output())==null)continue;
                    dirty=true;wake();int range=AnimalProduce.RANGE.get();
                    var bounds=new net.minecraft.world.phys.AABB(worldPosition.getX()-range,worldPosition.getY()-range,worldPosition.getZ()-range,
                        worldPosition.getX()+range,worldPosition.getY()+range,worldPosition.getZ()+range);
                    if(server.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,bounds,produce.predicate()).isEmpty())continue;
                    if(dismantling || isRemoved() || inventory.getItem(slot)!=input || !MachineRecipe.same(input,produce.input()) || outputPlan(produce.output())==null)return;
                    reserve(List.of(inventory.removeItem(slot,1)),produce.output(),10);return;
                }
            } finally {findingRecipe=false;}
        } else if(definition.implementation().equals("AutoBrewer")) {
            var result=Brewing.brew(inventory.getItem(0),inventory.getItem(1));
            if(result.isEmpty() || outputPlan(result)==null) return;
            var consumed=List.of(inventory.removeItem(0,1),inventory.removeItem(1,1));
            reserve(consumed,result,60);return;
        } else if(definition.implementation().equals("AutoAnvil")) {
            for(int slot=0;slot<2;slot++) {
                var item=inventory.getItem(slot);var tape=inventory.getItem(1-slot);
                if(item.getMaxDamage()<=0 || item.getDamageValue()<=0) continue;
                if(item.getCount()!=1 || !tape.is(SlimefunForge.item("duct_tape"))) return;
                var repaired=item.copy();
                repaired.setDamageValue(Math.max(0,item.getDamageValue()-item.getMaxDamage()/(definition.id().equals("auto_anvil_2")?4:10)));
                if(outputPlan(repaired)==null) return;
                var consumed=List.of(inventory.removeItem(0,1),inventory.removeItem(1,1));
                reserve(consumed,repaired,60);return;
            }
        } else if(definition.implementation().equals("ElectricGoldPan")) {
            if(!GoldPanDrops.outputLimitOverridden(definition.id()) && !inventory.getItem(2).isEmpty() && !inventory.getItem(3).isEmpty()) return;
            boolean retry=false;
            for(int slot=0;slot<2;slot++) {
                int group=GoldPanDrops.inputGroup(inventory.getItem(slot));
                if(group<0 || !GoldPanDrops.canProduce(group,result->outputPlan(result)!=null)) continue;
                var result=GoldPanDrops.pick(group);
                if(!result.isEmpty() && outputPlan(result)!=null) {
                    var consumed=inventory.removeItem(slot,1);
                    reserve(List.of(consumed),result,((group==0?3:4)/definition.speed())*2); return;
                }
                retry=true;
            }
            if(retry) { dirty=true; wake(); }
        } else if(definition.implementation().equals("ElectricDustWasher")) {
            boolean retry=false;
            for(int slot=0;slot<2;slot++) {
                var input=inventory.getItem(slot); ItemStack result;
                boolean sifted=MachineRecipe.same(input,new ItemStack(SlimefunForge.item("sifted_ore")));
                if(sifted) {
                    if(!OreWashing.legacyDust()) {
                        // RC-37 stops the entire input search here, even if the next input could fit.
                        if(!inventory.getItem(2).isEmpty() && !inventory.getItem(3).isEmpty()) return;
                    } else if(!OreWashing.canProduce(dust->outputPlan(dust)!=null)) continue;
                    result=OreWashing.randomDust();
                } else if(MachineRecipe.same(input,new ItemStack(SlimefunForge.item("pulverized_ore")))) result=new ItemStack(SlimefunForge.item("pure_ore_cluster"));
                else if(MachineRecipe.same(input,new ItemStack(Items.SAND))) result=new ItemStack(SlimefunForge.item("salt"));
                else continue;
                if(outputPlan(result)!=null) {
                    var consumed=inventory.removeItem(slot,1); reserve(List.of(consumed),result,(4/definition.speed())*2); return;
                }
                if(sifted) retry=true;
            }
            if(retry) { dirty=true; wake(); }
        } else if(oilPump()) {
            var oil=new ItemStack(SlimefunForge.item("bucket_of_oil")); if(outputPlan(oil)==null) return;
            for(int slot=0;slot<2;slot++) {
                var bucket=inventory.getItem(slot);
                if(!MachineRecipe.same(bucket,new ItemStack(Items.BUCKET))) continue;
                if(GeoResourceData.get(server).extractOne(server,worldPosition,GeoResources.OIL)) {
                    var consumed=inventory.removeItem(slot,1); reserve(List.of(consumed),oil,52);
                } else {
                    var returned=outputPlan(bucket); if(returned==null) return;
                    inventory.setItem(slot,ItemStack.EMPTY); commit(2,returned);
                }
                return;
            }
        } else {
            String station=recipeStation(definition.implementation());
            if(station==null)throw new IllegalStateException("Unsupported processor "+definition.id());
            for(var recipe:MachineRecipe.forStation(level,station)) {
                if(recipe.shaped) continue;
                var input=copySlots(0); boolean matches=true;
                var consumed=new ArrayList<ItemStack>();
                boolean[] used=new boolean[inputSlots()];
                for(var required:recipe.inputs) {
                    if(required.isEmpty()) continue;
                    int count=station.equals("freezer")?Freezing.amount(definition.id(),required):required.getCount();
                    int found=-1;
                    // Recycle real native potions, including their contents; other ingredients keep strict identity.
                    boolean potion=station.equals("auto_drier") && (required.is(Items.POTION) || required.is(Items.SPLASH_POTION) || required.is(Items.LINGERING_POTION));
                    for(int i=0;i<inputSlots();i++) if(!used[i] && (potion?input.getItem(i).is(required.getItem()):MachineRecipe.same(input.getItem(i),required)) && input.getItem(i).getCount()>=count) { found=i; break; }
                    if(found<0) { matches=false; break; }
                    consumed.add(input.removeItem(found,count)); used[found]=true;
                }
                if(!matches) continue;
                if(outputPlan(remainderFor(consumed),recipe.output)==null) return;
                commit(0,input); reserve(consumed,recipe.output,recipe.ticks/10/Math.max(1,definition.speed())); return;
            }
        }
    }
    static String recipeStation(String implementation){
        return switch(implementation) {
                case "Refinery" -> "refinery";
                case "ElectricPress" -> "electric_press";
                case "ElectricIngotFactory" -> "ingot_factory";
                case "ElectricIngotPulverizer" -> "ingot_pulverizer";
                case "ElectricFurnace" -> "electric_furnace";
                case "ElectricSmeltery" -> "electric_smeltery";
                case "CarbonPress" -> "carbon_press";
                case "ElectrifiedCrucible" -> "electrified_crucible";
                case "ElectricOreGrinder" -> "electric_ore_grinder";
                case "HeatedPressureChamber" -> "heated_pressure_chamber";
                case "Freezer" -> "freezer";
                case "AutoDrier" -> "auto_drier";
                case "FoodFabricator" -> "food_fabricator";
                case "FoodComposter" -> "food_composter";
                default -> null;
            };
    }
    private void chargeItems(){
        if(energy<definition.consumption()||!dirty)return;
        dirty=false;lookups++;
        for(int slot=0;slot<inputSlots();slot++){
            var item=inventory.getItem(slot);if(item.isEmpty()||!ItemAccess.enabled(level,item))continue;
            if(item.getItem() instanceof Rechargeable rechargeable){
                if(rechargeable.addItemCharge(item,definition.consumption()/2F)){energy-=definition.consumption();operations++;inventory.setChanged();}
                else {var planned=outputPlan(item);if(planned!=null){inventory.setItem(slot,ItemStack.EMPTY);commit(inputSlots(),planned);}}
                return;
            }
            var key=net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item.getItem());
            if(key.getNamespace().equals(SlimefunForge.ID)&&SlimefunForge.CONTENT.containsKey(key.getPath())){
                var planned=outputPlan(item);if(planned!=null){inventory.setItem(slot,ItemStack.EMPTY);commit(inputSlots(),planned);}
            }
        }
    }
    private boolean accepts(int slot,ItemStack stack) {
        if(slot<0 || slot>=inputSlots()) return false;
        if(!definition.implementation().equals("HeatedPressureChamber") && !definition.implementation().equals("ElectricSmeltery")) return true;
        boolean any=false;
        for(int i=0;i<inputSlots();i++) if(MachineRecipe.same(inventory.getItem(i),stack)) any=true;
        if(!any) return true;
        var here=inventory.getItem(slot); if(!MachineRecipe.same(here,stack) || here.getCount()>=here.getMaxStackSize()) return false;
        for(int i=0;i<inputSlots();i++) {
            var other=inventory.getItem(i);
            if(MachineRecipe.same(other,stack) && other.getCount()<here.getCount() && other.getCount()<other.getMaxStackSize()) return false;
        }
        return true;
    }
    private LazyOptional<IItemHandler> createItems() { return LazyOptional.of(()->new InvWrapper(inventory) {
        @Override public ItemStack insertItem(int slot,ItemStack stack,boolean simulate) { return accepts(slot,stack)?super.insertItem(slot,stack,simulate):stack; }
        @Override public ItemStack extractItem(int slot,int amount,boolean simulate) { return slot>=inputSlots() && slot<inventory.getContainerSize()?super.extractItem(slot,amount,simulate):ItemStack.EMPTY; }
        @Override public boolean isItemValid(int slot,ItemStack stack) { return accepts(slot,stack); }
    }); }
    private final class Power implements IEnergyStorage {
        @Override public int receiveEnergy(int amount,boolean simulate) {
            int accepted=Math.min(Math.max(0,amount),definition.capacity()-energy);
            if(!simulate && accepted>0) { energy+=accepted; setChanged(); wake(); } return accepted;
        }
        @Override public int extractEnergy(int amount,boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return energy; }
        @Override public int getMaxEnergyStored() { return definition.capacity(); }
        @Override public boolean canReceive() { return true; }
        @Override public boolean canExtract() { return false; }
    }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap,@Nullable Direction side) {
        if(!isRemoved() && cap==ForgeCapabilities.ENERGY) return power.cast();
        if(!isRemoved() && cap==ForgeCapabilities.ITEM_HANDLER) return items.cast();
        return super.getCapability(cap,side);
    }
    @Override public void invalidateCaps() { super.invalidateCaps(); power.invalidate(); items.invalidate(); }
    @Override public void reviveCaps() { super.reviveCaps(); power=LazyOptional.of(Power::new); items=createItems(); wake(); }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        var slots=NonNullList.withSize(inventory.getContainerSize(),ItemStack.EMPTY); for(int i=0;i<slots.size();i++) slots.set(i,inventory.getItem(i));
        ContainerHelper.saveAllItems(tag,slots); tag.putInt("energy",energy);
        if(!pending.isEmpty()) {
            var operation=new CompoundTag(); operation.put("result",pending.save(new CompoundTag()));
            if(!remainder.isEmpty()) operation.put("remainder",remainder.save(new CompoundTag()));
            var inputs=new ListTag(); for(var ingredient:ingredients) inputs.add(ingredient.save(new CompoundTag())); operation.put("ingredients",inputs);
            operation.putInt("total",total); operation.putInt("progress",progress); tag.put("operation",operation);
        }
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag); loading=true;
        var slots=NonNullList.withSize(inventory.getContainerSize(),ItemStack.EMPTY); ContainerHelper.loadAllItems(tag,slots);
        for(int i=0;i<slots.size();i++) inventory.setItem(i,slots.get(i)); loading=false;
        energy=Math.max(0,Math.min(definition.capacity(),tag.getInt("energy")));
        pending=ItemStack.EMPTY; remainder=ItemStack.EMPTY; ingredients=List.of(); total=0; progress=0;
        if(tag.contains("operation",Tag.TAG_COMPOUND)) {
            var op=tag.getCompound("operation"); pending=ItemStack.of(op.getCompound("result")); total=op.getInt("total"); progress=op.getInt("progress");
            remainder=ItemStack.of(op.getCompound("remainder"));
            var inputs=op.getList("ingredients",Tag.TAG_COMPOUND); var restored=new ArrayList<ItemStack>();
            for(int i=0;i<inputs.size();i++) restored.add(ItemStack.of(inputs.getCompound(i))); ingredients=List.copyOf(restored);
            boolean validRemainder=Enchanting.machine(definition.implementation())?Enchanting.validSecond(definition.implementation(),pending,remainder):ItemStack.matches(remainder,remainderFor(ingredients));
            if(pending.isEmpty() || pending.getCount()>pending.getMaxStackSize() || !validRemainder || ingredients.isEmpty() || ingredients.size()>inputSlots() || ingredients.stream().anyMatch(s->s.isEmpty() || s.getCount()>s.getMaxStackSize()) || total<0 || total>7200 || progress<0 || progress>total)
                throw new IllegalArgumentException("Invalid reserved processing operation at "+worldPosition);
        }
        dirty=true; wake();
    }
    @Override public Component getDisplayName() { return Component.translatable("item.slimefun."+definition.id()); }
    @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player player) { return new ProcessingMenu(id,inventory,this); }
}

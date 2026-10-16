package io.github.slimefun4.forge;

import java.util.*;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;

/** A selection and charge, never a paid queue: one complete inventory transaction per interval. */
public final class AutoCrafterEntity extends BlockEntity {
    public final Catalog.Entry definition;
    private ResourceLocation selected;
    private boolean enabled=true,working;
    private int energy,revision;
    private long operations;
    private LazyOptional<IEnergyStorage> power=LazyOptional.of(Power::new);
    public AutoCrafterEntity(BlockPos pos,BlockState state) { super(SlimefunForge.AUTOCRAFTER_ENTITY.get(),pos,state); definition=((AutoCrafterBlock)state.getBlock()).definition; }
    public ResourceLocation selected() { return selected; }
    public boolean enabled() { return enabled; }
    public int energy() { return energy; }
    public int revision() { return revision; }
    public long operations() { return operations; }
    public boolean vanilla() { return definition.implementation().equals("VanillaAutoCrafter"); }
    public boolean supports(Recipe<?> recipe) {
        if(vanilla()) return recipe instanceof ShapedRecipe shaped && shaped.getWidth()<=3 && shaped.getHeight()<=3 || recipe instanceof ShapelessRecipe && recipe.getIngredients().size()<=9;
        return recipe instanceof MachineRecipe machine && (machine.station.equals(definition.implementation().equals("ArmorAutoCrafter")?"armor_forge":"enhanced_crafting_table")||AddonMachines.autoCraft(AddonMachines.key(definition.id()),machine.station));
    }
    @Nullable public Recipe<?> recipe() {
        var value=level==null || selected==null?null:level.getRecipeManager().byKey(selected).orElse(null);
        return value!=null && supports(value)?value:null;
    }
    public void select(@Nullable ResourceLocation key) { selected=key; if(key==null) enabled=true; revision++; setChanged(); wake(); }
    public void setEnabled(boolean value) { if(enabled!=value) { enabled=value; revision++; setChanged(); wake(); } }
    public void wake() {
        if(level!=null && !level.isClientSide && !isRemoved() && !level.getBlockTicks().hasScheduledTick(worldPosition,getBlockState().getBlock()))
            level.scheduleTick(worldPosition,getBlockState().getBlock(),MachineClock.delay(level));
    }
    @Override public void onLoad() { super.onLoad(); EnergyNetwork.add(this,definition.implementation()); wake(); }
    @Override public void setRemoved() { EnergyNetwork.remove(this); super.setRemoved(); }
    @Override public void onChunkUnloaded() { EnergyNetwork.remove(this); super.onChunkUnloaded(); }
    @Nullable Container inventory() {
        var pos=worldPosition.below();
        if(!level.hasChunkAt(pos)) return null;
        var state=level.getBlockState(pos);
        if(!state.is(AutoCrafterBlock.STORAGE)) return null;
        if(state.getBlock() instanceof ChestBlock chest) {
            if(state.getValue(ChestBlock.TYPE)!=ChestType.SINGLE && !level.hasChunkAt(pos.relative(ChestBlock.getConnectedDirection(state)))) return null;
            return ChestBlock.getContainer(chest,state,level,pos,true);
        }
        return level.getBlockEntity(pos) instanceof Container container?container:null;
    }
    public void work() {
        if(!(level instanceof ServerLevel server) || isRemoved() || working || !enabled || energy<definition.consumption()) return;
        var recipe=recipe(); if(recipe==null) return;
        var inventory=inventory(); if(inventory==null) return;
        working=true;
        try {
            if(!craft(inventory,recipe)) return;
            energy-=definition.consumption(); operations++; setChanged(); wake();
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,worldPosition.getX()+.5,worldPosition.getY()+.8,worldPosition.getZ()+.5,6,0,0,0,1);
        } finally { working=false; }
    }
    private boolean craft(Container inventory,Recipe<?> recipe) {
        if(recipe instanceof MachineRecipe machine)return craftMachine(inventory,machine);
        var before=MachineRecipe.copy(inventory); boolean empty=false;
        for(int i=0;i<before.getContainerSize();i++) if(before.getItem(i).isEmpty()) { empty=true; break; }
        if(!empty) return false;
        List<Predicate<ItemStack>> ingredients=new ArrayList<>();
        for(var ingredient:recipe.getIngredients()) if(!ingredient.isEmpty()) ingredients.add(actual->allowedVanilla(actual) && ingredient.test(actual));
        if(ingredients.isEmpty()) return false;
        var planned=MachineRecipe.copy(before); var result=recipe.getResultItem(level.registryAccess()).copy();
        if(result.isEmpty()) return false;
        var outputs=new ArrayList<ItemStack>(); boolean backpackCopied=false;
        for(var ingredient:ingredients) {
            int found=-1;
            for(int i=0;i<planned.getContainerSize();i++) if(!planned.getItem(i).isEmpty() && ingredient.test(planned.getItem(i))) { found=i; break; }
            if(found<0) return false;
            var original=planned.getItem(found);
            if(!backpackCopied && result.getItem() instanceof io.github.slimefun4.forge.api.BackpackApi.Bag && original.getItem() instanceof io.github.slimefun4.forge.api.BackpackApi.Bag) { if(original.hasTag())result.setTag(original.getTag().copy());if(Soulbinding.bound(original))Soulbinding.bind(result);backpackCopied=true; }
            if(original.hasCraftingRemainingItem()) outputs.add(original.getCraftingRemainingItem());
            else if(original.is(Items.POTION) || original.is(Items.DRAGON_BREATH) || original.is(Items.HONEY_BOTTLE)) outputs.add(new ItemStack(Items.GLASS_BOTTLE));
            original.shrink(1);
        }
        outputs.add(0,result);
        for(var output:outputs) if(!insert(planned,inventory,output)) return false;
        for(int i=0;i<before.getContainerSize();i++) if(!ItemStack.matches(before.getItem(i),inventory.getItem(i))) return false;
        MachineRecipe.commit(inventory,planned);
        return true;
    }
    private boolean craftMachine(Container inventory,MachineRecipe recipe){
        var before=MachineRecipe.copy(inventory);
        var matching=recipe.shaped?new MachineRecipe(recipe.getId(),recipe.station,recipe.inputs,recipe.output,false,recipe.ticks,recipe.oreDoubling):recipe;
        var debit=matching.debits(before);
        if(debit==null||!ItemAccess.enabled(level,recipe.output))return false;
        for(int slot=0;slot<debit.length;slot++)if(debit[slot]>0&&!ItemAccess.enabled(level,before.getItem(slot)))return false;
        var plan=matching.prepare(before);if(plan==null)return false;
        for(var output:plan.outputs())if(!insert(plan.input(),inventory,output.copy()))return false;
        if(recipe()!=recipe||!enabled||isRemoved()||inventory()!=inventory)return false;
        for(int slot=0;slot<before.getContainerSize();slot++)if(!ItemStack.matches(before.getItem(slot),inventory.getItem(slot)))return false;
        MachineRecipe.commit(inventory,plan.input());return true;
    }
    private static boolean allowedVanilla(ItemStack item) {
        var key=net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item.getItem());
        return key!=null && (!key.getNamespace().equals(SlimefunForge.ID) || Set.of("synthetic_diamond","synthetic_sapphire","synthetic_emerald","synthetic_shulker_shell","gold_4k").contains(key.getPath()));
    }
    private static boolean insert(SimpleContainer planned,Container target,ItemStack output) {
        for(int pass=0;pass<2;pass++) for(int slot=0;slot<planned.getContainerSize() && !output.isEmpty();slot++) {
            var stored=planned.getItem(slot);
            if((pass==0?stored.isEmpty() || !ItemStack.isSameItemSameTags(stored,output):!stored.isEmpty()) || !target.canPlaceItem(slot,output)) continue;
            int amount=Math.min(output.getCount(),Math.min(target.getMaxStackSize(),output.getMaxStackSize())-stored.getCount());
            if(amount<=0) continue;
            if(stored.isEmpty()) planned.setItem(slot,output.copyWithCount(amount)); else stored.grow(amount);
            output.shrink(amount);
        }
        return output.isEmpty();
    }
    private final class Power implements IEnergyStorage {
        @Override public int receiveEnergy(int amount,boolean simulate) {
            int accepted=Math.max(0,Math.min(amount,definition.capacity()-energy));
            if(!simulate && accepted>0) { energy+=accepted; setChanged(); wake(); }
            return accepted;
        }
        @Override public int extractEnergy(int amount,boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return energy; }
        @Override public int getMaxEnergyStored() { return definition.capacity(); }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return true; }
    }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> capability,@Nullable Direction side) {
        if(!isRemoved() && capability==ForgeCapabilities.ENERGY) return power.cast();
        return super.getCapability(capability,side);
    }
    @Override public void invalidateCaps() { super.invalidateCaps(); power.invalidate(); }
    @Override public void reviveCaps() { super.reviveCaps(); power=LazyOptional.of(Power::new); wake(); }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag); tag.putInt("energy",energy); tag.putBoolean("enabled",enabled); if(selected!=null) tag.putString("recipe",selected.toString());
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag); energy=Math.max(0,Math.min(definition.capacity(),tag.getInt("energy"))); selected=ResourceLocation.tryParse(tag.getString("recipe")); enabled=tag.getBoolean("enabled"); revision++; wake();
    }
}

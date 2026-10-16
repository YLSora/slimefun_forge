package io.github.slimefun4.forge.api;

import io.github.slimefun4.forge.ItemRules;
import net.minecraft.core.Direction;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;

/** Native Item contract consumed by charging stations, solar helmets, charge command and tooltips.
 * Charge belongs to one nonstackable item, in hundredths of a joule. FE transfers whole joules.
 * Item subclasses expose energyCapability from initCapabilities; server writes require the owning thread.
 */
public interface Rechargeable {
    float getMaxItemCharge(ItemStack stack);
    private float capacity(ItemStack stack){
        if(stack.isEmpty()||stack.getCount()!=1||stack.getMaxStackSize()!=1)throw new IllegalArgumentException("Rechargeable items must be nonstackable singleton stacks");
        float capacity=getMaxItemCharge(stack);ItemRules.charge(0,capacity);return capacity;
    }
    default float getItemCharge(ItemStack stack){
        float capacity=capacity(stack);var tag=stack.getTag();if(tag==null||!tag.contains("charge"))return 0;
        if(!tag.contains("charge",Tag.TAG_ANY_NUMERIC))throw new IllegalArgumentException("Invalid item charge tag");
        return ItemRules.charge(tag.getFloat("charge"),capacity);
    }
    default void setItemCharge(ItemStack stack,float charge){float valid=ItemRules.charge(charge,capacity(stack));stack.getOrCreateTag().putFloat("charge",valid);}
    default boolean addItemCharge(ItemStack stack,float amount){
        float before=getItemCharge(stack),after=ItemRules.addCharge(before,amount,capacity(stack));if(after==before)return false;setItemCharge(stack,after);return true;
    }
    default boolean removeItemCharge(ItemStack stack,float amount){
        if(!Float.isFinite(amount)||amount<=0)throw new IllegalArgumentException("Charge amount must be positive and finite");
        float before=getItemCharge(stack);if(before<amount)return false;
        float after=ItemRules.subtractCharge(before,amount,capacity(stack));if(after==before)return false;setItemCharge(stack,after);return true;
    }
    default ICapabilityProvider energyCapability(ItemStack stack){
        var energy=LazyOptional.<IEnergyStorage>of(()->new IEnergyStorage(){
            @Override public int receiveEnergy(int amount,boolean simulate){float charge=getItemCharge(stack);int accepted=ItemRules.receiveCharge(charge,amount,capacity(stack));if(!simulate&&accepted>0)addItemCharge(stack,accepted);return accepted;}
            @Override public int extractEnergy(int amount,boolean simulate){return 0;}
            @Override public int getEnergyStored(){return (int)Math.floor(getItemCharge(stack));}
            @Override public int getMaxEnergyStored(){return (int)Math.floor(capacity(stack));}
            @Override public boolean canReceive(){return true;}
            @Override public boolean canExtract(){return false;}
        });
        return new ICapabilityProvider(){@Override public <T> LazyOptional<T> getCapability(Capability<T> capability,Direction side){return capability==ForgeCapabilities.ENERGY?energy.cast():LazyOptional.empty();}};
    }
}

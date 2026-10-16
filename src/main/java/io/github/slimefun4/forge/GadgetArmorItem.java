package io.github.slimefun4.forge;

import io.github.slimefun4.forge.api.Rechargeable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

public class GadgetArmorItem extends ArmorItem implements io.github.slimefun4.forge.api.EquipmentApi.FlightGear {
    protected final Catalog.Entry entry;
    protected GadgetArmorItem(Catalog.Entry entry) {
        super(entry.material().startsWith("leather_")?ArmorMaterials.LEATHER:ArmorMaterials.IRON,
            Type.valueOf(entry.material().substring(entry.material().indexOf('_')+1).toUpperCase(java.util.Locale.ROOT)),new Properties());
        this.entry=entry;
    }
    public static Item create(Catalog.Entry entry) {
        if(entry.behavior().equals("jetpack")||entry.behavior().equals("jetboots"))
            return entry.material().startsWith("leather_")?new ColoredJet(entry):new Jet(entry);
        return entry.material().startsWith("leather_")?new Colored(entry):new GadgetArmorItem(entry);
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        if(player instanceof ServerPlayer p&&!ResearchData.canUse(p,p.getItemInHand(hand))) {
            ItemAccess.denied(p,p.getItemInHand(hand));p.inventoryMenu.sendAllDataToRemote();return InteractionResultHolder.fail(p.getItemInHand(hand));
        }
        return super.use(level,player,hand);
    }
    @Override public void onArmorTick(ItemStack stack,Level level,Player player) {
        if(entry.id().equals("solar_helmet")&&player instanceof ServerPlayer server)io.github.slimefun4.forge.api.EquipmentApi.chargeFromSun(server,stack,.1F);
    }
    @Override public io.github.slimefun4.forge.api.EquipmentApi.Flight flight(ItemStack stack){
        var mode=entry.behavior().equals("jetpack")?io.github.slimefun4.forge.api.EquipmentApi.FlightMode.JETPACK:entry.behavior().equals("jetboots")?io.github.slimefun4.forge.api.EquipmentApi.FlightMode.JETBOOTS:io.github.slimefun4.forge.api.EquipmentApi.FlightMode.PARACHUTE;
        return new io.github.slimefun4.forge.api.EquipmentApi.Flight(mode,entry.thrust());
    }
    private static class Colored extends GadgetArmorItem implements DyeableLeatherItem {
        Colored(Catalog.Entry entry){super(entry);}
        @Override public int getColor(ItemStack stack){return hasCustomColor(stack)?DyeableLeatherItem.super.getColor(stack):entry.armorColor();}
    }
    public static class Jet extends GadgetArmorItem implements Rechargeable {
        Jet(Catalog.Entry entry){super(entry);}
        @Override public float getMaxItemCharge(ItemStack stack){return entry.capacity();}
        @Override public net.minecraftforge.common.capabilities.ICapabilityProvider initCapabilities(ItemStack stack,net.minecraft.nbt.CompoundTag tag){return energyCapability(stack);}
    }
    private static final class ColoredJet extends Jet implements DyeableLeatherItem {
        ColoredJet(Catalog.Entry entry){super(entry);}
        @Override public int getColor(ItemStack stack){return hasCustomColor(stack)?DyeableLeatherItem.super.getColor(stack):entry.armorColor();}
    }
}

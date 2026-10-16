package io.github.slimefun4.forge.api;

import io.github.slimefun4.forge.*;
import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Native equipment behavior shared with core gadgets; ordinary armor effects use onArmorTick. */
public final class EquipmentApi {
    public enum FlightMode { JETPACK, JETBOOTS, PARACHUTE }
    public record Flight(FlightMode mode,double thrust) {
        public Flight { Objects.requireNonNull(mode);EquipmentRules.thrust(thrust); }
        public EquipmentSlot slot(){return mode==FlightMode.JETBOOTS?EquipmentSlot.FEET:EquipmentSlot.CHEST;}
    }
    /** Implement on a native Item. Query is pure and receives a copy. Jets must also be Rechargeable.
     * A crouch transition starts the existing core task. Release, empty charge, replacement or travel stops it.
     */
    public interface FlightGear { Flight flight(ItemStack stack); }
    /** Call from a native helmet's onArmorTick. Uses core cadence, daylight and exposed Overworld skylight.
     * Only the actual worn stack may supply charge. Targets keep the same Rechargeable NBT/FE source.
     */
    public static int chargeFromSun(ServerPlayer player,ItemStack helmet,float amount){return FlightGadgets.chargeFromSun(player,helmet,amount);}
    private EquipmentApi(){}
}

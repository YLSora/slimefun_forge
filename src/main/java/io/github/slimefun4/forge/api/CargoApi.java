package io.github.slimefun4.forge.api;

import io.github.slimefun4.forge.CargoNetwork;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Native nodes share the core filtering, routing, rollback and fair cursor. */
public final class CargoApi {
    public enum Role { MANAGER, CONNECTOR, INPUT, OUTPUT }
    public interface Node {
        Role cargoRole();
        default int channel(){return 0;}
        /** Direction from this node to its inventory; null disables this endpoint. */
        default Direction attachedFace(){return null;}
        default boolean accepts(ItemStack stack){return true;}
        default boolean roundRobin(){return false;}
        default boolean smartFill(){return false;}
        default boolean visualizer(){return false;}
        default void cargoStatus(Component status){}
    }
    /** Implement on an inventory BE to select capability slots for this transfer direction/item. */
    public interface Endpoint {int[] cargoSlots(Direction capabilityFace,boolean extracting,ItemStack offered,int slotCount);}
    public static void register(BlockEntity owner){if(!(owner instanceof Node))throw new IllegalArgumentException("Cargo node required");CargoNetwork.add(owner);}
    public static void unregister(BlockEntity owner){CargoNetwork.remove(owner);}
    public static void tick(BlockEntity manager){CargoNetwork.tick(manager);}
    public static java.util.Optional<NetworkApi.View> network(ServerLevel level,BlockPos pos){return CargoNetwork.network(level,pos);}
    public static boolean matches(ItemStack current,ItemStack filter,boolean checkLore){return CargoNetwork.matches(current,filter,checkLore);}
    private CargoApi(){}
}

package io.github.slimefun4.forge.api;

import io.github.slimefun4.forge.EnergyNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/** A native FE block joins the Slimefun grid only after explicit role registration. */
public final class EnergyApi {
    public enum Role { REGULATOR, CONNECTOR, GENERATOR, CAPACITOR, CONSUMER }
    public interface Member {
        Role energyRole();
        /** Source/capacitor absolute buffer write, without external callbacks. Validate 0..FE capacity and mark dirty. */
        default void networkEnergy(int amount){throw new IllegalStateException("This member has no writable source buffer");}
        default void networkTick(){}
        default void networkStatus(Component status){}
    }
    public static void register(BlockEntity owner){if(!(owner instanceof Member member))throw new IllegalArgumentException("Energy member required");EnergyNetwork.add(owner,"addon:"+member.energyRole());}
    public static void unregister(BlockEntity owner){EnergyNetwork.remove(owner);}
    public static void tick(BlockEntity regulator){EnergyNetwork.tick(regulator);}
    public static boolean connected(ServerLevel level,BlockPos pos){NetworkApi.check(level);return EnergyNetwork.connected(level,pos);}
    public static java.util.Optional<NetworkApi.View> network(ServerLevel level,BlockPos pos){return EnergyNetwork.network(level,pos);}
    private EnergyApi(){}
}

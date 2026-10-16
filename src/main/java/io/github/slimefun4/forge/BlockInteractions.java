package io.github.slimefun4.forge;

import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** RC-37 BlockUseHandler runs even when a player crouches with an item. */
@Mod.EventBusSubscriber(modid=SlimefunForge.ID)
public final class BlockInteractions {
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void directUse(PlayerInteractEvent.RightClickBlock event) {
        if(!event.getEntity().isSecondaryUseActive() || event.getUseBlock()==Event.Result.DENY) return;
        var block=event.getLevel().getBlockState(event.getPos()).getBlock();
        if(block instanceof CrucibleBlock || block instanceof ComposterBlock || block instanceof ProjectorBlock
            || block instanceof ElevatorBlock || block instanceof GeoScannerBlock || block instanceof GpsControlPanelBlock
            || block instanceof CargoBlock cargo && (cargo.definition.id().equals("cargo_manager") || cargo.definition.id().equals("cargo_node"))
            || block instanceof MachineBlock machine && (machine.definition.implementation().equals("EnergyConnector") || machine.definition.implementation().equals("SolarGenerator"))) {
            event.setUseBlock(Event.Result.ALLOW);
        }
    }
    private BlockInteractions() {}
}

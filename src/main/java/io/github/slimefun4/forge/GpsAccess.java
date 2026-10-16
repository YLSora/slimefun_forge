package io.github.slimefun4.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import net.minecraftforge.server.permission.nodes.*;

@Mod.EventBusSubscriber(modid=SlimefunForge.ID)
public final class GpsAccess {
    public static final PermissionNode<Boolean> BYPASS=new PermissionNode<>(SlimefunForge.ID,"gps.bypass",PermissionTypes.BOOLEAN,(player,id,context)->player!=null&&player.server.getPlayerList().isOp(player.getGameProfile()));
    @SubscribeEvent public static void register(PermissionGatherEvent.Nodes event){event.addNodes(BYPASS);}
    public static boolean bypass(ServerPlayer player){return PermissionAPI.getPermission(player,BYPASS);}
    public static boolean fixed(Level level,BlockPos pos){var block=level.getBlockState(pos).getBlock();return block instanceof GpsControlPanelBlock||block instanceof GeoScannerBlock;}
    public static boolean mayOpen(ServerPlayer player,BlockPos pos){return player.level().mayInteract(player,pos)||bypass(player);}
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void inventoryOnly(PlayerInteractEvent.RightClickBlock event){
        if(event.getEntity() instanceof ServerPlayer player&&fixed(player.level(),event.getPos())&&!player.level().mayInteract(player,event.getPos())&&bypass(player)){
            // The permission opens these inventories; it must not authorize the held item's use.
            event.setUseItem(Event.Result.DENY);
            if(event.getUseBlock()!=Event.Result.DENY)event.setUseBlock(Event.Result.ALLOW);
        }
    }
}

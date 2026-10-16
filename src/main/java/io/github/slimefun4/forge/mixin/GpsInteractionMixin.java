package io.github.slimefun4.forge.mixin;

import io.github.slimefun4.forge.GpsAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** Vanilla spawn protection runs before Forge's block-use event. */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class GpsInteractionMixin {
    @Redirect(method="handleUseItemOn",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerLevel;mayInteract(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean slimefun$gpsInventoryAccess(ServerLevel level,Player player,BlockPos pos){
        if(level.mayInteract(player,pos))return true;
        if(player instanceof ServerPlayer server){
            if(GpsAccess.fixed(level,pos)&&GpsAccess.bypass(server))return true;
            // Vanilla rolls back the predicted block here, but skips the inventory correction.
            server.inventoryMenu.sendAllDataToRemote();
        }
        return false;
    }
}

package io.github.slimefun4.forge.mixin;

import io.github.slimefun4.forge.Soulbinding;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class SoulboundDeathMixin {
    @Inject(method="dropEquipment",at=@At("HEAD"))
    private void slimefun$keepBoundEquipment(CallbackInfo ci) {
        io.github.slimefun4.forge.GpsWaypoints.death((Player)(Object)this);
        Soulbinding.capture((Player)(Object)this);
    }
}

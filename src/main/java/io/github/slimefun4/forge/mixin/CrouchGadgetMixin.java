package io.github.slimefun4.forge.mixin;

import io.github.slimefun4.forge.FlightGadgets;
import io.github.slimefun4.forge.EquipmentArmorItem;
import io.github.slimefun4.forge.EquipmentElytraItem;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class CrouchGadgetMixin {
    @Inject(method="setSprinting",at=@At("HEAD"))
    private void slimefun$sprint(boolean sprinting,CallbackInfo ci){if(sprinting&&(Object)this instanceof ServerPlayer player&&!player.isSprinting())io.github.slimefun4.forge.TalismanItem.sprint(player);}
    @Inject(method="setSharedFlag",at=@At("HEAD"))
    private void slimefun$glide(int flag,boolean enabled,CallbackInfo ci){
        if(flag==7&&(Object)this instanceof ServerPlayer player&&player.isFallFlying()!=enabled){EquipmentArmorItem.glideChanged(player,enabled);EquipmentElytraItem.glideChanged(player,enabled);}
    }
    @Inject(method="setShiftKeyDown",at=@At("HEAD"))
    private void slimefun$crouch(boolean crouching,CallbackInfo ci){
        if((Object)this instanceof ServerPlayer player&&player.isShiftKeyDown()!=crouching){FlightGadgets.crouch(player,crouching);io.github.slimefun4.forge.InfusedMagnetItem.crouch(player,crouching);}
    }
}

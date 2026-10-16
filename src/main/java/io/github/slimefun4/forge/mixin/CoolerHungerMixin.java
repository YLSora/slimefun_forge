package io.github.slimefun4.forge.mixin;

import io.github.slimefun4.forge.CoolerItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FoodData.class)
public abstract class CoolerHungerMixin {
    @Shadow private int foodLevel;
    @Shadow private int lastFoodLevel;
    @Inject(method="tick",at=@At(value="FIELD",target="Lnet/minecraft/world/food/FoodData;foodLevel:I",opcode=Opcodes.PUTFIELD,shift=At.Shift.AFTER))
    private void slimefun$hungerLoss(Player player,CallbackInfo ci) {
        if(foodLevel<lastFoodLevel&&player instanceof ServerPlayer server)CoolerItem.request(server);
    }
}

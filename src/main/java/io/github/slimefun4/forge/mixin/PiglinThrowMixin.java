package io.github.slimefun4.forge.mixin;

import io.github.slimefun4.forge.StrangeNetherGoo;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** The original drop call site gives provenance once, without rerolling saved/teleported entities. */
@Mixin(BehaviorUtils.class)
public abstract class PiglinThrowMixin {
    @Redirect(method="throwItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;F)V",
        at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private static boolean slimefun$drop(Level level,Entity entity,LivingEntity source,ItemStack original,Vec3 target,Vec3 velocity,float height) {
        var item=(ItemEntity)entity;
        item.setItem(StrangeNetherGoo.replaceDrop(source,item.getItem()));
        return level.addFreshEntity(item);
    }
}

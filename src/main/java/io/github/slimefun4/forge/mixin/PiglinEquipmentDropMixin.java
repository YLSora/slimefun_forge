package io.github.slimefun4.forge.mixin;

import io.github.slimefun4.forge.StrangeNetherGoo;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** CraftBukkit forceDrops sites; death loot and unforced spawnAtLocation are deliberately excluded. */
@Mixin(Mob.class)
public abstract class PiglinEquipmentDropMixin {
    @Redirect(method="equipItemIfPossible",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Mob;spawnAtLocation(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/entity/item/ItemEntity;"))
    private ItemEntity slimefun$equipment(Mob source,ItemStack stack) {
        return source.spawnAtLocation(StrangeNetherGoo.replaceDrop(source,stack));
    }
    @Redirect(method={"dropLeash","restoreLeashFromSave"},at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Mob;spawnAtLocation(Lnet/minecraft/world/level/ItemLike;)Lnet/minecraft/world/entity/item/ItemEntity;"))
    private ItemEntity slimefun$leash(Mob source,ItemLike item) {
        return source.spawnAtLocation(StrangeNetherGoo.replaceDrop(source,new ItemStack(item)));
    }
}

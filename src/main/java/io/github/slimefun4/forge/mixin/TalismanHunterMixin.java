package io.github.slimefun4.forge.mixin;

import java.util.function.Consumer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(LivingEntity.class)
public abstract class TalismanHunterMixin {
    @Redirect(method="dropFromLootTable",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/storage/loot/LootTable;getRandomItems(Lnet/minecraft/world/level/storage/loot/LootParams;JLjava/util/function/Consumer;)V"))
    private void slimefun$naturalDrops(LootTable table,LootParams params,long seed,Consumer<ItemStack> consumer){io.github.slimefun4.forge.TalismanItem.mobDrops(table,params,seed,consumer);}
}

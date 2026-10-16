package io.github.slimefun4.forge.mixin;

import java.util.UUID;
import net.minecraft.world.entity.monster.ZombieVillager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ZombieVillager.class)
public interface ZombieCureAccess {
    @Invoker("startConverting") void slimefun$cure(UUID player,int ticks);
}

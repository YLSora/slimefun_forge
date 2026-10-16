package io.github.slimefun4.forge.mixin;

import io.github.slimefun4.forge.AncientRituals;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkMap.class)
public abstract class RitualChunkMixin {
    @Shadow @Final private ServerLevel level;
    @Inject(method="onFullChunkStatusChange",at=@At("TAIL"))
    private void slimefun$ready(ChunkPos pos,FullChunkStatus status,CallbackInfo callback) {
        // A retained FULL chunk can become accessible again without a ChunkEvent.Load.
        if(status.isOrAfter(FullChunkStatus.FULL))AncientRituals.chunkReady(level,pos.toLong());
    }
}

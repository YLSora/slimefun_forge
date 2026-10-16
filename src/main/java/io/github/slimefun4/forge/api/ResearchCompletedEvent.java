package io.github.slimefun4.forge.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;

/** Server-thread non-cancellable notification after a newly granted research is stored and marked dirty.
 * Used by learning completion and trusted ResearchApi grants; repeated grants do not notify again.
 * This is distinct from the cancellable, post-payment ResearchUnlockEvent.
 */
public final class ResearchCompletedEvent extends PlayerEvent {
    private final ResourceLocation research;
    public ResearchCompletedEvent(ServerPlayer player, ResourceLocation research) {
        super(player); this.research = java.util.Objects.requireNonNull(research);
    }
    public ResourceLocation getResearch() { return research; }
}

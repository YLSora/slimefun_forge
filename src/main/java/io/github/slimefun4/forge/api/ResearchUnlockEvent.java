package io.github.slimefun4.forge.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Cancelable;

/** Server-thread learning acceptance after XP payment (or a free knowledge-sharing request).
 * Cancellation prevents progress but does not refund the payment, matching RC-37.
 * Use PlayerPreResearchEvent for a zero-cost veto; ResearchCompletedEvent confirms the stored result.
 */
@Cancelable
public final class ResearchUnlockEvent extends PlayerEvent {
    private final ResourceLocation research;
    public ResearchUnlockEvent(ServerPlayer player,ResourceLocation research){super(player);this.research=java.util.Objects.requireNonNull(research);}
    public ResourceLocation getResearch(){return research;}
}

package io.github.slimefun4.forge.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Cancelable;

/** Server-thread guide/API learning request before checking XP or charging it. Cancellation costs nothing.
 * Does not fire for trusted grants, knowledge sharing, completed/disabled research, or the same research already being learned.
 * Source is the full ID of the clicked registered item; it is not a mutable inventory reference.
 */
@Cancelable
public final class PlayerPreResearchEvent extends PlayerEvent {
    private final ResourceLocation research, item;
    public PlayerPreResearchEvent(ServerPlayer player, ResourceLocation research, ResourceLocation item) {
        super(player);
        this.research = java.util.Objects.requireNonNull(research);
        this.item = java.util.Objects.requireNonNull(item);
    }
    public ResourceLocation getResearch() { return research; }
    public ResourceLocation getItem() { return item; }
}

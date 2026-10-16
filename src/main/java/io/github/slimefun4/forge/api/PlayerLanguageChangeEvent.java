package io.github.slimefun4.forge.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;

/** Effective guide language changed; synchronous server game bus, after preference/client/config change.
 * Not cancelable. Consumers should invalidate their per-player translated caches.
 */
public final class PlayerLanguageChangeEvent extends PlayerEvent {
    private final String previous,current;
    public PlayerLanguageChangeEvent(ServerPlayer player,String previous,String current){super(player);this.previous=previous;this.current=current;}
    public String getPreviousLanguage(){return previous;}
    public String getLanguage(){return current;}
}

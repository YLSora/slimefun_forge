package io.github.slimefun4.forge.api;

import net.minecraftforge.eventbus.api.Event;

/** Posted once to each mod's MOD event bus after common setup validates/freezes all addon definitions.
 * Both physical sides receive this non-cancellable loading-thread notification. It has no world/player;
 * use native recipe data/RecipeManager for recipes, not runtime registration from this callback.
 */
public final class AddonRegistryFinalizedEvent extends Event implements net.minecraftforge.fml.event.IModBusEvent {
    private final SlimefunAddonApi.Content content;
    public AddonRegistryFinalizedEvent(SlimefunAddonApi.Content content) { this.content = java.util.Objects.requireNonNull(content); }
    public SlimefunAddonApi.Content getContent() { return content; }
}

package io.github.slimefun4.forge.api;

import io.github.slimefun4.forge.AddonContent;
import io.github.slimefun4.forge.ResearchData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.Map;

/** Supported addon entry points. Register immutable IDs in the addon constructor, on both physical sides.
 * Definitions freeze during common setup, after Forge item registration. Registration never loads a world.
 * Invalid/duplicate definitions throw IllegalArgumentException; late registration throws IllegalStateException.
 */
public final class SlimefunAddonApi {
    /** Guide metadata; GuideApi optionally supplies hierarchy and behavior. Title is a language resource key. */
    public record Category(ResourceLocation id, String title, ResourceLocation icon) {}
    /** Research costs experience levels. Its ID also identifies progress in the world's existing SavedData. */
    public record Research(ResourceLocation id, String title, int cost) {}
    /** A guide item belongs to one category. A null research explicitly means no research gate. */
    public record Entry(ResourceLocation item, ResourceLocation category, ResourceLocation research) {
        public Entry(ResourceLocation item, ResourceLocation category) { this(item, category, null); }
    }
    /** Validated addon definitions, frozen for this mod-loading lifecycle. Values contain no live game state. */
    public record Content(Map<ResourceLocation, Category> categories, Map<ResourceLocation, Research> researches,
                          Map<ResourceLocation, Entry> entries) {
        public Content {
            categories = Map.copyOf(categories);
            researches = Map.copyOf(researches);
            entries = Map.copyOf(entries);
        }
    }

    /** Register a category before common setup; IDs must belong to an installed addon, not minecraft/slimefun. */
    public static void registerCategory(Category category) { AddonContent.register(category); }
    /** Register a research before common setup; cost must be non-negative and title non-blank. */
    public static void registerResearch(Research research) { AddonContent.register(research); }
    /** Register an item before common setup; duplicate item ownership is rejected. */
    public static void registerEntry(Entry entry) { AddonContent.register(entry); }
    /** Read the complete addon metadata after common setup. Early queries fail instead of exposing partial state.
     * Core items and other native types are queried through ForgeRegistries, not a second mutable registry.
     */
    public static Content content() { return AddonContent.content(); }
    /** Check use on the owning server thread before committing an addon action. Does not debit or unlock research.
     * Addon behavior must call this at commit. Supported native player workstations also enforce it;
     * autonomous machines check enable policy without inventing a player's research or permission.
     */
    public static boolean canUse(ServerPlayer player, ItemStack item) {
        if (!player.server.isSameThread()) throw new IllegalStateException("Addon use check requires the server thread");
        return ResearchData.canUse(player,item);
    }
    private SlimefunAddonApi() {}
}

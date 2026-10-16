package io.github.slimefun4.forge.api;

import io.github.slimefun4.forge.AddonResearch;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Research services use the existing overworld SavedData. All calls require the owning server thread.
 * Queries never charge experience. Explicit grants/revocations are trusted server administration operations;
 * addons must authorize their commands or packets before calling them. IDs always include a namespace.
 */
public final class ResearchApi {
    /** One research's effective policy and immutable associated item IDs. Completed means stored progress,
     * independently of creative mode, disabled research gates, or the global researching switch.
     */
    public record State(ResourceLocation id, String title, int cost, boolean enabled, boolean completed,
                        List<ResourceLocation> items) {
        public State { items = List.copyOf(items); }
    }
    /** Query a registered core/addon research. Unknown IDs are errors, including disabled gates' typos. */
    public static State get(MinecraftServer server, UUID player, ResourceLocation research) {
        return AddonResearch.get(server, player, research);
    }
    /** Immutable states for every core/addon research, sorted by full ID. Offline UUIDs may be queried. */
    public static List<State> all(MinecraftServer server, UUID player) { return AddonResearch.all(server, player); }
    /** Read actual completion; this deliberately differs from canUse's creative/config bypass rules. */
    public static boolean hasUnlocked(MinecraftServer server, UUID player, ResourceLocation research) {
        return get(server, player, research).completed();
    }
    /** Start learning from an actual registered guide item, using guide policy, XP, events and animation.
     * Returns true only if a new request was accepted; completion is observed via ResearchCompletedEvent.
     * ItemStack data is never retained. Repeated/busy/denied requests return false without another charge.
     */
    public static boolean learn(ServerPlayer player, ItemStack source) { return AddonResearch.learn(player, source); }
    /** Grant immediately without XP or cancellable learning events. Cancels pending learning of this research.
     * Returns true only for newly stored progress, and posts ResearchCompletedEvent after committing it.
     */
    public static boolean grant(ServerPlayer player, ResourceLocation research) { return AddonResearch.grant(player, research); }
    /** Revoke stored progress and pending learning of this research. No XP refund; returns whether either changed. */
    public static boolean revoke(ServerPlayer player, ResourceLocation research) { return AddonResearch.revoke(player, research); }
    /** Grant every registered research immediately; returns the count newly granted. */
    public static int grantAll(ServerPlayer player) { return AddonResearch.grantAll(player); }
    /** Clear all stored progress and pending learning for this player. No XP refund; returns removed progress count. */
    public static int reset(ServerPlayer player) { return AddonResearch.reset(player); }
    /** UUID administration uses the same store; offline changes emit no fabricated PlayerEvent. */
    public static boolean grant(MinecraftServer server,UUID owner,ResourceLocation research){return AddonResearch.grant(server,owner,research);}
    public static boolean revoke(MinecraftServer server,UUID owner,ResourceLocation research){return AddonResearch.revoke(server,owner,research);}
    public static int reset(MinecraftServer server,UUID owner){return AddonResearch.reset(server,owner);}
    private ResearchApi() {}
}

package io.github.slimefun4.forge.api;

import io.github.slimefun4.forge.NativeItemSpawns;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Business item generation, separate from native vanilla loot and EntityJoinLevelEvent. Server thread only. */
public final class ItemSpawnApi {
    public enum Reason { ANCIENT_PEDESTAL_PLACE_ITEM, BROKEN_SPAWNER_DROP, CARGO_OVERFLOW, MULTIBLOCK_MACHINE_OVERFLOW, CHRISTMAS_PRESENT_OPENED, EASTER_EGG_OPENED, GOLD_PAN_USE, MISC }
    /** Event + validated private entity, not yet added. A specialised producer owns its final commit. */
    public static ItemEntity prepare(ServerLevel level,ServerPlayer player,Reason reason,Vec3 at,ItemStack stack){return NativeItemSpawns.prepare(level,player,reason,at,stack);}
    /** Null on cancellation, invalidated source or rejected native entity join; input is never modified. */
    public static ItemEntity spawn(ServerLevel level,ServerPlayer player,Reason reason,Vec3 at,ItemStack stack,BooleanSupplier sourceValid){return NativeItemSpawns.spawn(level,player,reason,at,stack,sourceValid);}
    /** Takes ownership of an ALREADY withdrawn output. Cancelled/unloaded output remains in native SavedData.
     * Retries every 20 ticks without chunk loading; do not also keep or give this output in the caller.
     */
    public static void reserve(ServerLevel level,UUID owner,Reason reason,Vec3 at,ItemStack stack){NativeItemSpawns.reserve(level,owner,reason,at,stack);}
    private ItemSpawnApi(){}
}

package io.github.slimefun4.forge.api;

import io.github.slimefun4.forge.AddonItems;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;

/** Small native Item/Block traits. Implement on the registered class, not on a second item wrapper. */
public final class ItemApi {
    public enum Match { RECIPE, FILTER, STACK }
    /** RECIPE/FILTER may ignore irrelevant state. STACK can only narrow native ItemStack equality.
     * Consumers call both directions on copies: the relation must be symmetric and side-effect free.
     */
    public interface Distinctive { boolean matches(ItemStack item,ItemStack other,Match purpose); }
    public interface Soulbound { default boolean soulbound(ItemStack item){return true;} }
    public interface Radioactive { int radiation(ItemStack item); }
    public enum Protection { RADIATION, BEES, FLIGHT_IMPACT }
    public record ArmorProtection(Set<Protection> protections,ResourceLocation suit,boolean fullSuit) {
        public ArmorProtection { protections=Set.copyOf(protections);if(fullSuit&&suit==null)throw new IllegalArgumentException("Full protective suits need an ID"); }
    }
    public interface ProtectiveArmor { ArmorProtection protection(ItemStack stack); }
    /** Implement on a Block; stops native hopper push/pull, including hopper minecart pulls, not cargo. */
    public interface NotHopperable {}
    /** Implement on a BlockItem/Item; native placement and dispenser placement are rejected. */
    public interface NotPlaceable {}
    /** Implement on a native BE only when its placed identity needs instance NBT. Query must be pure. */
    public interface PlacedItem { ItemStack placedItem(); }
    /** Loaded native identity, including reinforced spawner type. Never loads a chunk or returns live NBT. */
    public static ItemStack placedItem(net.minecraft.world.level.Level level,BlockPos pos){return AddonItems.placedItem(level,pos);}
    /** Ordinary enchanting policy overrides are forbidden; safety enable/permission/world settings remain. */
    public interface FixedSettings {
        String fixedSettingsReason();
        default boolean allowEnchanting(ItemStack stack){return true;}
        default boolean allowDisenchanting(ItemStack stack){return true;}
    }
    public record Snapshot(ResourceLocation item,CompoundTag data) {
        public Snapshot { Objects.requireNonNull(item);data=Objects.requireNonNull(data).copy(); }
        @Override public CompoundTag data(){return data.copy();}
    }
    public static Snapshot snapshot(ItemStack item){var data=item.save(new CompoundTag());data.remove("id");data.remove("Count");return new Snapshot(BuiltInRegistries.ITEM.getKey(item.getItem()),data);}
    public static boolean matches(ItemStack first,ItemStack second,Match purpose){return AddonItems.matches(first,second,purpose);}
    public static boolean protectedFrom(ServerPlayer player,Protection protection){return AddonItems.protectedFrom(player,protection);}
    public static int exposure(ServerPlayer player){return AddonItems.exposure(player);}
    /** 0..100; replaces the existing persistent exposure, does not create a second status system. */
    public static void exposure(ServerPlayer player,int value){AddonItems.exposure(player,value);}
    public static boolean soulbound(ItemStack stack){return AddonItems.soulbound(stack);}
    /** World/item enable policy for automation without a player; does not grant player research or permission. */
    public static boolean enabled(ServerLevel level,ItemStack stack){return AddonItems.enabled(level,stack);}
    /** Pure preview/commit contract for external callers, not a second native right-click dispatcher. */
    public record ExternalContext(ServerPlayer actor,java.util.UUID owner,net.minecraft.world.level.block.entity.BlockEntity machine,BlockPos pos,boolean simulate) {
        public ExternalContext { Objects.requireNonNull(actor);Objects.requireNonNull(owner);pos=Objects.requireNonNull(pos).immutable(); }
        public ServerLevel level(){return actor.serverLevel();}
    }
    public record ExternalResult(boolean success,Component message,List<ItemStack> outputs) {
        public ExternalResult { message=Objects.requireNonNull(message).copy();outputs=outputs.stream().map(ItemStack::copy).toList();if(!success&&!outputs.isEmpty())throw new IllegalArgumentException("Failed external interactions cannot output items");for(var stack:outputs)if(stack.isEmpty()||stack.getCount()<1||stack.getCount()>stack.getMaxStackSize())throw new IllegalArgumentException("Invalid external interaction output"); }
        @Override public Component message(){return message.copy();}
        @Override public List<ItemStack> outputs(){return outputs.stream().map(ItemStack::copy).toList();}
    }
    /** Implement on a native Block. Simulation may preview outputs but must not mutate state or deliver items. */
    public interface ExternallyInteractable { ExternalResult interact(ExternalContext context); }
    /** Explicit native Block opt-in for robot harvesting; native loot and onRemove own each drop once. */
    public interface AutomationBreak { boolean mayBreak(ExternalContext context); }
    /** Checks loaded target, distance, world/item policy and native right-click protection first.
     * Returns outputs to the caller, which must deliver them exactly once and preserve overflow.
     */
    public static ExternalResult interact(ServerPlayer actor,BlockPos pos,boolean simulate){return AddonItems.interact(actor,pos,simulate);}
    public static ExternalResult interact(AndroidApi.Robot robot,BlockPos pos,boolean simulate){return AddonItems.interact(robot,pos,simulate);}
    private ItemApi(){}
}

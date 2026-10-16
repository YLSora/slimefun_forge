package io.github.slimefun4.forge.api;

import io.github.slimefun4.forge.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;

/** Native Item traits. All access requires the server thread; callers authorize offline administration. */
public final class BackpackApi {
    public interface Bag {
        int rows(ItemStack handle);
        default boolean accepts(ItemStack stack){return !(stack.getItem() instanceof Bag)&&stack.getItem().canFitInsideContainerItems();}
    }
    public interface Cooling extends Bag {
        boolean canFeed(ItemStack drink);
        List<MobEffectInstance> effects(ItemStack drink);
        default float saturation(ItemStack drink){return 6;}
        /** RC-37 juices leave no bottle. Native drinks may explicitly return one legal remainder. */
        default ItemStack remainder(ItemStack drink){return ItemStack.EMPTY;}
    }
    public record Snapshot(UUID world,UUID owner,int id,ResourceLocation type,int rows,List<ItemStack> contents){
        public Snapshot{PlayerRules.slots(rows);contents=contents.stream().map(ItemStack::copy).toList();if(contents.size()!=rows*9)throw new IllegalArgumentException("Backpack snapshot size");}
        @Override public List<ItemStack> contents(){return contents.stream().map(ItemStack::copy).toList();}
    }
    public static boolean open(ServerPlayer player,InteractionHand hand){return BackpackItem.open(player,hand);}
    /** Native EnderChestInventory remains the sole source. */
    public static boolean openEnder(ServerPlayer player,InteractionHand hand){return BackpackItem.openEnder(player,hand);}
    public static Optional<Snapshot> snapshot(MinecraftServer server,UUID owner,int id){return BackpackData.get(server).snapshot(owner,id);}
    /** Copy/edit/validate/commit. Closes viewers first and rejects reentrant edits or nested bags. */
    public static void edit(MinecraftServer server,UUID owner,int id,Consumer<List<ItemStack>> edit){BackpackData.get(server).edit(server,owner,id,edit);}
    public static void expand(MinecraftServer server,UUID owner,int id,int rows){BackpackData.get(server).expand(server,owner,id,rows);}
    public static void close(MinecraftServer server,UUID owner,int id){BackpackData.get(server).close(server,owner,id);}
    public static ItemStack restore(MinecraftServer server,UUID owner,int id){return BackpackData.get(server).restore(owner,id);}
    private BackpackApi(){}
}

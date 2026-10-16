package io.github.slimefun4.forge;

import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class Soulbinding {
    private static final String FLAG="slimefun_soulbound", SAVED="slimefun_soulbound_items";
    private static final Component LORE=Component.translatable("tooltip.slimefun.soulbound").withStyle(ChatFormatting.GRAY);
    private static final Set<String> INTRINSIC=Catalog.ITEMS.stream().filter(e->e.implementation().equals("SoulboundItem")||e.implementation().equals("SoulboundBackpack")).map(Catalog.Entry::id).collect(Collectors.toUnmodifiableSet());
    public static boolean bound(ItemStack item) {
        return bound(item,null);
    }
    public static boolean bound(ItemStack item,net.minecraft.world.level.Level level) {
        if(item.isEmpty())return false;
        var id=BuiltInRegistries.ITEM.getKey(item.getItem());
        if(item.getItem() instanceof io.github.slimefun4.forge.api.ItemApi.Soulbound addon&&addon.soulbound(item.copy())&&ItemAccess.enabled(level,item))return true;
        if(ItemAccess.enabled(level,"ancient_rune_soulbound")&&item.hasTag()&&item.getTag().contains(FLAG,Tag.TAG_BYTE))return true;
        if(id.getNamespace().equals("slimefun")&&INTRINSIC.contains(id.getPath()))return ItemAccess.enabled(level,id.getPath());
        return false;
    }
    private static boolean isBindingLore(Tag line) {
        try { return LORE.equals(Component.Serializer.fromJson(line.getAsString())); }catch(RuntimeException invalid){return false;}
    }
    public static ListTag comparableLore(ListTag lines) {
        var result=new ListTag();for(var line:lines)if(!isBindingLore(line))result.add(line.copy());return result;
    }
    public static CompoundTag comparableTag(ItemStack item) {
        if(!item.hasTag())return null;var tag=item.getTag().copy();tag.remove(FLAG);
        if(tag.contains("display",Tag.TAG_COMPOUND)) {
            var display=tag.getCompound("display");
            if(display.contains("Lore",Tag.TAG_LIST)){var lore=comparableLore(display.getList("Lore",Tag.TAG_STRING));if(lore.isEmpty())display.remove("Lore");else display.put("Lore",lore);}
            if(display.isEmpty())tag.remove("display");
        }
        return tag.isEmpty()?null:tag;
    }
    public static void bind(ItemStack item) {
        if(item.isEmpty())throw new IllegalArgumentException("Cannot bind empty stack");if(bound(item))return;
        item.getOrCreateTag().putByte(FLAG,(byte)1);var display=item.getOrCreateTagElement("display");var lore=display.getList("Lore",Tag.TAG_STRING);lore.add(StringTag.valueOf(Component.Serializer.toJson(LORE)));display.put("Lore",lore);
    }
    public static void capture(Player player) {
        if(!(player instanceof ServerPlayer)||player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY))return;
        var saved=player.getPersistentData().getList(SAVED,Tag.TAG_COMPOUND);var inv=player.getInventory();
        for(int slot=0;slot<inv.getContainerSize();slot++)if(bound(inv.getItem(slot),player.level())) {
            var row=inv.getItem(slot).save(new CompoundTag());row.putInt("Slot",slot);saved.add(row);inv.setItem(slot,ItemStack.EMPTY);
        }
        if(!saved.isEmpty())player.getPersistentData().put(SAVED,saved);
    }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        if(!event.isWasDeath()||!(event.getEntity() instanceof ServerPlayer player))return;
        var original=event.getOriginal().getPersistentData();var saved=original.getList(SAVED,Tag.TAG_COMPOUND).copy();original.remove(SAVED);player.getPersistentData().remove(SAVED);
        for(var value:saved) {
            var row=(CompoundTag)value;var item=ItemStack.of(row);int slot=row.getInt("Slot");
            if(slot>=0&&slot<player.getInventory().getContainerSize()&&player.getInventory().getItem(slot).isEmpty())player.getInventory().setItem(slot,item);
            else player.getInventory().placeItemBackInInventory(item);
        }
    }
    private Soulbinding() {}
}

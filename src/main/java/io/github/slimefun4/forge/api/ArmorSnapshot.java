package io.github.slimefun4.forge.api;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Full native stack NBT, including count/damage. Immutable snapshots never expose the live equipped stack. */
public final class ArmorSnapshot {
    private static final List<EquipmentSlot> SLOTS=List.of(EquipmentSlot.FEET,EquipmentSlot.LEGS,EquipmentSlot.CHEST,EquipmentSlot.HEAD);
    private final Map<EquipmentSlot,CompoundTag> stacks;
    public ArmorSnapshot(Map<EquipmentSlot,CompoundTag> stacks){var copy=new EnumMap<EquipmentSlot,CompoundTag>(EquipmentSlot.class);for(var slot:SLOTS)copy.put(slot,stacks.getOrDefault(slot,new CompoundTag()).copy());this.stacks=Map.copyOf(copy);}
    public static ArmorSnapshot capture(ServerPlayer player){NetworkApi.check(player.serverLevel());var values=new EnumMap<EquipmentSlot,CompoundTag>(EquipmentSlot.class);for(var slot:SLOTS)values.put(slot,player.getItemBySlot(slot).save(new CompoundTag()));return new ArmorSnapshot(values);}
    public static ArmorSnapshot fromPlayerData(CompoundTag player){var values=new EnumMap<EquipmentSlot,CompoundTag>(EquipmentSlot.class);for(var value:player.getList("Inventory",Tag.TAG_COMPOUND)){var stack=(CompoundTag)value;int index=stack.getByte("Slot")-100;if(index>=0&&index<4){var copy=stack.copy();copy.remove("Slot");values.put(SLOTS.get(index),copy);}}return new ArmorSnapshot(values);}
    public CompoundTag data(EquipmentSlot slot){if(!stacks.containsKey(slot))throw new IllegalArgumentException("Not an armor slot");return stacks.get(slot).copy();}
    public ItemStack stack(EquipmentSlot slot){return ItemStack.of(data(slot));}
    public boolean diverged(ServerPlayer player){return !equals(capture(player));}
    @Override public boolean equals(Object other){return other instanceof ArmorSnapshot value&&stacks.equals(value.stacks);}
    @Override public int hashCode(){return stacks.hashCode();}
}

package io.github.slimefun4.forge;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class SpawnerItems {
    public static final ForgeConfigSpec CONFIG;
    public static final ForgeConfigSpec.BooleanValue ALLOW_SPAWN_EGGS;
    static { var builder=new ForgeConfigSpec.Builder();ALLOW_SPAWN_EGGS=builder.define("allow-spawn-eggs",true);CONFIG=builder.build(); }
    public static Optional<EntityType<?>> type(ItemStack stack) {
        return stack.hasTag()?EntityType.byString(stack.getTag().getString("slimefun_spawner_type")):Optional.empty();
    }
    public static ItemStack stack(boolean repaired,EntityType<?> type) {
        var result=new ItemStack(SlimefunForge.item(repaired?"reinforced_spawner":"broken_spawner"));
        result.getOrCreateTag().putString("slimefun_spawner_type",EntityType.getKey(type).toString());return result;
    }
    public static ItemStack mined(BlockEntity entity) {
        if(!(entity instanceof SpawnerBlockEntity spawner))return ItemStack.EMPTY;
        var data=spawner.getSpawner().save(new CompoundTag());
        return EntityType.byString(data.getCompound("SpawnData").getCompound("entity").getString("id"))
            .map(type->stack(spawner.getPersistentData().getBoolean("slimefun_reinforced_spawner"),type)).orElse(ItemStack.EMPTY);
    }
    private static void tooltip(ItemStack stack,List<Component> lines) {
        lines.add(type(stack).map(type->Component.translatable("tooltip.slimefun.spawner_type",type.getDescription()))
            .orElse(Component.translatable("tooltip.slimefun.spawner_unset")));
    }
    public static final class Broken extends Item {
        public Broken() { super(new Properties()); }
        @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag) { tooltip(stack,lines); }
        @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) { return InteractionResultHolder.fail(player.getItemInHand(hand)); }
    }
    public static final class Repaired extends ResearchBlockItem {
        public Repaired() { super(Blocks.SPAWNER); }
        @Override public void registerBlocks(Map<Block,Item> map,Item item) {}
        @Override public String getDescriptionId() { return "item.slimefun.reinforced_spawner"; }
        @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag) { tooltip(stack,lines); }
        @Override public InteractionResult place(BlockPlaceContext context) {
            var stack=context.getItemInHand();
            if(type(stack).isEmpty())return InteractionResult.FAIL;
            return super.place(context);
        }
        @Override protected boolean updateCustomBlockEntityTag(BlockPos pos,Level level,Player player,ItemStack stack,BlockState state) {
            if(!level.isClientSide&&level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner) {
                var type=type(stack);if(type.isEmpty())return false;
                // Only the captured entity type is applied; command-only spawner NBT is never imported.
                spawner.setEntityId(type.get(),level.random);spawner.getPersistentData().putBoolean("slimefun_reinforced_spawner",true);spawner.setChanged();
                level.sendBlockUpdated(pos,state,state,3);return true;
            }
            return false;
        }
    }
    @SubscribeEvent public static void interact(PlayerInteractEvent.RightClickBlock event) {
        if(!(event.getLevel().getBlockEntity(event.getPos()) instanceof SpawnerBlockEntity spawner)||!spawner.getPersistentData().getBoolean("slimefun_reinforced_spawner"))return;
        if(event.getEntity() instanceof ServerPlayer player&&!ResearchData.canUse(player,new ItemStack(SlimefunForge.item("reinforced_spawner")))) {
            ItemAccess.denied(player,"reinforced_spawner");event.setCanceled(true);event.setCancellationResult(InteractionResult.FAIL);
        } else if(!ALLOW_SPAWN_EGGS.get()&&event.getItemStack().getItem() instanceof SpawnEggItem) { event.setCanceled(true);event.setCancellationResult(InteractionResult.FAIL); }
    }
    private SpawnerItems() {}
}

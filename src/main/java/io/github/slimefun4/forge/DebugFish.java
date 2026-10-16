package io.github.slimefun4.forge;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;

public final class DebugFish extends Item {
    private record Placement(ServerPlayer player,ServerLevel level,BlockPos pos,long due){}
    private static final List<Placement> PLACEMENTS=new ArrayList<>();
    public DebugFish(){super(new Item.Properties().stacksTo(1));}
    @Override public void appendHoverText(ItemStack stack,net.minecraft.world.level.Level level,List<Component> lines,TooltipFlag flag){for(String action:List.of("inspect","break","clear","place"))lines.add(Component.translatable("item.slimefun.debug_fish."+action));}
    private static boolean held(PlayerInteractEvent event){return event.getHand()==InteractionHand.MAIN_HAND&&event.getItemStack().is(SlimefunForge.DEBUG_FISH.get());}
    private static boolean allowed(PlayerInteractEvent event){
        if(!(event.getEntity() instanceof ServerPlayer player))return false;
        if(!AdminCommands.canDebugFish(player)){Messages.chat(player,Component.translatable("command.slimefun.debug_fish_denied"));return false;}
        return !player.isSpectator()&&event.getLevel().mayInteract(player,event.getPos())&&player.mayUseItemAt(event.getPos(),event.getFace(),event.getItemStack());
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void left(PlayerInteractEvent.LeftClickBlock event){
        if(!held(event))return;
        if(event.getLevel().isClientSide){if(event.getEntity().isShiftKeyDown())event.setCanceled(true);return;}
        if(!allowed(event)){event.setCanceled(true);return;}
        if(event.getEntity().isShiftKeyDown()){event.setCanceled(true);if(event.getAction()==PlayerInteractEvent.LeftClickBlock.Action.START)clear((ServerLevel)event.getLevel(),event.getPos());}
    }
    public static void right(PlayerInteractEvent.RightClickBlock event){
        if(!held(event))return;event.setCanceled(true);event.setCancellationResult(InteractionResult.SUCCESS);if(!allowed(event))return;
        var player=(ServerPlayer)event.getEntity();var level=player.serverLevel();
        if(player.isShiftKeyDown()){var pos=event.getPos().relative(event.getFace());if(!level.isOutsideBuildHeight(pos)&&level.getWorldBorder().isWithinBounds(pos))PLACEMENTS.add(new Placement(player,level,pos,level.getGameTime()+2));}
        else inspect(player,event.getPos());
    }
    private static Catalog.Entry entry(ServerLevel level,BlockPos pos){var state=level.getBlockState(pos);var id=BuiltInRegistries.BLOCK.getKey(state.getBlock());if(id.getNamespace().equals(SlimefunForge.ID))return Catalog.entry(id.getPath());var entity=level.getBlockEntity(pos);return state.is(Blocks.SPAWNER)&&entity!=null&&entity.getPersistentData().getBoolean("slimefun_reinforced_spawner")?Catalog.entry("reinforced_spawner"):null;}
    private static void inspect(ServerPlayer player,BlockPos pos){
        var level=player.serverLevel();var state=level.getBlockState(pos);var entry=entry(level,pos);var entity=level.getBlockEntity(pos);
        if(entry==null){var tags=new TreeSet<String>();state.getTags().filter(t->t.location().getNamespace().equals(SlimefunForge.ID)).forEach(t->tags.add(t.location().toString()));new ItemStack(state.getBlock()).getTags().filter(t->t.location().getNamespace().equals(SlimefunForge.ID)).forEach(t->tags.add(t.location().toString()));if(!tags.isEmpty())player.sendSystemMessage(GuideContent.localize(player,Component.translatable("command.slimefun.debug_tags",BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),String.join("\n",tags))));return;}
        var data=entity==null?new CompoundTag():entity.saveWithFullMetadata();var lines=new ArrayList<Component>();lines.add(Component.literal(entry.material()+" @ "+pos.toShortString()));lines.add(Component.translatable("command.slimefun.debug_identity",entry.id()));
        if(state.hasProperty(HeadBlock.POSE)){int pose=state.getValue(HeadBlock.POSE);lines.add(Component.translatable("command.slimefun.debug_"+(pose<16?"rotation":"facing"),pose<16?pose:HeadBlock.wallFacing(state)));}else if(state.getBlock() instanceof AndroidBlock)lines.add(Component.translatable("command.slimefun.debug_facing",state.getValue(AndroidBlock.FACING)));
        lines.add(Component.translatable("command.slimefun.debug_inventory",entity instanceof MenuProvider?"✔":"✘"));lines.add(Component.translatable("command.slimefun.debug_tick",level.getBlockTicks().hasScheduledTick(pos,state.getBlock())?"✔":"✘",entity!=null&&state.getBlock() instanceof EntityBlock provider&&provider.getTicker(level,state,entity.getType())!=null?"✔":"✘",entity instanceof ReactorEntity||entity instanceof CargoEntity cargo&&!cargo.manager()?"✔":"✘"));
        if(entity!=null)entity.getCapability(ForgeCapabilities.ENERGY).ifPresent(power->lines.add(Component.translatable("command.slimefun.debug_energy",power.getEnergyStored(),power.getMaxEnergyStored(),power.canReceive()?"✔":"✘",power.canExtract()?"✔":"✘")));
        var snapshot=Timings.latest(player.server);if(snapshot!=null){var key=new Timings.Key(level.dimension().location(),pos,SlimefunForge.ID+":"+entry.id());var sample=snapshot.blocks().get(key);if(sample!=null){long item=0,chunk=0;for(var row:snapshot.blocks().entrySet()){if(row.getKey().item().equals(key.item()))item+=row.getValue().nanoseconds();if(row.getKey().dimension().equals(key.dimension())&&row.getKey().pos().getX()>>4==pos.getX()>>4&&row.getKey().pos().getZ()>>4==pos.getZ()>>4)chunk+=row.getValue().nanoseconds();}lines.add(Component.translatable("command.slimefun.debug_timings",sample.nanoseconds(),item,chunk,snapshot.ticks()));}}
        lines.add(Component.translatable("command.slimefun.debug_state",NbtUtils.writeBlockState(state).toString()));lines.add(Component.translatable("command.slimefun.debug_entity",data.toString()));for(var line:lines)player.sendSystemMessage(GuideContent.localize(player,line));
    }
    private static <T extends Comparable<T>> BlockState property(BlockState target,BlockState source,Property<T> property){return target.setValue(property,source.getValue(property));}
    private static void clear(ServerLevel level,BlockPos pos){
        var state=level.getBlockState(pos);var entry=entry(level,pos);if(entry==null)return;
        if(state.is(Blocks.SPAWNER)){var entity=level.getBlockEntity(pos);entity.getPersistentData().remove("slimefun_reinforced_spawner");entity.setChanged();level.sendBlockUpdated(pos,state,state,3);return;}
        BlockState replacement;
        if(state.hasProperty(HeadBlock.POSE)||state.getBlock() instanceof AndroidBlock)replacement=ClearedHeadBlock.copy(state);
        else {var vanilla=BuiltInRegistries.BLOCK.get(new ResourceLocation("minecraft",entry.material()));if(vanilla==Blocks.AIR)throw new IllegalStateException("Missing native debug material: "+entry.material());replacement=vanilla.defaultBlockState();for(var key:state.getProperties())if(replacement.hasProperty(key))replacement=property(replacement,state,key);}
        var viewers=level.players().stream().filter(p->p.containerMenu!=p.inventoryMenu&&p.containerMenu.stillValid(p)).toList();var entity=level.getBlockEntity(pos);var nativeData=entity instanceof Container?entity.saveWithoutMetadata():null;
        if(entity instanceof GeoMinerEntity miner)miner.clear();
        if(entity instanceof ProcessingEntity machine)machine.clear();
        // Removing the entity first invalidates capabilities/indexes without dropping the deleted virtual inventory.
        level.removeBlockEntity(pos);level.setBlockAndUpdate(pos,replacement);
        if(nativeData!=null&&level.getBlockEntity(pos)!=null){var caps=nativeData.getCompound("ForgeCaps");for(String key:List.copyOf(caps.getAllKeys()))if(key.startsWith("slimefun:"))caps.remove(key);level.getBlockEntity(pos).load(nativeData);level.getBlockEntity(pos).setChanged();}
        for(var viewer:viewers)if(!viewer.containerMenu.stillValid(viewer))viewer.closeContainer();
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        for(var iterator=PLACEMENTS.iterator();iterator.hasNext();){var task=iterator.next();if(task.level.getServer()!=event.getServer()||task.level.getGameTime()<task.due)continue;iterator.remove();var player=task.player;
            if(event.getServer().getPlayerList().getPlayer(player.getUUID())!=player||!AdminCommands.canDebugFish(player)||!task.level.hasChunkAt(task.pos)||!task.level.mayInteract(player,task.pos))continue;
            task.level.setBlockAndUpdate(task.pos,SlimefunForge.CLEARED_HEAD.get().defaultBlockState());task.level.playSound(null,task.pos,SoundEvents.BAMBOO_PLACE,SoundSource.PLAYERS,1,1);
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){PLACEMENTS.removeIf(task->task.level.getServer()==event.getServer());}
}

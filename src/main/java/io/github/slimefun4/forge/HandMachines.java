package io.github.slimefun4.forge;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Containers;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.MinecraftForge;

/** The two manual stations whose ingredients come from the player's hand. */
public final class HandMachines {
    private static final Set<ServerPlayer> CRAFTING=Collections.newSetFromMap(new IdentityHashMap<>());
    static void craft(ServerPlayer player,ManualMachines.Station station,BlockPos clicked) {
        if(!CRAFTING.add(player))return;
        try {
            var level=player.serverLevel();var held=player.getMainHandItem();var before=held.copy();int selected=player.getInventory().selected;
            boolean pan=station.type().equals("automated_panning_machine");
            ItemStack output;
            CompostingQueue queue=null;
            if(pan) {
                int group=GoldPanDrops.inputGroup(held);if(group<0)return;
                queue=level.getChunkAt(clicked).getCapability(CompostingQueue.CAPABILITY).resolve().orElse(null);if(queue==null)return;
                output=GoldPanDrops.pick(group);
            } else {
                output=saw(held);if(output.isEmpty())return;
            }
            var event=new MultiBlockCraftEvent(player,station.type(),clicked,List.of(before),output);
            if(MinecraftForge.EVENT_BUS.post(event))return;
            if(!player.isAlive()||player.serverLevel()!=level||player.getInventory().selected!=selected||player.getMainHandItem()!=held
                ||!ItemStack.matches(held,before)||!station.equals(ManualMachines.detect(level,clicked))||!player.mayBuild()||!level.mayInteract(player,clicked)
                ||!ItemAccess.canUse(player,station.type()))return;
            if(!player.isCreative())held.shrink(1);
            if(pan)queue.startPanning(clicked,before,event.getOutput(),player.getUUID());
            else {
                deliver(level,clicked,clicked,event.getOutput(),player.getUUID());
                level.levelEvent(2001,clicked,Block.getId(Block.byItem(before.getItem()).defaultBlockState()));
            }
        } finally { CRAFTING.remove(player); }
    }
    static ItemStack saw(ItemStack input) {
        if(input.is(ItemTags.LOGS)) {
            var id=BuiltInRegistries.ITEM.getKey(input.getItem());String name=id.getPath().replace("stripped_","");int end=name.lastIndexOf('_');
            if(end>0)return new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(id.getNamespace(),name.substring(0,end)+"_planks")),8);
        } else if(input.is(ItemTags.PLANKS))return new ItemStack(Items.STICK,4);
        return ItemStack.EMPTY;
    }
    static void deliver(ServerLevel level,BlockPos center,BlockPos drop,ItemStack output,UUID owner) {
        if(output.isEmpty())return;
        for(var direction:List.of(Direction.UP,Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST)) {
            var target=OutputChestBlock.outputAt(level,center.relative(direction));if(target==null)continue;
            var planned=MachineRecipe.copy(target);
            if(planned.addItem(output.copy()).isEmpty()){MachineRecipe.commit(target,planned);return;}
        }
        io.github.slimefun4.forge.api.ItemSpawnApi.reserve(level,owner,io.github.slimefun4.forge.api.ItemSpawnApi.Reason.MULTIBLOCK_MACHINE_OVERFLOW,net.minecraft.world.phys.Vec3.atCenterOf(drop),output);
    }
    private HandMachines() {}
}

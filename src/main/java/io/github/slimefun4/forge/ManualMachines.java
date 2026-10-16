package io.github.slimefun4.forge;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Recognizes only local loaded blocks on interaction. Delayed paid outputs belong to the dispenser. */
public final class ManualMachines {
    private static final net.minecraft.tags.TagKey<Block> IGNITION=BlockTags.create(SlimefunForge.key("ignition_chambers"));
    public record Station(String type,BlockPos inventory) {}
    private static final Set<Container> CRAFT_EVENTS=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<UUID> INTERACTING=new HashSet<>();
    private static boolean is(Level level,BlockPos pos,Block block) {
        if(!level.hasChunkAt(pos)) return false;
        var state=level.getBlockState(pos);
        if(block==Blocks.OAK_FENCE) return state.is(BlockTags.WOODEN_FENCES);
        if(block==Blocks.FIRE) return state.is(BlockTags.FIRE);
        return state.is(block);
    }
    private static boolean pair(Level level,BlockPos center,Block block) {
        return (is(level,center.east(),block) && is(level,center.west(),block)) ||
               (is(level,center.north(),block) && is(level,center.south(),block));
    }
    public static Station detect(Level level,BlockPos clicked) {
        BlockPos below=clicked.below();
        if(is(level,clicked,Blocks.STONECUTTER)&&is(level,below,Blocks.IRON_BLOCK)&&pair(level,clicked,Blocks.SMOOTH_STONE_SLAB))return new Station("table_saw",clicked);
        if(is(level,clicked,Blocks.OAK_TRAPDOOR)&&is(level,below,Blocks.CAULDRON))return new Station("automated_panning_machine",below);
        if(is(level,clicked,Blocks.CRAFTING_TABLE)) {
            for(Direction dir : Direction.Plane.HORIZONTAL) {
                if(is(level,clicked.relative(dir),Blocks.DISPENSER) && is(level,clicked.relative(dir.getOpposite()),Blocks.BOOKSHELF))
                    return new Station("magic_workbench",clicked.relative(dir));
            }
            if(is(level,below,Blocks.DISPENSER)) return new Station("enhanced_crafting_table",below);
        }
        if(is(level,clicked,Blocks.OAK_FENCE)) {
            if(is(level,below,Blocks.DISPENSER)&&pair(level,below,Blocks.BRICKS))
                return is(level,below.below(),Blocks.FIRE)&&level.getBlockState(below).getValue(DispenserBlock.FACING)==Direction.UP?new Station("makeshift_smeltery",below):null;
            if(is(level,clicked.above(),Blocks.DISPENSER) && is(level,below,Blocks.CAULDRON))
                return new Station("ore_washer",clicked.above());
            if(is(level,below,Blocks.DISPENSER)) return new Station("grind_stone",below);
        }
        if(is(level,clicked,Blocks.ANVIL) && is(level,below,Blocks.DISPENSER)) return new Station("armor_forge",below);
        if(is(level,clicked,Blocks.NETHER_BRICK_FENCE) && is(level,below,Blocks.DISPENSER)) {
            if(is(level,clicked.above(),Blocks.GLASS)) return new Station("juicer",below);
            if(pair(level,below,Blocks.IRON_BARS)) return new Station("ore_crusher",below);
            if(pair(level,below,Blocks.PISTON)) return new Station("compressor",below);
            if(pair(level,below,Blocks.NETHER_BRICKS) && is(level,below.below(),Blocks.FIRE)) return new Station("smeltery",below);
        }
        if(is(level,clicked,Blocks.CAULDRON) && is(level,clicked.above(),Blocks.GLASS)
            && is(level,clicked.above(2),Blocks.DISPENSER)) {
            for(Direction dir : List.of(Direction.EAST,Direction.NORTH)) {
                Direction other=dir.getOpposite();
                if(is(level,clicked.relative(dir),Blocks.PISTON) && is(level,clicked.relative(other),Blocks.PISTON)
                    && is(level,clicked.above().relative(dir),Blocks.PISTON) && is(level,clicked.above().relative(other),Blocks.PISTON)
                    && is(level,clicked.above(2).relative(dir),Blocks.SMOOTH_STONE_SLAB)
                    && is(level,clicked.above(2).relative(other),Blocks.SMOOTH_STONE_SLAB)) return new Station("pressure_chamber",clicked.above(2));
            }
        }
        return null;
    }
    public static void interact(PlayerInteractEvent.RightClickBlock event) {
        if(event.getHand()!=InteractionHand.MAIN_HAND) return;
        var level=event.getLevel(); var station=detect(level,event.getPos());
        if(station==null) return;
        event.setCanceled(true); event.setCancellationResult(InteractionResult.SUCCESS);
        if(!(event.getEntity() instanceof ServerPlayer player)) return;
        if(!level.mayInteract(player,event.getPos()) || !player.mayBuild()) return;
        if(!ItemAccess.canUse(player,station.type)) { ItemAccess.denied(player,station.type); return; }
        if(!INTERACTING.add(player.getUUID()))return;
        try {if(net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new MultiBlockInteractEvent(player,AddonMachines.key(station.type),station.inventory,event.getPos(),event.getFace())))return;}finally{INTERACTING.remove(player.getUUID());}
        if(!player.isAlive()||player.isSpectator()||player.level()!=level||!player.mayBuild()||player.distanceToSqr(event.getPos().getCenter())>64||!level.mayInteract(player,event.getPos())||!station.equals(detect(level,event.getPos()))||!ItemAccess.canUse(player,station.type))return;
        if(station.type.equals("table_saw")||station.type.equals("automated_panning_machine")) { HandMachines.craft(player,station,event.getPos());return; }
        if(!(level.getBlockEntity(station.inventory) instanceof DispenserBlockEntity inv)) return;
        if(CRAFT_EVENTS.contains(inv))return;
        if(station.type.equals("ore_washer")) {
            if(wash(level,station,inv,player)) ManualCraftEffects.immediate((ServerLevel)level,station.type,event.getPos());
            return;
        }
        var recipe=findRecipe(level,station.type,inv);
        if(recipe!=null) {
            boolean checkOutput=!Set.of("grind_stone","compressor","pressure_chamber","juicer").contains(station.type);
            if(checkOutput&&!ResearchData.canUse(player,recipe.output)) { ItemAccess.denied(player,recipe.output); return; }
            if(station.type.equals("juicer")||station.type.equals("makeshift_smeltery")) {
                var before=MachineRecipe.copy(inv);
                var craftEvent=new MultiBlockCraftEvent(player,station.type,station.inventory,recipe.inputs,recipe.output);
                CRAFT_EVENTS.add(inv);
                try {if(net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(craftEvent))return;}finally{CRAFT_EVENTS.remove(inv);}
                if(!station.equals(detect(level,event.getPos()))||level.getBlockEntity(station.inventory)!=inv)return;
                for(int slot=0;slot<inv.getContainerSize();slot++)if(!ItemStack.matches(inv.getItem(slot),before.getItem(slot)))return;
                if(!player.isAlive()||player.isSpectator()||player.level()!=level||!player.mayBuild()||player.distanceToSqr(event.getPos().getCenter())>64||!level.mayInteract(player,event.getPos())||!ItemAccess.canUse(player,station.type)||!ResearchData.canUse(player,craftEvent.getOutput()))return;
                recipe=new MachineRecipe(recipe.getId(),recipe.station,recipe.inputs,craftEvent.getOutput(),recipe.shaped,recipe.ticks,recipe.oreDoubling);
            }
            boolean delayed=Set.of("compressor","pressure_chamber","armor_forge","magic_workbench").contains(station.type);
            boolean crafted=delayed?ManualCraftingQueue.start(inv,recipe,player,event.getPos()):craft(level,station,inv,recipe);
            if(crafted) {
                if(!delayed) ManualCraftEffects.immediate((ServerLevel)level,station.type,event.getPos());
                if(station.type.equals("smeltery") && level.random.nextInt(100)<34 && !renewFire(level,station.inventory,player)) {
                    var fire=station.inventory.below();
                    level.levelEvent(2001,fire,Block.getId(level.getBlockState(fire)));
                    level.removeBlock(fire,false);
                }
                if(station.type.equals("makeshift_smeltery")) {
                    var fire=station.inventory.below();level.levelEvent(2001,fire,Block.getId(level.getBlockState(fire)));level.removeBlock(fire,false);
                }
            }
            else Messages.chat(player,Component.translatable("message.slimefun.full_inventory"));
            return;
        }
        boolean grid=Set.of("enhanced_crafting_table","armor_forge","magic_workbench").contains(station.type);
        Messages.chat(player,Component.translatable("message.slimefun."+(grid?(inv.isEmpty()?"inventory_empty":"no_recipe"):"unknown_material")));
    }
    private static MachineRecipe findRecipe(Level level,String type,Container inv) {
        var recipes=MachineRecipe.forStation(level,type.equals("makeshift_smeltery")?"smeltery":type);
        if(type.equals("makeshift_smeltery"))recipes=recipes.stream().filter(recipe->recipe.inputs.stream().filter(input->!input.isEmpty()).count()==1).toList();
        if(Set.of("grind_stone","compressor","ore_crusher","pressure_chamber","juicer").contains(type)) {
            // RC-37 visits input slots first and requires a complete ingredient in one stack.
            for(int slot=0;slot<inv.getContainerSize();slot++) for(var recipe:recipes) {
                ItemStack required=null;
                for(var ingredient:recipe.inputs) if(!ingredient.isEmpty()) {
                    if(required!=null) { required=null; break; }
                    required=ingredient;
                }
                if(required!=null && MachineRecipe.same(inv.getItem(slot),required)
                    && inv.getItem(slot).getCount()>=required.getCount()) return recipe;
            }
            return null;
        }
        for(var recipe:recipes) if(recipe.debits(inv)!=null) return recipe;
        return null;
    }
    private static boolean hasSpace(Container target,MachineRecipe recipe,ItemStack... extras) {
        if(recipe.station.equals("ore_washer") && extras.length>0 && !OreWashing.legacyOre()) {
            // Default RC-37 sieve output needs an empty slot before the input is consumed.
            boolean empty=false;
            for(int slot=0;slot<target.getContainerSize();slot++) if(target.getItem(slot).isEmpty()) { empty=true; break; }
            if(!empty) return false;
        }
        // Grid tables use the transaction's post-consumption check. Other machines check first.
        if(recipe.shaped) return true;
        var planned=MachineRecipe.copy(target);
        if(!planned.addItem(recipe.output.copy()).isEmpty()) return false;
        for(var extra:extras) if(!planned.addItem(extra.copy()).isEmpty()) return false;
        return true;
    }
    private static boolean craft(Level level,Station station,Container input,MachineRecipe recipe,ItemStack... extras) {
        // RC-37 checks UP, NORTH, EAST, SOUTH, WEST; ordinary containers are not output chests.
        for(Direction direction : List.of(Direction.UP,Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST)) {
            BlockPos target=station.inventory.relative(direction);
            var output=OutputChestBlock.outputAt(level,target);
            if(output!=null && hasSpace(output,recipe,extras) && recipe.craft(input,output,extras)) return true;
        }
        return hasSpace(input,recipe,extras) && recipe.craft(input,input,extras);
    }
    public static boolean renewFire(Level level,BlockPos dispenser,ServerPlayer player) {
        for(Direction direction : List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST)) {
            BlockPos target=dispenser.relative(direction);
            if(!level.hasChunkAt(target)||!(level.getBlockEntity(target) instanceof Container chamber)||!(chamber instanceof IgnitionChamberEntity||level.getBlockState(target).is(IGNITION))||!ItemAccess.enabled(level,new ItemStack(level.getBlockState(target).getBlock())))continue;
            for(int i=0;i<chamber.getContainerSize();i++) {
                var tool=chamber.getItem(i);
                if(!tool.is(Items.FLINT_AND_STEEL)||!ItemAccess.enabled(level,tool)) continue;
                if(tool.isDamageableItem()) {
                    tool.setDamageValue(tool.getDamageValue()+1);
                    if(tool.getDamageValue()>=tool.getMaxDamage()) {
                        tool.shrink(1);
                        level.playSound(null,dispenser,SoundEvents.ITEM_BREAK,SoundSource.BLOCKS,1,1);
                    }
                    chamber.setChanged();
                }
                level.playSound(null,dispenser,SoundEvents.ITEM_BREAK,SoundSource.BLOCKS,1,1);
                return true;
            }
            Messages.chat(player,Component.translatable("message.slimefun.ignition_no_flint"));
            return false;
        }
        return false;
    }
    private static boolean wash(Level level,Station station,Container inv,ServerPlayer player) {
        MachineRecipe recipe=null;ItemStack[] extras={};
        for(int i=0;i<inv.getContainerSize();i++) {
            var input=inv.getItem(i);
            if(MachineRecipe.same(input,new ItemStack(SlimefunForge.item("sifted_ore")))) {
                recipe=new MachineRecipe(SlimefunForge.key("washing_ore"),"ore_washer",List.of(new ItemStack(SlimefunForge.item("sifted_ore"))),OreWashing.randomDust(),false,1,false);
                extras=new ItemStack[]{new ItemStack(SlimefunForge.item("stone_chunk"))};break;
            }
            if(MachineRecipe.same(input,new ItemStack(Items.SAND)) && input.getCount()>=2) {
                recipe=new MachineRecipe(SlimefunForge.key("washing_salt"),"ore_washer",List.of(new ItemStack(Items.SAND,2)),new ItemStack(SlimefunForge.item("salt")),false,1,false);break;
            }
            if(MachineRecipe.same(input,new ItemStack(SlimefunForge.item("pulverized_ore")))) {
                recipe=new MachineRecipe(SlimefunForge.key("washing_cluster"),"ore_washer",List.of(new ItemStack(SlimefunForge.item("pulverized_ore"))),new ItemStack(SlimefunForge.item("pure_ore_cluster")),false,1,false);break;
            }
        }
        boolean crafted=recipe!=null&&craft(level,station,inv,recipe,extras);
        if(!crafted)Messages.chat(player,Component.translatable("message.slimefun."+(recipe==null?"unknown_material":"full_inventory")));
        return crafted;
    }
    private ManualMachines() {}
}

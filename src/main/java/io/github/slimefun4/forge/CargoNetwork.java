package io.github.slimefun4.forge;

import io.github.slimefun4.forge.api.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.*;

/** Loaded positions only. No forced chunk loads, async world access, or recurring discovery scans. */
public final class CargoNetwork {
    public static boolean matches(ItemStack item,ItemStack filter,boolean checkLore){return CargoFilter.matches(item,filter,checkLore);}
    private static final Map<Level,State> WORLDS=new WeakHashMap<>();
    private static final DustParticleOptions VISUALIZER_DUST=new DustParticleOptions(new org.joml.Vector3f(0,0,1),3);
    private static final class State {
        final NetworkApi.Network topology;
        final Map<BlockPos,Grid> grids=new HashMap<>();
        boolean working;
        State(ServerLevel level){topology=new NetworkApi.Network(level,NetworkApi.Roots.LOWEST,4096);}
    }
    private static final class Grid {
        int delay;
        final Map<BlockPos,Integer> roundRobin=new HashMap<>();
    }
    private record Endpoint(BlockPos pos,BlockEntity entity,LazyOptional<IItemHandler> capability,IItemHandler items,int[] slots,Container vanilla,boolean originalStacking) {
        boolean valid(){var level=entity.getLevel();return level!=null&&!entity.isRemoved()&&level.hasChunkAt(pos)&&level.getBlockEntity(pos)==entity&&capability.isPresent()&&loadedInventory(entity);}
        boolean matches(ItemStack current,ItemStack offered){if(AddonContent.ENTRIES.containsKey(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(offered.getItem())))return AddonItems.matches(current,offered,io.github.slimefun4.forge.api.ItemApi.Match.STACK);return originalStacking?(!(current.getItem() instanceof BackpackItem)||CargoFilter.matches(current,offered,true))&&CargoFilter.inventoryMatches(current,offered,true):ItemStack.isSameItemSameTags(current,offered);}
        ItemStack insert(int slot,ItemStack offered,boolean simulate){
            var current=items.getStackInSlot(slot);
            if(!current.isEmpty()&&!matches(current,offered))return offered;
            // RC-37 grows the destination stack; its metadata survives a loose match.
            if(vanilla==null){var rest=items.insertItem(slot,current.isEmpty()?offered:current.copyWithCount(offered.getCount()),simulate);return offered.copyWithCount(rest.getCount());}
            int accepted=Math.min(offered.getCount(),Math.min(vanilla.getMaxStackSize(),offered.getMaxStackSize())-current.getCount());
            if(accepted<=0)return offered;
            // Bukkit CargoUtils writes the selected slot directly, including non-recipe brewing ingredients.
            if(!simulate){vanilla.setItem(slot,(current.isEmpty()?offered:current).copyWithCount(current.getCount()+accepted));vanilla.setChanged();}
            return offered.copyWithCount(offered.getCount()-accepted);
        }
    }
    public static void unload(Level level) { WORLDS.remove(level); }
    public static boolean connected(Level level,BlockPos pos){
        return level instanceof ServerLevel server&&network(server,pos).filter(v->v.complete()&&!v.overflow()).isPresent();
    }
    public static Optional<NetworkApi.View> network(ServerLevel level,BlockPos pos){NetworkApi.check(level);var state=WORLDS.get(level);return state==null?Optional.empty():state.topology.at(pos);}
    public static void add(BlockEntity node) {
        if(!(node.getLevel() instanceof ServerLevel level)||node.isRemoved())return;NetworkApi.check(level);
        var role=((CargoApi.Node)node).cargoRole();var state=WORLDS.computeIfAbsent(level,k->new State(level));
        state.topology.register(node,role==CargoApi.Role.MANAGER?NetworkApi.Role.ROOT:role==CargoApi.Role.CONNECTOR?NetworkApi.Role.CONNECTOR:NetworkApi.Role.TERMINAL,6);
    }
    public static void remove(BlockEntity node) {
        var state=WORLDS.get(node.getLevel()); if(state==null) return;
        if(state.topology.unregister(node))state.grids.remove(node.getBlockPos());
        if(state.topology.positions().isEmpty())WORLDS.remove(node.getLevel());
    }
    public static void tick(BlockEntity manager) {
        if(!(manager.getLevel() instanceof ServerLevel level)||!NetworkApi.live(level,manager))return;NetworkApi.check(level);
        var state=WORLDS.get(level);if(state==null||state.working)return;var managerNode=(CargoApi.Node)manager;
        if(managerNode.cargoRole()!=CargoApi.Role.MANAGER)throw new IllegalArgumentException("Not a cargo manager");
        state.working=true;try{
        var view=state.topology.tick(manager,NetworkSettings.MAX_STEPS.get());
        if(!view.complete()||view.overflow()){managerNode.cargoStatus(Component.literal(view.overflow()?"Network exceeds 4096 nodes":"Classifying network"));return;}
        var grid=state.grids.computeIfAbsent(manager.getBlockPos().immutable(),k->new Grid());
        // RC-37 has one active manager; other managers report the conflict rather than routing twice.
        if(!view.root().equals(manager.getBlockPos())) { managerNode.cargoStatus(Component.translatable("menu.slimefun.cargo.conflict").withStyle(net.minecraft.ChatFormatting.DARK_RED)); return; }
        var inputs=new ArrayList<BlockEntity>(); var outputs=new HashMap<Integer,List<BlockEntity>>();
        for(var pos : view.members()) {
            var entity=state.topology.member(pos);if(!(entity instanceof CargoApi.Node node))continue;
            if(node.channel()<0||node.channel()>15)throw new IllegalStateException("Cargo channel must be 0..15");
            if(node.cargoRole()==CargoApi.Role.INPUT)inputs.add(entity);
            else if(node.cargoRole()==CargoApi.Role.OUTPUT)outputs.computeIfAbsent(node.channel(),k->new ArrayList<>()).add(entity);
        }
        managerNode.cargoStatus(view.members().size()<=1?Component.translatable("menu.slimefun.cargo.empty").withStyle(net.minecraft.ChatFormatting.RED):Component.translatable("menu.slimefun.cargo.online").withStyle(net.minecraft.ChatFormatting.GREEN,net.minecraft.ChatFormatting.BOLD));
        if(view.members().size()<=1)return;
        if(grid.delay<NetworkSettings.CARGO_DELAY.get()){grid.delay++;return;}grid.delay=0;
        for(var input : inputs) {
            var destinations=outputs.get(((CargoApi.Node)input).channel());
            if(destinations!=null&&!destinations.isEmpty())try(var sample=Timings.start(level,input.getBlockPos(),net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(input.getBlockState().getBlock()).toString())){route(grid,input,destinations);}
        }
        // RC-37 NetworkVisualizer: blue dust, size 3, node centers; the regulator is excluded.
        if(NetworkSettings.VISUALIZER.get()&&managerNode.visualizer())for(var pos:view.members())if(!pos.equals(view.root())&&level.hasChunkAt(pos))level.sendParticles(VISUALIZER_DUST,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,1,0,0,0,1);
        }finally{state.working=false;}
    }
    private static boolean loadedInventory(BlockEntity entity){
        var state=entity.getBlockState();return !(entity instanceof ChestBlockEntity)||state.getValue(ChestBlock.TYPE)==ChestType.SINGLE||entity.getLevel().hasChunkAt(entity.getBlockPos().relative(ChestBlock.getConnectedDirection(state)));
    }
    private static Endpoint endpoint(BlockEntity node,ItemStack offered) {
        var settings=(CargoApi.Node)node;var attached=settings.attachedFace();if(attached==null)return null;
        var level=node.getLevel();var facing=attached.getOpposite();var pos=node.getBlockPos().relative(attached);
        if(level==null || !level.hasChunkAt(pos)) return null;
        var entity=level.getBlockEntity(pos); if(entity==null || entity.isRemoved() || !loadedInventory(entity)) return null;
        var type=entity.getType();boolean furnace=type==BlockEntityType.FURNACE||type==BlockEntityType.SMOKER||type==BlockEntityType.BLAST_FURNACE,brewer=type==BlockEntityType.BREWING_STAND;
        var capability=entity.getCapability(ForgeCapabilities.ITEM_HANDLER,furnace||brewer?null:facing);var inventory=capability.orElse(null);if(inventory==null)return null;
        int first=0,limit=inventory.getSlots();
        // RC-37 selects native slots by transport flow and item, independently of the wall-head side.
        if(furnace){if(settings.cargoRole()==CargoApi.Role.INPUT){first=2;limit=3;}else if(AbstractFurnaceBlockEntity.isFuel(offered)){first=offered.is(net.minecraft.tags.ItemTags.LOGS)?0:1;limit=2;}else limit=1;}
        else if(brewer){if(settings.cargoRole()==CargoApi.Role.INPUT||offered.getItem() instanceof PotionItem)limit=3;else if(offered.is(Items.BLAZE_POWDER)){first=4;limit=5;}else{first=3;limit=4;}}
        var namespace=net.minecraftforge.registries.ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(type).getNamespace();
        int[] selected=entity instanceof CargoApi.Endpoint custom?Objects.requireNonNull(custom.cargoSlots(facing,settings.cargoRole()==CargoApi.Role.INPUT,offered.copy(),inventory.getSlots())).clone():java.util.stream.IntStream.range(first,limit).toArray();
        var unique=new HashSet<Integer>();for(int slot:selected)if(slot<0||slot>=inventory.getSlots()||!unique.add(slot))throw new IllegalStateException("Invalid or duplicate cargo slot at "+pos);
        return new Endpoint(pos,entity,capability,inventory,selected,furnace||brewer?(Container)entity:null,namespace.equals(SlimefunForge.ID)||namespace.equals("minecraft")&&entity instanceof Container);
    }
    private static void route(Grid grid,BlockEntity input,List<BlockEntity> destinations) {
        var inputNode=(CargoApi.Node)input;
        var source=endpoint(input,ItemStack.EMPTY); if(source==null) return;
        for(int slot:source.slots) {
            var candidate=source.items.extractItem(slot,64,true);
            if(candidate.isEmpty()||!inputNode.accepts(candidate.copy())) continue;
            int remaining=candidate.getCount(); int cursor=grid.roundRobin.getOrDefault(input.getBlockPos(),0);int start=inputNode.roundRobin()&&cursor<destinations.size()?cursor:0;
            for(int i=0;i<destinations.size() && remaining>0;i++) {
                int index=(start+i)%destinations.size(); var output=destinations.get(index);
                if(!((CargoApi.Node)output).accepts(candidate.copy())) continue;
                var target=endpoint(output,candidate);
                if(target==null || target.pos.equals(source.pos) || target.items==source.items) continue;
                for(int outSlot:target.slots) {
                    var existing=target.items.getStackInSlot(outSlot);
                    if(inputNode.smartFill() && !existing.isEmpty() && target.matches(existing,candidate)
                        && existing.getCount()>=Math.min(existing.getMaxStackSize(),target.items.getSlotLimit(outSlot))) break;
                    var offer=candidate.copyWithCount(remaining);
                    int accepted=remaining-target.insert(outSlot,offer,true).getCount();
                    if(accepted<=0) continue;
                    if(!eligible(input,output,source,target,candidate))return;
                    var extracted=source.items.extractItem(slot,accepted,false);
                    if(extracted.isEmpty()) return;
                    var rejected=eligible(input,output,source,target,candidate)&&extracted.getCount()<=accepted&&ItemStack.isSameItemSameTags(extracted,candidate)?target.insert(outSlot,extracted,false):extracted;
                    int moved=extracted.getCount()-rejected.getCount(); remaining-=moved;
                    if(moved>0) {
                        // RC-37 advances round robin only after the whole withdrawn stack is delivered.
                        if(remaining==0&&inputNode.roundRobin())grid.roundRobin.put(input.getBlockPos(),(index+1)%destinations.size());
                        if(DebugLogging.enabled(input.getLevel(),DebugLogging.CARGO))DebugLogging.log(input.getLevel(),DebugLogging.CARGO,"Transfer {} {} -> {} item={} moved={}",input.getLevel().dimension().location(),source.pos,target.pos,candidate,moved);
                    }
                    if(!rejected.isEmpty()) {
                        // Partial extraction leaves a stack in native output slots that reject ordinary insertion.
                        if(source.valid()&&source.vanilla!=null){rejected=source.insert(slot,rejected,false);if(rejected.isEmpty())return;}
                        // A third-party handler changed its answer between simulation and commit.
                        // Try the source first; only failed returns obey the explicit overflow deletion option.
                        var rest=source.valid()?ItemHandlerHelper.insertItemStacked(source.items,rejected,false):rejected;
                        if(!rest.isEmpty()&&!NetworkSettings.DELETE_EXCESS.get()) io.github.slimefun4.forge.api.ItemSpawnApi.reserve((ServerLevel)input.getLevel(),null,io.github.slimefun4.forge.api.ItemSpawnApi.Reason.CARGO_OVERFLOW,new net.minecraft.world.phys.Vec3(source.pos.getX()+.5,source.pos.getY()+1,source.pos.getZ()+.5),rest);
                        return;
                    }
                    // The upstream node fills one eligible slot in each destination per interval.
                    break;
                }
            }
            return;
        }
    }
    private static boolean eligible(BlockEntity input,BlockEntity output,Endpoint source,Endpoint target,ItemStack candidate){
        var from=(CargoApi.Node)input;var to=(CargoApi.Node)output;
        if(!from.accepts(candidate.copy())||!to.accepts(candidate.copy()))return false;
        var state=WORLDS.get(input.getLevel());if(state==null||state.topology.at(input.getBlockPos()).filter(v->v.complete()&&!v.overflow()&&v.members().contains(output.getBlockPos())).isEmpty())return false;
        return from.cargoRole()==CargoApi.Role.INPUT&&to.cargoRole()==CargoApi.Role.OUTPUT&&from.channel()==to.channel()&&from.attachedFace()!=null&&to.attachedFace()!=null
            &&input.getBlockPos().relative(from.attachedFace()).equals(source.pos)&&output.getBlockPos().relative(to.attachedFace()).equals(target.pos)
            &&NetworkApi.live((ServerLevel)input.getLevel(),input)&&NetworkApi.live((ServerLevel)output.getLevel(),output)&&source.valid()&&target.valid();
    }
    private CargoNetwork() {}
}

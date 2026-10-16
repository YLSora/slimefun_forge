package io.github.slimefun4.forge.api;

import io.github.slimefun4.forge.NetworkTopology;
import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Loaded, six-axis networks. Call register on load/role change and unregister on removal/cap invalidation. */
@Mod.EventBusSubscriber(modid="slimefun")
public final class NetworkApi {
    public enum Role { ROOT, CONNECTOR, TERMINAL }
    public enum Roots { EXCLUSIVE, LOWEST }
    public record View(BlockPos root,SortedSet<BlockPos> members,boolean complete,boolean overflow) {}
    private static final Set<Network> NETWORKS=Collections.newSetFromMap(new WeakHashMap<>());
    public static final class Network {
        private final WeakReference<ServerLevel> world;
        private final Map<BlockPos,WeakReference<BlockEntity>> owners=new HashMap<>();
        private final NetworkTopology<BlockPos> topology;
        private boolean closed;
        public Network(ServerLevel level,Roots roots,int maxNodes){
            check(level);world=new WeakReference<>(level);
            topology=new NetworkTopology<>(NetworkTopology.Roots.valueOf(roots.name()),maxNodes,(pos,range)->{var positions=new ArrayList<BlockPos>(6*range);for(var side:Direction.values())for(int i=1;i<=range;i++)positions.add(pos.relative(side,i));return positions;});
            NETWORKS.add(this);
        }
        private ServerLevel level(){var level=world.get();if(level==null||closed)throw new IllegalStateException("Network level unloaded");check(level);return level;}
        private boolean loaded(BlockPos pos){var level=world.get();if(level==null)return false;var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);if(chunk==null)return false;var ref=owners.get(pos);if(ref==null)return true;var owner=ref.get();return owner!=null&&!owner.isRemoved()&&chunk.getBlockEntity(pos)==owner;}
        public void register(BlockEntity owner,Role role,int range){
            var level=level();if(!live(level,owner))throw new IllegalArgumentException("Network member is not a live loaded block");
            var pos=owner.getBlockPos().immutable();
            topology.put(pos,switch(role){case ROOT->NetworkTopology.Role.ROOT;case CONNECTOR->NetworkTopology.Role.RELAY;case TERMINAL->NetworkTopology.Role.TERMINAL;},range);
            var previous=owners.put(pos,new WeakReference<>(owner));if(previous!=null&&previous.get()!=owner)topology.dirty();
        }
        public boolean unregister(BlockEntity owner){if(closed)return false;level();var previous=owners.get(owner.getBlockPos());if(previous==null||previous.get()!=owner)return false;owners.remove(owner.getBlockPos());topology.remove(owner.getBlockPos());return true;}
        public void dirty(){level();topology.dirty();}
        public Set<BlockPos> positions(){level();return topology.positions();}
        public BlockEntity member(BlockPos pos){var level=level();var ref=owners.get(pos);var owner=ref==null?null:ref.get();return owner!=null&&live(level,owner)?owner:null;}
        public View tick(BlockEntity root,int positionBudget){level();if(member(root.getBlockPos())!=root)throw new IllegalArgumentException("Stale network root");return view(topology.step(root.getBlockPos(),positionBudget,this::loaded));}
        public Optional<View> at(BlockPos pos){level();return topology.at(pos,this::loaded).map(Network::view);}
        public boolean visualize(ServerPlayer player,BlockPos pos){
            var level=level();if(player.serverLevel()!=level||!player.isAlive()||player.distanceToSqr(pos.getCenter())>4096||!loaded(pos)||!level.mayInteract(player,pos))return false;
            var view=at(pos).orElse(null);if(view==null)return false;
            for(var node:view.members())if(!node.equals(view.root())&&loaded(node)&&level.mayInteract(player,node))level.sendParticles(player,ParticleTypes.END_ROD,false,node.getX()+.5,node.getY()+.5,node.getZ()+.5,1,0,0,0,0);return true;
        }
        public void close(){var level=world.get();if(level!=null)check(level);closed=true;owners.clear();topology.clear();}
        private static View view(NetworkTopology.View<BlockPos> value){return new View(value.root(),value.members(),value.complete(),value.overflow());}
    }
    public static void check(ServerLevel level){if(!level.getServer().isSameThread())throw new IllegalStateException("Network service requires its server thread");}
    public static boolean live(ServerLevel level,BlockEntity owner){var pos=owner.getBlockPos();var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);return owner.getLevel()==level&&!owner.isRemoved()&&chunk!=null&&chunk.getBlockEntity(pos)==owner;}
    @SubscribeEvent public static void unload(LevelEvent.Unload event){for(var network:List.copyOf(NETWORKS))if(network.world.get()==event.getLevel())network.close();}
    private NetworkApi(){}
}

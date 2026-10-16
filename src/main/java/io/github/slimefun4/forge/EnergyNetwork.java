package io.github.slimefun4.forge;

import io.github.slimefun4.forge.api.*;
import java.util.*;
import java.text.*;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;

/** One classified native graph and one centralized buffer transaction per regulator cycle. */
public final class EnergyNetwork {
    private static final Map<Level,State> WORLDS=new WeakHashMap<>();
    private static final DecimalFormat NUMBER=new DecimalFormat("#.##",DecimalFormatSymbols.getInstance(Locale.ROOT));
    private static final class State {
        final NetworkApi.Network topology;final Map<BlockPos,String> kinds=new HashMap<>();final Map<BlockPos,Long> ticks=new HashMap<>();boolean working;
        State(ServerLevel level){topology=new NetworkApi.Network(level,NetworkApi.Roots.EXCLUSIVE,4096);}
    }
    private record Port(BlockEntity owner,IEnergyStorage storage,EnergyApi.Role role,int stored,int capacity) {
        boolean valid(ServerLevel level){return NetworkApi.live(level,owner)&&owner.getCapability(ForgeCapabilities.ENERGY).orElse(null)==storage;}
    }
    public static void unload(Level level){WORLDS.remove(level);}
    public static void recipesReloaded(Level level){
        if(!(level instanceof ServerLevel server))return;var state=WORLDS.get(level);if(state==null)return;
        for(var pos:state.topology.positions()){
            var entity=state.topology.member(pos);
            if(entity instanceof ProcessingEntity processor)processor.recipesReloaded();else if(entity instanceof AutoCrafterEntity crafter)crafter.wake();
            else if(entity instanceof GeoMinerEntity miner)miner.wake();else if(entity instanceof ReactorEntity reactor)reactor.wake();else if(entity instanceof MachineEntity machine)machine.wake();
        }
    }
    public static void add(MachineEntity machine){add(machine,machine.definition.implementation());}
    public static void add(BlockEntity entity,String kind){
        if(!(entity.getLevel() instanceof ServerLevel level)||entity.isRemoved())return;NetworkApi.check(level);
        var role=role(entity,kind);var state=WORLDS.computeIfAbsent(level,k->new State(level));
        state.topology.register(entity,role==EnergyApi.Role.REGULATOR?NetworkApi.Role.ROOT:role==EnergyApi.Role.CONNECTOR||role==EnergyApi.Role.CAPACITOR?NetworkApi.Role.CONNECTOR:NetworkApi.Role.TERMINAL,7);
        state.kinds.put(entity.getBlockPos().immutable(),kind);
    }
    private static EnergyApi.Role role(BlockEntity entity,String kind){
        if(entity instanceof EnergyApi.Member member)return Objects.requireNonNull(member.energyRole());
        if(kind.equals("EnergyRegulator"))return EnergyApi.Role.REGULATOR;if(kind.equals("EnergyConnector"))return EnergyApi.Role.CONNECTOR;if(kind.equals("Capacitor"))return EnergyApi.Role.CAPACITOR;
        return entity instanceof ReactorEntity||entity instanceof MachineEntity machine&&machine.definition.production()>0?EnergyApi.Role.GENERATOR:EnergyApi.Role.CONSUMER;
    }
    public static void remove(BlockEntity entity){
        var state=WORLDS.get(entity.getLevel());if(state==null)return;if(!state.topology.unregister(entity))return;
        state.kinds.remove(entity.getBlockPos());state.ticks.remove(entity.getBlockPos());if(state.kinds.isEmpty())WORLDS.remove(entity.getLevel());
    }
    public static boolean registered(Level level,BlockPos pos){var state=WORLDS.get(level);return state!=null&&state.topology.member(pos)!=null;}
    public static Optional<NetworkApi.View> network(ServerLevel level,BlockPos pos){NetworkApi.check(level);var state=WORLDS.get(level);return state==null?Optional.empty():state.topology.at(pos);}
    public static boolean connected(Level level,BlockPos pos){return level instanceof ServerLevel server&&registered(level,pos)&&network(server,pos).filter(v->v.complete()&&!v.overflow()).isPresent();}
    private static void status(BlockEntity root,Component text){if(root instanceof MachineEntity machine)machine.networkStatus(text);else if(root instanceof EnergyApi.Member member)member.networkStatus(text);}
    public static void tick(BlockEntity regulator){
        if(!(regulator.getLevel() instanceof ServerLevel level)||!NetworkApi.live(level,regulator))return;NetworkApi.check(level);
        var state=WORLDS.get(level);if(state==null||state.working)return;
        if(role(regulator,state.kinds.getOrDefault(regulator.getBlockPos(),""))!=EnergyApi.Role.REGULATOR)throw new IllegalArgumentException("Not an energy regulator");
        state.working=true;
        try{
            var grid=state.topology.tick(regulator,NetworkSettings.MAX_STEPS.get());
            if(!grid.root().equals(regulator.getBlockPos())){status(regulator,Component.translatable("message.slimefun.network_conflict").withStyle(ChatFormatting.DARK_RED));return;}
            if(!grid.complete()||grid.overflow()){status(regulator,Component.literal(grid.overflow()?"Network exceeds 4096 nodes":"Classifying network"));return;}
            var sources=new ArrayList<Port>();var consumers=new ArrayList<Port>();
            for(var pos:grid.members()){
                if(pos.equals(grid.root()))continue;var entity=state.topology.member(pos);if(entity==null)continue;
                var previous=state.ticks.put(pos,level.getGameTime());
                if(previous==null||previous!=level.getGameTime()){
                    if(entity instanceof ReactorEntity reactor)try(var sample=Timings.start(level,pos,reactor.definition.id())){reactor.work();}
                    else if(entity instanceof EnergyApi.Member member)member.networkTick();
                }
                if(!NetworkApi.live(level,entity))continue;var storage=entity.getCapability(ForgeCapabilities.ENERGY).orElse(null);if(storage==null)continue;
                var role=role(entity,state.kinds.getOrDefault(pos,""));int capacity=storage.getMaxEnergyStored(),stored=storage.getEnergyStored();if(capacity<0||stored<0||stored>capacity)throw new IllegalStateException("Invalid network energy buffer at "+pos);
                var port=new Port(entity,storage,role,stored,capacity);
                if(role==EnergyApi.Role.GENERATOR||role==EnergyApi.Role.CAPACITOR)sources.add(port);else if(role==EnergyApi.Role.CONSUMER&&storage.canReceive())consumers.add(port);
            }
            if(sources.isEmpty()&&consumers.isEmpty()){status(regulator,Component.translatable("message.slimefun.network_missing"));return;}
            long supply=sources.stream().mapToLong(Port::stored).sum(),demand=0;var demands=new ArrayList<Integer>();
            for(var port:consumers){int space=port.storage.receiveEnergy(Integer.MAX_VALUE,true);if(space<0)throw new IllegalStateException("Negative simulated energy demand");demand+=space;demands.add(space);}
            int[] planned=NetworkRules.distribute(supply,demands);
            // Simulation callbacks may unload/replace blocks or change a source; abort before any debit.
            for(var port:sources)if(!port.valid(level)||port.storage.getEnergyStored()!=port.stored||port.storage.getMaxEnergyStored()!=port.capacity)return;
            for(var port:consumers)if(!port.valid(level))return;
            if(state.topology.at(regulator.getBlockPos()).filter(grid::equals).isEmpty())return;
            long remaining=supply-Arrays.stream(planned).mapToLong(i->i).sum();
            sources.sort(Comparator.comparingInt(p->p.role==EnergyApi.Role.CAPACITOR?0:1));
            for(var port:sources)if(port.valid(level)){int amount=(int)Math.min(remaining,port.capacity);store(port,amount);remaining-=amount;}
            // Debit all sources before receiver callbacks. Refund rejected units into current buffers, never overwrite later receipts.
            long refund=0;
            try{for(int i=0;i<consumers.size();i++){
                var port=consumers.get(i);int offered=planned[i];if(!port.valid(level)){refund+=offered;continue;}
                int accepted=port.storage.receiveEnergy(offered,false);if(accepted<0||accepted>offered)throw new IllegalStateException("Invalid energy receiver result");refund+=offered-accepted;
            }}finally{for(var port:sources)if(refund>0&&port.valid(level)){int current=port.storage.getEnergyStored();int amount=(int)Math.min(refund,Math.max(0,port.storage.getMaxEnergyStored()-current));store(port,current+amount);refund-=amount;}}
            long difference=supply-demand;boolean loss=difference<0;double number=Math.abs(difference);String suffix="";
            if(number>=1_000_000_000){number/=1_000_000_000;suffix="B";}else if(number>=1_000_000){number/=1_000_000;suffix="M";}else if(number>=1000){number/=1000;suffix="K";}
            status(regulator,Component.literal((loss?"- ":"+ ")+NUMBER.format(number)+suffix+" J ⚡").withStyle(loss?ChatFormatting.RED:ChatFormatting.GREEN));
        }finally{state.working=false;}
    }
    private static void store(Port port,int amount){
        if(port.owner instanceof EnergyApi.Member member)member.networkEnergy(amount);else if(port.owner instanceof MachineEntity machine)machine.networkEnergy(amount);else if(port.owner instanceof ReactorEntity reactor)reactor.networkEnergy(amount);else throw new IllegalStateException("Unwritable energy source");
        if(port.storage.getEnergyStored()!=amount)throw new IllegalStateException("Energy member must store the absolute buffer value");
    }
    private EnergyNetwork(){}
}

package io.github.slimefun4.forge;

import java.util.*;
import java.util.function.Function;
import java.util.concurrent.CompletableFuture;
import com.mojang.brigadier.exceptions.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;

/** On-demand, server-thread callback sampling. Child work is charged to its own block. */
public final class Timings {
    private static final Map<MinecraftServer,State> SERVERS=new IdentityHashMap<>();
    public record Key(ResourceLocation dimension,BlockPos pos,String item){}
    public record Sample(long nanoseconds,int calls){public long average(){return calls==0?0:nanoseconds/calls;}}
    public record Snapshot(long tick,int ticks,Map<Key,Sample> blocks,long nanoseconds){public Snapshot{blocks=Map.copyOf(blocks);}}
    public enum Order { HIGH,LOW,AVERAGE }
    public enum Group { ITEM, ADDON, CHUNK }
    public record Summary(String name,long nanoseconds,int blocks,int calls,double percent){
        public long average(){return calls==0?0:nanoseconds/calls;}
        /** Rating of a sampled callback's average, not the entire server tick. */
        public String rating(){long average=average();return average<100_000?"fast":average<500_000?"moderate":average<1_000_000?"slow":"very_slow";}
    }
    private record Request(CommandSourceStack source,boolean verbose,Order order){}
    private record Row(String name,long nanoseconds,int blocks,int calls){long average(){return nanoseconds/blocks;}}
    private static final class State {
        final MinecraftServer server;final List<CompletableFuture<Snapshot>> pending=new ArrayList<>();
        final List<Request> requests=new ArrayList<>();final Map<Key,Sample> samples=new HashMap<>();final Deque<Span> stack=new ArrayDeque<>();
        boolean active;int remaining;long rootNanoseconds;Snapshot latest;
        State(MinecraftServer server){this.server=server;}
    }
    private static void thread(MinecraftServer server){if(!server.isSameThread())throw new IllegalStateException("Diagnostics require server thread");}
    public static Snapshot latest(MinecraftServer server){thread(server);var state=SERVERS.get(server);return state==null?null:state.latest;}
    /** Join the current ten-tick sample or schedule the next one. Result callbacks run on the server thread. */
    public static CompletableFuture<Snapshot> sample(MinecraftServer server){thread(server);if(server.isStopped())throw new IllegalStateException("Server stopped");var result=new CompletableFuture<Snapshot>();SERVERS.computeIfAbsent(server,State::new).pending.add(result);return result;}
    public static <T extends net.minecraft.world.level.block.entity.BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> ticker(net.minecraft.world.level.block.entity.BlockEntityTicker<T> ticker){
        if(ticker==null)return null;
        return (level,pos,state,entity)->{try(var sample=start(level,pos,state)){ticker.tick(level,pos,state,entity);}};
    }
    public static Span start(Level level,BlockPos pos,BlockState block){
        if(!active(level))return null;var item=new net.minecraft.world.item.ItemStack(block.getBlock());return ItemAccess.id(item)==null?null:start(level,pos,BuiltInRegistries.ITEM.getKey(item.getItem()).toString());
    }
    private static boolean active(Level level){if(level==null||level.isClientSide||!level.getServer().isSameThread())return false;var state=SERVERS.get(level.getServer());return state!=null&&state.active;}
    public static Span start(Level level,BlockPos pos,String item){
        if(!active(level))return null;var id=new ResourceLocation(item.indexOf(':')<0?"slimefun:"+item:item);if(!BuiltInRegistries.ITEM.containsKey(id)||ItemAccess.id(new net.minecraft.world.item.ItemStack(BuiltInRegistries.ITEM.get(id)))==null)throw new IllegalArgumentException("Unknown diagnostic item "+id);var state=SERVERS.get(level.getServer());var span=new Span(state,new Key(level.dimension().location(),pos.immutable(),id.toString()));state.stack.push(span);return span;
    }
    public static final class Span implements AutoCloseable {
        private final State owner;private final Key key;private final long started=System.nanoTime();private long children;private boolean closed;
        private Span(State owner,Key key){this.owner=owner;this.key=key;}
        @Override public void close(){
            thread(owner.server);if(closed)return;if(owner.stack.peek()!=this||!owner.active)throw new IllegalStateException("Timing scopes must close in reverse order within the same tick");
            long elapsed=System.nanoTime()-started;owner.stack.pop();closed=true;
            owner.samples.merge(key,new Sample(EventRules.exclusive(elapsed,children),1),(a,b)->new Sample(Math.addExact(a.nanoseconds,b.nanoseconds),Math.addExact(a.calls,b.calls)));
            if(owner.stack.isEmpty())owner.rootNanoseconds+=elapsed;else owner.stack.peek().children+=elapsed;
        }
    }
    static int request(CommandSourceStack source,String arguments)throws CommandSyntaxException{
        var flags=new HashSet<String>();for(String word:arguments.strip().split("\\s+"))if(word.startsWith("--")){
            String flag=word.toLowerCase(Locale.ROOT);if(!Set.of("--avg","--low","--verbose").contains(flag))throw new SimpleCommandExceptionType(Messages.format(source,Component.translatable("command.slimefun.timings_flag",word),true)).create();flags.add(flag);
        }
        boolean verbose=flags.contains("--verbose");if(verbose&&source.getEntity() instanceof ServerPlayer)throw new SimpleCommandExceptionType(Messages.format(source,Component.translatable("command.slimefun.timings_verbose_player"),true)).create();
        var state=SERVERS.computeIfAbsent(source.getServer(),State::new);state.requests.add(new Request(source,verbose,flags.contains("--avg")?Order.AVERAGE:flags.contains("--low")?Order.LOW:Order.HIGH));
        Messages.command(source,Component.translatable("command.slimefun.timings_wait"),true);return 1;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void tick(TickEvent.ServerTickEvent event){
        var server=event.getServer();var state=SERVERS.get(server);if(state==null)return;
        if(event.phase==TickEvent.Phase.START){if(!state.active&&(!state.requests.isEmpty()||!state.pending.isEmpty())){state.active=true;state.remaining=10;state.rootNanoseconds=0;state.samples.clear();}return;}
        if(!state.stack.isEmpty()){state.active=false;state.stack.clear();state.samples.clear();state.requests.clear();var pending=List.copyOf(state.pending);state.pending.clear();for(var future:pending)future.completeExceptionally(new IllegalStateException("Timing scope crossed a tick boundary"));return;}
        if(!state.active||--state.remaining>0)return;
        state.active=false;state.latest=new Snapshot(server.getTickCount(),10,Map.copyOf(state.samples),state.rootNanoseconds);state.samples.clear();var requests=List.copyOf(state.requests);state.requests.clear();var pending=List.copyOf(state.pending);state.pending.clear();for(var future:pending)future.complete(state.latest);
        for(var request:requests){var source=request.source;if(!AdminCommands.canTime(source))continue;if(source.getEntity() instanceof ServerPlayer player&&server.getPlayerList().getPlayer(player.getUUID())!=player)continue;send(state.latest,request);}
    }
    private static String millis(long nanos){return String.format(Locale.ROOT,"%.3f",nanos/1_000_000.0);}
    private static void send(Snapshot snapshot,Request request){
        var source=request.source;Messages.command(source,Component.translatable("command.slimefun.timings_title"),false);
        Messages.command(source,Component.translatable("command.slimefun.timings_total",millis(snapshot.nanoseconds),snapshot.ticks,String.format(Locale.ROOT,"%.3f",snapshot.nanoseconds/(snapshot.ticks*500_000.0))),false);
        group(snapshot,request,"items",Key::item,snapshot.blocks.size());
        group(snapshot,request,"chunks",k->k.dimension+" ("+(k.pos.getX()>>4)+","+(k.pos.getZ()>>4)+")",-1);
        group(snapshot,request,"mods",k->k.item.substring(0,k.item.indexOf(':')),-1);
    }
    private static void group(Snapshot snapshot,Request request,String name,Function<Key,String> key,int count){
        var groups=new HashMap<String,Row>();snapshot.blocks.forEach((block,time)->{String id=key.apply(block);groups.merge(id,new Row(id,time.nanoseconds,1,time.calls),(a,b)->new Row(id,a.nanoseconds+b.nanoseconds,a.blocks+b.blocks,a.calls+b.calls));});
        Comparator<Row> comparator=request.order==Order.AVERAGE?Comparator.comparingLong(Row::average).reversed():request.order==Order.LOW?Comparator.comparingLong(Row::nanoseconds):Comparator.comparingLong(Row::nanoseconds).reversed();
        var rows=groups.values().stream().sorted(comparator.thenComparing(Row::name)).toList();var details=new ArrayList<String>();int hidden=0;
        for(var row:rows){if(request.verbose||details.size()<20&&(details.size()<6||row.nanoseconds>260_000)){boolean avg=request.order==Order.AVERAGE;details.add(Messages.format(request.source,Component.translatable("command.slimefun.timings_row"+(avg?"_average":""),row.name,row.blocks,millis(avg?row.average():row.nanoseconds),millis(avg?row.nanoseconds:row.average()),row.calls),false).getString());}else hidden++;}
        if(hidden>0)details.add(Messages.format(request.source,Component.translatable("command.slimefun.timings_more",hidden),false).getString());var message=Component.translatable("command.slimefun.timings_"+name,count<0?groups.size():count);
        if(!details.isEmpty()){String text=String.join("\n",details);if(request.source.getEntity() instanceof ServerPlayer)message.withStyle(style->style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,Component.literal(text))));else message.append("\n"+text);}
        Messages.command(request.source,message,false);
    }
    public static List<Summary> summaries(Snapshot snapshot,Group group,Order order){
        var values=new HashMap<String,Summary>();snapshot.blocks.forEach((key,sample)->{String name=switch(group){case ITEM->key.item;case ADDON->new ResourceLocation(key.item).getNamespace();case CHUNK->key.dimension+" ("+(key.pos.getX()>>4)+","+(key.pos.getZ()>>4)+")";};values.merge(name,new Summary(name,sample.nanoseconds,1,sample.calls,0),(a,b)->new Summary(name,Math.addExact(a.nanoseconds,b.nanoseconds),a.blocks+b.blocks,Math.addExact(a.calls,b.calls),0));});
        Comparator<Summary> compare=order==Order.AVERAGE?Comparator.comparingLong(Summary::average).reversed():order==Order.LOW?Comparator.comparingLong(Summary::nanoseconds):Comparator.comparingLong(Summary::nanoseconds).reversed();
        return values.values().stream().map(row->new Summary(row.name,row.nanoseconds,row.blocks,row.calls,snapshot.nanoseconds==0?0:100.0*row.nanoseconds/snapshot.nanoseconds)).sorted(compare.thenComparing(Summary::name)).toList();
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){var state=SERVERS.remove(event.getServer());if(state!=null)for(var future:List.copyOf(state.pending))future.cancel(false);}
    private Timings(){}
}

package io.github.slimefun4.forge.api;

import io.github.slimefun4.forge.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** On-demand diagnostics only; no network, telemetry, background world access or implicit sampling. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=SlimefunForge.ID)
public final class DiagnosticApi {
    private static final Map<MinecraftServer,Map<ResourceLocation,Long>> ERRORS=new WeakHashMap<>();
    /** Recoverable callback boundary. False means reported failure, not rollback; caller must pause safely.
     * Fatal Errors propagate. The original throwable reaches the logger with complete native context.
     */
    public static boolean execute(ServerLevel level,BlockPos pos,ResourceLocation item,String task,Runnable action){
        check(level.getServer());Objects.requireNonNull(pos);validate(item,task);
        return DiagnosticRules.execute(action,error->report(level,pos,item,task,error));
    }
    /** Counts reports per item for this server lifecycle; preserves the full cause and suppressed errors. */
    public static long report(ServerLevel level,BlockPos pos,ResourceLocation item,String task,Throwable cause){
        check(level.getServer());Objects.requireNonNull(pos);Objects.requireNonNull(cause);
        validate(item,task);
        var counts=ERRORS.computeIfAbsent(level.getServer(),server->new HashMap<>());long count=counts.compute(item,(key,old)->DiagnosticRules.increment(old==null?0:old));
        com.mojang.logging.LogUtils.getLogger().error("Slimefun callback failure addon={} item={} dimension={} pos={} task={} count={}",item.getNamespace(),item,level.dimension().location(),pos.toShortString(),task,count,cause);
        return count;
    }
    public static long errors(MinecraftServer server,ResourceLocation item){check(server);return ERRORS.getOrDefault(server,Map.of()).getOrDefault(Objects.requireNonNull(item),0L);}
    private static void validate(ResourceLocation item,String task){if(item==null||!net.minecraftforge.registries.ForgeRegistries.ITEMS.containsKey(item)||task==null||task.isBlank())throw new IllegalArgumentException("Error report requires a registered full item ID and task");}
    private static void check(MinecraftServer server){if(!server.isSameThread())throw new IllegalStateException("Diagnostics require server thread");}
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void stop(net.minecraftforge.event.server.ServerStoppedEvent event){ERRORS.remove(event.getServer());}
    public static CompletableFuture<Timings.Snapshot> sample(MinecraftServer server){return Timings.sample(server);}
    /** Empty means never sampled, not zero execution time. Snapshot tick identifies stale measurements. */
    public static Optional<Timings.Snapshot> latest(MinecraftServer server){return Optional.ofNullable(Timings.latest(server));}
    /** Null means not currently sampled. Use try-with-resources; close in reverse order in the same tick.
     * Item must be a core/registered addon item. Nested time is charged exclusively to the innermost scope.
     */
    public static Timings.Span start(ServerLevel level,BlockPos pos,ResourceLocation item){if(!level.getServer().isSameThread())throw new IllegalStateException("Diagnostics require server thread");return Timings.start(level,pos,item.toString());}
    public static List<Timings.Summary> summaries(Timings.Snapshot sample,Timings.Group group,Timings.Order order){return Timings.summaries(sample,group,order);}
    public static Optional<String> debugCase(MinecraftServer server){if(!server.isSameThread())throw new IllegalStateException("Diagnostics require server thread");return Optional.ofNullable(DebugLogging.current(server));}
    public static void debug(ServerLevel level,ResourceLocation test,String message,Object...arguments){DebugLogging.log(level,test.toString(),message,arguments);}
    private DiagnosticApi(){}
}

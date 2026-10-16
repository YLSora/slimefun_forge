package io.github.slimefun4.forge;

import io.github.slimefun4.forge.api.Rechargeable;

import java.util.*;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.*;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.*;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import net.minecraftforge.server.permission.nodes.*;
import static net.minecraft.commands.Commands.*;

public final class AdminCommands {
    public static final ForgeConfigSpec CONFIG;
    public static final ForgeConfigSpec.BooleanValue DROP_EXCESS;
    private static final PermissionNode<Boolean> BACKPACK=node("command.backpack"),RESEARCH=node("cheat.researches"),CHARGE=node("command.charge"),STATS=node("stats.others"),TELEPORTER=node("command.teleporter"),DEBUG=node("command.debug"),TIMINGS=node("command.timings"),DEBUG_FISH=node("debugging");
    static {var builder=new ForgeConfigSpec.Builder();DROP_EXCESS=builder.define("drop-excess-sf-give-items",false);CONFIG=builder.build();}
    private static PermissionNode<Boolean> node(String key){return new PermissionNode<>("slimefun",key,PermissionTypes.BOOLEAN,(p,id,c)->p!=null&&p.hasPermissions(2));}
    private static boolean allowed(CommandSourceStack source,PermissionNode<Boolean> permission){return source.getEntity() instanceof ServerPlayer player?PermissionAPI.getPermission(player,permission):source.hasPermission(2);}
    private static boolean items(CommandSourceStack source){return source.getEntity() instanceof ServerPlayer player?Guide.canCheat(player):source.hasPermission(2);}
    static void register(IEventBus bus){ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER,CONFIG,"slimefun_forge/slimefun-commands-server.toml");MinecraftForge.EVENT_BUS.register(AdminCommands.class);MinecraftForge.EVENT_BUS.register(DebugLogging.class);MinecraftForge.EVENT_BUS.register(Timings.class);MinecraftForge.EVENT_BUS.register(DebugFish.class);}
    @SubscribeEvent public static void permissions(PermissionGatherEvent.Nodes event){event.addNodes(BACKPACK,RESEARCH,CHARGE,STATS,TELEPORTER,DEBUG,TIMINGS,DEBUG_FISH);}
    static boolean canDebugFish(ServerPlayer player){return PermissionAPI.getPermission(player,DEBUG_FISH);}
    static boolean canTime(CommandSourceStack source){return allowed(source,TIMINGS);}
    static boolean canTeleport(ServerPlayer player){return PermissionAPI.getPermission(player,TELEPORTER);}
    @SubscribeEvent public static void commands(RegisterCommandsEvent event){
        event.getDispatcher().register(literal("sf")
            .then(literal("help").executes(c->{c.getSource().sendSuccess(()->Component.translatable("command.slimefun.help"),false);return 1;}))
            .then(literal("debug_fish").requires(s->s.getEntity() instanceof ServerPlayer p&&canDebugFish(p)).executes(c->{var p=c.getSource().getPlayerOrException();var inventory=new net.minecraftforge.items.wrapper.PlayerMainInvWrapper(p.getInventory());var stack=new ItemStack(SlimefunForge.DEBUG_FISH.get());if(!net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(inventory,stack,true).isEmpty())throw error(c.getSource(),"bag_full");net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(inventory,stack,false);p.inventoryMenu.broadcastChanges();return 1;}))
            .then(literal("timings").requires(AdminCommands::canTime).executes(c->Timings.request(c.getSource(),""))
                .then(argument("flags",StringArgumentType.greedyString()).suggests((c,b)->SharedSuggestionProvider.suggest(List.of("--avg","--low","--verbose"),b)).executes(c->Timings.request(c.getSource(),StringArgumentType.getString(c,"flags")))))
            .then(literal("debug").requires(s->allowed(s,DEBUG)).executes(c->DebugLogging.command(c.getSource(),null))
                .then(argument("case",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(List.of(DebugLogging.CARGO,DebugLogging.PROFILE,"off","disable"),b)).executes(c->DebugLogging.command(c.getSource(),StringArgumentType.getString(c,"case")))))
            .then(literal("teleporter").requires(s->s.getEntity() instanceof ServerPlayer p&&canTeleport(p))
                .executes(c->GpsTeleportMenu.openCommand(c.getSource().getPlayerOrException(),c.getSource().getPlayerOrException().getUUID())?1:0)
                .then(argument("owner",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(c.getSource().getServer().getPlayerNames(),b)).executes(c->{
                    var owner=GpsWaypoints.get(c.getSource().getServer()).owner(c.getSource().getServer(),StringArgumentType.getString(c,"owner"));
                    if(owner==null)throw error(c.getSource(),"unknown_player");return GpsTeleportMenu.openCommand(c.getSource().getPlayerOrException(),owner)?1:0;
                })))
            .then(literal("versions").executes(c->{c.getSource().sendSuccess(()->Component.literal("Minecraft "+c.getSource().getServer().getServerVersion()+"; Forge "+net.minecraftforge.versions.forge.ForgeVersion.getVersion()+"; Slimefun Forge "+net.minecraftforge.fml.ModList.get().getModContainerById(SlimefunForge.ID).orElseThrow().getModInfo().getVersion()+"; ").append(Component.translatable("guide.slimefun.upstream")),false);return 1;}))
            .then(literal("backpack").requires(s->s.getEntity() instanceof ServerPlayer&&allowed(s,BACKPACK))
                .then(argument("owner",StringArgumentType.word()).then(argument("id",IntegerArgumentType.integer(0)).suggests((c,b)->{var data=BackpackData.get(c.getSource().getServer());return SharedSuggestionProvider.suggest(data.ids(data.owner(StringArgumentType.getString(c,"owner"))).stream().map(Object::toString),b);}).executes(AdminCommands::restore))))
            .then(literal("give").requires(AdminCommands::items).then(argument("player",EntityArgument.player()).then(argument("item",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(Catalog.ITEMS.stream().map(Catalog.Entry::id).filter(MachineClock::enabled),b)).executes(c->give(c,1)).then(argument("amount",IntegerArgumentType.integer(1)).executes(c->give(c,IntegerArgumentType.getInteger(c,"amount")))))))
            .then(literal("cheat").requires(s->s.getEntity() instanceof ServerPlayer&&items(s)).executes(c->{GuideMenu.openCreative(c.getSource().getPlayerOrException());return 1;}))
            .then(literal("charge").requires(s->s.getEntity() instanceof ServerPlayer&&allowed(s,CHARGE)).executes(c->{var p=c.getSource().getPlayerOrException();var stack=p.getMainHandItem();if(!(stack.getItem() instanceof Rechargeable charge))throw error(c.getSource(),"not_rechargeable");charge.setItemCharge(stack,charge.getMaxItemCharge(stack));p.inventoryMenu.broadcastChanges();success(c.getSource(),"charged");return 1;}))
            .then(literal("research").requires(s->allowed(s,RESEARCH)).then(argument("player",EntityArgument.player()).then(argument("research",StringArgumentType.greedyString()).suggests((c,b)->SharedSuggestionProvider.suggest(java.util.stream.Stream.concat(java.util.stream.Stream.of("all","reset"),ResearchData.all().stream().filter(ResearchSettings::registered).map(AddonContent::commandId)),b)).executes(AdminCommands::research))))
            .then(literal("stats").executes(c->stats(c.getSource(),c.getSource().getPlayerOrException())).then(argument("player",EntityArgument.player()).requires(s->allowed(s,STATS)).executes(c->stats(c.getSource(),EntityArgument.getPlayer(c,"player"))))));
    }
    private static CommandSyntaxException error(CommandSourceStack source,String key,Object...args){return new SimpleCommandExceptionType(Messages.format(source,Component.translatable("command.slimefun."+key,args),true)).create();}
    private static void success(CommandSourceStack source,String key,Object...args){Messages.command(source,Component.translatable("command.slimefun."+key,args),!key.equals("stats"));}
    private static int restore(CommandContext<CommandSourceStack> context)throws CommandSyntaxException{
        var source=context.getSource();var player=source.getPlayerOrException();var data=BackpackData.get(source.getServer());var owner=data.owner(StringArgumentType.getString(context,"owner"));
        if(owner==null)throw error(source,"unknown_backpack_owner");var restored=data.restore(owner,IntegerArgumentType.getInteger(context,"id"));if(restored.isEmpty())throw error(source,"unknown_backpack");
        var inventory=new net.minecraftforge.items.wrapper.PlayerMainInvWrapper(player.getInventory());
        if(!net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(inventory,restored,true).isEmpty())throw error(source,"bag_full");
        net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(inventory,restored,false);player.inventoryMenu.broadcastChanges();success(source,"restored");return 1;
    }
    private static int give(CommandContext<CommandSourceStack> context,int amount)throws CommandSyntaxException{
        var source=context.getSource();String id=StringArgumentType.getString(context,"item").toLowerCase(Locale.ROOT);
        var entry=Catalog.entry(id);if(entry==null)throw error(source,"unknown_item");var stack=Catalog.stack(SlimefunForge.item(id),amount);
        // Match the native /give bound, avoiding unbounded item-entity creation.
        if(amount>stack.getMaxStackSize()*100)throw error(source,"too_many");
        var player=EntityArgument.getPlayer(context,"player");Messages.chat(player,Component.translatable("command.slimefun.received",amount,GuideContent.name(entry.id())));
        var remainder=net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(new net.minecraftforge.items.wrapper.PlayerMainInvWrapper(player.getInventory()),stack,false);int inserted=amount-remainder.getCount();
        if(DROP_EXCESS.get())while(!remainder.isEmpty()){var dropped=remainder.split(Math.min(remainder.getCount(),remainder.getMaxStackSize()));inserted+=dropped.getCount();player.drop(dropped,false);}
        player.inventoryMenu.broadcastChanges();success(source,"given",amount,GuideContent.name(entry.id()),player.getName());return inserted;
    }
    private static int research(CommandContext<CommandSourceStack> context)throws CommandSyntaxException{
        if(!ResearchSettings.ENABLED.get())throw error(context.getSource(),"research_disabled");
        var source=context.getSource();var player=EntityArgument.getPlayer(context,"player");var value=StringArgumentType.getString(context,"research").toLowerCase(Locale.ROOT);var data=ResearchData.get(player);
        if(value.equals("reset")){GuideResearch.cancel(player);data.reset(player);Messages.chat(player,Component.translatable("command.slimefun.research_reset",player.getName()));}
        else if(value.equals("all")){
            GuideResearch.cancel(player);
            for(var research:ResearchData.all())if(ResearchSettings.registered(research)&&!data.completed(player,research)){
                success(source,"researched",player.getName(),GuideContent.localize(player,Component.translatable(AddonContent.researchKey(research))));
                GuideResearch.finish(player,research,false);
            }
        }
        else {var research=ResearchData.all().stream().filter(ResearchSettings::registered).filter(r->AddonContent.commandId(r).equals(value)).findFirst().orElseThrow(()->error(source,"unknown_research",value));GuideResearch.finish(player,research,true);}
        if(player.containerMenu instanceof GuideMenu menu)menu.refresh();return data.completed(player);
    }
    private static int stats(CommandSourceStack source,ServerPlayer player){var data=ResearchData.get(player);success(source,"stats",player.getName(),Guide.rank(player),data.completed(player),ResearchData.total(),data.spent(player));return data.completed(player);}
    private AdminCommands() {}
}

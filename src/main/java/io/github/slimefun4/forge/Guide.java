package io.github.slimefun4.forge;

import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import net.minecraftforge.server.permission.nodes.*;
import static net.minecraft.commands.Commands.*;

public final class Guide {
    public static final ForgeConfigSpec CONFIG;
    public static final ForgeConfigSpec.BooleanValue FIRST_JOIN,VANILLA,HIDDEN_SEARCH,FREE_CREATIVE,FIREWORKS,NO_ANIMATION,TRANSLATIONS;
    public static final ForgeConfigSpec.ConfigValue<String> LANGUAGE,CHAT_PREFIX;
    public static final ForgeConfigSpec.BooleanValue TALISMAN_ACTIONBAR;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> RANKS;
    private static final List<String> DEFAULT_RANKS=List.of(GuideContent.read("/slimefun/guide_ranks.json",String[].class));
    private static final PermissionNode<Boolean> GIVE=new PermissionNode<>("slimefun","command.guide",PermissionTypes.BOOLEAN,(p,id,c)->true);
    private static final PermissionNode<Boolean> OPEN=new PermissionNode<>("slimefun","command.open_guide",PermissionTypes.BOOLEAN,(p,id,c)->true);
    private static final PermissionNode<Boolean> CHEAT=new PermissionNode<>("slimefun","cheat.items",PermissionTypes.BOOLEAN,(p,id,c)->p!=null&&p.hasPermissions(2));
    private record Search(GuideMenu menu,long started) {}
    private static final Map<UUID,Search> SEARCHES=new ConcurrentHashMap<>();
    private static final Map<UUID,String> LANGUAGES=new HashMap<>();
    static {
        var builder=new ForgeConfigSpec.Builder();FIRST_JOIN=builder.define("receive-on-first-join",true);
        LANGUAGE=builder.define("language","en_us",v->v instanceof String s&&GuideContent.LOCALES.contains(s));TRANSLATIONS=builder.define("enable-translations",true);
        CHAT_PREFIX=builder.define("chat-prefix","&a&lSlimefun 4&7> ");builder.push("talismans");TALISMAN_ACTIONBAR=builder.define("use-actionbar",true);builder.pop();
        VANILLA=builder.define("show-vanilla-recipes",true);HIDDEN_SEARCH=builder.define("show-hidden-item-groups-in-search",false);
        builder.push("researches");FREE_CREATIVE=builder.define("free-in-creative-mode",true);FIREWORKS=builder.define("enable-fireworks",true);NO_ANIMATION=builder.define("disable-learning-animation",false);builder.pop();
        RANKS=builder.defineList("research-ranks",DEFAULT_RANKS,v->v instanceof String s&&!s.isBlank());CONFIG=builder.build();
    }
    static void register(IEventBus bus){
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER,CONFIG,"slimefun_forge/slimefun-guide-server.toml");MinecraftForge.EVENT_BUS.register(Guide.class);MinecraftForge.EVENT_BUS.register(GuideResearch.class);
        bus.addListener((net.minecraftforge.fml.event.config.ModConfigEvent.Reloading event)->{
            var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            if(server!=null)server.execute(()->refresh(server));
        });
    }
    private static void refresh(net.minecraft.server.MinecraftServer server){for(var player:server.getPlayerList().getPlayers()){observeLanguage(player);AddonGuides.refresh(player);}}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void recipes(net.minecraftforge.event.OnDatapackSyncEvent event){if(event.getPlayer()==null)refresh(event.getPlayerList().getServer());}
    @SubscribeEvent public static void permissions(PermissionGatherEvent.Nodes event){event.addNodes(GIVE,OPEN,CHEAT);}
    @SubscribeEvent public static void commands(RegisterCommandsEvent event){
        event.getDispatcher().register(literal("sf")
            .then(literal("guide").requires(s->s.getEntity() instanceof ServerPlayer p&&PermissionAPI.getPermission(p,GIVE)).executes(c->give(c.getSource().getPlayerOrException())))
            .then(literal("open_guide").requires(s->s.getEntity() instanceof ServerPlayer p&&PermissionAPI.getPermission(p,OPEN)).executes(c->{GuideMenu.open(c.getSource().getPlayerOrException());return 1;}))
            .then(literal("search").requires(s->s.getEntity() instanceof ServerPlayer p&&PermissionAPI.getPermission(p,OPEN)).then(argument("query",StringArgumentType.greedyString()).executes(c->{GuideMenu.search(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"query"));return 1;}))));
    }
    private static int give(ServerPlayer player){
        var book=new ItemStack(SlimefunForge.GUIDE.get());
        if(!player.getInventory().add(book)){Messages.chat(player,text(player,"bag_full"));return 0;}
        player.inventoryMenu.broadcastChanges();return 1;
    }
    @SubscribeEvent public static void joined(PlayerEvent.PlayerLoggedInEvent event){
        if(!(event.getEntity() instanceof ServerPlayer player))return;
        LANGUAGES.put(player.getUUID(),locale(player));
        var data=player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if(data.getBoolean("slimefun_joined"))return;
        data.putBoolean("slimefun_joined",true);player.getPersistentData().put(Player.PERSISTED_NBT_TAG,data);
        if(FIRST_JOIN.get())give(player);
    }
    public static void cancelSearch(ServerPlayer player){SEARCHES.remove(player.getUUID());}
    static boolean canCheat(ServerPlayer player){return PermissionAPI.getPermission(player,CHEAT);}
    static net.minecraft.nbt.CompoundTag preferences(ServerPlayer player){
        var persisted=player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);player.getPersistentData().put(Player.PERSISTED_NBT_TAG,persisted);
        var settings=persisted.getCompound("slimefun_guide");persisted.put("slimefun_guide",settings);return settings;
    }
    static boolean option(ServerPlayer player,String key){var data=preferences(player);return !data.contains(key)||data.getBoolean(key);}
    static String locale(ServerPlayer player){if(!player.server.isDedicatedServer()){String client=player.getLanguage();return GuideContent.LOCALES.contains(client)?client:"en_us";}if(!TRANSLATIONS.get())return LANGUAGE.get();String selected=preferences(player).getString("language");return GuideContent.LOCALES.contains(selected)?selected:LANGUAGE.get();}
    static void observeLanguage(ServerPlayer player){String current=locale(player),previous=LANGUAGES.put(player.getUUID(),current);if(previous!=null&&!previous.equals(current))MinecraftForge.EVENT_BUS.post(new io.github.slimefun4.forge.api.PlayerLanguageChangeEvent(player,previous,current));}
    static boolean allowed(ServerPlayer player,io.github.slimefun4.forge.api.GuideApi.Mode mode){return player.server.getPlayerList().getPlayer(player.getUUID())==player&&player.isAlive()&&ItemAccess.worldEnabled(player.level())&&(mode!=io.github.slimefun4.forge.api.GuideApi.Mode.CREATIVE||canCheat(player));}
    static net.minecraft.world.inventory.AbstractContainerMenu openPage(ServerPlayer player,io.github.slimefun4.forge.api.GuideApi.Mode mode,net.minecraft.world.MenuProvider provider){
        return openPage(player,mode,provider,()->true);
    }
    static net.minecraft.world.inventory.AbstractContainerMenu openPage(ServerPlayer player,io.github.slimefun4.forge.api.GuideApi.Mode mode,net.minecraft.world.MenuProvider provider,java.util.function.BooleanSupplier access){
        AddonGuides.check(player);if(!allowed(player,mode)||!access.getAsBoolean()){Messages.chat(player,text(player,"no_permission"));return null;}
        var event=new io.github.slimefun4.forge.api.GuideOpenEvent(player,mode,GuideSettings.heldBook(player),provider);
        if(MinecraftForge.EVENT_BUS.post(event)||!allowed(player,mode)||!access.getAsBoolean())return null;
        prepare(player);observeLanguage(player);var selected=event.getProvider();var created=new net.minecraft.world.inventory.AbstractContainerMenu[1];
        boolean opened=GuideContent.open(player,new net.minecraft.world.SimpleMenuProvider((id,inv,p)->created[0]=selected.createMenu(id,inv,p),selected.getDisplayName()));
        return opened?created[0]:null;
    }
    static Component rank(ServerPlayer player){var ranks=RANKS.get();int total=ResearchData.total();if(ranks.isEmpty())return Component.empty();String rank=ranks.get(total==0?0:Math.min(ranks.size()-1,ResearchData.get(player).completed(player)*(ranks.size()-1)/total));int index=DEFAULT_RANKS.indexOf(rank);return index<0?Component.literal(rank):Component.translatable("guide.slimefun.rank."+index);}
    static Component text(ServerPlayer player,String key,Object...args){return GuideContent.localize(player,Component.translatable("guide.slimefun."+key,args));}
    static List<Component> lines(ServerPlayer player,String key){var result=new ArrayList<Component>();var language=GuideContent.language(locale(player));for(int i=0;language.containsKey("guide.slimefun."+key+"."+i);i++)result.add(text(player,key+"."+i));return result;}
    static void prepare(ServerPlayer player){cancelSearch(player);GpsWaypoints.cancelPrompt(player);ProjectorEntity.cancelEdit(player);ElevatorMenu.cancelPrompt(player);AndroidScripts.cancelPrompt(player);}
    static void prompt(ServerPlayer player,GuideMenu menu){prepare(player);player.closeContainer();SEARCHES.put(player.getUUID(),new Search(menu,player.server.overworld().getGameTime()));Messages.chat(player,text(player,"search_prompt"));}
    @SubscribeEvent public static void chat(ServerChatEvent event){
        var player=event.getPlayer();var search=SEARCHES.get(player.getUUID());if(search==null)return;event.setCanceled(true);var query=event.getRawText();
        player.server.execute(()->{if(SEARCHES.remove(player.getUUID(),search)&&!GuideInput.expired(search.started(),player.server.overworld().getGameTime())&&player.isAlive()&&player.containerMenu==player.inventoryMenu&&query.length()<=256&&!query.equalsIgnoreCase("cancel"))GuideMenu.search(player,query,search.menu());});
    }
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.PlayerTickEvent event){
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END||!(event.player instanceof ServerPlayer player))return;
        observeLanguage(player);var search=SEARCHES.get(player.getUUID());if(search!=null&&(!player.isAlive()||GuideInput.expired(search.started(),player.server.overworld().getGameTime())))cancelSearch(player);
    }
    @SubscribeEvent public static void opened(PlayerContainerEvent.Open event){if(event.getEntity() instanceof ServerPlayer player)cancelSearch(player);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event){if(event.getEntity() instanceof ServerPlayer player)cancelSearch(player);}
    @SubscribeEvent public static void cloned(PlayerEvent.Clone event){if(event.getEntity() instanceof ServerPlayer player)cancelSearch(player);}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity() instanceof ServerPlayer player){cancelSearch(player);LANGUAGES.remove(player.getUUID());}}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){SEARCHES.clear();LANGUAGES.clear();}
    private Guide() {}
}

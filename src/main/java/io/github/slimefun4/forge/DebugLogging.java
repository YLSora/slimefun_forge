package io.github.slimefun4.forge;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Temporary, server-owned RC-37 diagnostic selection; no player data is modified. */
public final class DebugLogging {
    public static final String CARGO="slimefun_cargo_input_testing",PROFILE="slimefun_player_profile_data";
    private static final org.slf4j.Logger LOGGER=com.mojang.logging.LogUtils.getLogger();
    private static final Map<MinecraftServer,String> CASES=new WeakHashMap<>();
    public static String current(MinecraftServer server){return CASES.get(server);}
    public static boolean enabled(Level level,String test){return level!=null&&!level.isClientSide&&test.equals(current(level.getServer()));}
    public static void log(Level level,String test,String message,Object...args){if(level!=null&&level.getServer()!=null&&!level.getServer().isSameThread())throw new IllegalStateException("Debug logging requires server thread");if(enabled(level,test))LOGGER.info("[DEBUG "+test+"] "+message,args);}
    static int command(CommandSourceStack source,String test){
        if(test==null){String current=current(source.getServer());Messages.command(source,current==null?Component.translatable("command.slimefun.debug_none"):Component.translatable("command.slimefun.debug_current",current),true);}
        else if(test.equalsIgnoreCase("off")||test.equalsIgnoreCase("disable")){CASES.remove(source.getServer());Messages.command(source,Component.translatable("command.slimefun.debug_off"),true);}
        else {CASES.put(source.getServer(),test);Messages.command(source,Component.translatable("command.slimefun.debug_running",test),true);}
        return 1;
    }
    @SubscribeEvent public static void loaded(PlayerEvent.LoadFromFile event){var player=event.getEntity();if(enabled(player.level(),PROFILE))log(player.level(),PROFILE,"Loaded player {}",player.getUUID());}
    @SubscribeEvent public static void saved(PlayerEvent.SaveToFile event){var player=event.getEntity();if(enabled(player.level(),PROFILE))log(player.level(),PROFILE,"Saved player {}",player.getUUID());}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){CASES.remove(event.getServer());}
    private DebugLogging(){}
}

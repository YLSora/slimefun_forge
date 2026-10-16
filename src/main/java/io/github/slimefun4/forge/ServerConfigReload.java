package io.github.slimefun4.forge;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.file.FileWatcher;
import com.electronwill.nightconfig.toml.TomlFormat;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/** Own only this mod's server watches; NightConfig 3.6.4 discards coalesced events. */
final class ServerConfigReload implements AutoCloseable {
    private static final org.slf4j.Logger LOGGER=com.mojang.logging.LogUtils.getLogger();
    private static final Map<MinecraftServer,ServerConfigReload> ACTIVE=new IdentityHashMap<>();
    private final MinecraftServer server;
    private final IEventBus bus;
    private final WatchService watcher;
    private final Map<Path,ModConfig> configs=new HashMap<>();
    private volatile boolean closed;

    static void register(IEventBus bus) {
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event)-> {
            try { ACTIVE.put(event.getServer(),new ServerConfigReload(event.getServer(),bus)); }
            catch(IOException e) { throw new java.io.UncheckedIOException("Cannot watch Slimefun server configs",e); }
        });
        MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent event)-> {
            var running=ACTIVE.remove(event.getServer());
            if(running!=null)running.close();
        });
    }

    private ServerConfigReload(MinecraftServer server,IEventBus bus) throws IOException {
        this.server=server;this.bus=bus;watcher=FileSystems.getDefault().newWatchService();
        try {
            var directories=new HashSet<Path>();
            for(var config:ConfigTracker.INSTANCE.configSets().get(ModConfig.Type.SERVER)) {
                if(!config.getModId().equals(SlimefunForge.ID)||!(config.getConfigData() instanceof CommentedFileConfig file))continue;
                var path=file.getNioPath().toAbsolutePath().normalize();configs.put(path,config);
                if(directories.add(path.getParent()))path.getParent().register(watcher,StandardWatchEventKinds.ENTRY_MODIFY,StandardWatchEventKinds.ENTRY_CREATE);
                // removeWatch cancels the shared directory key, including other mods' configs.
                FileWatcher.defaultInstance().setWatch(file.getNioPath(),()->{});
            }
        } catch(IOException|RuntimeException e) { watcher.close();throw e; }
        var thread=new Thread(this::watch,"Slimefun server config watcher");thread.setDaemon(true);thread.start();
    }

    private void collect(WatchKey key,Set<ModConfig> changed) {
        for(var event:key.pollEvents()) {
            if(event.kind()==StandardWatchEventKinds.OVERFLOW)changed.addAll(configs.values());
            else {
                var config=configs.get(((Path)key.watchable()).resolve((Path)event.context()));
                if(config!=null)changed.add(config);
            }
        }
        if(!key.reset()&&!closed)LOGGER.warn("Slimefun config directory is no longer available: {}",key.watchable());
    }

    private void watch() {
        try {
            while(!closed) {
                var changed=new HashSet<ModConfig>();collect(watcher.take(),changed);
                long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(1);
                for(WatchKey key;System.nanoTime()<end&&(key=watcher.poll(100,TimeUnit.MILLISECONDS))!=null;)collect(key,changed);
                for(var config:changed)server.execute(()->reload(config));
            }
        } catch(ClosedWatchServiceException ignored) {
        } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private void reload(ModConfig config) {
        if(closed)return;
        var file=(CommentedFileConfig)config.getConfigData();
        try {
            // Apply one parsed snapshot; a second file read can catch a half-written editor save.
            var parsed=TomlFormat.instance().createParser().parse(Files.readString(file.getNioPath()));
            if(parsed.valueMap().equals(file.valueMap()))return;
            if(config.getSpec()==AddonSettings.CONFIG)AddonSettings.validate(parsed);
            int corrected=config.getSpec().correct(parsed);
            if(!parsed.valueMap().keySet().equals(file.valueMap().keySet()))throw new IllegalStateException("Loaded config keys differ from its spec");
            // Entry updates retain Forge's file object without autosaving incomplete tables.
            for(var entry:file.entrySet()) {
                entry.setValue(parsed.get(entry.getKey()));
                entry.setComment(parsed.getComment(entry.getKey()));
            }
            if(corrected>0)file.save();
        } catch(IOException|RuntimeException e) {
            LOGGER.warn("Slimefun config reload rejected for {}: {}",config.getFileName(),e.toString());
            return;
        }
        config.getSpec().afterReload();
        if(config.getSpec()==AddonSettings.CONFIG)AddonSettings.reloaded(server,file);
        bus.post(new ModConfigEvent.Reloading(config));
    }

    @Override public void close() {
        closed=true;
        try { watcher.close(); } catch(IOException e) { LOGGER.warn("Cannot close Slimefun config watcher",e); }
    }
}

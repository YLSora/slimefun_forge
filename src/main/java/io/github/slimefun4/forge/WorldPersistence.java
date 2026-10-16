package io.github.slimefun4.forge;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/** Native checkpoints and world-data backups after normal shutdown, including unloaded machines. */
public final class WorldPersistence {
    public static final ForgeConfigSpec CONFIG;
    public static final ForgeConfigSpec.IntValue MINUTES;
    public static final ForgeConfigSpec.BooleanValue BACKUP;
    private static final org.slf4j.Logger LOGGER=com.mojang.logging.LogUtils.getLogger();
    private static final Map<MinecraftServer,State> STATES=new IdentityHashMap<>();
    private static final Set<String> DATA_ROOTS=Set.of("level.dat","level.dat_old","playerdata","advancements","stats","data","region","entities","poi","DIM-1","DIM1","dimensions","serverconfig","datapacks","generated","resources.zip","icon.png");
    private static final class State {final Path world;long saved;boolean backup;State(MinecraftServer server){world=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();saved=server.overworld().getGameTime();}}
    static{var builder=new ForgeConfigSpec.Builder();MINUTES=builder.comment("Minutes between additional native world checkpoints. Vanilla saves remain controlled by Minecraft.").defineInRange("auto-save-delay-in-minutes",10,1,1440);BACKUP=builder.comment("Opt in to synchronously archive the entire native world after shutdown; this delays leaving the world. Retains 20 completed backups. Normal world saving does not require this.").define("backup-data",false);CONFIG=builder.build();}
    static void register(){ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER,CONFIG,"slimefun_forge/slimefun-storage-server.toml");MinecraftForge.EVENT_BUS.register(WorldPersistence.class);}
    public static long lastCheckpoint(MinecraftServer server){return STATES.computeIfAbsent(server,State::new).saved;}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){if(event.phase!=TickEvent.Phase.END)return;var server=event.getServer();var state=STATES.computeIfAbsent(server,State::new);long now=server.overworld().getGameTime();if(now<state.saved)state.saved=now;if(now-state.saved<MINUTES.get()*1200L)return;server.saveEverything(true,false,false);state.saved=now;}
    @SubscribeEvent public static void stopping(ServerStoppingEvent event){STATES.computeIfAbsent(event.getServer(),State::new).backup=BACKUP.get();}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){var state=STATES.remove(event.getServer());if(state==null||!state.backup)return;try{backup(state.world);}catch(IOException e){LOGGER.warn("Slimefun native world backup failed; original world files were not changed: {}",e.toString());}}
    private static void backup(Path world)throws IOException{
        Path folder=world.resolve("slimefun-backups");Files.createDirectories(folder);Path temporary=Files.createTempFile(folder,"pending-",".zip.tmp");
        try{
            try(var output=new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)));var roots=Files.list(world)){
                // Slimefun persists in native world data; unrelated client caches may still be open after server shutdown.
                for(var root:roots.filter(p->DATA_ROOTS.contains(p.getFileName().toString())&&!Files.isSymbolicLink(p)).sorted().toList())try(var files=Files.walk(root)){
                    for(var file:files.filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)).sorted().toList()){
                        String name=world.relativize(file).toString().replace('\\','/');output.putNextEntry(new ZipEntry(name));Files.copy(file,output);output.closeEntry();
                    }
                }
            }
            String name=String.format(Locale.ROOT,"%019d-%s.zip",System.currentTimeMillis(),UUID.randomUUID());Path target=folder.resolve(name);Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE);
            try(var files=Files.list(folder)){var backups=files.filter(p->p.getFileName().toString().matches("[0-9]{19}-[0-9a-f-]{36}\\.zip")).sorted().toList();for(int i=0;i<backups.size()-20;i++)Files.delete(backups.get(i));}
            LOGGER.info("Slimefun native world backup completed: {}",target.getFileName());
        }finally{Files.deleteIfExists(temporary);}
    }
    private WorldPersistence() {}
}

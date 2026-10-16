package io.github.slimefun4.forge;

import java.io.*;
import java.nio.file.*;
import java.util.function.*;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;

/** Native SavedData loading and atomic writes; an unreadable existing file never becomes empty writable data. */
final class NativeData {
    private static final java.util.Map<MinecraftServer,java.util.Set<String>> VERIFIED=new java.util.WeakHashMap<>();
    static <T extends SavedData> T get(MinecraftServer server,String id,Function<CompoundTag,T> load,Supplier<T> create){
        if(!server.isSameThread())throw new IllegalStateException("Player storage requires server thread");
        var verified=VERIFIED.computeIfAbsent(server,s->new java.util.HashSet<>());var file=server.getWorldPath(LevelResource.ROOT).resolve("data/"+id+".dat");
        if(!verified.contains(id)&&!Files.notExists(file))try{var root=NbtIo.readCompressed(file.toFile());if(root==null||!root.contains("data",Tag.TAG_COMPOUND)||!root.contains("DataVersion",Tag.TAG_INT))throw new IllegalStateException("Invalid native data envelope: "+file);}catch(IOException error){throw new UncheckedIOException("Cannot read Slimefun data: "+file,error);}
        var data=server.overworld().getDataStorage().computeIfAbsent(load,()->{if(Files.notExists(file))return create.get();try{return load.apply(NbtIo.readCompressed(file.toFile()).getCompound("data"));}catch(IOException error){throw new UncheckedIOException("Cannot load Slimefun data: "+file,error);}catch(RuntimeException error){throw new IllegalStateException("Invalid Slimefun data: "+file,error);}},id);
        verified.add(id);return data;
    }
    static void save(SavedData data,File file){
        if(!data.isDirty())return;Path target=file.toPath().toAbsolutePath(),temporary=null;RuntimeException failure=null;
        try{Files.createDirectories(target.getParent());temporary=Files.createTempFile(target.getParent(),target.getFileName()+".",".tmp");
            var root=new CompoundTag();root.put("data",data.save(new CompoundTag()));root.putInt("DataVersion",SharedConstants.getCurrentVersion().getDataVersion().getVersion());NbtIo.writeCompressed(root,temporary.toFile());
            Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);data.setDirty(false);
        }catch(IOException error){failure=new UncheckedIOException("Cannot atomically save "+target,error);throw failure;}catch(RuntimeException error){failure=error;throw error;}finally{if(temporary!=null)try{Files.deleteIfExists(temporary);}catch(IOException error){if(failure!=null)failure.addSuppressed(error);else throw new UncheckedIOException("Cannot remove incomplete data write "+temporary,error);}}
    }
    private NativeData(){}
}

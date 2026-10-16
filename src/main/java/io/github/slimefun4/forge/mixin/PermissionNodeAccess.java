package io.github.slimefun4.forge.mixin;

import net.minecraftforge.server.permission.nodes.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Forge's public constructors prepend a namespace; RC-37 permits exact arbitrary names. */
@Mixin(value=PermissionNode.class,remap=false)
public interface PermissionNodeAccess {
    @Invoker("<init>")
    static <T> PermissionNode<T> create(String name,PermissionType<T> type,PermissionNode.PermissionResolver<T> resolver,PermissionDynamicContextKey<?>... context){throw new AssertionError();}
}

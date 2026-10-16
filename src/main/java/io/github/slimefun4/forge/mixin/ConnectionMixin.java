package io.github.slimefun4.forge.mixin;

import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keep protocol changes and Netty read-interest changes on the same event loop. */
@Mixin(Connection.class)
abstract class ConnectionMixin {
    @Redirect(method = "sendPacket", at = @At(value = "INVOKE",
            target = "Lio/netty/channel/ChannelConfig;setAutoRead(Z)Lio/netty/channel/ChannelConfig;", remap = false))
    private io.netty.channel.ChannelConfig slimefun$serializeReadInterest(io.netty.channel.ChannelConfig config, boolean autoRead) {
        var loop = ((Connection) (Object) this).channel().eventLoop();
        // Keep send-side login bookkeeping synchronous; queue only read-interest changes.
        if (loop.inEventLoop()) config.setAutoRead(autoRead);
        else loop.execute(() -> config.setAutoRead(autoRead));
        return config;
    }
}

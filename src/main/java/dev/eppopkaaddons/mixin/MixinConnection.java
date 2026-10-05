package dev.eppopkaaddons.mixin;

import dev.eppopkaaddons.event.EventBus;
import dev.eppopkaaddons.event.impl.PacketEvent;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class MixinConnection {
    @Inject(method = "sendPacket", at = @At("HEAD"), cancellable = true)
    private void eppopkaaddons$sent(Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        if (EventBus.post(new PacketEvent.Sent(packet))) ci.cancel();
    }

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;genericsFtw(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;)V"), cancellable = true)
    private void eppopkaaddons$received(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
        if (EventBus.post(new PacketEvent.Received(packet))) ci.cancel();
    }
}

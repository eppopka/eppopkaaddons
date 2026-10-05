package dev.eppopkaaddons.mixin;

import dev.eppopkaaddons.event.EventBus;
import dev.eppopkaaddons.event.impl.MainThreadPacketReceivedEvent;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientPacketListener.class)
public class MixinClientPacketListener {
    @WrapOperation(method = "handleBundlePacket", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V"))
    private void eppopkaaddons$bundle(Packet<?> packet, PacketListener listener, Operation<Void> original) {
        if (EventBus.post(new MainThreadPacketReceivedEvent.Pre(packet))) return;
        original.call(packet, listener);
        EventBus.post(new MainThreadPacketReceivedEvent.Post(packet));
    }
}

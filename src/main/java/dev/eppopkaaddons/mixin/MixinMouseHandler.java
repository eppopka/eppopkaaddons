package dev.eppopkaaddons.mixin;

import dev.eppopkaaddons.event.EventBus;
import dev.eppopkaaddons.event.impl.MouseClickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MixinMouseHandler {
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void eppopkaaddons$mouse(long handle, MouseButtonInfo button, int action, CallbackInfo ci) {
        if (handle != minecraft.getWindow().handle()) return;
        if (EventBus.post(new MouseClickEvent(button.button(), action, button.modifiers()))) ci.cancel();
    }
}

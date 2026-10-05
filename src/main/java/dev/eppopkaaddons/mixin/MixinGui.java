package dev.eppopkaaddons.mixin;

import dev.eppopkaaddons.event.EventBus;
import dev.eppopkaaddons.event.impl.RenderOverlayEvent;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class MixinGui {
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;extractSleepOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"))
    private void eppopkaaddons$overlay(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        if (!minecraft.options.hideGui && !minecraft.debugEntries.isOverlayVisible()) {
            EventBus.post(new RenderOverlayEvent(graphics));
        }
    }
}

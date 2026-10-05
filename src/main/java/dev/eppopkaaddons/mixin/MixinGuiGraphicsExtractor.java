package dev.eppopkaaddons.mixin;

import dev.eppopkaaddons.features.impl.visual.Cosmetics;
import dev.eppopkaaddons.features.impl.visual.cosmetics.text.TextReplacer;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;

import java.util.List;

@Mixin(value = GuiGraphicsExtractor.class)
public abstract class MixinGuiGraphicsExtractor {
    @WrapMethod(method = "tooltip")
    private void onRenderTooltipPre(Font font, List<ClientTooltipComponent> lines, int xo, int yo, ClientTooltipPositioner positioner, @org.jspecify.annotations.Nullable Identifier style, Operation<Void> original) {
        if (Cosmetics.INSTANCE.enabled && Cosmetics.getCustomNames().getValue() && ! Cosmetics.getLoreNames().getValue()) TextReplacer.drawingTooltip = true;
        try {
            original.call(font, lines, xo, yo, positioner, style);
        } finally {
            TextReplacer.drawingTooltip = false;
        }
    }
}
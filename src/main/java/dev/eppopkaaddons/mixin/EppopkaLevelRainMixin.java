package dev.eppopkaaddons.mixin;

import dev.eppopkaaddons.features.impl.visual.AlwaysWater;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class EppopkaLevelRainMixin {
    @Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true)
    private void eppopkaaddons$forceRain(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (AlwaysWater.INSTANCE.enabled) cir.setReturnValue(1.0f);
    }
}
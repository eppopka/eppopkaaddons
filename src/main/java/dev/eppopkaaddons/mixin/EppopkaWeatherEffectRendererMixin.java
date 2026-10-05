package dev.eppopkaaddons.mixin;

import dev.eppopkaaddons.features.impl.visual.AlwaysWater;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WeatherEffectRenderer.class)
public abstract class EppopkaWeatherEffectRendererMixin {
    @ModifyExpressionValue(
        method = "extractRenderState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getHeight(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I")
    )
    private int eppopkaaddons$ignoreRoofs(int original, Level level, int ticks, float partialTick, Vec3 camera, WeatherRenderState state) {
        return AlwaysWater.INSTANCE.enabled ? (int) Math.floor(camera.y()) - state.radius : original;
    }

    @Inject(method = "getPrecipitationAt", at = @At("HEAD"), cancellable = true)
    private void eppopkaaddons$forceRainBiome(Level level, BlockPos pos, CallbackInfoReturnable<Biome.Precipitation> cir) {
        if (AlwaysWater.INSTANCE.enabled) cir.setReturnValue(Biome.Precipitation.RAIN);
    }
}
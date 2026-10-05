package dev.eppopkaaddons.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.eppopkaaddons.ui.clickgui.foundation.SpatialCameraController;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Isolated /ea camera hook. It intentionally contains none of NoammAddons'
 * camera-distance/FOV redirects, so both mods can be installed together.
 */
@Mixin(value = Camera.class, priority = 900)
public abstract class MixinSpatialCamera {
    @WrapOperation(
        method = "alignWithEntity",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setPosition(DDD)V")
    )
    private void eppopkaaddons$spatialPosition(
        Camera instance,
        double x,
        double y,
        double z,
        Operation<Void> original
    ) {
        double[] position = SpatialCameraController.interpolatePosition(x, y, z);
        original.call(instance, position[0], position[1], position[2]);
    }

    @WrapOperation(
        method = "alignWithEntity",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V"),
        require = 0
    )
    private void eppopkaaddons$spatialRotation(
        Camera instance,
        float yaw,
        float pitch,
        Operation<Void> original
    ) {
        float[] rotation = SpatialCameraController.interpolateRotation(yaw, pitch);
        original.call(instance, rotation[0], rotation[1]);
    }
}

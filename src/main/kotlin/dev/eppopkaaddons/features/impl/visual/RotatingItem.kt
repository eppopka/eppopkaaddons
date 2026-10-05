package dev.eppopkaaddons.features.impl.visual

import dev.eppopkaaddons.features.Feature
import dev.eppopkaaddons.config.types.DropdownSetting
import dev.eppopkaaddons.config.types.SliderSetting
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import kotlin.math.sin

object RotatingItem: Feature("Smoothly rotates the held item model itself (chaotic, clockwise, counterclockwise and more).", name = "Rotating Item") {
    private val mode by DropdownSetting("Mode", 0, listOf("Chaotic", "Clockwise", "Counterclockwise", "Spin", "Wave"))
        .withDescription("Movement pattern of the held item model.")
    private val speed by SliderSetting("Speed", 50, 5, 200, 5)
        .withDescription("Animation speed in percent.")
    private val amplitude by SliderSetting("Amplitude", 40, 5, 100, 5)
        .withDescription("How strongly the model tilts/moves (Chaotic and Wave).")
    private val offsetX by SliderSetting("Offset X", 0f, -0.5f, 0.5f, 0.01f)
        .withDescription("Moves the live first-person item transform horizontally.")
    private val offsetY by SliderSetting("Offset Y", 0f, -0.5f, 0.5f, 0.01f)
        .withDescription("Moves the live first-person item transform vertically.")
    private val offsetZ by SliderSetting("Offset Z", 0f, -0.5f, 0.5f, 0.01f)
        .withDescription("Moves the live first-person item transform in depth.")

    /**
     * Called from ItemInHandRendererMixin at the TAIL of applyItemArmTransform:
     * at that point the PoseStack origin sits exactly at the held item's grip,
     * so every rotation here spins the item MODEL in place instead of swinging
     * the whole arm around the screen.
     */
    @JvmStatic
    fun applyTransform(poseStack: PoseStack, leftHand: Boolean) {
        if (! enabled) return
        val t = System.nanoTime() / 1_000_000_000.0 * (speed.value / 50.0)
        val amp = amplitude.value / 100.0
        val deg = ((t * 90.0) % 360.0).toFloat()
        poseStack.translate(
            (offsetX.value * if (leftHand) -1f else 1f).toDouble(),
            offsetY.value.toDouble(),
            offsetZ.value.toDouble(),
        )

        when (mode.value) {
            0 -> { // Chaotic: the model tumbles unpredictably in place
                poseStack.mulPose(Axis.XP.rotationDegrees((sin(t * 2.1) * 35.0 * amp).toFloat()))
                poseStack.mulPose(Axis.YP.rotationDegrees((sin(t * 1.6 + 1.5) * 45.0 * amp).toFloat()))
                poseStack.mulPose(Axis.ZP.rotationDegrees((sin(t * 2.6 + 3.0) * 35.0 * amp).toFloat()))
            }
            1 -> poseStack.mulPose(Axis.ZP.rotationDegrees(- deg)) // Clockwise roll on screen
            2 -> poseStack.mulPose(Axis.ZP.rotationDegrees(deg))  // Counterclockwise roll
            3 -> poseStack.mulPose(Axis.YP.rotationDegrees(deg * 2f)) // Spin: carousel around vertical axis
            4 -> { // Wave: gentle bob + tilt of the model
                poseStack.translate(0.0, sin(t * 2.0) * 0.05 * amp, 0.0)
                poseStack.mulPose(Axis.ZP.rotationDegrees((sin(t * 2.0 + 0.8) * 15.0 * amp).toFloat()))
            }
        }
    }
}

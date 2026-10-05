package dev.eppopkaaddons.ui.clickgui.foundation

import dev.eppopkaaddons.EppopkaAddons.mc
import net.minecraft.client.CameraType
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/** Framerate-independent orbit camera for the /ea BIOS and Visual editor. */
object SpatialCameraController {
    private enum class Mode { MENU, VISUAL, VANILLA }
    private data class CameraPose(val position: Vec3, val yaw: Float, val pitch: Float)

    private var mode = Mode.VANILLA
    private var originalType: CameraType? = null
    private var from = CameraPose(Vec3.ZERO, 0f, 0f)
    private var transitionStarted = 0L
    private var transitionSeconds = 1.0
    private var overriding = false
    private var returning = false
    private var orbiting = false
    private var orbitYaw = 0.0
    private var orbitPitch = 0.0
    private var orbitYawTarget = 0.0
    private var orbitPitchTarget = 0.0
    private var orbitUpdatedAt = System.nanoTime()

    @JvmStatic
    var changingVanillaType = false
        private set

    @JvmStatic
    fun isFrontOverrideActive() = changingVanillaType

    fun openMenu() = moveTo(Mode.MENU, 1.05)
    fun focusVisual() = moveTo(Mode.VISUAL, 1.12)

    /**
     * The ItemAura editor deliberately uses the real vanilla first-person pass.
     * That pass is the preview: it contains the player's actual arm, resolved
     * held-item model and the same depth-tested aura that gameplay renders.
     */
    fun useLiveFirstPersonPreview() {
        if (originalType == null) originalType = mc.options.cameraType
        orbiting = false
        overriding = false
        returning = false
        mode = Mode.VANILLA
        setVanillaType(CameraType.FIRST_PERSON)
    }

    fun beginOrbit() {
        if (mode == Mode.MENU && overriding) orbiting = true
    }

    fun orbitBy(deltaX: Double, deltaY: Double) {
        if (!orbiting || mode != Mode.MENU) return
        orbitYawTarget = wrapRadians(orbitYawTarget - deltaX * .0105)
        orbitPitchTarget = (orbitPitchTarget + deltaY * .008).coerceIn(-.48, .42)
    }

    fun endOrbit() {
        orbiting = false
    }

    fun restore() {
        orbiting = false
        if (!overriding) {
            originalType?.let(::setVanillaType)
            originalType = null
            return
        }
        if (returning) return
        captureCurrent()
        mode = Mode.VANILLA
        returning = true
        transitionStarted = System.nanoTime()
        transitionSeconds = .82
        originalType?.let(::setVanillaType)
    }

    /** Screen teardown must never leave a camera override waiting on another frame. */
    fun forceRestore() {
        orbiting = false
        overriding = false
        returning = false
        mode = Mode.VANILLA
        originalType?.let(::setVanillaType)
        originalType = null
    }

    private fun moveTo(next: Mode, seconds: Double) {
        if (mc.player == null) return
        if (overriding && !returning && mode == next) return
        if (originalType == null) originalType = mc.options.cameraType
        captureCurrent()
        mode = next
        returning = false
        overriding = true
        transitionStarted = System.nanoTime()
        transitionSeconds = seconds
        orbitUpdatedAt = System.nanoTime()
        // The camera type only opens the third-person render path. MixinCamera owns the
        // rendered pose from this frame, so the raw F5 switch is never visible.
        setVanillaType(CameraType.THIRD_PERSON_BACK)
    }

    private fun captureCurrent() {
        val camera = mc.gameRenderer.mainCamera
        from = CameraPose(camera.position(), camera.yRot(), camera.xRot())
    }

    private fun setVanillaType(type: CameraType) {
        changingVanillaType = true
        try {
            mc.options.cameraType = type
        } finally {
            changingVanillaType = false
        }
    }

    @JvmStatic
    fun interpolatePosition(vanillaX: Double, vanillaY: Double, vanillaZ: Double): DoubleArray {
        if (!overriding) return doubleArrayOf(vanillaX, vanillaY, vanillaZ)
        val amount = easedProgress()
        val destination = if (mode == Mode.VANILLA) {
            Vec3(vanillaX, vanillaY, vanillaZ)
        } else {
            targetPose(mode).position
        }
        val position = if (mode == Mode.VANILLA) {
            lerp(from.position, destination, amount)
        } else {
            orbit(from.position, destination, amount)
        }
        if (returning && linearProgress() >= 1.0) completeReturn()
        return doubleArrayOf(position.x, position.y, position.z)
    }

    @JvmStatic
    fun interpolateRotation(vanillaYaw: Float, vanillaPitch: Float): FloatArray {
        if (!overriding) return floatArrayOf(vanillaYaw, vanillaPitch)
        val destination = if (mode == Mode.VANILLA) {
            CameraPose(Vec3.ZERO, vanillaYaw, vanillaPitch)
        } else {
            targetPose(mode)
        }
        val amount = easedProgress().toFloat()
        val yaw = from.yaw + wrapDegrees(destination.yaw - from.yaw) * amount
        val pitch = from.pitch + (destination.pitch - from.pitch) * amount
        return floatArrayOf(yaw, pitch)
    }

    private fun orbit(start: Vec3, destination: Vec3, amount: Double): Vec3 {
        val focus = playerFocus() ?: return lerp(start, destination, amount)
        val a = start.subtract(focus)
        val b = destination.subtract(focus)
        val startRadius = sqrt(a.x * a.x + a.z * a.z)
        val endRadius = sqrt(b.x * b.x + b.z * b.z)
        if (startRadius < .28 || endRadius < .28) return lerp(start, destination, amount)

        val startAngle = atan2(a.z, a.x)
        val angleDelta = wrapRadians(atan2(b.z, b.x) - startAngle)
        val angle = startAngle + angleDelta * amount
        val radius = startRadius + (endRadius - startRadius) * amount
        val lift = sin(PI * amount) * .16
        return Vec3(
            focus.x + cos(angle) * radius,
            start.y + (destination.y - start.y) * amount + lift,
            focus.z + sin(angle) * radius,
        )
    }

    private fun targetPose(targetMode: Mode): CameraPose {
        val player = mc.player ?: return from
        val focus = playerFocus() ?: return from
        val bodyYaw = Math.toRadians(player.yRot.toDouble())
        val forward = Vec3(-sin(bodyYaw), 0.0, cos(bodyYaw))
        val right = Vec3(cos(bodyYaw), 0.0, sin(bodyYaw))

        val position: Vec3
        val lookAt: Vec3
        if (targetMode == Mode.VISUAL) {
            // Front-left hero angle: the face/cosmetics are visible and the right side
            // remains available for the module panel.
            position = focus.add(forward.scale(3.05)).subtract(right.scale(1.15)).add(0.0, .18, 0.0)
            lookAt = focus.subtract(right.scale(.72)).add(0.0, .03, 0.0)
        } else {
            // Rear-right BIOS overview. Selecting Visual performs a real orbit around
            // the avatar instead of a short linear nudge.
            position = focus.subtract(forward.scale(3.45)).add(right.scale(.95)).add(0.0, .40, 0.0)
            lookAt = focus.add(right.scale(.42)).add(0.0, .09, 0.0)
        }

        val orbitPosition = if (targetMode == Mode.MENU) applyUserOrbit(position, focus) else position
        val delta = lookAt.subtract(orbitPosition)
        val horizontal = sqrt(delta.x * delta.x + delta.z * delta.z)
        val yaw = Math.toDegrees(atan2(-delta.x, delta.z)).toFloat()
        val pitch = Math.toDegrees(-atan2(delta.y, horizontal)).toFloat()
        return CameraPose(orbitPosition, yaw, pitch)
    }

    private fun applyUserOrbit(position: Vec3, focus: Vec3): Vec3 {
        val now = System.nanoTime()
        val dt = ((now - orbitUpdatedAt) / 1_000_000_000.0).coerceIn(0.0, .05)
        orbitUpdatedAt = now
        val follow = 1.0 - exp(-dt * if (orbiting) 18.0 else 10.0)
        orbitYaw += wrapRadians(orbitYawTarget - orbitYaw) * follow
        orbitPitch += (orbitPitchTarget - orbitPitch) * follow

        val relative = position.subtract(focus)
        val radiusXZ = sqrt(relative.x * relative.x + relative.z * relative.z)
        val baseYaw = atan2(relative.z, relative.x)
        val radius = sqrt(radiusXZ * radiusXZ + relative.y * relative.y)
        val basePitch = atan2(relative.y, radiusXZ)
        val yaw = baseYaw + orbitYaw
        val pitch = (basePitch + orbitPitch).coerceIn(-.72, .72)
        val horizontal = cos(pitch) * radius
        return Vec3(
            focus.x + cos(yaw) * horizontal,
            focus.y + sin(pitch) * radius,
            focus.z + sin(yaw) * horizontal,
        )
    }

    private fun playerFocus(): Vec3? = mc.player?.let { it.position().add(0.0, it.bbHeight * .61, 0.0) }

    private fun completeReturn() {
        overriding = false
        returning = false
        mode = Mode.VANILLA
        originalType = null
    }

    /** Quintic smootherstep: exact endpoints with zero velocity and acceleration. */
    private fun easedProgress(): Double {
        val t = linearProgress()
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0)
    }

    private fun linearProgress() =
        ((System.nanoTime() - transitionStarted) / 1_000_000_000.0 / transitionSeconds).coerceIn(0.0, 1.0)

    private fun lerp(a: Vec3, b: Vec3, amount: Double) = Vec3(
        a.x + (b.x - a.x) * amount,
        a.y + (b.y - a.y) * amount,
        a.z + (b.z - a.z) * amount,
    )

    private fun wrapDegrees(value: Float): Float {
        var result = value % 360f
        if (result >= 180f) result -= 360f
        if (result < -180f) result += 360f
        return result
    }

    private fun wrapRadians(value: Double): Double {
        var result = value % (PI * 2.0)
        if (result > PI) result -= PI * 2.0
        if (result < -PI) result += PI * 2.0
        return result
    }
}

package dev.eppopkaaddons.ui.clickgui.foundation

import dev.eppopkaaddons.EppopkaAddons.mc
import dev.eppopkaaddons.ui.clickgui.enums.SpatialCategory
import dev.eppopkaaddons.ui.utils.Resolution
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** Camera projection and stable world anchors used by the /ea holographic interface. */
object WorldSpaceRenderHandler {
    data class Projected(val x: Float, val y: Float, val depth: Double, val scale: Float)

    private fun playerBasis(): Triple<Vec3, Vec3, Vec3>? {
        val player = mc.player ?: return null
        val yaw = Math.toRadians(player.yRot.toDouble())
        val forward = Vec3(-sin(yaw), 0.0, cos(yaw))
        val right = Vec3(cos(yaw), 0.0, sin(yaw))
        val origin = player.position().add(0.0, player.bbHeight * .61, 0.0)
        return Triple(origin, right, forward)
    }

    /** Player-relative anchor used by the Visual character-editor layout. */
    fun anchor(rightOffset: Double, upOffset: Double, forwardOffset: Double, bobPhase: Double = 0.0): Vec3? {
        val (origin, right, forward) = playerBasis() ?: return null
        val bob = sin(FrameClock.now / 1_000_000_000.0 * 1.25 + bobPhase) * .045
        return origin.add(right.scale(rightOffset)).add(0.0, upOffset + bob, 0.0).add(forward.scale(forwardOffset))
    }

    fun project(world: Vec3): Projected? {
        val camera = mc.gameRenderer.mainCamera
        val relative = world.subtract(camera.position())
        val yaw = Math.toRadians(camera.yRot().toDouble())
        val pitch = Math.toRadians(camera.xRot().toDouble())
        val forward = Vec3(-sin(yaw) * cos(pitch), -sin(pitch), cos(yaw) * cos(pitch))
        val right = Vec3(cos(yaw), 0.0, sin(yaw))
        val up = forward.cross(right)
        val depth = relative.dot(forward)
        if (depth <= .12) return null
        val cameraX = relative.dot(right)
        val cameraY = relative.dot(up)
        val fov = Math.toRadians(mc.options.fov().get().toDouble().coerceIn(35.0, 110.0))
        val focal = Resolution.height * .5 / tan(fov * .5)
        val x = Resolution.width * .5 + cameraX / depth * focal
        val y = Resolution.height * .5 - cameraY / depth * focal
        val scale = (3.65 / depth).toFloat().coerceIn(.78f, 1.22f)
        return Projected(x.toFloat(), y.toFloat(), depth, scale)
    }

    fun hubCenter(upOffset: Double = 0.0, bobPhase: Double = 0.0): Projected? =
        anchor(0.0, upOffset, 0.0, bobPhase)?.let(::project)

    fun hubCard(category: SpatialCategory, index: Int): Rect? {
        val angle = -PI / 2.0 + index * PI * 2.0 / SpatialCategory.entries.size
        val point = anchor(
            rightOffset = cos(angle) * 2.70,
            upOffset = sin(angle) * 1.35,
            forwardOffset = sin(angle * 1.65 + index * .37) * .25,
            bobPhase = index * 1.31,
        ) ?: return null
        val p = project(point) ?: return null
        val compactScale = p.scale.coerceIn(.72f, 1.15f)
        val w = 150f * compactScale
        val h = 64f * compactScale
        return Rect(p.x - w * .5f, p.y - h * .5f, w, h)
    }

    /** Player-left in world projects to the open right side with the cinematic orbit. */
    fun controlPanel(): Rect {
        val point = anchor(-2.45, .08, -.12, .4) ?: return fallbackPanel()
        val p = project(point) ?: return fallbackPanel()
        val scale = p.scale.coerceIn(.9f, 1.08f)
        val width = 378f * scale
        val height = 478f.coerceAtMost(Resolution.height - 34f) * scale.coerceAtMost(1f)
        val centerX = p.x.coerceIn(Resolution.width * .67f, Resolution.width - width * .5f - 12f)
        val centerY = p.y.coerceIn(height * .5f + 12f, Resolution.height - height * .5f - 12f)
        return Rect(centerX - width * .5f, centerY - height * .5f, width, height)
    }

    private fun fallbackPanel() = Rect(Resolution.width - 394f, 18f, 376f, Resolution.height - 36f)
}

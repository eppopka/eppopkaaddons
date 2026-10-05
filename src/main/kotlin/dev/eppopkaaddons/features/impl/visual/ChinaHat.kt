package dev.eppopkaaddons.features.impl.visual

import dev.eppopkaaddons.config.types.ColorSetting
import dev.eppopkaaddons.config.types.SliderSetting
import dev.eppopkaaddons.config.types.ToggleSetting
import dev.eppopkaaddons.event.impl.RenderWorldEvent
import dev.eppopkaaddons.features.Feature
import dev.eppopkaaddons.utils.render.world.RenderBatcher
import net.minecraft.world.phys.Vec3
import java.awt.Color
import kotlin.math.cos
import kotlin.math.sin

object ChinaHat: Feature("Renders a china hat on top of your head.", name = "China Hat") {
    private val color by ColorSetting("Color", Color(255, 255, 255, 90), true)
    private val rainbow by ToggleSetting("Rainbow", false)
        .withDescription("Cycles the hat color over time.")
    private val radius by SliderSetting("Radius", 45, 20, 100, 5)
        .withDescription("Brim radius in centimeters.")
    private val coneHeight by SliderSetting("Height", 40, 10, 100, 5)
        .withDescription("Cone height in centimeters.")
    private val fill by ToggleSetting("Fill", true)
    private val lineWidth by SliderSetting("Line Width", 2, 1, 6, 1)
    private val phase by ToggleSetting("Phase", false)
        .withDescription("Visible through walls.")
    private val firstPerson by ToggleSetting("Hide In First Person", true)
        .withDescription("Hide the hat while in first person view.")

    private const val SEGMENTS = 32

    override fun init() {
        register<RenderWorldEvent> {
            val p = mc.player ?: return@register
            val ctx = event.ctx
            if (firstPerson.value && mc.options.cameraType.isFirstPerson) return@register

            val pt = mc.deltaTracker.getGameTimeDeltaPartialTick(true)
            val pos = p.getPosition(pt)
            val hitbox = p.bbHeight.toDouble()
            val baseY = pos.y + hitbox + 0.03
            val apex = Vec3(pos.x, baseY + coneHeight.value.toDouble() / 100.0, pos.z)
            val r = radius.value.toDouble() / 100.0

            val base = color.value
            val col = if (rainbow.value) {
                val hue = (System.currentTimeMillis() % 4000L) / 4000f
                val c = Color(Color.HSBtoRGB(hue, 0.75f, 1f))
                Color(c.red, c.green, c.blue, base.alpha)
            }
            else base

            val red = col.red / 255f
            val green = col.green / 255f
            val blue = col.blue / 255f
            val alpha = col.alpha / 255f

            ctx.matrixStack.pushPose()
            ctx.matrixStack.translate(- ctx.camera.pos.x, - ctx.camera.pos.y, - ctx.camera.pos.z)
            val pose = ctx.uMatrixStack()

            val apexX = apex.x.toFloat()
            val apexY = apex.y.toFloat()
            val apexZ = apex.z.toFloat()
            val rimY = baseY.toFloat()

            if (fill.value) {
                val buffer = RenderBatcher.filledBatch(phase.value)
                for (i in 0 until SEGMENTS) {
                    val a1 = i * (2.0 * Math.PI / SEGMENTS)
                    val a2 = (i + 1) * (2.0 * Math.PI / SEGMENTS)
                    val x1 = (pos.x + r * cos(a1)).toFloat()
                    val z1 = (pos.z + r * sin(a1)).toFloat()
                    val x2 = (pos.x + r * cos(a2)).toFloat()
                    val z2 = (pos.z + r * sin(a2)).toFloat()

                    buffer.vertex(pose, apexX, apexY, apexZ, red, green, blue, alpha)
                    buffer.vertex(pose, x1, rimY, z1, red, green, blue, alpha)
                    buffer.vertex(pose, x2, rimY, z2, red, green, blue, alpha)
                }
            }

            val outlineBuffer = RenderBatcher.lineBatch(phase.value)
            val width = lineWidth.value.toFloat()

            for (i in 0 until SEGMENTS) {
                val a1 = i * (2.0 * Math.PI / SEGMENTS)
                val a2 = (i + 1) * (2.0 * Math.PI / SEGMENTS)
                val x1 = (pos.x + r * cos(a1)).toFloat()
                val z1 = (pos.z + r * sin(a1)).toFloat()
                val x2 = (pos.x + r * cos(a2)).toFloat()
                val z2 = (pos.z + r * sin(a2)).toFloat()

                // Outline remains enabled; only its user-facing toggle was removed.
                outlineBuffer.line(pose, x1, rimY, z1, x2, rimY, z2, red, green, blue, 1f, width)
                if (i % 4 == 0) outlineBuffer.line(pose, apexX, apexY, apexZ, x1, rimY, z1, red, green, blue, 1f, width)
            }

            ctx.matrixStack.popPose()
        }
    }
}

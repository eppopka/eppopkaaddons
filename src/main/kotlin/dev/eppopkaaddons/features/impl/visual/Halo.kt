package dev.eppopkaaddons.features.impl.visual

import dev.eppopkaaddons.config.types.ColorSetting
import dev.eppopkaaddons.config.types.SliderSetting
import dev.eppopkaaddons.config.types.ToggleSetting
import dev.eppopkaaddons.event.impl.RenderWorldEvent
import dev.eppopkaaddons.features.Feature
import dev.eppopkaaddons.utils.SmoothHeight
import dev.eppopkaaddons.utils.render.world.RenderBatcher
import dev.eppopkaaddons.utils.render.world.RenderContext
import net.minecraft.world.phys.Vec3
import java.awt.Color
import kotlin.math.cos
import kotlin.math.sin

object Halo: Feature("Renders a customizable halo above your head.", name = "Halo") {
    private val color by ColorSetting("Color", Color.WHITE, true)
    private val opacity by SliderSetting("Opacity", 100, 5, 100, 5)
        .withDescription("Overall transparency of the whole halo.")
    private val rainbow by ToggleSetting("Rainbow", false)
        .withDescription("Cycles the halo color over time.")
    private val radius by SliderSetting("Radius", 35, 10, 100, 5)
        .withDescription("Halo radius in centimeters.")
    private val thickness by SliderSetting("Thickness", 3, 1, 10, 1)
    private val glow by SliderSetting("Glow", 60, 0, 100, 5)
        .withDescription("Size of the soft glow around the ring.")
    private val shimmer by ToggleSetting("Shimmer", true)
        .withDescription("Soft light slowly running around the ring.")
    private val height by SliderSetting("Height", 30, 0, 150, 5)
        .withDescription("Height above your head in centimeters.")
    private val bob by ToggleSetting("Bob", true)
        .withDescription("Gently floats up and down.")
    private val smoothing by SliderSetting("Smoothing", 9, 0, 30, 1)
        .withDescription("How softly the halo follows your hitbox when you sneak, unsneak, swim or crawl. Lower = slower, softer glide. 0 = instant, like vanilla.")
    private val phase by ToggleSetting("Phase", false)
        .withDescription("Visible through walls.")
    private val fill by ToggleSetting("Fill", false)
        .withDescription("Fills the halo with translucent polygons.")
    private val fillOpacity by SliderSetting("Fill Opacity", 30, 5, 100, 5)
        .withDescription("Opacity of the fill in percent.")
        .showIf { fill.value }
    private val firstPerson by ToggleSetting("Hide In First Person", true)
        .withDescription("Hide the halo while in first person view.")

    override fun init() {
        register<RenderWorldEvent> {
            val p = mc.player ?: return@register
            if (firstPerson.value && mc.options.cameraType.isFirstPerson) return@register
            val pt = mc.deltaTracker.getGameTimeDeltaPartialTick(true)
            val pos = p.getPosition(pt)

            val bobOffset = if (bob.value) sin(System.currentTimeMillis() / 600.0) * 0.06 else 0.0
            // Vanilla snaps bbHeight the instant you sneak, which makes the halo jump.
            // Ease it instead so it glides up and down.
            val hitbox = SmoothHeight.smooth("halo", p.bbHeight.toDouble(), smoothing.value.toDouble())
            val center = Vec3(pos.x, pos.y + hitbox + height.value.toDouble() / 100.0 + bobOffset, pos.z)

            val base = color.value
            val alpha = (base.alpha * opacity.value / 100f).toInt().coerceIn(0, 255)
            val col = if (rainbow.value) {
                val hue = (System.currentTimeMillis() % 4000L) / 4000f
                val c = Color(Color.HSBtoRGB(hue, 0.75f, 1f))
                Color(c.red, c.green, c.blue, alpha)
            }
            else Color(base.red, base.green, base.blue, alpha)

            if (fill.value) {
                val fillCol = Color(col.red, col.green, col.blue, (255 * fillOpacity.value / 100f * opacity.value / 100f).toInt().coerceIn(0, 255))
                renderDisc(event.ctx, center, radius.value.toDouble() / 100.0, fillCol, phase.value)
            }
            renderRing(event.ctx, center, radius.value.toDouble() / 100.0, col, thickness.value.toDouble(), phase.value)
        }
    }

    /** Filled polygon disc inside the ring. */
    private fun renderDisc(ctx: RenderContext, center: Vec3, radius: Double, color: Color, phase: Boolean) {
        ctx.matrixStack.pushPose()
        ctx.matrixStack.translate(- ctx.camera.pos.x, - ctx.camera.pos.y, - ctx.camera.pos.z)
        val buffer = RenderBatcher.filledBatch(phase)

        val r = color.red / 255f
        val g = color.green / 255f
        val b = color.blue / 255f
        val a = color.alpha / 255f
        val pose = ctx.uMatrixStack()
        val segments = 48
        val cxF = center.x.toFloat()
        val cyF = center.y.toFloat()
        val czF = center.z.toFloat()

        for (i in 0 until segments) {
            val a1 = i * (2.0 * Math.PI / segments)
            val a2 = (i + 1) * (2.0 * Math.PI / segments)
            val x1 = (center.x + radius * cos(a1)).toFloat()
            val z1 = (center.z + radius * sin(a1)).toFloat()
            val x2 = (center.x + radius * cos(a2)).toFloat()
            val z2 = (center.z + radius * sin(a2)).toFloat()

            buffer.vertex(pose, cxF, cyF, czF, r, g, b, a)
            buffer.vertex(pose, x1, cyF, z1, r, g, b, a)
            buffer.vertex(pose, x2, cyF, z2, r, g, b, a)

            // reversed winding so the disc is visible from above as well (backface culling)
            buffer.vertex(pose, cxF, cyF, czF, r, g, b, a)
            buffer.vertex(pose, x2, cyF, z2, r, g, b, a)
            buffer.vertex(pose, x1, cyF, z1, r, g, b, a)
        }

        ctx.matrixStack.popPose()
    }

    /**
     * Volumetric glowing ring: a real 3D torus (tube) built from three nested
     * translucent shells — a hot, whitened core tube plus two soft glow tubes
     * around it. Unlike a flat band, the halo has actual thickness and looks
     * three-dimensional from every angle.
     */
    private fun renderRing(ctx: RenderContext, center: Vec3, radius: Double, color: Color, thickness: Double, phase: Boolean) {
        ctx.matrixStack.pushPose()
        ctx.matrixStack.translate(- ctx.camera.pos.x, - ctx.camera.pos.y, - ctx.camera.pos.z)
        val buffer = RenderBatcher.filledBatch(phase)

        val r = color.red / 255f
        val g = color.green / 255f
        val b = color.blue / 255f
        val baseA = color.alpha / 255f
        // "hot" core lightened towards white so the ring reads as emitted light
        val coreR = r + (1f - r) * 0.5f
        val coreG = g + (1f - g) * 0.5f
        val coreB = b + (1f - b) * 0.5f

        val pose = ctx.uMatrixStack()
        val segments = 48
        val cross = 8
        val tubeCore = thickness / 200.0
        val spread = tubeCore * (1.0 + glow.value / 100.0 * 4.0)
        val time = System.currentTimeMillis() / 500.0

        fun shimmerAt(angle: Double): Float =
            if (! shimmer.value) 1f
            else (0.65 + 0.35 * sin(angle * 3.0 + time)).toFloat()

        // nested shells: solid hot core tube + two soft translucent glow tubes
        val shellRadius = doubleArrayOf(tubeCore, tubeCore + spread * 0.5, tubeCore + spread)
        val shellAlpha = floatArrayOf(1f, 0.35f, 0.12f)

        for (s in 0 until 3) {
            val tube = shellRadius[s]
            val aMul = shellAlpha[s]
            val cr = if (s == 0) coreR else r
            val cg = if (s == 0) coreG else g
            val cb = if (s == 0) coreB else b

            for (i in 0 until segments) {
                val t1 = i * (2.0 * Math.PI / segments)
                val t2 = (i + 1) * (2.0 * Math.PI / segments)
                val a1 = baseA * aMul * shimmerAt(t1)
                val a2 = baseA * aMul * shimmerAt(t2)

                for (k in 0 until cross) {
                    val p1 = k * (2.0 * Math.PI / cross)
                    val p2 = (k + 1) * (2.0 * Math.PI / cross)

                    fun vx(theta: Double, phi: Double): FloatArray {
                        val ring = radius + tube * cos(phi)
                        return floatArrayOf(
                            (center.x + ring * cos(theta)).toFloat(),
                            (center.y + tube * sin(phi)).toFloat(),
                            (center.z + ring * sin(theta)).toFloat()
                        )
                    }

                    fun emit(v: FloatArray, a: Float) {
                        buffer.vertex(pose, v[0], v[1], v[2], cr, cg, cb, a)
                    }

                    val v11 = vx(t1, p1)
                    val v21 = vx(t2, p1)
                    val v22 = vx(t2, p2)
                    val v12 = vx(t1, p2)

                    // tube surface quad as two triangles, emitted in both
                    // windings so backface culling never hides the halo
                    emit(v11, a1); emit(v21, a2); emit(v22, a2)
                    emit(v11, a1); emit(v22, a2); emit(v12, a1)
                    emit(v22, a2); emit(v21, a2); emit(v11, a1)
                    emit(v12, a1); emit(v22, a2); emit(v11, a1)
                }
            }
        }

        ctx.matrixStack.popPose()
    }
}

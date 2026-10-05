package dev.eppopkaaddons.features.impl.visual

import dev.eppopkaaddons.EppopkaAddons.mc
import dev.eppopkaaddons.config.types.SliderSetting
import dev.eppopkaaddons.config.types.TextInputSetting
import dev.eppopkaaddons.config.types.ToggleSetting
import dev.eppopkaaddons.event.impl.RenderOverlayEvent
import dev.eppopkaaddons.features.Feature
import dev.eppopkaaddons.ui.clickgui.foundation.FontSystem
import dev.eppopkaaddons.ui.clickgui.foundation.GlassRenderer.rounded
import dev.eppopkaaddons.ui.clickgui.foundation.Rect
import gg.essential.universal.USound
import net.minecraft.sounds.SoundEvents
import java.awt.Color
import kotlin.math.max

object DvdBounce : Feature(
    description = "A customizable bouncing overlay with exact corner-hit feedback.",
    name = "DVD Bounce",
) {
    private val text by TextInputSetting("Text", "EPPOPKA")
        .withDescription("Text shown inside the bouncing box.")
        .section("Appearance")
    private val boxWidth by SliderSetting("Box Width", 92, 48, 240, 2, " px")
    private val boxHeight by SliderSetting("Box Height", 38, 24, 120, 2, " px")
    private val cornerRadius by SliderSetting("Corner Radius", 9, 0, 40, 1, " px")
    private val speed by SliderSetting("Speed", 92f, 20f, 320f, 2f, " px/s")
        .section("Motion")
    private val cornerCounter by ToggleSetting("Corner Hit Counter", true).section("Corner Hits")
    private val cornerSound by ToggleSetting("Corner Hit Sound", true)
    private val cornerFlash by ToggleSetting("Corner Hit Flash", true)

    private val colors = arrayOf(
        Color(124, 113, 255), Color(63, 201, 173), Color(255, 99, 132),
        Color(255, 184, 77), Color(74, 168, 255), Color(210, 108, 255),
    )
    private var x = 24f
    private var y = 24f
    private var dx = 1f
    private var dy = .78f
    private var colorIndex = 0
    private var cornerHits = 0
    private var lastFrame = System.nanoTime()
    private var flashUntil = 0L

    override fun init() {
        register<RenderOverlayEvent> {
            val now = System.nanoTime()
            val dt = ((now - lastFrame) / 1_000_000_000.0).toFloat().coerceIn(0f, .05f)
            lastFrame = now
            val width = boxWidth.value.toFloat()
            val height = boxHeight.value.toFloat()
            val maxX = max(0f, mc.window.guiScaledWidth.toFloat() - width)
            val maxY = max(0f, mc.window.guiScaledHeight.toFloat() - height)

            x += dx * speed.value * dt
            y += dy * speed.value * dt
            var bouncedX = false
            var bouncedY = false
            if (x <= 0f || x >= maxX) {
                x = x.coerceIn(0f, maxX)
                dx = if (x <= 0f) 1f else -1f
                bouncedX = true
            }
            if (y <= 0f || y >= maxY) {
                y = y.coerceIn(0f, maxY)
                dy = if (y <= 0f) 1f else -1f
                bouncedY = true
            }
            if (bouncedX || bouncedY) colorIndex = (colorIndex + 1) % colors.size
            if (bouncedX && bouncedY) {
                cornerHits++
                flashUntil = System.currentTimeMillis() + 420L
                if (cornerSound.value) USound.playSoundStatic(SoundEvents.NOTE_BLOCK_PLING, .72f, 1.55f)
            }

            val ctx = event.context
            val color = colors[colorIndex]
            val box = Rect(x, y, width, height)
            ctx.rounded(box, cornerRadius.value.toFloat(), Color(color.red, color.green, color.blue, 220))
            ctx.rounded(box.inset(2f), (cornerRadius.value - 2).coerceAtLeast(0).toFloat(), Color(7, 10, 16, 225))
            ctx.rounded(Rect(box.x + 2f, box.y + 2f, box.width - 4f, 1f), .5f, Color(255, 255, 255, 35))
            val label = text.value.ifBlank { "DVD" }
            FontSystem.centered(ctx, FontSystem.ellipsize(label, width - 14f, .72f), box.centerX, box.centerY - 5f, color, .72f, true)

            if (cornerCounter.value) {
                val count = "CORNERS $cornerHits"
                val countWidth = FontSystem.width(count, .43f, true) + 10f
                ctx.rounded(Rect(box.right - countWidth, box.bottom + 4f, countWidth, 14f), 6f, Color(5, 8, 13, 205))
                FontSystem.centered(ctx, count, box.right - countWidth / 2f, box.bottom + 8f, Color(211, 216, 229), .43f, true)
            }
            if (cornerFlash.value && System.currentTimeMillis() < flashUntil) {
                val remaining = ((flashUntil - System.currentTimeMillis()) / 420f).coerceIn(0f, 1f)
                ctx.rounded(Rect(0f, 0f, mc.window.guiScaledWidth.toFloat(), mc.window.guiScaledHeight.toFloat()), 0f, Color(color.red, color.green, color.blue, (remaining * 42).toInt()))
                FontSystem.centered(ctx, "CORNER HIT  #$cornerHits", mc.window.guiScaledWidth / 2f, 20f, color, .72f, true)
            }
        }
    }

    override fun onEnable() {
        lastFrame = System.nanoTime()
        super.onEnable()
    }
}

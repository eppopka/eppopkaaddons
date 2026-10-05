package dev.eppopkaaddons.ui.clickgui

import dev.eppopkaaddons.config.types.ColorSetting
import dev.eppopkaaddons.features.impl.general.AetherOSSettings
import dev.eppopkaaddons.ui.clickgui.foundation.*
import dev.eppopkaaddons.ui.clickgui.foundation.GlassRenderer.pill
import dev.eppopkaaddons.ui.clickgui.foundation.GlassRenderer.rounded
import dev.eppopkaaddons.ui.clickgui.foundation.GlassRenderer.surface
import gg.essential.universal.UKeyboard
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.KeyEvent
import java.awt.Color
import kotlin.math.roundToInt

/**
 * Modal HSV/RGB editor shared by every ColorSetting.
 *
 * The picker writes directly to ConfigHolder.value while dragging, so every
 * gameplay/world preview sees the selected color on the same frame.
 */
class ColorPickerOverlay {
    private enum class DragTarget { SV, HUE, ALPHA }

    private val reveal = Spring(0f, 360f, 31f)
    private var setting: ColorSetting? = null
    private var shown = false
    private var dragTarget: DragTarget? = null
    private var hue = 0f
    private var saturation = 0f
    private var brightness = 1f
    private var alpha = 255
    private var hex = "#FFFFFFFF"
    private var hexFocused = false
    private var modal = Rect(0f, 0f, 0f, 0f)
    private var svRect = Rect(0f, 0f, 0f, 0f)
    private var hueRect = Rect(0f, 0f, 0f, 0f)
    private var alphaRect = Rect(0f, 0f, 0f, 0f)
    private var hexRect = Rect(0f, 0f, 0f, 0f)
    private var closeRect = Rect(0f, 0f, 0f, 0f)

    fun isActive() = setting != null

    fun open(next: ColorSetting) {
        setting = next
        val hsb = Color.RGBtoHSB(next.value.red, next.value.green, next.value.blue, null)
        hue = hsb[0]
        saturation = hsb[1]
        brightness = hsb[2]
        alpha = if (next.withAlpha) next.value.alpha else 255
        hex = formatHex(next.value, next.withAlpha)
        hexFocused = false
        dragTarget = null
        shown = true
        reveal.snap(0f)
    }

    fun close() {
        shown = false
        hexFocused = false
        dragTarget = null
    }

    fun reset() {
        shown = false
        setting = null
        hexFocused = false
        dragTarget = null
        reveal.snap(0f)
    }

    fun render(ctx: GuiGraphicsExtractor, parent: Rect, mouseX: Int, mouseY: Int, baseAlpha: Float) {
        val target = if (shown) 1f else 0f
        val progress = reveal.to(target).coerceIn(0f, 1f)
        if (!shown && progress < .01f) {
            setting = null
            return
        }
        val current = setting ?: return
        val width = minOf(352f, (parent.width - 24f).coerceAtLeast(250f))
        val height = minOf(366f, (parent.height - 24f).coerceAtLeast(270f))
        val base = Rect(parent.centerX - width * .5f, parent.centerY - height * .5f, width, height)
        modal = base.scaled(.94f + progress * .06f)
        val a = baseAlpha * progress
        val accent = current.value

        ctx.rounded(parent, 18f, Color(0, 0, 0, (102 * a).roundToInt()))
        ctx.surface(
            modal,
            18f,
            AetherOSSettings.surface((246 * a).roundToInt(), elevated = true),
            Color(accent.red, accent.green, accent.blue, (118 * a).roundToInt()),
        )
        FontSystem.draw(ctx, current.name.uppercase(), modal.x + 17f, modal.y + 14f, AetherOSSettings.primary((255 * a).roundToInt()), .78f, true)
        FontSystem.draw(ctx, "HSV / RGB", modal.x + 17f, modal.y + 38f, AetherOSSettings.secondary((230 * a).roundToInt()), .46f, true)
        closeRect = Rect(modal.right - 40f, modal.y + 14f, 24f, 24f)
        ctx.rounded(closeRect, 7f, Color(255, 255, 255, ((if (closeRect.contains(mouseX.toFloat(), mouseY.toFloat())) 30 else 14) * a).roundToInt()))
        FontSystem.centered(ctx, "×", closeRect.centerX, closeRect.y + 4f, Color(231, 234, 242, (255 * a).roundToInt()), .92f)

        val left = modal.x + 17f
        val contentWidth = modal.width - 34f
        svRect = Rect(left, modal.y + 64f, contentWidth, (modal.height - 198f).coerceAtLeast(104f))
        drawSaturationValue(ctx, svRect, a)

        hueRect = Rect(left, svRect.bottom + 13f, contentWidth, 15f)
        drawHue(ctx, hueRect, a)

        alphaRect = Rect(left, hueRect.bottom + 14f, contentWidth, 15f)
        drawAlpha(ctx, alphaRect, current.withAlpha, a)

        val footerY = alphaRect.bottom + 15f
        val swatch = Rect(left, footerY, 38f, 31f)
        ctx.rounded(swatch.expand(1f), 9f, Color(255, 255, 255, (44 * a).roundToInt()))
        ctx.rounded(swatch, 8f, current.value)
        hexRect = Rect(swatch.right + 10f, footerY, contentWidth - swatch.width - 10f, 31f)
        ctx.rounded(hexRect, 8f, Color(255, 255, 255, ((if (hexFocused) 25 else 12) * a).roundToInt()))
        if (hexFocused) ctx.rounded(hexRect, 8f, Color(accent.red, accent.green, accent.blue, (70 * a).roundToInt()))
        ctx.rounded(hexRect.inset(1f), 7f, Color(7, 11, 18, (225 * a).roundToInt()))
        FontSystem.draw(ctx, hex + if (hexFocused && (System.currentTimeMillis() / 450L) % 2L == 0L) "_" else "", hexRect.x + 11f, hexRect.y + 9f, Color(236, 239, 247, (255 * a).roundToInt()), .59f, true)
    }

    private fun drawSaturationValue(ctx: GuiGraphicsExtractor, rect: Rect, a: Float) {
        val columns = 36
        val rows = 22
        val cellW = rect.width / columns
        val cellH = rect.height / rows
        repeat(rows) { row ->
            val value = 1f - row.toFloat() / (rows - 1)
            repeat(columns) { column ->
                val sat = column.toFloat() / (columns - 1)
                val rgb = Color.getHSBColor(hue, sat, value)
                ctx.rounded(
                    Rect(rect.x + column * cellW, rect.y + row * cellH, cellW + .7f, cellH + .7f),
                    0f,
                    Color(rgb.red, rgb.green, rgb.blue, (255 * a).roundToInt()),
                )
            }
        }
        val markerX = rect.x + saturation * rect.width
        val markerY = rect.y + (1f - brightness) * rect.height
        ctx.pill(Rect(markerX - 6f, markerY - 6f, 12f, 12f), Color(0, 0, 0, (165 * a).roundToInt()))
        ctx.pill(Rect(markerX - 4f, markerY - 4f, 8f, 8f), Color(255, 255, 255, (245 * a).roundToInt()))
    }

    private fun drawHue(ctx: GuiGraphicsExtractor, rect: Rect, a: Float) {
        val steps = 42
        val width = rect.width / steps
        repeat(steps) { index ->
            val rgb = Color.getHSBColor(index.toFloat() / steps, 1f, 1f)
            ctx.rounded(Rect(rect.x + index * width, rect.y, width + .5f, rect.height), 0f, Color(rgb.red, rgb.green, rgb.blue, (255 * a).roundToInt()))
        }
        val x = rect.x + hue * rect.width
        ctx.rounded(Rect(x - 2f, rect.y - 3f, 4f, rect.height + 6f), 2f, Color(255, 255, 255, (255 * a).roundToInt()))
    }

    private fun drawAlpha(ctx: GuiGraphicsExtractor, rect: Rect, enabled: Boolean, a: Float) {
        val base = Color.getHSBColor(hue, saturation, brightness)
        val steps = 36
        val width = rect.width / steps
        repeat(steps) { index ->
            val amount = if (enabled) index.toFloat() / (steps - 1) else 1f
            val checker = if ((index / 2) % 2 == 0) 75 else 112
            ctx.rounded(Rect(rect.x + index * width, rect.y, width + .5f, rect.height), 0f, Color(checker, checker, checker, (255 * a).roundToInt()))
            ctx.rounded(Rect(rect.x + index * width, rect.y, width + .5f, rect.height), 0f, Color(base.red, base.green, base.blue, (amount * 255f * a).roundToInt()))
        }
        val x = rect.x + (if (enabled) alpha / 255f else 1f) * rect.width
        ctx.rounded(Rect(x - 2f, rect.y - 3f, 4f, rect.height + 6f), 2f, Color(255, 255, 255, (255 * a).roundToInt()))
        if (!enabled) FontSystem.centered(ctx, "OPAQUE", rect.centerX, rect.y + 3f, Color(245, 247, 251, (225 * a).roundToInt()), .42f, true)
    }

    fun click(x: Int, y: Int, button: Int): Boolean {
        if (!isActive()) return false
        if (button != 0) return true
        val px = x.toFloat()
        val py = y.toFloat()
        when {
            closeRect.contains(px, py) || !modal.contains(px, py) -> close()
            svRect.contains(px, py) -> { dragTarget = DragTarget.SV; updateDrag(px, py) }
            hueRect.expand(4f).contains(px, py) -> { dragTarget = DragTarget.HUE; updateDrag(px, py) }
            alphaRect.expand(4f).contains(px, py) -> { dragTarget = DragTarget.ALPHA; updateDrag(px, py) }
            hexRect.contains(px, py) -> hexFocused = true
            else -> hexFocused = false
        }
        return true
    }

    fun drag(x: Int, y: Int, button: Int): Boolean {
        if (!isActive() || button != 0 || dragTarget == null) return false
        updateDrag(x.toFloat(), y.toFloat())
        return true
    }

    fun release(button: Int) {
        if (button == 0) dragTarget = null
    }

    private fun updateDrag(x: Float, y: Float) {
        val current = setting ?: return
        when (dragTarget) {
            DragTarget.SV -> {
                saturation = ((x - svRect.x) / svRect.width).coerceIn(0f, 1f)
                brightness = (1f - (y - svRect.y) / svRect.height).coerceIn(0f, 1f)
            }
            DragTarget.HUE -> hue = ((x - hueRect.x) / hueRect.width).coerceIn(0f, 1f)
            DragTarget.ALPHA -> if (current.withAlpha) alpha = (((x - alphaRect.x) / alphaRect.width).coerceIn(0f, 1f) * 255f).roundToInt()
            null -> return
        }
        applyColor()
    }

    fun key(event: KeyEvent): Boolean {
        if (!isActive()) return false
        if (event.input() == UKeyboard.KEY_ESCAPE) {
            if (hexFocused) hexFocused = false else close()
            return true
        }
        if (!hexFocused) return true
        when (event.input()) {
            UKeyboard.KEY_ENTER -> hexFocused = false
            UKeyboard.KEY_BACKSPACE -> if (hex.length > 1) {
                hex = hex.dropLast(1)
                applyHexIfValid()
            }
            else -> return false
        }
        return true
    }

    fun char(char: Char): Boolean {
        if (!isActive() || !hexFocused) return false
        if (char in '0'..'9' || char.lowercaseChar() in 'a'..'f') {
            val limit = if (setting?.withAlpha == true) 9 else 7
            if (hex.length < limit) hex += char.uppercaseChar()
            applyHexIfValid()
        }
        return true
    }

    private fun applyHexIfValid() {
        val current = setting ?: return
        val raw = hex.removePrefix("#")
        val expected = if (current.withAlpha) 8 else 6
        if (raw.length != expected) return
        val value = raw.toLongOrNull(16) ?: return
        val red = ((value shr if (current.withAlpha) 24 else 16) and 0xFF).toInt()
        val green = ((value shr if (current.withAlpha) 16 else 8) and 0xFF).toInt()
        val blue = ((value shr if (current.withAlpha) 8 else 0) and 0xFF).toInt()
        alpha = if (current.withAlpha) (value and 0xFF).toInt() else 255
        val hsb = Color.RGBtoHSB(red, green, blue, null)
        hue = hsb[0]; saturation = hsb[1]; brightness = hsb[2]
        current.value = Color(red, green, blue, alpha)
    }

    private fun applyColor() {
        val current = setting ?: return
        val rgb = Color.getHSBColor(hue, saturation, brightness)
        current.value = Color(rgb.red, rgb.green, rgb.blue, if (current.withAlpha) alpha else 255)
        hex = formatHex(current.value, current.withAlpha)
    }

    private fun formatHex(color: Color, withAlpha: Boolean) =
        if (withAlpha) "#%02X%02X%02X%02X".format(color.red, color.green, color.blue, color.alpha)
        else "#%02X%02X%02X".format(color.red, color.green, color.blue)
}
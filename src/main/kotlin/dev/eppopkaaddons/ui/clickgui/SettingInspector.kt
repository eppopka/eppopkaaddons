package dev.eppopkaaddons.ui.clickgui

import dev.eppopkaaddons.config.ConfigHolder
import dev.eppopkaaddons.config.types.*
import dev.eppopkaaddons.features.Feature
import dev.eppopkaaddons.features.impl.general.AetherOSSettings
import dev.eppopkaaddons.features.impl.visual.ItemAura
import dev.eppopkaaddons.features.impl.visual.RotatingItem
import dev.eppopkaaddons.ui.clickgui.foundation.*
import dev.eppopkaaddons.ui.clickgui.foundation.GlassRenderer.pill
import dev.eppopkaaddons.ui.clickgui.foundation.GlassRenderer.rounded
import dev.eppopkaaddons.ui.clickgui.foundation.GlassRenderer.surface
import gg.essential.universal.UKeyboard
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.KeyEvent
import java.awt.Color

class SettingInspector {
    private data class Hit(val setting: ConfigHolder<*>, val rect: Rect)
    private val hits = ArrayList<Hit>()
    private var feature: Feature? = null
    private var area = Rect(0f, 0f, 0f, 0f)
    private var scroll = 0f
    private var maxScroll = 0f
    private var textInput: TextInputSetting? = null
    private var keyInput: KeybindSetting? = null
    private var draggingSlider: Pair<SliderSetting<*>, Rect>? = null
    private val colorPicker = ColorPickerOverlay()

    fun bind(next: Feature?) {
        feature = next; scroll = 0f; textInput = null; keyInput = null; draggingSlider = null; colorPicker.reset()
    }

    fun selected() = feature
    fun contains(x: Int, y: Int) = feature != null && area.contains(x.toFloat(), y.toFloat())

    fun render(ctx: GuiGraphicsExtractor, bounds: Rect, mouseX: Int, mouseY: Int, alpha: Float, accent: Color) {
        area = bounds
        val current = feature ?: return
        val liveHandPreview = current === ItemAura || current === RotatingItem
        val shellAlpha = if (liveHandPreview) 178 else 235
        val shell = AetherOSSettings.surface((shellAlpha * alpha).toInt())
        ctx.surface(bounds, 18f, shell, AetherOSSettings.border((38 * alpha).toInt()))
        ctx.pill(Rect(bounds.x + 17f, bounds.y + 17f, 36f, 36f), Color(accent.red, accent.green, accent.blue, 52))
        FontSystem.centered(ctx, current.name.take(1).uppercase(), bounds.x + 35f, bounds.y + 28f, accent, .82f, true)
        FontSystem.draw(ctx, current.name, bounds.x + 64f, bounds.y + 16f, AetherOSSettings.primary(), 1f, true)
        FontSystem.draw(ctx, if (current.enabled) "ACTIVE" else "INACTIVE", bounds.x + 64f, bounds.y + 40f, if (current.enabled) accent else AetherOSSettings.secondary(), .58f, true)
        val close = Rect(bounds.right - 42f, bounds.y + 18f, 25f, 25f)
        ctx.rounded(close, 6f, Color(255, 255, 255, if (close.contains(mouseX.toFloat(), mouseY.toFloat())) 28 else 12))
        FontSystem.centered(ctx, "×", close.centerX, close.y + 5f, Color(216, 219, 228), 1f)

        val settings = current.configSettings.filter { it.visibility() }
        val previewHeight = if (liveHandPreview) 72f else 0f
        if (liveHandPreview) {
            ItemAura.renderGuiPreview(
                ctx,
                Rect(bounds.x + 11f, bounds.y + 69f, bounds.width - 22f, previewHeight - 7f),
                alpha,
                current.name,
            )
        }
        val viewport = Rect(
            bounds.x + 11f,
            bounds.y + 70f + previewHeight,
            bounds.width - 22f,
            bounds.height - 83f - previewHeight,
        )
        val total = settings.size * 59f
        maxScroll = (total - viewport.height).coerceAtLeast(0f)
        scroll = scroll.coerceIn(0f, maxScroll)
        hits.clear()
        ctx.enableScissor(viewport.x.toInt(), viewport.y.toInt(), viewport.right.toInt(), viewport.bottom.toInt())
        settings.forEachIndexed { index, setting ->
            val row = Rect(viewport.x, viewport.y + index * 59f - scroll, viewport.width, 52f)
            if (row.bottom >= viewport.y && row.y <= viewport.bottom) {
                hits += Hit(setting, row)
                drawSetting(ctx, setting, row, mouseX, mouseY, accent)
            }
        }
        ctx.disableScissor()
        if (settings.isEmpty()) FontSystem.centered(ctx, "No configurable properties", viewport.centerX, viewport.centerY - 4f, Color(166, 171, 184), .78f)
        if (maxScroll > 0f) {
            val h = (viewport.height * viewport.height / total).coerceAtLeast(24f)
            ctx.pill(Rect(viewport.right - 2f, viewport.y + scroll / maxScroll * (viewport.height - h), 2f, h), accent.alpha(155))
        }
        colorPicker.render(ctx, bounds, mouseX, mouseY, alpha)
    }

    private fun drawSetting(ctx: GuiGraphicsExtractor, setting: ConfigHolder<*>, row: Rect, mx: Int, my: Int, accent: Color) {
        val hover = row.contains(mx.toFloat(), my.toFloat())
        ctx.rounded(row, 13f, Color(255, 255, 255, if (hover) 22 else 10))
        FontSystem.draw(ctx, FontSystem.ellipsize(setting.name, row.width * .53f, .74f), row.x + 13f, row.y + 10f, AetherOSSettings.primary(), .74f, true)
        setting.section?.let { FontSystem.draw(ctx, it.uppercase(), row.x + 13f, row.y + 33f, AetherOSSettings.secondary(220), .49f, true) }
        when (setting) {
            is ToggleSetting -> drawToggle(ctx, row, setting.value, accent)
            is DropdownSetting -> drawValue(ctx, row, setting.options.getOrNull(setting.value) ?: "—", accent)
            is SliderSetting<*> -> drawSlider(ctx, row, setting, accent)
            is ButtonSetting -> drawValue(ctx, row, "RUN", accent)
            is KeybindSetting -> drawValue(ctx, row, if (keyInput === setting) "PRESS KEY" else setting.displayName(), accent)
            is TextInputSetting -> drawValue(ctx, row, if (textInput === setting) "${setting.value}_" else setting.value.ifBlank { "EMPTY" }, accent)
            is ColorSetting -> {
                val checker = Rect(row.right - 48f, row.centerY - 11f, 36f, 22f)
                repeat(5) { column ->
                    repeat(3) { line ->
                        val shade = if ((column + line) % 2 == 0) 205 else 137
                        ctx.rounded(Rect(checker.x + column * 8f, checker.y + line * 8f, 8f, 8f), 0f, Color(shade, shade, shade))
                    }
                }
                ctx.rounded(Rect(row.right - 48f, row.centerY - 11f, 36f, 22f), 11f, Color(255,255,255,38))
                ctx.pill(Rect(row.right - 46f, row.centerY - 9f, 32f, 18f), setting.value)
            }
            is ColorCodeSetting -> drawValue(ctx, row, setting.value.name, accent)
            is MultiCheckboxSetting -> drawValue(ctx, row, "${setting.value.values.count { it }} / ${setting.value.size}", accent)
            is SoundSetting -> drawValue(ctx, row, FontSystem.ellipsize(setting.prettyName(setting.value) ?: setting.value.location.toString(), 106f, .58f), accent)
            else -> drawValue(ctx, row, setting.value.toString(), accent)
        }
    }

    private fun drawValue(ctx: GuiGraphicsExtractor, row: Rect, value: String, accent: Color) {
        val safe = FontSystem.ellipsize(value.uppercase(), row.width * .39f, .58f)
        val w = FontSystem.width(safe, .58f, true) + 20f
        val box = Rect(row.right - w - 12f, row.centerY - 12f, w, 24f)
        ctx.pill(box, Color(accent.red, accent.green, accent.blue, 31))
        FontSystem.centered(ctx, safe, box.centerX, box.y + 7f, accent, .58f, true)
    }

    private fun drawToggle(ctx: GuiGraphicsExtractor, row: Rect, on: Boolean, accent: Color) {
        val track = Rect(row.right - 51f, row.centerY - 10f, 39f, 20f)
        ctx.pill(track, if (on) accent.alpha(190) else Color(255, 255, 255, 22))
        ctx.pill(Rect(track.x + if (on) 21f else 3f, track.y + 3f, 14f, 14f), Color(248, 249, 252))
    }

    private fun drawSlider(ctx: GuiGraphicsExtractor, row: Rect, setting: SliderSetting<*>, accent: Color) {
        val progress = ((setting.value.toDouble() - setting.min.toDouble()) / (setting.max.toDouble() - setting.min.toDouble())).toFloat().coerceIn(0f, 1f)
        val track = Rect(row.right - 137f, row.centerY + 7f, 123f, 4f)
        ctx.pill(track, Color(255, 255, 255, 20)); ctx.pill(track.copy(width = track.width * progress), accent.alpha(220))
        ctx.pill(Rect(track.x + track.width * progress - 5f, track.y - 4f, 11f, 11f), Color(248, 249, 252))
        FontSystem.draw(ctx, "${setting.stringfy(setting.value)}${setting.suffix}", track.x, row.y + 9f, Color(196, 200, 211), .58f, true)
    }

    fun click(x: Int, y: Int, button: Int): Boolean {
        val current = feature ?: return false
        if (colorPicker.isActive()) return colorPicker.click(x, y, button)
        if (!area.contains(x.toFloat(), y.toFloat())) return false
        if (Rect(area.right - 42f, area.y + 18f, 25f, 25f).contains(x.toFloat(), y.toFloat())) { bind(null); return true }
        val hit = hits.asReversed().firstOrNull { it.rect.contains(x.toFloat(), y.toFloat()) } ?: return true
        val setting = hit.setting
        textInput = null; keyInput = null
        when (setting) {
            is ToggleSetting -> setting.value = !setting.value
            is DropdownSetting -> if (setting.options.isNotEmpty()) setting.value = if (button == 1) (setting.value - 1 + setting.options.size) % setting.options.size else (setting.value + 1) % setting.options.size
            is SliderSetting<*> -> if (button == 0) {
                draggingSlider = setting to hit.rect
                setSlider(setting, hit.rect, x)
            }
            is ButtonSetting -> setting()
            is KeybindSetting -> keyInput = setting
            is TextInputSetting -> textInput = setting
            is ColorSetting -> {
                colorPicker.open(setting)
            }
            is ColorCodeSetting -> {
                val i = ColorCodeSetting.COLORS.indexOf(setting.value).coerceAtLeast(0)
                setting.value = ColorCodeSetting.COLORS[(i + 1) % ColorCodeSetting.COLORS.size]
            }
        }
        return true
    }

    fun hasModal() = colorPicker.isActive()

    fun drag(x: Int, y: Int, button: Int): Boolean {
        if (colorPicker.drag(x, y, button)) return true
        if (button != 0) return false
        val (setting, row) = draggingSlider ?: return false
        setSlider(setting, row, x)
        return true
    }

    fun release(button: Int) {
        colorPicker.release(button)
        if (button == 0) draggingSlider = null
    }

    @Suppress("UNCHECKED_CAST")
    private fun setSlider(raw: SliderSetting<*>, row: Rect, x: Int) {
        val setting = raw as SliderSetting<Number>
        val start = row.right - 137f
        val percent = ((x - start) / 123f).coerceIn(0f, 1f)
        setting.value = setting.snapToStep(setting.min.toDouble() + (setting.max.toDouble() - setting.min.toDouble()) * percent)
    }

    fun scroll(delta: Double): Boolean {
        if (colorPicker.isActive()) return true
        if (feature == null) return false
        scroll = (scroll - delta.toFloat() * 31f).coerceIn(0f, maxScroll)
        return true
    }

    fun key(event: KeyEvent): Boolean {
        if (colorPicker.isActive() && colorPicker.key(event)) return true
        keyInput?.let {
            if (event.input() == UKeyboard.KEY_ESCAPE) it.value = UKeyboard.KEY_NONE else { it.value = event.key; it.scanCode = event.scancode; it.isMouse = false }
            keyInput = null; return true
        }
        textInput?.let {
            when (event.input()) {
                UKeyboard.KEY_ESCAPE, UKeyboard.KEY_ENTER -> textInput = null
                UKeyboard.KEY_BACKSPACE -> if (it.value.isNotEmpty()) it.value = it.value.dropLast(1)
                else -> return false
            }
            return true
        }
        return false
    }

    fun char(char: Char): Boolean {
        if (colorPicker.char(char)) return true
        val input = textInput ?: return false
        if (!char.isISOControl()) input.value += char
        return true
    }
}

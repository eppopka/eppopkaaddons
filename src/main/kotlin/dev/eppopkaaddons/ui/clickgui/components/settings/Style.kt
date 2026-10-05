package dev.eppopkaaddons.ui.clickgui.components.settings

import dev.eppopkaaddons.features.impl.general.AetherOSSettings
import dev.eppopkaaddons.ui.clickgui.foundation.FontSystem
import dev.eppopkaaddons.ui.clickgui.foundation.GlassRenderer.surface
import dev.eppopkaaddons.ui.clickgui.foundation.Rect
import gg.essential.universal.USound
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.sounds.SoundEvents
import java.awt.Color

/** Shared compatibility styling for non-/ea utility screens. */
object Style {
    val accentColor: Color get() = AetherOSSettings.currentAccent
    val accentColorTrans: Color get() = Color(accentColor.red, accentColor.green, accentColor.blue, 120)
    val settingBackgroundColor get() = AetherOSSettings.surface(32, elevated = true)
    val sliderBackgroundColor get() = AetherOSSettings.border(52)
    fun drawBackground(ctx: GuiGraphicsExtractor, x: Number, y: Number, w: Number, h: Number) =
        ctx.surface(
            Rect(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat()),
            6f,
            AetherOSSettings.surface(220),
            AetherOSSettings.border(42),
        )
    fun drawHoverBar(ctx: GuiGraphicsExtractor, x: Number, y: Number, height: Number, anim: Float) = ctx.fill(x.toInt(), (y.toFloat() + 3f).toInt(), (x.toFloat() + 2f).toInt(), (y.toFloat() + height.toFloat() - 3f).toInt(), Color(accentColor.red, accentColor.green, accentColor.blue, (220 * anim).toInt().coerceIn(0,255)).rgb)
    fun drawNudgedText(ctx: GuiGraphicsExtractor, text: String, x: Float, y: Float, anim: Float, color: Color = Color.WHITE) = FontSystem.draw(ctx, text, x + 2f * anim, y, color, .74f)
    fun playClickSound(pitch: Float) = USound.playSoundStatic(SoundEvents.UI_BUTTON_CLICK, .25f, pitch)
}

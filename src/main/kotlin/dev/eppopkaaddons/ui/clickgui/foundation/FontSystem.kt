package dev.eppopkaaddons.ui.clickgui.foundation

import dev.eppopkaaddons.EppopkaAddons
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import java.awt.Color
import kotlin.math.round

/** Native Unicode FontSet rendering with a larger, quantized baseline for crisp UTF-8 text. */
object FontSystem {
    private const val BASELINE = 1.12f

    private fun text(value: String, color: Color, bold: Boolean) = Component.literal(value).withStyle {
        it.withColor(color.rgb and 0xFFFFFF).withBold(bold).withShadowColor(0)
    }

    private fun crispScale(requested: Float) = (round(requested * BASELINE * 8f) / 8f).coerceAtLeast(.5f)
    private fun snap(value: Float) = round(value * 2f) * .5f

    fun draw(ctx: GuiGraphicsExtractor, value: String, x: Float, y: Float, color: Color, scale: Float = 1f, bold: Boolean = false) {
        val safe = value.replace('\u0000', '\uFFFD')
        val finalScale = crispScale(scale)
        ctx.pose().pushMatrix()
        ctx.pose().translate(snap(x), snap(y))
        ctx.pose().scale(finalScale, finalScale)
        ctx.text(EppopkaAddons.mc.font, text(safe, color, bold), 0, 0, color.rgb, false)
        ctx.pose().popMatrix()
    }

    fun width(value: String, scale: Float = 1f, bold: Boolean = false) =
        EppopkaAddons.mc.font.width(text(value, Color.WHITE, bold)) * crispScale(scale)

    fun centered(ctx: GuiGraphicsExtractor, value: String, x: Float, y: Float, color: Color, scale: Float = 1f, bold: Boolean = false) =
        draw(ctx, value, x - width(value, scale, bold) * .5f, y, color, scale, bold)

    fun ellipsize(value: String, maxWidth: Float, scale: Float): String {
        if (width(value, scale) <= maxWidth) return value
        var out = value
        while (out.isNotEmpty() && width("$out…", scale) > maxWidth) out = out.dropLast(1)
        return "$out…"
    }
}

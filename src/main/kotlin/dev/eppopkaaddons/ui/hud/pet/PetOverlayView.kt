package dev.eppopkaaddons.ui.hud.pet

import dev.eppopkaaddons.ui.clickgui.foundation.FontSystem
import dev.eppopkaaddons.ui.clickgui.foundation.GlassRenderer.pill
import dev.eppopkaaddons.ui.clickgui.foundation.GlassRenderer.rounded
import dev.eppopkaaddons.ui.clickgui.foundation.Rect
import dev.eppopkaaddons.ui.hud.hotbar.HotbarBox
import dev.eppopkaaddons.ui.hud.hotbar.HotbarPainter
import dev.eppopkaaddons.utils.pets.PetXpModel
import dev.eppopkaaddons.utils.render.Render2D.drawRect
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack
import java.awt.Color
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToLong

object PetOverlayView {
    const val WIDTH = 248f
    fun height(compact: Boolean) = if (compact) 104f else 152f

    fun draw(ctx: GuiGraphicsExtractor, reading: PetXpModel.Reading?, pendingName: String,
             stack: ItemStack, progress: Float?, rate: Double?, observedAt: Long, now: Long,
             cached: Boolean, preview: Boolean, compact: Boolean, look: HotbarPainter.Appearance,
             seconds: Float): Pair<Number, Number> {
        HotbarPainter.surface(ctx, HotbarBox(0f, 0f, WIDTH, height(compact)), look, seconds)
        val primary = Color(239, 244, 252)
        val muted = Color(151, 167, 188)
        ctx.rounded(Rect(12f, 12f, 34f, 34f), 8f, Color(255, 255, 255, 10))
        if (!stack.isEmpty) {
            // A native 16x16 GUI item stays inside this 34x34 frame at any HUD scale.
            ctx.pose().pushMatrix()
            try {
                ctx.pose().translate(17f, 17f)
                ctx.pose().scale(1.5f, 1.5f)
                ctx.item(stack, 0, 0)
            } finally { ctx.pose().popMatrix() }
        } else FontSystem.centered(ctx, "?", 29f, 22f, muted, 1.5f, true)

        val name = reading?.name ?: pendingName.ifBlank { "Pet Overlay" }
        FontSystem.draw(ctx, fit(name, WIDTH - 70f, 1.05f, true), 58f, 13f, primary, 1.05f, true)
        val levelLine = reading?.let { "LEVEL ${it.level}" + (it.rarity?.let { r -> "  ·  ${r.replace('_', ' ')}" } ?: "") }
            ?: "Waiting for pet data"
        FontSystem.draw(ctx, fit(levelLine, WIDTH - 70f, .74f, false), 58f, 29f, look.accent, .74f)
        val ageSeconds = ((now - observedAt).coerceAtLeast(0L) / 1000L)
        val status = when {
            preview -> "HUD Editor preview"
            reading?.source == PetXpModel.Source.UNKNOWN || observedAt == 0L -> "Open /pets to sync"
            cached -> "Cached · ${age(ageSeconds)} ago"
            else -> "Synced · ${age(ageSeconds)} ago"
        }
        FontSystem.draw(ctx, fit(status, WIDTH - 70f, .64f, false), 58f, 41f, muted, .64f)

        val next = when {
            reading?.maxed == true -> "MAX LEVEL"
            reading != null -> "LEVEL ${reading.level} → ${reading.level + 1}"
            else -> "PET XP"
        }
        FontSystem.draw(ctx, next, 12f, 56f, muted, .75f)
        val percentage = when {
            reading?.maxed == true -> "100%"
            reading?.progress != null -> "%.1f%%".format(Locale.US, min(99.9, reading.progress * 100))
            else -> "—"
        }
        FontSystem.draw(ctx, percentage, WIDTH - 12f - FontSystem.width(percentage, .8f), 55f,
            if (reading?.progress != null) primary else muted, .8f)
        val bar = Rect(12f, 68f, WIDTH - 24f, 6f)
        ctx.pill(bar, Color(255, 255, 255, 22))
        progress?.let {
            val fraction = if (reading?.maxed == true) 1f else it.coerceIn(0f, .9999f)
            if (fraction > 0f) ctx.pill(bar.copy(width = bar.width * fraction), look.accent)
        }
        val xp = when {
            reading?.maxed == true -> "Max level reached"
            reading?.intoLevel != null && reading.required != null ->
                "${number(reading.intoLevel)} / ${number(reading.required)} XP"
            reading?.progress != null -> "Progress from server pet lore"
            else -> "Open /pets to read accurate XP"
        }
        FontSystem.draw(ctx, fit(xp, WIDTH - 24f, .83f, false), 12f, 84f, primary, .83f)
        if (!compact) {
            ctx.drawRect(12f, 104f, WIDTH - 24f, 1f, Color(255, 255, 255, 20))
            FontSystem.draw(ctx, "XP/H (SAMPLED)", 12f, 116f, muted, .65f)
            FontSystem.draw(ctx, "NEXT (EST.)", 140f, 116f, muted, .65f)
            FontSystem.draw(ctx, rate?.let(::shortNumber) ?: "—", 12f, 130f, primary, 1f, true)
            val left = if (reading?.required != null && reading.intoLevel != null)
                (reading.required - reading.intoLevel).coerceAtLeast(0.0) else null
            val eta = if (reading?.maxed == true) "MAX" else if (rate != null && rate > 0 && left != null)
                duration(left / rate * 3600.0) else "—"
            FontSystem.draw(ctx, fit(eta, WIDTH - 152f, 1f, true), 140f, 130f, primary, 1f, true)
        }
        return WIDTH to height(compact)
    }

    private fun fit(text: String, width: Float, scale: Float, bold: Boolean): String {
        if (FontSystem.width(text, scale, bold) <= width) return text
        var shortened = text
        while (shortened.isNotEmpty() && FontSystem.width("$shortened…", scale, bold) > width) shortened = shortened.dropLast(1)
        return "$shortened…"
    }
    private fun number(value: Double) = "%,.0f".format(Locale.US, value)
    private fun shortNumber(value: Double): String = when {
        value >= 1e9 -> "%.2fB".format(Locale.US, value / 1e9)
        value >= 1e6 -> "%.2fM".format(Locale.US, value / 1e6)
        value >= 1e3 -> "%.1fk".format(Locale.US, value / 1e3)
        else -> number(value)
    }
    private fun age(seconds: Long) = when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m"
        else -> "${seconds / 3600}h"
    }
    private fun duration(seconds: Double): String {
        if (!seconds.isFinite()) return "—"
        val s = seconds.roundToLong().coerceAtLeast(0L)
        return when {
            s >= 86400 -> "${s / 86400}d ${s % 86400 / 3600}h"
            s >= 3600 -> "${s / 3600}h ${s % 3600 / 60}m"
            s >= 60 -> "${s / 60}m ${s % 60}s"
            else -> "${s}s"
        }
    }
}

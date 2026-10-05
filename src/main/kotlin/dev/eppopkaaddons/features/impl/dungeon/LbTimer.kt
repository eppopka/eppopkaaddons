package dev.eppopkaaddons.features.impl.dungeon

import dev.eppopkaaddons.config.types.DropdownSetting
import dev.eppopkaaddons.config.types.ToggleSetting
import dev.eppopkaaddons.event.impl.ChatMessageEvent
import dev.eppopkaaddons.event.impl.TickEvent
import dev.eppopkaaddons.event.impl.WorldChangeEvent
import dev.eppopkaaddons.features.Feature
import dev.eppopkaaddons.utils.NumbersUtils.toFixed
import dev.eppopkaaddons.utils.location.LocationUtils
import dev.eppopkaaddons.utils.render.Render2D.drawCenteredString
import dev.eppopkaaddons.utils.render.RenderHelper.width
import net.minecraft.client.gui.GuiGraphicsExtractor

object LbTimer: Feature("Shows how long the Storm lightning bolt (PY) phase still has left.", name = "LB Timer") {
    private val pyLbToggle by ToggleSetting("PY LB Timer", false)
        .section("Main")
        .withDescription("Tracks the Storm PY window in the F7/M7 boss fight.")

    private val showPrefix by ToggleSetting("Prefix", true).section("Display")
    private val showSuffix by ToggleSetting("Suffix", true)
    private val format by DropdownSetting("Format", 0, listOf("Seconds", "Ticks"))

    private const val PY_TICKS = 142
    private var lbTickTime = - 1

    init {
        hudElement(
            name = "LB Timer",
            shouldDraw = { isInF7Boss() },
            centered = true,
            render = { ctx, example -> renderTimer(ctx, example) }
        )
    }

    override fun init() {
        register<WorldChangeEvent> { reset() }

        register<ChatMessageEvent> {
            if (! pyLbToggle.value || ! isInF7Boss()) return@register
            when (event.unformattedText) {
                "[BOSS] Storm: ENERGY HEED MY CALL!",
                "[BOSS] Storm: THUNDER LET ME BE YOUR CATALYST!" -> lbTickTime = PY_TICKS

                "[BOSS] Storm: I should have known that I stood no chance." -> reset()
            }
        }

        register<TickEvent.Server> {
            if (! pyLbToggle.value || ! isInF7Boss()) return@register
            if (lbTickTime >= 0) lbTickTime --
            if (lbTickTime < 0) lbTickTime = - 1
        }
    }

    private fun isInF7Boss() = LocationUtils.inDungeon && LocationUtils.inBoss && LocationUtils.dungeonFloorNumber == 7

    private fun reset() {
        lbTickTime = - 1
    }

    private fun renderTimer(ctx: GuiGraphicsExtractor, example: Boolean): Pair<Float, Float> {
        val text = if (example) "§bPY: §a7.10s"
        else {
            if (lbTickTime == - 1) return 0f to 0f
            formatTimer(lbTickTime, PY_TICKS, "§bPY:")
        }

        ctx.drawCenteredString(text, 0f, 0f)
        return text.width().toFloat() to 9f
    }

    private fun formatTimer(time: Int, max: Int, prefixText: String): String {
        val color = when {
            time >= max * 0.66 -> "§a"
            time >= max * 0.33 -> "§6"
            else -> "§c"
        }
        val timeDisplay = if (format.value == 1) time.toString() else (time / 20f).toFixed(2)
        val prefix = if (showPrefix.value) "$prefixText " else ""
        val suffix = if (! showSuffix.value) "" else if (format.value == 1) "t" else "s"
        return prefix + color + timeDisplay + suffix
    }
}

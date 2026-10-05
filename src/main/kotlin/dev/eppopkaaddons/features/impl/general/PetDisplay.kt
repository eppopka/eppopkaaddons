package dev.eppopkaaddons.features.impl.general

import dev.eppopkaaddons.config.types.ColorSetting
import dev.eppopkaaddons.config.types.SliderSetting
import dev.eppopkaaddons.config.types.ToggleSetting
import dev.eppopkaaddons.event.EventBus
import dev.eppopkaaddons.event.impl.*
import dev.eppopkaaddons.features.Feature
import dev.eppopkaaddons.features.annotations.AlwaysActive
import dev.eppopkaaddons.ui.clickgui.enums.CategoryType
import dev.eppopkaaddons.ui.clickgui.foundation.FontSystem
import dev.eppopkaaddons.ui.clickgui.foundation.GlassRenderer.pill
import dev.eppopkaaddons.ui.clickgui.foundation.GlassRenderer.rounded
import dev.eppopkaaddons.ui.clickgui.foundation.Rect
import dev.eppopkaaddons.utils.ChatUtils.formattedText
import dev.eppopkaaddons.utils.ChatUtils.removeFormatting
import dev.eppopkaaddons.utils.ChatUtils.unformattedText
import dev.eppopkaaddons.utils.items.ItemUtils.lore
import dev.eppopkaaddons.utils.location.LocationUtils
import dev.eppopkaaddons.utils.remove
import dev.eppopkaaddons.utils.render.ItemRenderer
import dev.eppopkaaddons.utils.render.Render2D.highlight
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import java.awt.Color
import java.util.ArrayDeque
import kotlin.math.roundToLong

/** Active-pet HUD with a rolling, session-local XP rate and ETA estimates. */
@AlwaysActive
object PetDisplay : Feature(
    description = "Active pet, XP/hour and time-to-level estimates.",
    name = "Pet Overlay",
) {
    override val category = CategoryType.VISUAL

    private val compactMode by ToggleSetting("Compact Mode", false)
        .withDescription("Shows a single-line pet card with only the next-level ETA.")
        .section("Overlay")
    private val accentColor by ColorSetting("Accent", Color(124, 113, 255), false)
    private val backgroundOpacity by SliderSetting("Background Opacity", .88f, .25f, 1f, .01f)
    private val sampleMinutes by SliderSetting("XP Sample Window", 5, 1, 30, 1, " min")
        .withDescription("Controls how much recent XP is used for the XP/hour estimate.")

    private val autoPetTitles by ToggleSetting("Auto Pet Title", false).section("Notifications")
    private val autoPetTitlesDungeonOnly by ToggleSetting("Dungeons Only", false)
        .showIf { autoPetTitles.value }

    private val activePetHighlight by ToggleSetting("Highlight Active Pet", true).section("Pets Menu")
    private val petHighlightColor by ColorSetting("Highlight Color", Color.CYAN)
        .showIf { activePetHighlight.value }

    private val chatPetRuleRegex = Regex("§cAutopet §eequipped your §7\\[Lvl .*] (?<pet>.*)§e! §a§lVIEW RULE")
    private val chatSpawnRegex = Regex("§aYou summoned your (?<pet>.*)§a!")
    private val chatDespawnRegex = Regex("§aYou despawned your .*§a!")
    private val levelRegex = Regex("\\[Lvl (\\d+)]\\s*(.+)$")
    private val xpLineRegex = Regex("([\\d,.]+)\\s*/\\s*([\\d,.]+)")
    private val skillGainRegex = Regex("\\+([\\d,.]+)\\s+(?:Combat|Mining|Farming|Foraging|Fishing|Enchanting|Alchemy|Taming|Carpentry|Runecrafting|Social)(?:\\s|\\()", RegexOption.IGNORE_CASE)
    private val loadoutSlots = setOf(14, 15, 16, 23, 24, 25, 32, 33, 34, 41, 42, 43)
    private val petMenuRegex = Regex("^(\\(\\d/\\d\\) )?Pets$")

    private data class XpSample(val at: Long, val total: Double)
    private data class PetState(
        val stack: ItemStack,
        val level: Int,
        val maxLevel: Int,
        val rarityOffset: Int,
        val xpIntoLevel: Double,
        val xpForLevel: Double,
        val totalXp: Double,
        val maxTotalXp: Double,
    )

    private val samples = ArrayDeque<XpSample>()
    private val petCache = mutableMapOf<String, PetState>()
    private var activePetStack = ItemStack.EMPTY
    private var petName = ""
    private var petLevel = 0
    private var petMaxLevel = 100
    private var rarityOffset = 20
    private var xpIntoLevel = 0.0
    private var xpForLevel = 0.0
    private var totalXp = 0.0
    private var maxTotalXp = 0.0
    private var selectedPetSlot = -1
    private var autoPetTitle = ""
    private var autoPetTitleTicks = 0

    override fun init() {
        hudElement(
            "Pet Overlay",
            shouldDraw = { LocationUtils.inSkyblock && petName.isNotBlank() },
        ) { ctx, example ->
            val model = if (example) Preview else snapshot()
            val width = if (compactMode.value) 166f else 196f
            val height = if (compactMode.value) 44f else 82f
            val bgAlpha = (backgroundOpacity.value * 245).toInt().coerceIn(0, 245)
            ctx.rounded(Rect(0f, 0f, width, height), 10f, Color(6, 9, 15, bgAlpha))
            ctx.rounded(Rect(0f, 0f, 3f, height), 1.5f, accentColor.value)
            ctx.rounded(Rect(1f, 1f, width - 2f, 1f), .5f, Color(255, 255, 255, 22))

            if (!activePetStack.isEmpty) {
                // ItemRenderer is centered around the supplied point. Keeping the
                // point inside the card fixes the pet portrait sitting above it.
                ItemRenderer.drawBatchedItemStack(ctx, activePetStack, 14, 14, 1.55f)
                ItemRenderer.endItemRendererBatch(ctx)
            }
            FontSystem.draw(ctx, FontSystem.ellipsize(model.name, width - 68f, .67f), 51f, 9f, Color(244, 246, 251), .67f, true)
            FontSystem.draw(ctx, "LVL ${model.level}", 51f, 21f, accentColor.value, .54f, true)
            val progress = model.progress.coerceIn(0f, 1f)
            val bar = Rect(11f, 36f, width - 22f, 4f)
            ctx.pill(bar, Color(255, 255, 255, 22))
            ctx.pill(bar.copy(width = bar.width * progress), accentColor.value)

            if (!compactMode.value) {
                FontSystem.draw(ctx, "XP/H", 11f, 49f, Color(139, 148, 166), .46f, true)
                FontSystem.draw(ctx, formatXp(model.xpPerHour), 48f, 49f, Color(226, 231, 240), .5f, true)
                FontSystem.draw(ctx, "NEXT", 11f, 65f, Color(139, 148, 166), .46f, true)
                FontSystem.draw(ctx, formatDuration(model.nextSeconds), 48f, 65f, Color(226, 231, 240), .5f, true)
                FontSystem.draw(ctx, "MAX", 111f, 65f, Color(139, 148, 166), .46f, true)
                FontSystem.draw(ctx, formatDuration(model.maxSeconds), 139f, 65f, Color(226, 231, 240), .5f, true)
            }
            width to height
        } defaults {
            x = 18f
            y = 82f
            scale = 1f
        }

        hudElement(
            "Auto Pet Title",
            enabled = { autoPetTitles.value },
            shouldDraw = { ticker.isActive },
            centered = true,
        ) { context, example ->
            val text = if (example) "&6Golden Dragon" else autoPetTitle
            dev.eppopkaaddons.utils.render.Render2D.run { context.drawCenteredString(text, 0, 0) }
            dev.eppopkaaddons.utils.render.RenderHelper.run { text.width() to text.height() }
        } defaults { scale = 2.5f }

        register<ChatMessageEvent> {
            val msg = event.formattedText
            if (chatDespawnRegex.matches(msg)) {
                clearPet()
                return@register
            }
            val summoned = chatSpawnRegex.find(msg)?.groups?.get("pet")?.value
            val auto = chatPetRuleRegex.find(msg)?.groups?.get("pet")?.value
            val next = (summoned ?: auto)?.removeFormatting()?.replace(Regex("\\s*✦$"), "") ?: return@register
            setPetName(next)
            if (auto != null && autoPetTitles.value && enabled && (!autoPetTitlesDungeonOnly.value || LocationUtils.inDungeon)) {
                autoPetTitle = auto
                autoPetTitleTicks = 40
                ticker.register()
            }
        }

        register<ActionBarMessageEvent> {
            if (petName.isBlank() || xpForLevel <= 0.0) return@register
            val gain = skillGainRegex.findAll(event.unformattedText)
                .sumOf { it.groupValues[1].replace(",", "").toDoubleOrNull() ?: 0.0 }
            if (gain <= 0.0) return@register
            addPetXp(gain)
        }

        register<ContainerFullyOpenedEvent> {
            selectedPetSlot = -1
            if (!event.title.unformattedText.matches(petMenuRegex)) return@register
            for ((slot, stack) in event.items) {
                val lore = stack.lore
                val active = lore.any { it.removeFormatting() == "Click to despawn!" }
                if (syncFromPetItem(stack, lore.map { it.removeFormatting() }, active) && active) {
                    selectedPetSlot = slot
                }
            }
        }

        register<ContainerEvent.SlotClick> {
            if (event.button != 0 || event.clickType != ContainerInput.PICKUP || event.slotId !in loadoutSlots) return@register
            if (!event.screen.title.unformattedText.endsWith(") Loadouts")) return@register
            player.containerMenu.items[event.slotId].lore.find { it.startsWith("§7Pet: ") }?.let {
                setPetName(levelRegex.find(it.removeFormatting())?.groupValues?.get(2) ?: return@let)
            }
        }

        register<ContainerEvent.Render.Slot.Pre> {
            if (!enabled || !activePetHighlight.value || event.slot.index != selectedPetSlot) return@register
            event.slot.highlight(event.context, petHighlightColor.value, 1)
        }
        register<PacketEvent.Sent> {
            if (event.packet is ServerboundContainerClosePacket) selectedPetSlot = -1
        }
        register<MainThreadPacketReceivedEvent.Pre> {
            if (event.packet is ClientboundContainerClosePacket) selectedPetSlot = -1
        }
    }

    private fun syncFromPetItem(stack: ItemStack, lore: List<String>, activate: Boolean): Boolean {
        val formattedName = stack.hoverName.formattedText
        val cleanName = formattedName.removeFormatting()
        val match = levelRegex.find(cleanName) ?: return false
        val level = match.groupValues[1].toIntOrNull() ?: return false
        val name = match.groupValues[2].trim().removeSuffix(" ✦")
        val ratio = lore.asSequence().mapNotNull(xpLineRegex::find).firstOrNull() ?: return false
        val current = ratio.groupValues[1].replace(",", "").toDoubleOrNull() ?: 0.0
        val needed = ratio.groupValues[2].replace(",", "").toDoubleOrNull() ?: 0.0
        val offset = rarityOffsetFrom(formattedName)
        val max = if (name.equals("Golden Dragon", true) || name.equals("Jade Dragon", true) || name.equals("Rose Dragon", true)) 200 else 100
        val state = PetState(
            stack.copy(),
            level,
            max,
            offset,
            current,
            needed,
            totalBeforeLevel(level, offset, max) + current,
            totalBeforeLevel(max, offset, max),
        )
        petCache[name.lowercase()] = state
        if (!activate) return true

        val changed = !name.equals(petName, true)
        petName = name
        applyState(state)
        if (changed) samples.clear()
        recordSample()
        return true
    }

    private fun setPetName(name: String) {
        val clean = name.removeFormatting().trim()
        if (clean.equals(petName, true)) return
        petName = clean
        petCache[clean.lowercase()]?.let {
            applyState(it)
            samples.clear()
            recordSample()
            return
        }
        activePetStack = ItemStack.EMPTY
        petLevel = 0
        xpIntoLevel = 0.0
        xpForLevel = 0.0
        totalXp = 0.0
        maxTotalXp = 0.0
        samples.clear()
    }

    private fun applyState(state: PetState) {
        activePetStack = state.stack.copy()
        petLevel = state.level
        petMaxLevel = state.maxLevel
        rarityOffset = state.rarityOffset
        xpIntoLevel = state.xpIntoLevel
        xpForLevel = state.xpForLevel
        totalXp = state.totalXp
        maxTotalXp = state.maxTotalXp
    }

    private fun addPetXp(gain: Double) {
        totalXp += gain
        xpIntoLevel += gain
        while (xpForLevel > 0.0 && xpIntoLevel >= xpForLevel && petLevel < petMaxLevel) {
            xpIntoLevel -= xpForLevel
            petLevel++
            xpForLevel = requirementForLevel(petLevel, rarityOffset, petMaxLevel).toDouble()
        }
        recordSample()
    }

    private fun recordSample() {
        val now = System.currentTimeMillis()
        samples.addLast(XpSample(now, totalXp))
        val cutoff = now - sampleMinutes.value * 60_000L
        while (samples.size > 2 && samples.first.at < cutoff) samples.removeFirst()
    }

    private data class OverlayModel(
        val name: String,
        val level: Int,
        val progress: Float,
        val xpPerHour: Double,
        val nextSeconds: Double?,
        val maxSeconds: Double?,
    )

    private fun snapshot(): OverlayModel {
        val first = samples.firstOrNull()
        val last = samples.lastOrNull()
        val rate = if (first != null && last != null && last.at - first.at >= 10_000L) {
            ((last.total - first.total) * 3_600_000.0 / (last.at - first.at)).coerceAtLeast(0.0)
        } else 0.0
        val nextLeft = (xpForLevel - xpIntoLevel).coerceAtLeast(0.0)
        val maxLeft = (maxTotalXp - totalXp).coerceAtLeast(0.0)
        return OverlayModel(
            petName,
            petLevel,
            if (xpForLevel > 0) (xpIntoLevel / xpForLevel).toFloat() else 0f,
            rate,
            if (rate > 0 && petLevel < petMaxLevel) nextLeft / rate * 3600.0 else null,
            if (rate > 0 && petLevel < petMaxLevel) maxLeft / rate * 3600.0 else null,
        )
    }

    private fun clearPet() {
        petName = ""
        petLevel = 0
        activePetStack = ItemStack.EMPTY
        samples.clear()
        selectedPetSlot = -1
    }

    private fun rarityOffsetFrom(formatted: String): Int {
        val code = Regex("§([0-9a-f])", RegexOption.IGNORE_CASE).find(formatted)?.groupValues?.get(1)?.lowercase()
        return when (code) {
            "f" -> 0
            "a" -> 6
            "9" -> 11
            "5" -> 16
            "6", "d" -> 20
            else -> 20
        }
    }

    private fun totalBeforeLevel(level: Int, offset: Int, max: Int): Double {
        var total = 0.0
        for (current in 1 until level.coerceAtMost(max)) total += requirementForLevel(current, offset, max)
        return total
    }

    private fun requirementForLevel(level: Int, offset: Int, max: Int): Int {
        val index = offset + level - 1
        return when {
            index in PET_LEVELS.indices -> PET_LEVELS[index]
            max == 200 -> 1_886_700
            else -> 0
        }
    }

    private fun formatXp(value: Double): String = when {
        value <= 0 -> "—"
        value >= 1_000_000 -> "%.2fM".format(value / 1_000_000.0)
        value >= 1_000 -> "%.1fk".format(value / 1_000.0)
        else -> value.roundToLong().toString()
    }

    private fun formatDuration(seconds: Double?): String {
        if (seconds == null || !seconds.isFinite()) return "—"
        val s = seconds.roundToLong().coerceAtLeast(0)
        return when {
            s >= 86_400 -> "${s / 86_400}d ${(s % 86_400) / 3600}h"
            s >= 3_600 -> "${s / 3600}h ${(s % 3600) / 60}m"
            s >= 60 -> "${s / 60}m ${s % 60}s"
            else -> "${s}s"
        }
    }

    private val ticker = EventBus.listener<TickEvent.Start> {
        autoPetTitleTicks--
        if (autoPetTitleTicks <= 0) listener.unregister()
    }

    private val Preview = OverlayModel("Golden Dragon", 147, .64f, 1_240_000.0, 83.0, 232_200.0)

    private val PET_LEVELS = intArrayOf(
        100,110,120,130,145,160,175,190,210,230,250,275,300,330,360,400,440,490,540,600,
        660,730,800,880,960,1050,1150,1260,1380,1510,1650,1800,1960,2130,2310,2500,2700,2920,3160,3420,
        3700,4000,4350,4750,5200,5700,6300,7000,7800,8700,9700,10800,12000,13300,14700,16200,17800,19500,21300,23200,
        25200,27400,29800,32400,35200,38200,41400,44800,48400,52200,56200,60400,64800,69400,74200,79200,84700,90700,97200,104200,
        111700,119700,128200,137200,146700,156700,167700,179700,192700,206700,221700,237700,254700,272700,291700,311700,333700,357700,383700,411700,
        441700,476700,516700,561700,611700,666700,726700,791700,861700,936700,1016700,1101700,1191700,1286700,1386700,1496700,1616700,1746700,1886700,
    )
}

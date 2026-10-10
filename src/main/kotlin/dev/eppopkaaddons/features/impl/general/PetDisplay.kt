package dev.eppopkaaddons.features.impl.general

import dev.eppopkaaddons.config.types.ButtonSetting
import dev.eppopkaaddons.config.types.ColorSetting
import dev.eppopkaaddons.config.types.DropdownSetting
import dev.eppopkaaddons.config.types.SliderSetting
import dev.eppopkaaddons.config.types.ToggleSetting
import dev.eppopkaaddons.event.EventBus
import dev.eppopkaaddons.event.impl.*
import dev.eppopkaaddons.features.Feature
import dev.eppopkaaddons.features.annotations.AlwaysActive
import dev.eppopkaaddons.ui.hud.hotbar.HotbarPainter
import dev.eppopkaaddons.ui.hud.pet.PetOverlayView
import dev.eppopkaaddons.utils.ChatUtils.unformattedText
import dev.eppopkaaddons.utils.items.ItemUtils.customData
import dev.eppopkaaddons.utils.items.ItemUtils.lore
import dev.eppopkaaddons.utils.location.LocationUtils
import dev.eppopkaaddons.utils.pets.PetXpHistory
import dev.eppopkaaddons.utils.pets.PetXpModel
import dev.eppopkaaddons.utils.render.Render2D.highlight
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import java.awt.Color
import kotlin.jvm.optionals.getOrNull
import kotlin.math.exp

/** Server-snapshot XP card. Never adds generic skill gains to pet experience. */
@AlwaysActive
object PetDisplay : Feature(
    description = "A styled active-pet card with verified server XP, sync age and sampled estimates.",
    name = "Pet Overlay",
) {
    private val style by DropdownSetting("Style", 1, listOf("Minimal", "Glass", "Neon")).section("Appearance")
    private val compactMode by ToggleSetting("Compact Mode", false)
    private val accent by ColorSetting("Accent Color", Color(94, 159, 232), false).jsonName("Accent")
    private val background by ColorSetting("Background Color", Color(12, 17, 25), false)
    private val backgroundOpacity by SliderSetting("Background Opacity", .75f, 0f, 1f, .01f)
    private val cornerRadius by SliderSetting("Corner Radius", 9f, 0f, 18f, .5f)
    private val gradient by ToggleSetting("Gradient", true)
    private val gradientColor by ColorSetting("Gradient Color", Color(26, 34, 48), false).showIf { gradient.value }
    private val glow by ToggleSetting("Glow", true).section("Effects")
    private val shadow by ToggleSetting("Shadow", true)
    private val shimmer by ToggleSetting("Animated Shimmer", false)
    private val smoothProgress by ToggleSetting("Smooth Progress", true)
    private val sampleMinutes by SliderSetting("XP Sample Window", 5, 1, 30, 1, " min").section("Data")
        .withDescription("XP/hour is sampled from actual pet-data updates, not the skill action bar.")
    private val refresh by ButtonSetting("Sync Pet Data (/pets)") {
        if (LocationUtils.inSkyblock) mc.player?.connection?.sendCommand("pets")
    }.withDescription("Opens the Pets menu to obtain the server's current pet XP and texture.")
    private val autoPetTitles by ToggleSetting("Auto Pet Title", false).section("Notifications")
    private val autoPetTitlesDungeonOnly by ToggleSetting("Dungeons Only", false).showIf { autoPetTitles.value }
    private val activePetHighlight by ToggleSetting("Highlight Active Pet", true).section("Pets Menu")
    private val petHighlightColor by ColorSetting("Highlight Color", Color.CYAN).showIf { activePetHighlight.value }

    private data class State(val key: String, val reading: PetXpModel.Reading, val stack: ItemStack,
                             val at: Long, val stableIdentity: Boolean)
    private val cache = linkedMapOf<String, State>()
    private var active: State? = null
    private var pendingName = ""
    private var cached = false
    private val history = PetXpHistory()
    private var selectedPetSlot = -1
    private var menuId = -1
    private var autoTitle = ""
    private var titleTicks = 0
    private var paintedKey = ""
    private var paintedProgress = 0f
    private var lastDraw = 0L
    private val effectEpoch = System.nanoTime()
    private val petMenu = Regex("^(?:\\(\\d+/\\d+\\)\\s*)?Pets$", RegexOption.IGNORE_CASE)
    private val summon = Regex("^You summoned your (.+?)!$", RegexOption.IGNORE_CASE)
    private val autopet = Regex("^Autopet equipped your (.+?)!", RegexOption.IGNORE_CASE)
    private val despawn = Regex("^You despawned your .+!$", RegexOption.IGNORE_CASE)

    override fun init() {
        hudElement("Pet Overlay", shouldDraw = { LocationUtils.inSkyblock }) { ctx, example ->
            val state = active
            val reading = if (example && state == null) preview() else state?.reading
            val now = System.currentTimeMillis()
            val drawTime = System.nanoTime()
            val elapsed = if (lastDraw == 0L) 0f else ((drawTime - lastDraw) / 1e9).toFloat().coerceIn(0f, .1f)
            lastDraw = drawTime
            val key = "${state?.key}:${reading?.level}:$example"
            val target = reading?.progress?.toFloat()
            if (target == null || key != paintedKey || !smoothProgress.value || !motion()) {
                paintedProgress = target ?: 0f
                paintedKey = key
            } else paintedProgress += (target - paintedProgress) * (1.0 - exp(-14.0 * elapsed)).toFloat()
            val look = HotbarPainter.Appearance(
                HotbarPainter.Style.entries[style.value.coerceIn(0, 2) + 1],
                background.value, gradientColor.value, accent.value, backgroundOpacity.value,
                cornerRadius.value, 1f, gradient.value, false,
                shadow.value, .65f, glow.value, .5f, 6f,
                shimmer.value && motion(), .12f, .2f, 4f,
            )
            PetOverlayView.draw(ctx, reading, pendingName, state?.stack ?: ItemStack.EMPTY,
                if (target != null) paintedProgress else null,
                if (example && state == null) 1_240_000.0 else history.perHour(now, sampleMinutes.value),
                state?.at ?: 0L, now, cached, example, compactMode.value, look,
                ((drawTime - effectEpoch) / 1e9).toFloat())
        } defaults { x = 18f; y = 82f; scale = 1f }

        hudElement("Auto Pet Title", enabled = { autoPetTitles.value },
            shouldDraw = { titleTicker.isActive }, centered = true) { ctx, example ->
            val text = if (example) "&6Golden Dragon" else autoTitle
            dev.eppopkaaddons.utils.render.Render2D.run { ctx.drawCenteredString(text, 0, 0) }
            dev.eppopkaaddons.utils.render.RenderHelper.run { text.width() to text.height() }
        } defaults { scale = 2.5f }

        register<WorldChangeEvent> {
            cache.clear(); clearActive(); menuId = -1; selectedPetSlot = -1
            titleTicker.unregister(); titleTicks = 0
        }
        register<ChatMessageEvent> {
            val text = PetXpModel.clean(event.unformattedText)
            if (despawn.matches(text)) { clearActive(); return@register }
            val auto = autopet.find(text)?.groupValues?.get(1)
            val selected = summon.find(text)?.groupValues?.get(1) ?: auto ?: return@register
            selectByName(selected)
            if (auto != null && enabled && autoPetTitles.value &&
                (!autoPetTitlesDungeonOnly.value || LocationUtils.inDungeon)) {
                autoTitle = "&6${PetXpModel.named(auto)?.second ?: PetXpModel.normalizeName(auto)}"
                titleTicks = 40
                titleTicker.register()
            }
        }
        register<ContainerFullyOpenedEvent> {
            if (!petMenu.matches(PetXpModel.clean(event.title.unformattedText))) return@register
            beginMenu(event.windowId)
            selectedPetSlot = -1
            event.items.forEach { (slot, stack) -> observe(stack, slot, event.windowId, true) }
        }
        register<MainThreadPacketReceivedEvent.Post> {
            when (val packet = event.packet) {
                is ClientboundContainerSetSlotPacket -> {
                    val pets = isPetsMenu(packet.containerId)
                    observe(packet.item, packet.slot, packet.containerId, pets)
                }
                is ClientboundContainerSetContentPacket -> {
                    val pets = isPetsMenu(packet.containerId())
                    if (pets) selectedPetSlot = -1
                    packet.items().forEachIndexed { slot, stack -> observe(stack, slot, packet.containerId(), pets) }
                }
                is ClientboundContainerClosePacket -> {
                    if (packet.containerId == menuId) { menuId = -1; selectedPetSlot = -1 }
                }
            }
        }
        register<PacketEvent.Sent> {
            if (event.packet is ServerboundContainerClosePacket) { menuId = -1; selectedPetSlot = -1 }
        }
        register<ContainerEvent.Render.Slot.Pre> {
            if (enabled && activePetHighlight.value && event.slot.index == selectedPetSlot &&
                petMenu.matches(PetXpModel.clean(event.screen.title.unformattedText))) {
                event.slot.highlight(event.context, petHighlightColor.value, 1)
            }
        }
        register<ContainerEvent.SlotClick> {
            if (event.button != 0 || event.clickType != ContainerInput.PICKUP ||
                !event.screen.title.unformattedText.endsWith(") Loadouts")) return@register
            val stack = mc.player?.containerMenu?.items?.getOrNull(event.slotId) ?: return@register
            stack.lore.map(PetXpModel::clean).firstOrNull { it.startsWith("Pet: ") }
                ?.substringAfter("Pet: ")?.let(::selectByName)
        }
    }

    private fun isPetsMenu(id: Int): Boolean {
        val screen = mc.screen as? AbstractContainerScreen<*> ?: return false
        val matches = mc.player?.containerMenu?.containerId == id && petMenu.matches(PetXpModel.clean(screen.title.unformattedText))
        if (matches) beginMenu(id)
        return matches
    }

    private fun beginMenu(id: Int) {
        if (menuId != id) {
            // Without a UUID we cannot prove that a slot in a new window is the
            // same pet. Do not merge its rates or retain ambiguous old copies.
            cache.entries.removeAll { !it.value.stableIdentity }
            menuId = id
        }
    }

    private fun observe(stack: ItemStack, slot: Int, container: Int, petsMenu: Boolean) {
        if (stack.isEmpty) return
        val data = stack.customData
        val attributes = data.getCompound("ExtraAttributes").getOrNull() ?: data
        val json = data.getString("petInfo").getOrNull()?.takeIf { it.isNotBlank() }
            ?: attributes.getString("petInfo").getOrNull()
        val lore = stack.lore.map(PetXpModel::clean)
        val reading = PetXpModel.read(stack.hoverName.unformattedText, lore, json) ?: return
        // UUID distinguishes two pets with the same name/rarity. A window+slot
        // fallback is not reused between windows for rate estimation.
        val uuid = reading.uuid ?: data.getString("uuid").getOrNull()?.takeIf { it.isNotBlank() }
            ?: attributes.getString("uuid").getOrNull()?.takeIf { it.isNotBlank() }
        val key = uuid ?: "${reading.type}:${reading.rarity}:window$container:slot$slot"
        val isActive = petsMenu && (reading.active || lore.any { it.equals("Click to despawn!", true) })
        val matchingActive = active?.key == key
        if (!petsMenu && !matchingActive) return
        val state = State(key, reading, stack.copy(), System.currentTimeMillis(), uuid != null)
        cache[key] = state
        if (cache.size > 512) cache.entries.firstOrNull { it.key != active?.key }?.key?.let(cache::remove)
        if (petsMenu && matchingActive && !isActive && lore.any { it.equals("Click to summon!", true) }) {
            clearActive()
            selectedPetSlot = -1
            return
        }
        if (isActive || matchingActive) {
            if (active?.key != key) history.clear()
            active = state; pendingName = reading.name; cached = false
            if (petsMenu && isActive) selectedPetSlot = slot
            if (reading.source != PetXpModel.Source.UNKNOWN) reading.totalXp?.let {
                history.record(it, state.at, sampleMinutes.value)
            }
        }
    }

    private fun selectByName(value: String) {
        val header = PetXpModel.named(value)
        val name = header?.second ?: PetXpModel.normalizeName(value)
        val matches = cache.values.filter { it.reading.name.equals(name, true) &&
            (header == null || it.reading.level == header.first) }
        // Ambiguous duplicate pets are deliberately not guessed.
        if (matches.size == 1) {
            val state = matches.single()
            if (active?.key != state.key) history.clear()
            active = state; cached = true
        } else { active = null; history.clear() }
        pendingName = name
        paintedKey = ""
    }
    private fun clearActive() {
        active = null; pendingName = ""; history.clear(); cached = false; paintedKey = ""
    }
    private fun motion() = AetherOSSettings.animations.value && !AetherOSSettings.reducedMotion.value
    private fun preview(): PetXpModel.Reading {
        val start = requireNotNull(PetXpModel.totalBeforeLevel("GOLDEN_DRAGON", "LEGENDARY", 147))
        return requireNotNull(PetXpModel.read("[Lvl 147] Golden Dragon", emptyList(),
            """{"type":"GOLDEN_DRAGON","tier":"LEGENDARY","exp":${start + 1_207_488}}"""))
    }
    private val titleTicker = EventBus.listener<TickEvent.Start> {
        if (--titleTicks <= 0) listener.unregister()
    }
}

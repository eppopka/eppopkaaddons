package dev.eppopkaaddons.features.impl.qol

//#if CHEAT

import dev.eppopkaaddons.event.impl.ChatMessageEvent
import dev.eppopkaaddons.event.impl.ContainerFullyOpenedEvent
import dev.eppopkaaddons.event.impl.MainThreadPacketReceivedEvent
import dev.eppopkaaddons.features.Feature
import dev.eppopkaaddons.config.types.ButtonSetting
import dev.eppopkaaddons.config.types.RangeSliderSetting
import dev.eppopkaaddons.config.types.SliderSetting
import dev.eppopkaaddons.config.types.ToggleSetting
import dev.eppopkaaddons.utils.ChatUtils
import dev.eppopkaaddons.utils.ChatUtils.unformattedText
import dev.eppopkaaddons.utils.GuiUtils
import dev.eppopkaaddons.utils.PlayerUtils
import dev.eppopkaaddons.utils.ThreadUtils
import dev.eppopkaaddons.utils.dungeons.DungeonListener
import dev.eppopkaaddons.utils.dungeons.enums.DungeonClass
import dev.eppopkaaddons.utils.items.ItemUtils.skyblockId
import dev.eppopkaaddons.utils.location.LocationUtils
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.world.item.ItemStack
import kotlin.random.Random

/**
 * Auto Rag - full Ragnarock rotation, fired automatically on the M7 dragons line.
 *
 * Sequence:
 *  1. the Wither King says his dragons line in phase 5 (or you press Cast Now);
 *  2. find the Ragnarock Axe in the hotbar and swap to it;
 *  3. right click it after a randomized delay;
 *  4. wait for the axe to actually PROC. The proc is detected the same way the
 *     dungeon Ragnarock feature detects it: an entity.wolf.death sound packet
 *     while the axe is held (any pitch except 1.4920635, which is a different
 *     wolf sound);
 *  5. swap to Sword of Bad Health, or to Terminator if the sword is missing;
 *  6. optionally run /pet and click the Ender Dragon pet.
 *
 * Every delay is randomized inside a min/max window, and the pet click gets an
 * extra millisecond level jitter, so two casts never produce the same timing
 * fingerprint.
 */
object AutoRag: Feature(
    description = "Casts Ragnarock on the M7 dragons line, then swaps to your weapon and optionally summons the Ender Dragon pet.",
    name = "Auto Rag"
) {
    private val castNow by ButtonSetting("Cast Now") { cast(manual = true) }
        .withDescription("Runs the whole rotation right now, ignoring the M7 trigger.")
    private val classCheck by ToggleSetting("Class Check", true)
        .withDescription("Only cast as Archer, Mage or Berserk. Tank and Healer never cast.")
    private val swapBack by ToggleSetting("Swap To Weapon", true)
        .withDescription("After the proc, swap to Sword of Bad Health, or to Terminator if the sword is missing.")
    private val petSwap by ToggleSetting("Summon Ender Dragon")
        .withDescription("After the proc, run /pet and click the Ender Dragon pet in the menu.")
    private val castDelay by RangeSliderSetting("Cast Delay", 2, 6, 1, 20, 1, " ticks")
        .withDescription("Randomized delay between swapping to the axe and right clicking it.")
    private val menuDelay by RangeSliderSetting("Menu Delay", 4, 10, 1, 20, 1, " ticks")
        .withDescription("Randomized delay before clicking the pet once the menu opens.")
    private val timeout by SliderSetting("Timeout", 5, 2, 15, 1)
        .withDescription("Seconds to wait for the proc before giving up.")

    private const val RAG_ID = "RAGNAROCK_AXE"
    private const val SWORD_ID = "SWORD_OF_BAD_HEALTH"
    private const val BOW_ID = "TERMINATOR"
    private const val PET_NAME = "ender dragon"
    private const val M7_RAG_MESSAGE = "[BOSS] Wither King: I no longer wish to fight, but I know that will not stop you."

    private val cancelRegex = Regex("Ragnarock was cancelled due to (?:being hit|taking damage)!")

    /** Classes that are allowed to cast while Class Check is on. */
    private val castingClasses = setOf(DungeonClass.Archer, DungeonClass.Mage, DungeonClass.Berserk)

    /** True between the right click and the proc / cancel / timeout. */
    private var casting = false

    /** True between sending /pet and clicking the pet, so we never hijack an unrelated menu. */
    private var awaitingPetMenu = false

    /** Random tick count inside an inclusive window, tolerant of the sliders being set backwards. */
    private fun randomTicks(a: Int, b: Int): Int {
        val low = minOf(a, b)
        val high = maxOf(a, b)
        return if (low == high) low else Random.nextInt(low, high + 1)
    }

    private fun canCast(): Boolean {
        if (! classCheck.value) return true
        val clazz = DungeonListener.thePlayer?.clazz ?: return false
        return clazz in castingClasses
    }

    fun cast(manual: Boolean = false) {
        if (! enabled) return
        if (mc.player == null) return

        if (casting) {
            if (manual) ChatUtils.modMessage("&eAlready casting Ragnarock.")
            return
        }

        if (! canCast()) {
            if (manual) ChatUtils.modMessage("&cClass Check: only Archer, Mage and Berserk cast Ragnarock.")
            return
        }

        val ragSlot = PlayerUtils.findHotbarSlot { it.skyblockId == RAG_ID }
        if (ragSlot == null) {
            if (manual) ChatUtils.modMessage("&cNo Ragnarock Axe in your hotbar!")
            return
        }

        casting = true
        PlayerUtils.swapToSlot(ragSlot)

        // Randomized swap -> right click gap, plus a sub tick millisecond offset.
        ThreadUtils.scheduledTask(randomTicks(castDelay.value.lower, castDelay.value.upper)) {
            if (! casting) return@scheduledTask
            ThreadUtils.setTimeout(Random.nextLong(0L, 45L)) {
                if (! casting) return@setTimeout
                mc.execute { PlayerUtils.rightClick() }
            }
        }

        // Safety net: never stay stuck in the casting state.
        ThreadUtils.setTimeout(timeout.value * 1000L) {
            if (! casting) return@setTimeout
            ChatUtils.modMessage("&cRagnarock did not proc in time.")
            finish()
        }
    }

    /** Called on proc / cancel / timeout: swap weapon and optionally summon the pet. */
    private fun finish() {
        if (! casting) return
        casting = false

        if (swapBack.value) {
            val weapon = PlayerUtils.findHotbarSlot { it.skyblockId == SWORD_ID }
                ?: PlayerUtils.findHotbarSlot { it.skyblockId == BOW_ID }

            if (weapon != null) PlayerUtils.swapToSlot(weapon)
            else ChatUtils.modMessage("&cNeither Sword of Bad Health nor Terminator found in the hotbar.")
        }

        if (petSwap.value) {
            awaitingPetMenu = true
            ThreadUtils.setTimeout(Random.nextLong(180L, 520L)) {
                if (! awaitingPetMenu) return@setTimeout
                ChatUtils.sendCommand("/pet")
            }
            // If the menu never opens (command failed, lobby, etc.) drop the flag.
            ThreadUtils.setTimeout(7000L) { awaitingPetMenu = false }
        }
    }

    private fun matchesPet(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        return stack.hoverName.unformattedText.lowercase().contains(PET_NAME)
    }

    override fun init() {
        // Proc detection, identical to the dungeon Ragnarock feature.
        register<MainThreadPacketReceivedEvent.Pre> {
            if (! casting) return@register
            val packet = event.packet
            if (packet !is ClientboundSoundPacket) return@register
            if (packet.sound.value().location().path != "entity.wolf.death") return@register
            if (packet.pitch.toDouble() == 1.4920635) return@register

            val held = mc.player?.mainHandItem ?: return@register
            if (held.skyblockId != RAG_ID) return@register

            finish()
        }

        register<ChatMessageEvent> {
            val msg = event.unformattedText

            if (casting && msg.matches(cancelRegex)) {
                ChatUtils.modMessage("&cRagnarock cancelled.")
                finish()
                return@register
            }

            if (! casting && LocationUtils.F7Phase == 5 && msg == M7_RAG_MESSAGE) cast()
        }

        // Click the Ender Dragon pet once the /pet menu is fully loaded.
        register<ContainerFullyOpenedEvent> {
            if (! awaitingPetMenu) return@register
            if (! event.title.unformattedText.lowercase().contains("pet")) return@register
            awaitingPetMenu = false

            val slot = event.items.entries.find { matchesPet(it.value) }?.key
            if (slot == null) {
                ChatUtils.modMessage("&cEnder Dragon pet not found on this page.")
                return@register
            }

            ThreadUtils.scheduledTask(randomTicks(menuDelay.value.lower, menuDelay.value.upper)) {
                GuiUtils.clickSlot(slot, GuiUtils.ButtonType.LEFT)
                ThreadUtils.scheduledTask(randomTicks(2, 5)) { mc.player?.closeContainer() }
            }
        }
    }
}
//#endif

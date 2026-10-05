package dev.eppopkaaddons.features.impl.qol

//#if CHEAT

import dev.eppopkaaddons.event.impl.TickEvent
import dev.eppopkaaddons.features.Feature
import dev.eppopkaaddons.mixin.IKeyMapping
import dev.eppopkaaddons.config.types.SliderSetting
import dev.eppopkaaddons.config.types.ToggleSetting
import net.minecraft.client.KeyMapping
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import kotlin.random.Random

/**
 * Auto Reel (Hypixel Skyblock Hunting).
 *
 * v2 changes after in-game testing:
 * - Detection now targets the actual "REEL" ArmorStand label (nameplate stand),
 *   scanned from the whole loaded entity list instead of the render list, with
 *   a much larger default range (the label floats well above the mob).
 * - The click now goes through the real vanilla right-click input path
 *   (KeyMapping.click on your Use Item key), exactly like a physical mouse
 *   press, instead of calling gameMode.useItem directly. This is the main fix
 *   for "it detects but nothing happens".
 * - Humanization: randomized reaction delay between Min/Max Reaction, so every
 *   reel is pressed at a slightly different time instead of on a fixed tick.
 * - Each REEL label is only reacted to once, so it can't machine-gun clicks.
 */
object AutoReel: Feature(name = "Auto Reel") {
    private val range by SliderSetting("Range", 16f, 4f, 32f, 1f)
        .withDescription("How far away a REEL label may be for it to count.")
    private val requireLasso by ToggleSetting("Require Lasso In Hand")
        .withDescription("Only reel while you are actually holding a Lasso. Turn this off if detection of your lasso item fails.")
    private val minReaction by SliderSetting("Min Reaction (ms)", 160, 0, 1000, 10)
        .withDescription("Lower bound of the randomized human reaction delay.")
    private val maxReaction by SliderSetting("Max Reaction (ms)", 420, 0, 1500, 10)
        .withDescription("Upper bound of the randomized human reaction delay.")
    private val cooldown by SliderSetting("Cooldown (Ticks)", 10, 1, 40, 1)
        .withDescription("Minimum ticks between two automatic clicks.")

    private var pendingTicks = -1
    private var handledStandId = -1
    private var lastClickTick = 0L

    private fun isHoldingLasso(): Boolean {
        val player = mc.player ?: return false
        return listOf(player.mainHandItem, player.offhandItem).any {
            ! it.isEmpty && it.hoverName.string.contains("Lasso", ignoreCase = true)
        }
    }

    private fun findReelStand(player: Player): ArmorStand? {
        val level = mc.level ?: return null
        val box = player.boundingBox.inflate(range.value.toDouble())
        return level.getEntitiesOfClass(ArmorStand::class.java, box) { stand ->
            stand.customName?.string?.contains("REEL", ignoreCase = true) == true
        }.minByOrNull { it.distanceToSqr(player) }
    }

    private fun randomDelayTicks(): Int {
        val min = minReaction.value
        val max = maxReaction.value.coerceAtLeast(min)
        val ms = if (max <= min) min else Random.nextInt(min, max + 1)
        return (ms / 50).coerceAtLeast(0)
    }

    private fun pressUseKey() {
        // Real right-click: bumps the Use Item keybind's click counter, so vanilla
        // runs its normal startUseItem() logic on the next tick (item use, entity
        // interaction, hand swing and packets all handled by the game itself).
        val key = (mc.options.keyUse as IKeyMapping).key
        KeyMapping.click(key)
    }

    init {
        register<TickEvent.End> {
            if (! enabled) return@register
            val player = mc.player ?: return@register
            val now = mc.level?.gameTime ?: return@register

            if (pendingTicks > 0) {
                pendingTicks --
                return@register
            }
            if (pendingTicks == 0) {
                pendingTicks = - 1
                if (! requireLasso.value || isHoldingLasso()) {
                    pressUseKey()
                    lastClickTick = now
                }
                return@register
            }

            if (requireLasso.value && ! isHoldingLasso()) return@register
            if (now - lastClickTick < cooldown.value.toLong()) return@register

            val stand = findReelStand(player)
            if (stand == null) {
                handledStandId = - 1
                return@register
            }
            if (stand.id == handledStandId) return@register

            handledStandId = stand.id
            pendingTicks = randomDelayTicks()
        }
    }
}
//#endif

package dev.eppopkaaddons.features.impl.hunting

//#if CHEAT

import dev.eppopkaaddons.config.types.ColorSetting
import dev.eppopkaaddons.config.types.SliderSetting
import dev.eppopkaaddons.config.types.TextInputSetting
import dev.eppopkaaddons.config.types.ToggleSetting
import dev.eppopkaaddons.event.impl.CheckEntityGlowEvent
import dev.eppopkaaddons.event.impl.EntityUnloadEvent
import dev.eppopkaaddons.event.impl.MainThreadPacketReceivedEvent
import dev.eppopkaaddons.event.impl.RenderWorldEvent
import dev.eppopkaaddons.event.impl.WorldChangeEvent
import dev.eppopkaaddons.features.Feature
import dev.eppopkaaddons.utils.ChatUtils.formattedText
import dev.eppopkaaddons.utils.ChatUtils.removeFormatting
import dev.eppopkaaddons.utils.MathUtils.add
import dev.eppopkaaddons.utils.render.RenderHelper.renderVec
import dev.eppopkaaddons.utils.render.world.Render3D.renderTracer
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand
import java.awt.Color

/**
 * Highlights any mob whose nameplate matches a name you type in.
 *
 * Skyblock mobs are ordinary vanilla entities with an invisible marker armor
 * stand floating above them carrying the display name, so the name is matched on
 * the stand and the glow is applied to the mob right underneath it.
 */
object BestiaryESP: Feature(
    description = "Highlights every mob whose name contains one of your search terms.",
    name = "Bestiary ESP"
) {
    private val names by TextInputSetting("Names", "")
        .section("Search")
        .withDescription("Comma separated names to look for, e.g. \"Skeleton, Zombie\". A term matches when it appears anywhere in the mob's name.")

    private val exactMatch by ToggleSetting("Exact Match", false)
        .withDescription("Require the whole name to equal the term instead of just containing it.")

    private val glowColor by ColorSetting("Glow Color", Color.CYAN, false)
        .section("Rendering")
        .withDescription("Colour used for the glow and the tracer.")

    private val showTracer by ToggleSetting("Show Tracer", true)
        .withDescription("Draws a line to the nearest match.")

    private val maxDistance by SliderSetting("Tracer Range", 0, 0, 100, 5)
        .withDescription("Only draw the tracer within this many blocks. 0 = no limit.")
        .showIf { showTracer.value }

    /** entity id -> mob, so the nearest one can be found for the tracer */
    private val matches = HashMap<Int, Entity>()

    /** Armor stand ids already resolved, so each nameplate is handled once. */
    private val checked = HashSet<Int>()

    /** Search terms, re-split only when the setting text actually changes. */
    private var terms = emptyList<String>()
    private var termsSource: String? = null

    private fun terms(): List<String> {
        val raw = names.value
        if (raw != termsSource) {
            termsSource = raw
            terms = raw.split(',').map { it.trim() }.filter(String::isNotEmpty)
            // A term that no longer matches must not keep old mobs glowing.
            matches.clear()
            checked.clear()
        }
        return terms
    }

    private fun matchesName(name: String, terms: List<String>) = terms.any { term ->
        if (exactMatch.value) name.equals(term, ignoreCase = true)
        else name.contains(term, ignoreCase = true)
    }

    override fun init() {
        register<MainThreadPacketReceivedEvent.Post> {
            val terms = terms()
            if (terms.isEmpty()) return@register
            if (event.packet !is ClientboundSetEntityDataPacket) return@register

            val stand = mc.level?.getEntity(event.packet.id) as? ArmorStand ?: return@register
            val name = stand.customName?.formattedText?.removeFormatting() ?: return@register

            if (matchesName(name, terms)) resolveMob(stand)
        }

        register<EntityUnloadEvent> {
            matches.remove(event.entity.id)
            checked.remove(event.entity.id)
        }

        register<WorldChangeEvent> {
            matches.clear()
            checked.clear()
        }

        register<CheckEntityGlowEvent> {
            if (terms().isEmpty()) return@register
            if (event.entity.id in matches) event.color = glowColor.value
        }

        register<RenderWorldEvent> {
            if (! showTracer.value || terms().isEmpty()) return@register
            val player = mc.player ?: return@register

            val nearest = matches.values
                .filter { it.isAlive }
                .minByOrNull { it.distanceToSqr(player) }
                ?: return@register

            val limit = maxDistance.value
            if (limit > 0 && nearest.distanceToSqr(player) > (limit.toDouble() * limit)) return@register

            event.ctx.renderTracer(nearest.renderVec.add(y = 1.0), glowColor.value, 2)
        }
    }

    /**
     * Finds the mob a nameplate belongs to.
     *
     * Hypixel spawns the marker stand right after its mob, so the mob almost always
     * holds the previous entity id; the box scan below it is the fallback for the
     * cases where that does not hold.
     */
    private fun resolveMob(stand: ArmorStand) {
        if (! checked.add(stand.id)) return

        val candidate = stand.level().getEntity(stand.id - 1)
        if (candidate != null && candidate !is ArmorStand) {
            matches[candidate.id] = candidate
            return
        }

        stand.level()
            .getEntities(stand, stand.boundingBox.move(0.0, - 1.0, 0.0)) { it !is ArmorStand }
            .firstOrNull { it.id !in matches }
            ?.let { matches[it.id] = it }
    }
}
//#endif

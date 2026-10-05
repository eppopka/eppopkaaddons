package dev.eppopkaaddons.features.impl.hunting

import dev.eppopkaaddons.event.impl.MainThreadPacketReceivedEvent
import dev.eppopkaaddons.event.impl.RenderWorldEvent
import dev.eppopkaaddons.event.impl.TickEvent
import dev.eppopkaaddons.event.impl.WorldChangeEvent
import dev.eppopkaaddons.features.Feature
import dev.eppopkaaddons.config.types.ColorSetting
import dev.eppopkaaddons.config.types.ToggleSetting
import dev.eppopkaaddons.utils.location.LocationUtils
import dev.eppopkaaddons.utils.location.WorldType
import dev.eppopkaaddons.utils.render.world.Render3D.renderBoxBounds
import dev.eppopkaaddons.utils.render.world.Render3D.renderString
import dev.eppopkaaddons.utils.render.world.Render3D.renderTracer
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.awt.Color

/**
 * Highlights "Invisibug" mobs (The Rift - Galatea) through walls.
 *
 * Invisibugs render as nothing on the client - Hypixel only gives them away
 * through one very specific, otherwise-pointless particle packet (a single
 * CRIT particle with zero spread/speed/count, always-show + override-limiter
 * flags set) spawned right next to a nameless, itemless marker armor stand.
 * We watch for that exact packet shape, confirm the marker is really there,
 * and only start drawing once we've seen it happen 4 times near the same
 * spot (matches the reference implementation's confidence threshold).
 *
 * Ported from InvisibugHighlight.java (nofrills) provided by the user.
 */
object InvisibugESP: Feature("Highlights Invisibug mobs through walls in Galatea.", name = "Invisibug ESP") {

    private val color by ColorSetting("Highlight Color", Color(255, 0, 0, 130), true)
        .withDescription("Color used for the box, tracer and label.")

    private val showTracer by ToggleSetting("Show Tracer", true)
        .withDescription("Draws a line to the nearest Invisibug.")

    private val showDistance by ToggleSetting("Show Distance", true)
        .withDescription("Shows the distance to each Invisibug in meters.")

    private class Invisibug(initialPos: Vec3) {
        var updateTicks = 20
        val positions = ArrayList<Vec3>().apply { add(initialPos) }

        fun isNear(pos: Vec3) = positions.isNotEmpty() && pos.distanceTo(positions.last()) <= 0.3

        fun add(pos: Vec3) {
            if (positions.size == 4) positions.removeAt(0)
            positions.add(pos)
            updateTicks = 20
        }
    }

    private val invisibugs = ArrayList<Invisibug>()

    private fun inArea() = LocationUtils.world == WorldType.Galatea

    // NOTE: field/accessor names (getCount/getMaxSpeed/getXDist/.../alwaysShow/isOverrideLimiter)
    // are copied from the reference file, which used the same Mojang-mapping style this project
    // uses elsewhere. If the compiler complains here, this packet was turned into a record in
    // 26.1.2 and the accessors lost their get/is prefix (count(), maxSpeed(), xDist(), yDist(),
    // zDist(), overrideLimiter(), alwaysShow(), particle(), x(), y(), z()) - send the error and
    // I'll swap the names over.
    private fun isInvisibugParticle(packet: ClientboundLevelParticlesPacket): Boolean {
        return packet.particle.type == ParticleTypes.CRIT &&
            packet.count == 1 &&
            packet.maxSpeed == 0.0f &&
            packet.xDist == 0.0f &&
            packet.yDist == 0.0f &&
            packet.zDist == 0.0f &&
            packet.alwaysShow() &&
            packet.isOverrideLimiter
    }

    private fun hasInvisibugMarker(pos: Vec3): Boolean {
        val player = mc.player ?: return false
        val level = mc.level ?: return false
        val nearby = level.getEntities(player, AABB.ofSize(pos, 1.0, 2.0, 1.0)) { true }

        val marker = nearby.singleOrNull() as? ArmorStand ?: return false
        return marker.isMarker && marker.customName == null && marker.mainHandItem.isEmpty
    }

    override fun init() {
        register<MainThreadPacketReceivedEvent.Post> {
            if (!enabled || !LocationUtils.inSkyblock || !inArea()) return@register
            val packet = event.packet
            if (packet !is ClientboundLevelParticlesPacket) return@register
            if (!isInvisibugParticle(packet)) return@register

            val pos = Vec3(packet.x, packet.y, packet.z)
            if (!hasInvisibugMarker(pos)) return@register

            for (bug in invisibugs) {
                if (bug.isNear(pos)) {
                    bug.add(pos)
                    return@register
                }
            }
            invisibugs.add(Invisibug(pos))
        }

        register<TickEvent.Start> {
            if (!enabled || !LocationUtils.inSkyblock || !inArea()) return@register
            val it2 = invisibugs.iterator()
            while (it2.hasNext()) {
                val bug = it2.next()
                if (bug.updateTicks > 0) bug.updateTicks--
                if (bug.updateTicks == 0) it2.remove()
            }
        }

        register<WorldChangeEvent> {
            invisibugs.clear()
        }

        register<RenderWorldEvent> {
            if (!enabled || !LocationUtils.inSkyblock || !inArea()) return@register
            val player = mc.player ?: return@register
            val confirmed = invisibugs.filter { it.positions.size == 4 }
            if (confirmed.isEmpty()) return@register

            for (bug in confirmed) {
                val pos = bug.positions.last()
                val label = if (showDistance.value) {
                    val dist = pos.distanceTo(player.position())
                    "Invisibug (${"%.1f".format(dist)}m)"
                } else "Invisibug"

                event.ctx.renderString(label, pos.add(0.0, 1.3, 0.0), color.value, 1.4f, true)
                event.ctx.renderBoxBounds(AABB.ofSize(pos, 1.0, 1.0, 1.0), color.value, color.value, true, true, true)
            }

            if (showTracer.value) {
                val nearest = confirmed.minByOrNull { it.positions.last().distanceToSqr(player.position()) }
                if (nearest != null) {
                    event.ctx.renderTracer(nearest.positions.last().add(0.0, 0.5, 0.0), color.value, 2)
                }
            }
        }
    }
}

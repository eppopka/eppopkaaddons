package dev.eppopkaaddons.features.impl.visual

import dev.eppopkaaddons.features.Feature
import dev.eppopkaaddons.config.types.DropdownSetting
import dev.eppopkaaddons.config.types.SliderSetting
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.core.particles.ParticleTypes
import kotlin.random.Random

object AmbientParticles: Feature("Spawns ambient particles around you to make the world feel more atmospheric.", name = "Ambient Particles") {
    private val particle by DropdownSetting("Particle", 0, listOf(
        "End Rod", "Fireflies", "Cherry Petals", "Snowflakes", "Souls", "Portal", "Witch", "Happy Villager"
    ))
    private val amount by SliderSetting("Amount", 2, 1, 10, 1)
        .withDescription("Particles spawned per tick.")
    private val radius by SliderSetting("Radius", 12, 4, 32, 2)
        .withDescription("Spawn radius around you in blocks.")

    override fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (! enabled) return@register
            if (client.isPaused) return@register
            val level = client.level ?: return@register
            val p = client.player ?: return@register

            val type = when (particle.value) {
                0 -> ParticleTypes.END_ROD
                1 -> ParticleTypes.FIREFLY
                2 -> ParticleTypes.CHERRY_LEAVES
                3 -> ParticleTypes.SNOWFLAKE
                4 -> ParticleTypes.SOUL
                5 -> ParticleTypes.PORTAL
                6 -> ParticleTypes.WITCH
                else -> ParticleTypes.HAPPY_VILLAGER
            }

            val r = radius.value.toDouble()
            repeat(amount.value) {
                val x = p.x + (Random.nextDouble() - 0.5) * 2.0 * r
                val y = p.y + Random.nextDouble() * 8.0 - 1.5
                val z = p.z + (Random.nextDouble() - 0.5) * 2.0 * r

                level.addParticle(
                    type,
                    x, y, z,
                    (Random.nextDouble() - 0.5) * 0.02,
                    - 0.005 - Random.nextDouble() * 0.01,
                    (Random.nextDouble() - 0.5) * 0.02
                )
            }
        }
    }
}

package dev.eppopkaaddons.features.impl.hunting

import dev.eppopkaaddons.config.types.ColorSetting
import dev.eppopkaaddons.config.types.DropdownSetting
import dev.eppopkaaddons.config.types.SliderSetting
import dev.eppopkaaddons.config.types.ToggleSetting
import dev.eppopkaaddons.event.impl.RenderWorldEvent
import dev.eppopkaaddons.event.impl.TickEvent
import dev.eppopkaaddons.event.impl.WorldChangeEvent
import dev.eppopkaaddons.features.Feature
import dev.eppopkaaddons.utils.PlayerUtils
import dev.eppopkaaddons.utils.WorldUtils
import dev.eppopkaaddons.utils.render.world.Render3D.renderBoxBounds
import dev.eppopkaaddons.utils.render.world.Render3D.renderString
import dev.eppopkaaddons.utils.render.world.Render3D.renderTracer
import com.google.gson.JsonParser
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.level.block.entity.SkullBlockEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.awt.Color
import java.util.Base64
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

object TikiESP : Feature(
    "Highlights Sneaky, Cheeky and Shrieky Tiki towers and optionally automates Black Hole placement.",
    name = "Tiki ESP"
) {
    private val esp by ToggleSetting("ESP", true)
    private val style by DropdownSetting("ESP Style", 2, listOf("Outline", "Filled", "Both"))
    private val tracer by ToggleSetting("Tracer", false)
    private val distance by ToggleSetting("Distance", true)
    private val sneakyColor by ColorSetting("Sneaky Color", Color(85, 255, 85, 110), true)
    private val cheekyColor by ColorSetting("Cheeky Color", Color(255, 200, 45, 110), true)
    private val shriekyColor by ColorSetting("Shrieky Color", Color(255, 85, 255, 110), true)

    private val particles by ToggleSetting("Particles", false).section("Particles")
    private val particleStyle by DropdownSetting("Particle Style", 1, listOf("Rings", "Double Helix", "Sparkles"))
    private val particleColor by ColorSetting("Particle Color", Color(85, 255, 255, 210), true)
    private val particleCount by SliderSetting("Particle Count", 18, 4, 48, 1)

    private val autoBlackHole by ToggleSetting("Auto Black Hole", false).section("Automation")
    private val activationRadius by SliderSetting("Activation Radius", 6f, 1f, 24f, 0.5f)
    private val cooldown by SliderSetting("Use Cooldown", 8, 1, 60, 1)
    private val switchToHuntaxe by ToggleSetting("Switch To Huntaxe", true)
    private val requireNametag by ToggleSetting("Require BLACK HOLE Nametag", true)

    private data class Tower(val bottom: BlockPos, val type: TikiType)
    private data class Pending(val tower: Tower, val started: Long)

    private var towers = emptyList<Tower>()
    private var ticks = 19
    private var lastUse = 0L
    private var pending: Pending? = null

    override fun init() {
        register<TickEvent.End> {
            if (++ticks >= 20) {
                ticks = 0
                scan()
            }
            automate()
        }
        register<WorldChangeEvent> {
            towers = emptyList()
            pending = null
            lastUse = 0L
        }
        register<RenderWorldEvent> {
            val player = mc.player ?: return@register
            towers.forEach { tower ->
                if (!matches(tower)) return@forEach
                val color = tower.type.color()
                val center = tower.bottom.center.add(0.0, 1.0, 0.0)
                if (esp.value) {
                    event.ctx.renderBoxBounds(
                        AABB.encapsulatingFullBlocks(tower.bottom, tower.bottom.above(2)),
                        color,
                        color,
                        outline = style.value != 1,
                        fill = style.value != 0,
                        phase = true
                    )
                }
                if (tracer.value) event.ctx.renderTracer(center, color, 2.5)
                if (distance.value) {
                    val meters = player.position().distanceTo(center).roundToInt()
                    event.ctx.renderString("${tower.type.display} Tiki • ${meters}m", center.add(0.0, 2.0, 0.0), color, 1.2f, true)
                }
                if (particles.value) drawParticles(event, center)
            }
        }
    }

    private fun scan() {
        val player = mc.player ?: return
        val level = mc.level ?: return
        towers = WorldUtils.getBlockEntityList().asSequence()
            .filter { it.distSqr(player.blockPosition()) <= 192.0 * 192.0 }
            .mapNotNull { pos ->
                val skull = level.getBlockEntity(pos) as? SkullBlockEntity ?: return@mapNotNull null
                val type = TikiType.entries.firstOrNull { it.bottom == textureId(skull) } ?: return@mapNotNull null
                Tower(pos, type).takeIf(::matches)
            }.toList()
    }

    private fun matches(tower: Tower): Boolean {
        val level = mc.level ?: return false
        return textureId(level.getBlockEntity(tower.bottom.above()) as? SkullBlockEntity) == tower.type.middle &&
            textureId(level.getBlockEntity(tower.bottom.above(2)) as? SkullBlockEntity) == tower.type.top
    }

    private fun textureId(skull: SkullBlockEntity?): String {
        val profile = skull?.ownerProfile?.partialProfile() ?: return ""
        val payload = profile.properties()["textures"].firstOrNull()?.value ?: return ""
        return runCatching {
            val json = String(Base64.getDecoder().decode(payload))
            JsonParser.parseString(json).asJsonObject
                .getAsJsonObject("textures").getAsJsonObject("SKIN")["url"].asString.substringAfterLast('/')
        }.getOrDefault("")
    }

    private fun automate() {
        val player = mc.player ?: return
        val level = mc.level ?: return
        if (mc.screen != null || player.isUsingItem) return
        val now = System.currentTimeMillis()

        pending?.let { waiting ->
            val confirmed = !requireNametag.value || hasBlackHole(waiting.tower)
            if (confirmed || now - waiting.started > 5_000L) {
                if (confirmed && switchToHuntaxe.value) {
                    PlayerUtils.findHotbarSlot { it.hoverName.string.contains("Huntaxe", true) }
                        ?.let(PlayerUtils::swapToSlot)
                }
                pending = null
            }
        }

        if (!autoBlackHole.value || pending != null || now - lastUse < cooldown.value * 1_000L) return
        val nearest = towers.asSequence()
            .filter { it.bottom.center.distanceTo(player.position()) <= activationRadius.value }
            .filterNot(::hasBlackHole)
            .minByOrNull { it.bottom.center.distanceToSqr(player.position()) } ?: return
        val slot = PlayerUtils.findHotbarSlot { it.hoverName.string.contains("Black Hole", true) } ?: return

        PlayerUtils.swapToSlot(slot)
        PlayerUtils.rightClick()
        lastUse = now
        pending = Pending(nearest, now)
    }

    private fun hasBlackHole(tower: Tower): Boolean {
        val level = mc.level ?: return false
        val area = AABB.ofSize(tower.bottom.center.add(0.0, 1.0, 0.0), 12.0, 8.0, 12.0)
        return level.getEntitiesOfClass(ArmorStand::class.java, area) {
            it.customName?.string?.contains("BLACK HOLE", true) == true
        }.isNotEmpty()
    }

    private fun drawParticles(event: RenderWorldEvent, center: Vec3) {
        val time = System.nanoTime() / 1_000_000_000.0
        repeat(particleCount.value) { i ->
            val progress = i.toDouble() / particleCount.value
            val strand = i and 1
            val angle: Double
            val radius: Double
            val y: Double
            when (particleStyle.value) {
                0 -> {
                    val ring = i % 3
                    angle = time * (1.2 + ring * 0.22) + progress * Math.PI * 6.0
                    radius = 0.68 + sin(time * 2.2 + i * 0.7) * 0.08
                    y = -1.28 + ring * 1.24 + sin(angle * 2.0) * 0.08
                }
                1 -> {
                    val p = (i / 2.0) / (particleCount.value / 2.0)
                    angle = time * 1.7 + p * Math.PI * 4.0 + strand * Math.PI
                    radius = 0.65
                    y = -1.42 + ((p * 3.15 + time * 0.38) % 3.15)
                }
                else -> {
                    angle = i * 2.399963 + time * 0.35 + sin(time * 0.9 + i) * 0.35
                    radius = 0.36 + (i % 6) * 0.075
                    y = -1.42 + ((i * 0.37 + time * 0.48) % 3.2)
                }
            }
            val pos = center.add(cos(angle) * radius, y, sin(angle) * radius)
            val size = if (particleStyle.value == 2) 0.08 + sin(time * 4.0 + i) * 0.025 else 0.075
            val core = AABB.ofSize(pos, size, size, size)
            val glow = AABB.ofSize(pos, size * 3.0, size * 3.0, size * 3.0)
            event.ctx.renderBoxBounds(glow, particleColor.value, particleColor.value.let { Color(it.red, it.green, it.blue, 28) }, false, true, true)
            event.ctx.renderBoxBounds(core, particleColor.value, particleColor.value, false, true, true)
        }
    }

    private enum class TikiType(val display: String, val bottom: String, val middle: String, val top: String) {
        SNEAKY("Sneaky", "247da91d9d4dd57bad2678baf648e4d9dd4b9f454a606962ceaac11ed00dea9f", "dfe6925f85cf02d2f8b501291d26aa5f4f6a136de0e974c2e9e3c20633ae9202", "bb19e5b72167db517ff8b9e2dd0735996641bdb6b6267a758ad2864022023cb7"),
        CHEEKY("Cheeky", "3eac8820104cc421e0392142dc7c79eb84bd2be8f8155c29d72f5fc58d4ee953", "b89f1cb02d4acd71940d9e6c29fe75ce3b15d291d9e383030b59e068b5f5a135", "28bdbd87b64e049562c986bb4c5b1336d92e68431260a9e0e8f3338d302b9761"),
        SHRIEKY("Shrieky", "419773998a0b85ffebde5ff925ea4c9b9e007436b0eb9a43afd2d3b367d241f6", "7a267c2699e38062ea4baedbfa4bb80a631048f7c8d2ee4c460f40036b084b2b", "f62bef8ca58d83766c7e26f9fb4fe06c11b93542a9c10f36f460a3b427748223");

        fun color() = when (this) {
            SNEAKY -> sneakyColor.value
            CHEEKY -> cheekyColor.value
            SHRIEKY -> shriekyColor.value
        }
    }
}
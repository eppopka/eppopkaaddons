package dev.eppopkaaddons.ui.clickgui.enums

import dev.eppopkaaddons.features.Feature
import dev.eppopkaaddons.features.FeatureManager
import dev.eppopkaaddons.features.impl.general.AetherOSSettings

private fun key(value: String) = value.filter(Char::isLetterOrDigit).lowercase()
private fun keys(vararg value: String) = value.map(::key).toSet()

enum class SpatialCategory(
    val title: String,
    val eyebrow: String,
    val mark: String,
    val accent: Int,
    private val assignments: Set<String>,
) {
    VISUAL("Visual", "COSMETICS / PRESENCE", "V", 0xFF9B8CFF.toInt(), keys("AlwaysWater", "AmbientParticles", "ChinaHat", "Cosmetics", "DvdBounce", "Halo", "ItemAura", "MoveHearts", "MoveHotbar", "RotatingItem")),
    QOL("QoL", "QUIET AUTOMATION", "Q", 0xFF65D7C0.toInt(), keys("AutoRag", "AutoReel")),
    DUNGEON("Dungeon", "ROUTES / ENCOUNTERS", "D", 0xFFFFB86B.toInt(), keys("OdinDungeonWaypoints", "ShitterList")),
    HUNTING("Hunting", "TARGETS / TRACKING", "H", 0xFFFF7290.toInt(), keys("BestiaryESP", "InvisibugESP")),
    GENERAL("Global", "CORE UTILITIES", "G", 0xFF7DB7FF.toInt(), keys("PetDisplay"));

    fun modules(): List<Feature> {
        val all = FeatureManager.features.asSequence()
            .filterNot { it === AetherOSSettings }
            .toList()
        if (this != GENERAL) return all.filter { key(it::class.simpleName ?: it.jsonName) in assignments }.sortedBy { it.name }
        val claimed = entries.asSequence().filterNot { it == GENERAL }.flatMap { it.assignments.asSequence() }.toSet()
        return all.filter { key(it::class.simpleName ?: it.jsonName) !in claimed }.sortedBy { it.name }
    }
}

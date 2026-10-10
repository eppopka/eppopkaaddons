package dev.eppopkaaddons.ui.clickgui.enums

import dev.eppopkaaddons.features.Feature
import dev.eppopkaaddons.features.FeatureManager
import dev.eppopkaaddons.features.impl.general.AetherOSSettings

enum class SpatialCategory(
    val title: String,
    val eyebrow: String,
    val mark: String,
    val accent: Int,
    val featureCategory: CategoryType,
) {
    VISUAL("Visual", "COSMETICS / PRESENCE", "V", 0xFF9B8CFF.toInt(), CategoryType.VISUAL),
    QOL("QoL", "QUIET AUTOMATION", "Q", 0xFF65D7C0.toInt(), CategoryType.QOL),
    DUNGEON("Dungeon", "ROUTES / ENCOUNTERS", "D", 0xFFFFB86B.toInt(), CategoryType.DUNGEON),
    HUNTING("Hunting", "TARGETS / TRACKING", "H", 0xFFFF7290.toInt(), CategoryType.HUNTING),
    GENERAL("Global", "CORE UTILITIES", "G", 0xFF7DB7FF.toInt(), CategoryType.GENERAL);

    fun modules(): List<Feature> = FeatureManager.features.asSequence()
        .filterNot { it === AetherOSSettings }
        .filter { it.category == featureCategory }
        .sortedBy { it.name }
        .toList()
}

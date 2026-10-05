package dev.eppopkaaddons.features.impl.visual.cosmetics.badges

import dev.eppopkaaddons.features.impl.visual.Cosmetics
import dev.eppopkaaddons.features.impl.visual.cosmetics.CosmeticData
import dev.eppopkaaddons.features.impl.visual.cosmetics.text.TextReplacer
import net.minecraft.network.chat.Component
import java.util.UUID

/** UUID gate for nametag and tab-list replacement. No third-party badges are accepted. */
object BadgeText {
    @Volatile private var allowed = emptySet<UUID>()

    fun init(people: Map<UUID, CosmeticData>) {
        allowed = people.filterValues(CosmeticData::hasCustomName).keys
    }

    fun rebuild() = Unit

    @JvmStatic
    fun decorate(name: Component, uuid: UUID): Component {
        if (!Cosmetics.enabled || !Cosmetics.customNames.value || uuid !in allowed || !BadgeManager.isAllowed(uuid)) return name
        return TextReplacer.fn(name)
    }
}

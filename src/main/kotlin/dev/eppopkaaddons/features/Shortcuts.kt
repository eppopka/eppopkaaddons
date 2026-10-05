package dev.eppopkaaddons.features

import dev.eppopkaaddons.EppopkaAddons
import gg.essential.universal.UMinecraft
import gg.essential.universal.wrappers.UPlayer

interface Shortcuts {
    val Shortcuts.mc get() = EppopkaAddons.mc
    val Shortcuts.scope get() = EppopkaAddons.scope
    val Shortcuts.cacheData get() = EppopkaAddons.cacheData
    val Shortcuts.player get() = requireNotNull(UPlayer.getPlayer()) { "tried to access player before it was loaded" }
    val Shortcuts.level get() = requireNotNull(UMinecraft.getWorld()) { "tried to access level before world was loaded" }
    val Shortcuts.gameMode get() = requireNotNull(mc.gameMode) { "mc.gameMode is null" }
}
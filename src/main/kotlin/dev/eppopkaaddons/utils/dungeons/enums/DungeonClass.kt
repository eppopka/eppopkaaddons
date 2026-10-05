package dev.eppopkaaddons.utils.dungeons.enums

import gg.essential.universal.ChatColor

/** Fixed class colors used by retained dungeon automation and HUD infrastructure. */
enum class DungeonClass(private val chatColor: ChatColor) {
    Archer(ChatColor.DARK_RED),
    Berserk(ChatColor.GOLD),
    Healer(ChatColor.DARK_PURPLE),
    Mage(ChatColor.DARK_AQUA),
    Tank(ChatColor.DARK_GREEN),
    Empty(ChatColor.BLACK);

    val color get() = chatColor.color!!
    val code get() = chatColor.toString()

    companion object {
        fun fromName(name: String) = entries.find { it.name.equals(name, ignoreCase = true) } ?: Empty
    }
}

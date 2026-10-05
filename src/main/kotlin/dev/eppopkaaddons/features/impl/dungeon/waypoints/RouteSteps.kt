package dev.eppopkaaddons.features.impl.dungeon.waypoints

object RouteSteps {
    private val prefix = Regex("""^\s*(\d+(?:\s*/\s*\d+)*)""")

    fun parse(title: String?): List<Int> {
        if (title.isNullOrBlank()) return emptyList()
        val value = prefix.find(title)?.groupValues?.getOrNull(1) ?: return emptyList()
        return value.split('/').mapNotNull { it.trim().toIntOrNull() }.distinct()
    }
}
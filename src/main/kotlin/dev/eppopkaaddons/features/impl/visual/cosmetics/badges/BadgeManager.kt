package dev.eppopkaaddons.features.impl.visual.cosmetics.badges

import dev.eppopkaaddons.features.impl.visual.cosmetics.CosmeticData
import dev.eppopkaaddons.utils.GsonUtils
import dev.eppopkaaddons.utils.network.WebUtils
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import java.util.UUID

/** Loads only the owner-maintained UUID allow-list used by Custom Names. */
object BadgeManager {
    const val URL = "https://raw.githubusercontent.com/eppopka/badge-for-eppopkaaddons/main/badge.json"
    const val SEPARATOR = '\uE100'

    @Volatile private var allowedIds = emptySet<UUID>()
    fun isAllowed(uuid: UUID) = uuid in allowedIds
    fun charOf(id: String): Char? = null // retained for binary/source compatibility with old packs

    suspend fun load(): Result<Map<UUID, CosmeticData>> = WebUtils.get(URL).mapCatching { response ->
        if (!response.status.isSuccess()) error("HTTP ${response.status.value}")
        val raw = response.bodyAsText().trim()
        var candidate = raw
        var parsed: Map<String, CosmeticData>? = null
        repeat(3) {
            parsed = runCatching { GsonUtils.decode<Map<String, CosmeticData>>(candidate) }.getOrNull()
            if (parsed != null) return@repeat
            candidate += "}"
        }
        val strict = (parsed ?: error("Invalid custom-name JSON"))
            .mapNotNull { (id, data) ->
                val uuid = runCatching { UUID.fromString(id) }.getOrNull() ?: return@mapNotNull null
                if (!data.hasCustomName) return@mapNotNull null
                uuid to data
            }
            .toMap()
        allowedIds = strict.keys
        strict
    }
}

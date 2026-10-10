package dev.eppopkaaddons.utils.pets

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.Locale
import kotlin.math.abs

/** Only server-observed pet data is accepted. Skill action bars are NOT pet XP. */
object PetXpModel {
    enum class Source { METADATA, LORE, PERCENTAGE, UNKNOWN }

    data class Reading(
        val name: String,
        val level: Int,
        val type: String,
        val rarity: String?,
        val uuid: String?,
        val active: Boolean,
        val totalXp: Double?,
        val maxLevel: Int,
        val intoLevel: Double?,
        val required: Double?,
        val progress: Double?,
        val maxed: Boolean,
        val source: Source,
    )
    data class Level(val level: Int, val maxLevel: Int, val into: Double, val required: Double?, val maxed: Boolean)

    private val formatting = Regex("§[0-9A-FK-OR]", RegexOption.IGNORE_CASE)
    private val namePattern = Regex("^\\[Lv(?:l)?\\s+(\\d+)]\\s+(.+)$", RegexOption.IGNORE_CASE)
    private val progressPattern = Regex("^Progress to (?:Level|Lvl)\\s+(\\d+):\\s*([\\d.]+)%", RegexOption.IGNORE_CASE)
    private val numberPattern = Regex("^([\\d,]+(?:\\.\\d+)?)\\s*([KMBT]?)$", RegexOption.IGNORE_CASE)
    private val ratioPattern = Regex("([\\d,.]+\\s*[KMBT]?)\\s*/\\s*([\\d,.]+\\s*[KMBT]?)\\s*(?:XP)?$", RegexOption.IGNORE_CASE)
    private val rarityPattern = Regex("^(?:SHINY\\s+)?(VERY SPECIAL|SPECIAL|MYTHIC|LEGENDARY|EPIC|RARE|UNCOMMON|COMMON)(?:\\s+PET)?$", RegexOption.IGNORE_CASE)
    private val extended = setOf("GOLDEN_DRAGON", "JADE_DRAGON", "ROSE_DRAGON")
    private val offsets = mapOf("COMMON" to 0, "UNCOMMON" to 6, "RARE" to 11, "EPIC" to 16,
        "LEGENDARY" to 20, "MYTHIC" to 20, "SPECIAL" to 16, "VERY_SPECIAL" to 20)

    fun clean(value: String) = formatting.replace(value, "").trim()
    fun normalizeName(value: String): String = clean(value).replace(Regex("\\s*✦$"), "").trim()
    fun named(value: String): Pair<Int, String>? = namePattern.find(normalizeName(value))?.let {
        val level = it.groupValues[1].toIntOrNull() ?: return null
        level to normalizeName(it.groupValues[2])
    }

    fun amount(value: String): Double? {
        val match = numberPattern.matchEntire(value.trim().replace("\u00a0", " ")) ?: return null
        val base = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        val multiplier = when (match.groupValues[2].uppercase(Locale.ROOT)) {
            "K" -> 1e3; "M" -> 1e6; "B" -> 1e9; "T" -> 1e12; else -> 1.0
        }
        return (base * multiplier).takeIf { it.isFinite() && it >= 0 }
    }

    fun effectiveTier(tier: String?, heldItem: String?): String? {
        val normalized = tier?.uppercase(Locale.ROOT)?.replace(' ', '_') ?: return null
        val ladder = listOf("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC")
        val index = ladder.indexOf(normalized)
        return if (heldItem == "PET_ITEM_TIER_BOOST" && index in 0..4) ladder[index + 1] else normalized
    }

    fun requirements(type: String, tier: String): List<Long>? {
        val offset = if (type == "BINGO") 0 else offsets[tier] ?: return null
        val all = if (type in extended) standard.toList() + List(100) { 1_886_700L } else standard.toList()
        return all.drop(offset).take(if (type in extended) 199 else 99)
    }

    fun level(type: String, tier: String, totalXp: Double): Level? {
        if (!totalXp.isFinite() || totalXp < 0) return null
        val costs = requirements(type, tier) ?: return null
        var remaining = totalXp
        var current = 1
        for (cost in costs) {
            if (remaining < cost) return Level(current, costs.size + 1, remaining, cost.toDouble(), false)
            remaining -= cost
            current++
        }
        return Level(current, costs.size + 1, 0.0, null, true)
    }

    fun totalBeforeLevel(type: String, tier: String, level: Int): Double? =
        requirements(type, tier)?.take((level - 1).coerceAtLeast(0))?.sum()?.toDouble()

    fun read(displayName: String, rawLore: List<String>, petInfo: String?): Reading? {
        val (observedLevel, name) = named(displayName) ?: return null
        if (observedLevel < 1) return null
        val lore = rawLore.map(::clean)
        val info = petInfo?.let { runCatching { JsonParser.parseString(it).asJsonObject }.getOrNull() }
        val type = text(info, "type")?.uppercase(Locale.ROOT)
            ?: name.uppercase(Locale.ROOT).replace(Regex("[^A-Z0-9]+"), "_").trim('_')
        val tier = effectiveTier(text(info, "tier"), text(info, "heldItem")) ?: lore.asReversed()
            .firstNotNullOfOrNull { rarityPattern.matchEntire(it)?.groupValues?.get(1)?.uppercase(Locale.ROOT)?.replace(' ', '_') }
        val total = runCatching { info?.get("exp")?.asDouble }.getOrNull()?.takeIf { it.isFinite() && it >= 0 }
        val uuid = text(info, "uuid")?.takeIf { it.isNotBlank() }
        val active = runCatching { info?.get("active")?.asBoolean == true }.getOrDefault(false)
        val computed = if (tier != null && total != null) level(type, tier, total) else null
        val maxLevel = if (type in extended) 200 else maxOf(100, observedLevel)
        fun result(into: Double?, needed: Double?, progress: Double?, maxed: Boolean, source: Source) =
            Reading(name, observedLevel, type, tier, uuid, active, total,
                if (maxed) observedLevel else maxLevel, into, needed, progress, maxed, source)

        if (lore.any { it.equals("MAX LEVEL", true) }) return result(null, null, 1.0, true, Source.LORE)

        val progressIndex = lore.indexOfFirst { line -> progressPattern.find(line)?.groupValues?.get(1)?.toIntOrNull() == observedLevel + 1 }
        val percent = if (progressIndex >= 0) progressPattern.find(lore[progressIndex])?.groupValues?.get(2)?.toDoubleOrNull()
            ?.takeIf { it.isFinite() && it >= 0 && it < 100 }?.div(100.0) else null
        // Metadata contains TOTAL lifetime pet XP, not XP within the current level.
        // Verify its computed level (and visible percentage when available) first.
        if (computed != null && computed.level == observedLevel && (!computed.maxed || progressIndex < 0)) {
            if (computed.maxed) return result(null, null, 1.0, true, Source.METADATA)
            val ratio = computed.into / requireNotNull(computed.required)
            if (percent == null || abs(ratio - percent) <= .015) {
                return result(computed.into, computed.required, ratio, false, Source.METADATA)
            }
        }

        val candidates = if (progressIndex >= 0) lore.drop(progressIndex + 1).take(3) else
            lore.filter { it.matches(Regex("^(?:XP|EXP|Experience)\\s*:.*", RegexOption.IGNORE_CASE)) }
        for (line in candidates) {
            if (line.contains("candy", true)) continue
            val match = ratioPattern.find(line) ?: continue
            val current = amount(match.groupValues[1]) ?: continue
            val needed = amount(match.groupValues[2]) ?: continue
            if (needed <= 0 || current >= needed) continue // Never silently clamp bad data to 100%.
            val ratio = current / needed
            if (percent != null && abs(ratio - percent) > .02) continue
            return result(current, needed, percent ?: ratio, false, Source.LORE)
        }
        if (percent != null) return result(null, null, percent, false, Source.PERCENTAGE)
        return result(null, null, null, false, Source.UNKNOWN)
    }

    private fun text(info: JsonObject?, key: String): String? =
        runCatching { info?.get(key)?.takeUnless { it.isJsonNull }?.asString }.getOrNull()

    // Numerical game data: NotEnoughUpdates-REPO/constants/pets.json.
    // https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO/blob/master/constants/pets.json
    private val standard = longArrayOf(
        100L, 110L, 120L, 130L, 145L, 160L, 175L, 190L, 210L, 230L,
        250L, 275L, 300L, 330L, 360L, 400L, 440L, 490L, 540L, 600L,
        660L, 730L, 800L, 880L, 960L, 1050L, 1150L, 1260L, 1380L, 1510L,
        1650L, 1800L, 1960L, 2130L, 2310L, 2500L, 2700L, 2920L, 3160L, 3420L,
        3700L, 4000L, 4350L, 4750L, 5200L, 5700L, 6300L, 7000L, 7800L, 8700L,
        9700L, 10800L, 12000L, 13300L, 14700L, 16200L, 17800L, 19500L, 21300L, 23200L,
        25200L, 27400L, 29800L, 32400L, 35200L, 38200L, 41400L, 44800L, 48400L, 52200L,
        56200L, 60400L, 64800L, 69400L, 74200L, 79200L, 84700L, 90700L, 97200L, 104200L,
        111700L, 119700L, 128200L, 137200L, 146700L, 156700L, 167700L, 179700L, 192700L, 206700L,
        221700L, 237700L, 254700L, 272700L, 291700L, 311700L, 333700L, 357700L, 383700L, 411700L,
        441700L, 476700L, 516700L, 561700L, 611700L, 666700L, 726700L, 791700L, 861700L, 936700L,
        1016700L, 1101700L, 1191700L, 1286700L, 1386700L, 1496700L, 1616700L, 1746700L, 1886700L,
    )
}

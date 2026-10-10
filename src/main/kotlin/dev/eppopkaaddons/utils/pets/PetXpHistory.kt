package dev.eppopkaaddons.utils.pets

import java.util.ArrayDeque

/** Rates from observed server snapshots only; no fabricated/action-bar XP gains. */
class PetXpHistory {
    private data class Sample(val time: Long, val xp: Double)
    private val samples = ArrayDeque<Sample>()
    fun clear() = samples.clear()

    fun record(totalXp: Double, time: Long, windowMinutes: Int) {
        if (!totalXp.isFinite() || totalXp < 0) return
        val last = samples.lastOrNull()
        if (last != null && (totalXp < last.xp || time < last.time)) samples.clear()
        if (samples.lastOrNull()?.time == time) samples.removeLast()
        samples.addLast(Sample(time, totalXp))
        val cutoff = time - windowMinutes.coerceAtLeast(1) * 60_000L
        while (samples.size > 2 && samples.first.time < cutoff) samples.removeFirst()
    }

    fun perHour(now: Long, windowMinutes: Int): Double? {
        val first = samples.firstOrNull() ?: return null
        val last = samples.lastOrNull() ?: return null
        if (samples.size < 2 || last.time - first.time < 10_000L || last.xp <= first.xp) return null
        if (now - last.time > windowMinutes.coerceAtLeast(1) * 60_000L || now <= first.time) return null
        return (last.xp - first.xp) * 3_600_000.0 / (now - first.time)
    }
}

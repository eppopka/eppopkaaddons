package dev.eppopkaaddons.utils

import kotlin.math.exp

/** Frame-rate independent smoothing shared by cosmetic player attachments. */
object SmoothHeight {
    private data class State(var value: Double, var time: Long)
    private val states = HashMap<String, State>()

    fun smooth(key: String, target: Double, strength: Double): Double {
        val now = System.nanoTime()
        val state = states.getOrPut(key) { State(target, now) }
        val dt = ((now - state.time) / 1_000_000_000.0).coerceIn(0.0, 0.1)
        state.time = now

        if (strength <= 0.0) {
            state.value = target
            return target
        }

        val blend = 1.0 - exp(-strength * dt)
        state.value += (target - state.value) * blend
        return state.value
    }
}
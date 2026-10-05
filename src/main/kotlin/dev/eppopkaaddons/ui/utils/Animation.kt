package dev.eppopkaaddons.ui.utils

import dev.eppopkaaddons.features.impl.general.AetherOSSettings
import dev.eppopkaaddons.ui.clickgui.foundation.MotionClock

class Animation(var duration: Long = 200, initialValue: Float = 0f) {
    var value: Float = initialValue
        private set

    private var startValue: Float = initialValue
    private var targetValue: Float = initialValue
    private var startTime: Long = MotionClock.nowNanos

    fun update(newTarget: Float) {
        if (newTarget != targetValue) {
            targetValue = newTarget
            startValue = value
            startTime = MotionClock.nowNanos
        }
        if (!AetherOSSettings.animations.value || AetherOSSettings.reducedMotion.value) {
            set(newTarget)
            return
        }
        val scaledDuration = duration.toDouble() / AetherOSSettings.animationSpeed.value.coerceAtLeast(.25f)
        val elapsed = (MotionClock.nowNanos - startTime) / 1_000_000.0
        val progress = (elapsed / scaledDuration.coerceAtLeast(1.0)).coerceIn(0.0, 1.0)

        value = if (progress >= 1.0) targetValue
        else startValue + (targetValue - startValue) * easeOutQuad(progress).toFloat()
    }

    fun set(v: Float) {
        this.value = v
        this.startValue = v
        this.targetValue = v
        this.startTime = MotionClock.nowNanos
    }

    companion object {
        fun easeOutQuad(t: Double) = 1.0 - (1.0 - t) * (1.0 - t)
        fun easeInOutCubic(t: Double) = if (t < 0.5) 4 * t * t * t else (t - 1) * (2 * t - 2) * (2 * t - 2) + 1
    }
}
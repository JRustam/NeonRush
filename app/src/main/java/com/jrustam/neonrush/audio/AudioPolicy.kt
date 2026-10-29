package com.jrustam.neonrush.audio

/** Platform-free policy shared by preferences, playback, and unit tests. */
object AudioPolicy {
    fun volume(value: Float, fallback: Float): Float =
        if (value.isFinite()) value.coerceIn(0f, 1f) else fallback.coerceIn(0f, 1f)

    fun musicAllowed(foreground: Boolean, sceneMusic: Boolean, focused: Boolean, volume: Float) =
        foreground && sceneMusic && focused && volume > 0f
}

enum class SoundEffect { CRASH, ENERGY, WHOOSH, POWER_UP, GAME_OVER, CLICK }

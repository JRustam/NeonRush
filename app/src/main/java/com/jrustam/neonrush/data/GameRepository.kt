package com.jrustam.neonrush.data

import android.content.Context
import com.jrustam.neonrush.audio.AudioPolicy
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.gameData by preferencesDataStore(name = "neon_rush")
data class Preferences(val highScore: Int = 0, val particles: Boolean = true, val haptics: Boolean = true, val musicVolume: Float = .55f, val sfxVolume: Float = .8f, val loaded: Boolean = false)

@Singleton
class GameRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private val music = floatPreferencesKey("music_volume")
    private val sfx = floatPreferencesKey("sfx_volume")
    private val high = intPreferencesKey("high_score")
    private val particles = booleanPreferencesKey("particles")
    private val haptics = booleanPreferencesKey("haptics")
    val preferences = context.gameData.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map { Preferences(it[high] ?: 0, it[particles] ?: true, it[haptics] ?: true, AudioPolicy.volume(it[music] ?: .55f, .55f), AudioPolicy.volume(it[sfx] ?: .8f, .8f), loaded = true) }

    suspend fun recordScore(score: Int) {
        context.gameData.edit { it[high] = maxOf(score, it[high] ?: 0) }
    }
    suspend fun setMusicVolume(value: Float) { context.gameData.edit { it[music] = AudioPolicy.volume(value, .55f) } }
    suspend fun setSfxVolume(value: Float) { context.gameData.edit { it[sfx] = AudioPolicy.volume(value, .8f) } }
    suspend fun setParticles(enabled: Boolean) { context.gameData.edit { it[particles] = enabled } }
    suspend fun setHaptics(enabled: Boolean) { context.gameData.edit { it[haptics] = enabled } }
}

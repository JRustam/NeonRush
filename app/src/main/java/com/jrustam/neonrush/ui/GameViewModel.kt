package com.jrustam.neonrush.ui

import com.jrustam.neonrush.R
import com.jrustam.neonrush.audio.SoundEffect
import kotlinx.coroutines.channels.BufferOverflow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jrustam.neonrush.data.GameRepository
import com.jrustam.neonrush.data.Preferences
import com.jrustam.neonrush.game.*
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Menu : Screen
    data object Playing : Screen
    data object Paused : Screen
    data object GameOver : Screen
    data object Settings : Screen
}

data class Hud(
    val score: Int = 0,
    val level: Int = 1,
    val shield: Float = 0f,
    val slow: Float = 0f,
    val multiplier: Float = 0f
)

private const val SOUND_EVENT_BUFFER_CAPACITY = 32
private const val HUD_UPDATE_INTERVAL_SECONDS = .1f

@HiltViewModel
class GameViewModel @Inject constructor(private val repository: GameRepository) : ViewModel() {
    private val _sounds = MutableSharedFlow<SoundEffect>(
        extraBufferCapacity = SOUND_EVENT_BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val sounds = _sounds.asSharedFlow()
    private fun click() {
        _sounds.tryEmit(SoundEffect.CLICK)
    }

    private val engine = GameEngine()
    private val _screen = MutableStateFlow<Screen>(Screen.Menu)
    val screen = _screen.asStateFlow()
    private val _frame = MutableStateFlow(GameFrame())
    val frame = _frame.asStateFlow()
    private val _hud = MutableStateFlow(Hud())
    val hud = _hud.asStateFlow()
    val preferences =
        repository.preferences.stateIn(viewModelScope, SharingStarted.Eagerly, Preferences())
    private val _saveError = MutableStateFlow<Int?>(null)
    val saveError = _saveError.asStateFlow()
    var previousBest = 0
        private set
    private var hudAt = 0f

    fun start() {
        click()
        previousBest = preferences.value.highScore
        engine.reset(); _frame.value = engine.frame; _hud.value = Hud(); hudAt = 0f
        _screen.value = Screen.Playing
    }

    fun moveTo(x: Float) {
        if (_screen.value == Screen.Playing) engine.moveTo(x)
    }

    fun tick(dt: Float) {
        if (_screen.value != Screen.Playing) return
        engine.advance(dt)
        engine.drainEvents().forEach { event ->
            _sounds.tryEmit(
                when (event) {
                    GameEvent.CRASH -> SoundEffect.CRASH
                    GameEvent.ENERGY_COLLECTED -> SoundEffect.ENERGY
                    GameEvent.NEAR_MISS -> SoundEffect.WHOOSH
                    GameEvent.POWER_ACTIVATED, GameEvent.SHIELD_BROKEN -> SoundEffect.POWER_UP
                }
            )
        }
        val f = engine.frame
        _frame.value = f
        if (f.elapsed >= hudAt || f.ended) {
            _hud.value = Hud(f.score, f.level, f.shield, f.slow, f.multiplier)
            hudAt = f.elapsed + HUD_UPDATE_INTERVAL_SECONDS
        }
        if (f.ended) {
            _screen.value = Screen.GameOver
            _sounds.tryEmit(SoundEffect.GAME_OVER)
            persist { repository.recordScore(f.score) }
        }
    }

    fun pause(userInitiated: Boolean = true) {
        if (_screen.value == Screen.Playing) {
            if (userInitiated) click()
            _screen.value = Screen.Paused
        }
    }

    fun resume() {
        if (_screen.value == Screen.Paused) {
            click(); _screen.value = Screen.Playing
        }
    }

    fun menu() {
        click()
        if (_screen.value == Screen.Paused || _screen.value == Screen.Playing) {
            val score = engine.frame.score
            persist { repository.recordScore(score) }
        }
        _screen.value = Screen.Menu
    }

    fun settings() {
        click(); _screen.value = Screen.Settings
    }

    fun particles(value: Boolean) {
        click(); persist { repository.setParticles(value) }
    }

    fun haptics(value: Boolean) {
        click(); persist { repository.setHaptics(value) }
    }

    fun musicVolume(value: Float) {
        persist { repository.setMusicVolume(value) }
    }

    fun sfxVolume(value: Float) {
        persist { repository.setSfxVolume(value) }
    }

    private fun persist(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action(); _saveError.value = null
            } catch (_: IOException) {
                _saveError.value = R.string.error_save
            }
        }
    }
}

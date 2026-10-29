package com.jrustam.neonrush

import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.jrustam.neonrush.audio.GameAudio
import com.jrustam.neonrush.ui.Screen
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.viewModels
import com.jrustam.neonrush.ui.GameViewModel
import com.jrustam.neonrush.ui.NeonRushApp
import com.jrustam.neonrush.ui.theme.NeonRushTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var audio: GameAudio

    private val game: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        audio.onInterruption = { game.pause(userInitiated = false) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                val prefs = game.preferences.first { it.loaded }
                audio.apply {
                    setVolumes(prefs.musicVolume, prefs.sfxVolume)
                    setSceneMusic(game.screen.value.let { it == Screen.Menu || it == Screen.Settings || it == Screen.Playing })
                    setForeground(true)
                }
                try {
                    launch { game.preferences.collect { audio.setVolumes(it.musicVolume, it.sfxVolume) } }
                    launch { game.screen.collect { audio.setSceneMusic(it == Screen.Menu || it == Screen.Settings || it == Screen.Playing) } }
                    launch { game.sounds.collect(audio::play) }
                    kotlinx.coroutines.awaitCancellation()
                } finally {
                    audio.setForeground(false)
                }
            }
        }
        setContent { NeonRushTheme { NeonRushApp(game) } }
    }

    override fun onDestroy() { audio.close(); super.onDestroy() }

    override fun onPause() {
        audio.setForeground(false)
        game.pause(userInitiated = false)
        super.onPause()
    }
}

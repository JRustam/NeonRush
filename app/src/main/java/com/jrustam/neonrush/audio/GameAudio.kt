package com.jrustam.neonrush.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.jrustam.neonrush.R
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject

/** Activity-owned native resources. All calls and callbacks run on the main thread. */
@ActivityScoped
class GameAudio @Inject constructor(@ApplicationContext private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val manager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val pool = SoundPool.Builder().setMaxStreams(8).setAudioAttributes(attributes).build()
    private val samples = mutableMapOf<SoundEffect, Int>()
    private val loaded = mutableSetOf<Int>()
    private val streams = ArrayDeque<Int>()
    private var player: MediaPlayer? = null
    private var prepared = false
    private var musicFailed = false
    private var closed = false
    private var foreground = false
    private var sceneMusic = true
    private var focused = false
    private var blocked = false
    private var ducked = false
    private var musicVolume = .55f
    private var sfxVolume = .8f
    private var lastWhoosh = -1000L
    private var receiverRegistered = false
    var onInterruption: (() -> Unit)? = null

    private val gameOverCue = Runnable { playNow(SoundEffect.GAME_OVER) }
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener({ change ->
            if (!closed) when (change) {
                AudioManager.AUDIOFOCUS_GAIN -> {
                    focused = true; blocked = false; ducked = false; syncMusic()
                }

                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    ducked = true; syncMusic()
                }

                AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    focused = false; blocked =
                        true; stopEffects(); pauseMusic(); onInterruption?.invoke()
                }
            }
        }, handler).build()
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                blocked =
                    true; pauseMusic(); stopEffects(); abandonFocus(); onInterruption?.invoke()
            }
        }
    }

    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0 && !closed) loaded.add(id) }
        mapOf(
            SoundEffect.CRASH to R.raw.crash, SoundEffect.ENERGY to R.raw.energy,
            SoundEffect.WHOOSH to R.raw.whoosh, SoundEffect.POWER_UP to R.raw.power_up,
            SoundEffect.GAME_OVER to R.raw.game_over, SoundEffect.CLICK to R.raw.click
        ).forEach { (effect, resource) -> samples[effect] = pool.load(context, resource, 1) }
    }

    fun setForeground(value: Boolean) {
        if (closed || foreground == value) return
        foreground = value
        if (value) {
            blocked = false
            ContextCompat.registerReceiver(
                context,
                noisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
            syncMusic()
        } else {
            pauseMusic(); stopEffects(); abandonFocus()
            if (receiverRegistered) {
                context.unregisterReceiver(noisyReceiver); receiverRegistered = false
            }
        }
    }

    fun setSceneMusic(enabled: Boolean) {
        sceneMusic = enabled; syncMusic()
    }

    fun setVolumes(music: Float, sfx: Float) {
        musicVolume = AudioPolicy.volume(music, .55f)
        sfxVolume = AudioPolicy.volume(sfx, .8f)
        if (sfxVolume == 0f) stopEffects()
        syncMusic()
    }

    private fun acquireFocus(): Boolean {
        if (!foreground || closed || blocked) return false
        if (!focused) focused =
            manager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return focused
    }

    private fun ensurePlayer() {
        if (player != null || musicFailed || closed) return
        val candidate = MediaPlayer()
        player = candidate
        try {
            candidate.setAudioAttributes(attributes)
            context.resources.openRawResourceFd(R.raw.afterlight).use { asset ->
                candidate.setDataSource(asset.fileDescriptor, asset.startOffset, asset.length)
            }
            candidate.isLooping = true
            candidate.setOnPreparedListener {
                if (!closed && player === it) {
                    prepared = true; syncMusic()
                }
            }
            candidate.setOnErrorListener { failed, what, extra ->
                Log.w("NeonRushAudio", "Music decoder error: $what/$extra")
                if (player === failed) {
                    player = null; prepared = false; musicFailed = true
                }
                failed.release()
                true
            }
            candidate.prepareAsync()
        } catch (error: Exception) {
            Log.w("NeonRushAudio", "Music unavailable", error)
            candidate.release(); player = null; prepared = false; musicFailed = true
        }
    }

    private fun syncMusic() {
        if (closed) return
        val wanted = foreground && sceneMusic && musicVolume > 0f
        if (wanted && acquireFocus()) ensurePlayer()
        val current = player
        if (prepared && current != null) {
            val gain = musicVolume * if (ducked) .2f else 1f
            current.setVolume(gain, gain)
            if (AudioPolicy.musicAllowed(
                    foreground,
                    sceneMusic,
                    focused && !blocked,
                    musicVolume
                )
            ) {
                if (!current.isPlaying) current.start()
            } else if (current.isPlaying) current.pause()
        }
        if (musicVolume == 0f && sfxVolume == 0f) abandonFocus()
    }

    fun play(effect: SoundEffect) {
        if (closed || !foreground) return
        if (effect == SoundEffect.CLICK) {
            // Explicit user interaction may reclaim focus after an interruption.
            blocked = false
            handler.removeCallbacks(gameOverCue)
            syncMusic()
        }
        if (effect == SoundEffect.GAME_OVER) {
            handler.removeCallbacks(gameOverCue)
            handler.postDelayed(gameOverCue, 350L)
        } else playNow(effect)
    }

    private fun playNow(effect: SoundEffect) {
        if (closed || !foreground || sfxVolume <= 0f || !acquireFocus()) return
        val sample = samples[effect]?.takeIf { it in loaded } ?: return
        if (effect == SoundEffect.WHOOSH) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastWhoosh < 120) return
            lastWhoosh = now
        }
        val gain =
            sfxVolume * (if (effect == SoundEffect.WHOOSH) .45f else .85f) * (if (ducked) .2f else 1f)
        val stream = pool.play(sample, gain, gain, if (effect == SoundEffect.CRASH) 2 else 1, 0, 1f)
        if (stream != 0) {
            streams.addLast(stream)
            if (streams.size > 32) streams.removeFirst()
        }
    }

    private fun pauseMusic() {
        if (prepared && player?.isPlaying == true) player?.pause()
    }

    private fun stopEffects() {
        handler.removeCallbacks(gameOverCue)
        streams.forEach(pool::stop); streams.clear()
    }

    private fun abandonFocus() {
        manager.abandonAudioFocusRequest(focusRequest); focused = false; ducked = false
    }

    fun close() {
        if (closed) return
        setForeground(false)
        closed = true
        handler.removeCallbacksAndMessages(null)
        onInterruption = null
        player?.release(); player = null; prepared = false
        pool.release(); loaded.clear()
    }
}

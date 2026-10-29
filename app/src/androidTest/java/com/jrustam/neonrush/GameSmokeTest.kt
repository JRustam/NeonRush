package com.jrustam.neonrush

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.AudioManager
import android.media.AudioAttributes
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import com.jrustam.neonrush.data.GameRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Captures actual emulator-rendered app UI. No injected scores, mocked frames, or fabricated artwork. */
class GameSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun navigationLifecycleSettingsAndRealScreenshots() {
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("NEON").assertIsDisplayed()
        capture("main-menu.png")
        compose.onNodeWithText("START RUN   →").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(2800)
        compose.onNodeWithContentDescription("Pause game").assertIsDisplayed()
        capture("gameplay.png")

        compose.onNodeWithContentDescription("Pause game").performClick()
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithText("SIGNAL PAUSED").assertIsDisplayed()
        compose.onNodeWithText("RESUME RUN").performClick()
        compose.mainClock.advanceTimeBy(32)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithText("SIGNAL PAUSED").assertIsDisplayed()
        compose.onNodeWithText("RESUME RUN").performClick()

        // Let the real unmodified simulation run until a stationary ship naturally crashes.
        var ended = false
        for (step in 0 until 480) {
            compose.mainClock.advanceTimeBy(250)
            ended = compose.onAllNodesWithText("SIGNAL LOST").fetchSemanticsNodes().isNotEmpty()
            if (ended) break
        }
        assertTrue("Expected a natural collision within 120 simulated seconds", ended)
        compose.onNodeWithText("SIGNAL LOST").assertIsDisplayed()
        capture("game-over.png")
        compose.onNodeWithText("MAIN MENU").performClick()
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithText("SETTINGS").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(32)

        val repository = GameRepository(InstrumentationRegistry.getInstrumentation().targetContext)
        val previous = runBlocking { repository.preferences.first() }
        try {
            compose.onNodeWithContentDescription("Music volume").performSemanticsAction(SemanticsActions.SetProgress) { it(.25f) }
            compose.onNodeWithContentDescription("Sound effects volume").performSemanticsAction(SemanticsActions.SetProgress) { it(.35f) }
            compose.waitUntil(5000) {
                runBlocking { repository.preferences.first().let { it.musicVolume == .25f && it.sfxVolume == .35f } }
            }
            compose.activityRule.scenario.recreate()
            compose.mainClock.advanceTimeBy(64)
            compose.onNodeWithText("25%").assertExists()
            compose.onNodeWithText("35%").assertExists()
        } finally {
            runBlocking { repository.setMusicVolume(previous.musicVolume); repository.setSfxVolume(previous.sfxVolume) }
        }
    }

    @Test fun nativePlaybackPausesResumesAndMutes() {
        compose.mainClock.autoAdvance = false
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = GameRepository(context)
        val previous = runBlocking { repository.preferences.first() }
        val manager = context.getSystemService(AudioManager::class.java)
        fun playing() = manager.activePlaybackConfigurations.any {
            it.audioAttributes.usage == AudioAttributes.USAGE_GAME
        }
        try {
            runBlocking { repository.setMusicVolume(.4f); repository.setSfxVolume(.8f) }
            compose.mainClock.advanceTimeBy(100)
            compose.waitUntil(5000) { playing() }
            compose.onNodeWithText("START RUN   →").performScrollTo().performClick()
            compose.mainClock.advanceTimeBy(32)
            compose.onNodeWithContentDescription("Pause game").performClick()
            compose.mainClock.advanceTimeBy(32)
            compose.waitUntil(5000) { !playing() }
            compose.onNodeWithText("RESUME RUN").performClick()
            compose.mainClock.advanceTimeBy(32)
            compose.waitUntil(5000) { playing() }
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            compose.waitUntil(5000) { !playing() }
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.mainClock.advanceTimeBy(32)
            compose.onNodeWithText("SIGNAL PAUSED").assertIsDisplayed()
            compose.onNodeWithText("RESUME RUN").performClick()
            compose.mainClock.advanceTimeBy(32)
            compose.waitUntil(5000) { playing() }
            runBlocking { repository.setMusicVolume(0f); repository.setSfxVolume(0f) }
            compose.waitUntil(5000) { !playing() }
            compose.activityRule.scenario.recreate()
            compose.mainClock.advanceTimeBy(64)
            compose.waitForIdle()
            assertFalse("Saved mute must survive Activity recreation", playing())
        } finally {
            runBlocking { repository.setMusicVolume(previous.musicVolume); repository.setSfxVolume(previous.sfxVolume) }
        }
    }

    @Test fun audioAssetsDecodeOnAndroid() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (resource in listOf(R.raw.afterlight, R.raw.crash, R.raw.energy, R.raw.whoosh, R.raw.power_up, R.raw.game_over, R.raw.click)) {
            val retriever = MediaMetadataRetriever()
            try {
                context.resources.openRawResourceFd(resource).use {
                    retriever.setDataSource(it.fileDescriptor, it.startOffset, it.length)
                }
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
                assertTrue("Audio asset should have a nonzero duration", duration > 0)
                if (resource == R.raw.afterlight) assertTrue(duration in 38000..39000)
                else assertTrue("SoundPool assets must remain short", duration < 2000)
            } finally { retriever.release() }
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val folder = File(compose.activity.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(folder, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

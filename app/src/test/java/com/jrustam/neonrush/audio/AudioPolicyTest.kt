package com.jrustam.neonrush.audio

import com.jrustam.neonrush.game.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class AudioPolicyTest {
    @Test fun musicRequiresForegroundSceneFocusAndNonzeroVolume() {
        assertTrue(AudioPolicy.musicAllowed(true, true, true, .5f))
        assertFalse(AudioPolicy.musicAllowed(false, true, true, .5f))
        assertFalse(AudioPolicy.musicAllowed(true, false, true, .5f))
        assertFalse(AudioPolicy.musicAllowed(true, true, false, .5f))
        assertFalse(AudioPolicy.musicAllowed(true, true, true, 0f))
    }
    @Test fun volumeHandlesCorruptValuesAndBounds() {
        assertEquals(0f, AudioPolicy.volume(-1f, .5f), 0f)
        assertEquals(1f, AudioPolicy.volume(2f, .5f), 0f)
        assertEquals(.5f, AudioPolicy.volume(Float.NaN, .5f), 0f)
        assertEquals(.5f, AudioPolicy.volume(Float.POSITIVE_INFINITY, .5f), 0f)
    }
    @Test fun nearMissOnlyFiresAtCrossingAndWithinAudibleRange() {
        assertTrue(Collision.nearMiss(554f, 556f, 50f, 20f))
        assertTrue(Collision.nearMiss(554f, 556f, -50f, 20f))
        assertFalse(Collision.nearMiss(556f, 558f, 50f, 20f))
        assertFalse(Collision.nearMiss(552f, 554f, 50f, 20f))
        assertFalse(Collision.nearMiss(554f, 556f, 10f, 20f))
        assertFalse(Collision.nearMiss(554f, 556f, 120f, 20f))
    }
    @Test fun collisionEventsDrainOnceAndDoNotReplayAfterDeathOrReset() {
        val engine = GameEngine(Random(42))
        val events = mutableListOf<GameEvent>()
        repeat(1200) {
            engine.frame.entities.firstOrNull { it.kind == EntityKind.Hazard }?.let { engine.moveTo(it.x) }
            engine.advance(1f / 60)
            events += engine.drainEvents()
            if (engine.frame.ended) return@repeat
        }
        assertTrue(engine.frame.ended)
        assertEquals(1, events.count { it == GameEvent.CRASH })
        engine.advance(.05f)
        assertTrue(engine.drainEvents().isEmpty())
        engine.reset()
        assertTrue(engine.drainEvents().isEmpty())
    }
}

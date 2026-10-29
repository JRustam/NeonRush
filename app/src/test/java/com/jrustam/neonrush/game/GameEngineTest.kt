package com.jrustam.neonrush.game

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class GameEngineTest {
    @Test
    fun energyAndMultiplierScoring() {
        assertEquals(50, Scoring.energy(false))
        assertEquals(100, Scoring.energy(true))
        assertEquals(25f, Scoring.survival(2.5f, false), .001f)
        assertEquals(50f, Scoring.survival(2.5f, true), .001f)
    }

    @Test
    fun stationaryOverlapAndTangency() {
        assertTrue(Collision.sweptCircles(3f, 4f, 3f, 4f, 5f))
        assertFalse(Collision.sweptCircles(3f, 4.1f, 3f, 4.1f, 5f))
    }

    @Test
    fun fastObstacleCannotTunnelThroughPlayer() {
        assertTrue(Collision.sweptCircles(0f, -100f, 0f, 100f, 20f))
        assertFalse(Collision.sweptCircles(21f, -100f, 21f, 100f, 20f))
    }

    @Test
    fun horizontalPlayerSweepHitsObstacle() {
        assertTrue(Collision.sweptCircles(-80f, 0f, 80f, 0f, 25f))
        assertFalse(Collision.sweptCircles(-80f, 30f, 80f, 30f, 25f))
    }

    @Test
    fun collisionDoesNotExtrapolatePastSegment() {
        assertFalse(Collision.sweptCircles(40f, 0f, 30f, 0f, 20f))
    }

    @Test
    fun simulationAwardsSurvivalPointsAndResets() {
        val engine = GameEngine(Random(7))
        repeat(60) { engine.advance(1f / 60) }
        assertEquals(10, engine.frame.score)
        assertEquals(1f, engine.frame.elapsed, .001f)
        engine.reset()
        assertEquals(GameFrame(), engine.frame)
    }

    @Test
    fun playerStaysInBoundsAndStallsAreCapped() {
        val engine = GameEngine(Random(7))
        engine.moveTo(-999f)
        repeat(30) { engine.advance(.05f) }
        assertTrue(engine.frame.playerX >= 24f)
        val before = engine.frame.elapsed
        engine.advance(10f)
        assertEquals(.05f, engine.frame.elapsed - before, .001f)
    }

    @Test
    fun invalidDeltaDoesNotChangeState() {
        val engine = GameEngine()
        engine.advance(Float.NaN); engine.advance(-1f)
        assertEquals(GameFrame(), engine.frame)
    }
}

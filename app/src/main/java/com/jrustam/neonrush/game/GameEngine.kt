package com.jrustam.neonrush.game

import kotlin.math.*
import kotlin.random.Random

const val WORLD_WIDTH = 360f
const val WORLD_HEIGHT = 640f
const val PLAYER_Y = 555f
const val PLAYER_RADIUS = 11f

enum class GameEvent { CRASH, ENERGY_COLLECTED, NEAR_MISS, POWER_ACTIVATED, SHIELD_BROKEN }

enum class Pickup { SHIELD, SLOW, MULTIPLIER }
sealed class EntityKind {
    data object Hazard : EntityKind()
    data object Energy : EntityKind()
    data class Power(val pickup: Pickup) : EntityKind()
}

data class Entity(
    val id: Long,
    val x: Float,
    val y: Float,
    val radius: Float,
    val speed: Float,
    val kind: EntityKind
)

data class Particle(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val life: Float,
    val pink: Boolean
)

data class GameFrame(
    val playerX: Float = 180f,
    val elapsed: Float = 0f,
    val score: Int = 0,
    val orbs: Int = 0,
    val shield: Float = 0f,
    val slow: Float = 0f,
    val multiplier: Float = 0f,
    val entities: List<Entity> = emptyList(),
    val particles: List<Particle> = emptyList(),
    val ended: Boolean = false,
    val flash: Float = 0f,
) {
    val level: Int get() = 1 + (elapsed / 15).toInt()
}

fun Float.inArena() = coerceIn(24f, WORLD_WIDTH - 24f)

object Scoring {
    const val ENERGY_POINTS = 50
    fun energy(multiplied: Boolean): Int = ENERGY_POINTS * if (multiplied) 2 else 1
    fun survival(seconds: Float, multiplied: Boolean): Float =
        seconds * 10 * if (multiplied) 2 else 1
}

object Collision {
    fun nearMiss(
        previousY: Float,
        nextY: Float,
        horizontalGap: Float,
        hazardRadius: Float
    ): Boolean =
        previousY <= PLAYER_Y && nextY > PLAYER_Y &&
                abs(horizontalGap) > PLAYER_RADIUS + hazardRadius &&
                abs(horizontalGap) < PLAYER_RADIUS + hazardRadius + 50f

    /** Relative swept circles: catches fast objects and player motion between simulation steps. */
    fun sweptCircles(ax: Float, ay: Float, bx: Float, by: Float, radius: Float): Boolean {
        val dx = bx - ax
        val dy = by - ay
        val length = dx * dx + dy * dy
        val t = if (length == 0f) 0f else (-(ax * dx + ay * dy) / length).coerceIn(0f, 1f)
        return (ax + t * dx).pow(2) + (ay + t * dy).pow(2) <= radius * radius
    }
}

/** Pure Kotlin simulation. World coordinates are independent of display density and aspect ratio. */
class GameEngine(private val random: Random = Random.Default) {
    var frame = GameFrame()
        private set
    private var targetX = 180f
    private var points = 0.0
    private var spawnIn = .6f
    private var energyIn = .8f
    private var powerIn = 7f
    private var nextId = 0L
    private var grace = 0f
    private val events = mutableListOf<GameEvent>()
    fun drainEvents(): List<GameEvent> = events.toList().also { events.clear() }

    fun reset() {
        events.clear()
        frame = GameFrame(); targetX = 180f; points = 0.0
        spawnIn = .6f; energyIn = .8f; powerIn = 7f; grace = 0f; nextId = 0
    }

    fun moveTo(x: Float) {
        targetX = x.inArena()
    }

    fun advance(seconds: Float) {
        if (frame.ended || !seconds.isFinite() || seconds <= 0f) return
        // Cap long stalls; the app lifecycle pauses gameplay instead of catching up off screen.
        var remaining = seconds.coerceAtMost(.05f)
        while (remaining > 0f && !frame.ended) {
            val dt = min(remaining, 1f / 120)
            step(dt)
            remaining -= dt
        }
    }

    private fun step(dt: Float) {
        val old = frame
        val time = old.elapsed + dt
        val x = old.playerX + (targetX - old.playerX) * (1 - exp(-22f * dt))
        var shield = (old.shield - dt).coerceAtLeast(0f)
        var slow = (old.slow - dt).coerceAtLeast(0f)
        var multiplier = (old.multiplier - dt).coerceAtLeast(0f)
        grace = (grace - dt).coerceAtLeast(0f)
        val worldDt = dt * if (slow > 0f) .42f else 1f
        var orbs = old.orbs
        var dead = false
        var flash = (old.flash - dt * 3).coerceAtLeast(0f)
        points += Scoring.survival(dt, multiplier > 0f)
        val entities = ArrayList<Entity>()
        val particles = old.particles.mapNotNullTo(ArrayList()) {
            if (it.life <= dt) null else it.copy(
                x = it.x + it.vx * dt,
                y = it.y + it.vy * dt,
                life = it.life - dt
            )
        }

        fun burst(px: Float, py: Float, pink: Boolean) {
            repeat(14) {
                val angle = random.nextFloat() * 2f * PI.toFloat()
                val velocity = 30f + random.nextFloat() * 105f
                particles.add(
                    Particle(
                        px,
                        py,
                        cos(angle) * velocity,
                        sin(angle) * velocity,
                        .5f + random.nextFloat() * .3f,
                        pink
                    )
                )
            }
        }
        for (entity in old.entities) {
            val moved = entity.copy(y = entity.y + entity.speed * worldDt)
            val hit = Collision.sweptCircles(
                entity.x - old.playerX, entity.y - PLAYER_Y,
                moved.x - x, moved.y - PLAYER_Y, PLAYER_RADIUS + entity.radius
            )
            if (hit) {
                when (val kind = entity.kind) {
                    EntityKind.Hazard -> {
                        if (grace <= 0f) {
                            if (shield > 0f) {
                                shield = 0f; grace = .8f; flash =
                                    .5f; events.add(GameEvent.SHIELD_BROKEN)
                            } else {
                                dead = true; flash = 1f; events.add(GameEvent.CRASH)
                            }
                        }
                        burst(moved.x, moved.y, true)
                    }

                    EntityKind.Energy -> {
                        points += Scoring.energy(multiplier > 0f); orbs++; events.add(GameEvent.ENERGY_COLLECTED); burst(
                            moved.x,
                            moved.y,
                            false
                        )
                    }

                    is EntityKind.Power -> {
                        events.add(GameEvent.POWER_ACTIVATED)
                        when (kind.pickup) {
                            Pickup.SHIELD -> shield = 12f
                            Pickup.SLOW -> slow = 6f
                            Pickup.MULTIPLIER -> multiplier = 8f
                        }
                        burst(moved.x, moved.y, false)
                    }
                }
            } else if (moved.y < WORLD_HEIGHT + 30) {
                entities.add(moved)
                if (entity.kind == EntityKind.Hazard && Collision.nearMiss(
                        entity.y,
                        moved.y,
                        moved.x - x,
                        entity.radius
                    )
                ) {
                    events.add(GameEvent.NEAR_MISS)
                }
            }
        }
        fun spawn(kind: EntityKind, radius: Float, speed: Float) {
            // One hazard per wave always leaves room to dodge, even at maximum difficulty.
            entities.add(
                Entity(
                    nextId++,
                    28f + random.nextFloat() * 304f,
                    -25f,
                    radius,
                    speed,
                    kind
                )
            )
        }
        spawnIn -= worldDt
        energyIn -= worldDt
        powerIn -= dt
        if (spawnIn <= 0f) {
            spawn(
                EntityKind.Hazard,
                16f + random.nextFloat() * 7f,
                (150f + time * 2.1f).coerceAtMost(340f)
            )
            spawnIn += (.84f - time * .004f).coerceAtLeast(.30f)
        }
        if (energyIn <= 0f) {
            spawn(EntityKind.Energy, 8f, 140f); energyIn += 1.15f
        }
        if (powerIn <= 0f) {
            spawn(EntityKind.Power(Pickup.entries.random(random)), 12f, 115f)
            powerIn += 9f
        }
        frame = GameFrame(
            x,
            time,
            points.toInt(),
            orbs,
            shield,
            slow,
            multiplier,
            entities,
            particles.takeLast(160),
            dead,
            flash
        )
    }
}

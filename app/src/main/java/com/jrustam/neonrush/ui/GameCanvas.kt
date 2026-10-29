package com.jrustam.neonrush.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jrustam.neonrush.game.*
import com.jrustam.neonrush.ui.theme.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.math.*

@Composable
internal fun Arena(vm: GameViewModel, particles: Boolean, playing: Boolean, modifier: Modifier) {
    // Read this State only inside onDraw: simulation frames invalidate drawing, not composition.
    val frame = vm.frame.collectAsStateWithLifecycle()
    val labelPaint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE; textSize = 11f; textAlign = android.graphics.Paint.Align.CENTER; typeface = android.graphics.Typeface.MONOSPACE
    } }
    Canvas(modifier.clip(RoundedCornerShape(16.dp)).border(1.dp, Violet.copy(alpha = .2f), RoundedCornerShape(16.dp))
        .semantics { contentDescription = "Game arena. Drag horizontally to dodge pink hazards and collect cyan energy orbs." }
        .pointerInput(playing) {
            if (playing) detectDragGestures(
                onDragStart = { vm.moveTo(it.x / size.width * WORLD_WIDTH) },
                onDrag = { change, _ -> change.consume(); vm.moveTo(change.position.x / size.width * WORLD_WIDTH) }
            )
        }) {
        val f = frame.value
        scale(size.width / WORLD_WIDTH, pivot = Offset.Zero) {
            clipRect(0f, 0f, WORLD_WIDTH, WORLD_HEIGHT) {
                arenaBackground(f.elapsed)
                f.entities.forEach { e ->
                    val center = Offset(e.x, e.y)
                    when (val kind = e.kind) {
                        EntityKind.Hazard -> {
                            glow(center, e.radius * 2.5f, Pink, .15f)
                            drawLine(Pink.copy(alpha = .15f), center - Offset(0f, 32f), center, e.radius * 1.1f)
                            polygon(center, e.radius, 6, Pink, f.elapsed * 24f + e.id * 17f)
                            drawCircle(Pink.copy(alpha = .3f), 4f, center)
                        }
                        EntityKind.Energy -> {
                            glow(center, 25f, Cyan, .22f)
                            polygon(center, 8f, 4, Cyan, 0f)
                            drawCircle(Color.White, 2f, center)
                        }
                        is EntityKind.Power -> {
                            val color = when (kind.pickup) { Pickup.SHIELD -> Cyan; Pickup.SLOW -> Violet; Pickup.MULTIPLIER -> Pink }
                            glow(center, 32f, color, .25f)
                            drawCircle(Ink, 13f, center)
                            drawCircle(color, 13f, center, style = Stroke(1.5f))
                            val label = when (kind.pickup) { Pickup.SHIELD -> "S"; Pickup.SLOW -> "~"; Pickup.MULTIPLIER -> "2×" }
                            drawContext.canvas.nativeCanvas.drawText(label, e.x, e.y + 4, labelPaint)
                        }
                    }
                }
                if (particles) f.particles.forEach { particle ->
                    drawCircle((if (particle.pink) Pink else Cyan).copy(alpha = (particle.life / .8f).coerceIn(0f, 1f)), 1.8f, Offset(particle.x, particle.y))
                }
                if (!f.ended) ship(Offset(f.playerX, PLAYER_Y), f.elapsed, f.shield > 0)
                if (f.slow > 0) drawRect(Violet.copy(alpha = .04f), size = Size(WORLD_WIDTH, WORLD_HEIGHT))
                if (f.flash > 0) drawRect(Pink.copy(alpha = f.flash * .2f), size = Size(WORLD_WIDTH, WORLD_HEIGHT))
                if (f.elapsed < 3f) {
                    labelPaint.alpha = ((1f - f.elapsed / 3f) * 180).toInt()
                    drawContext.canvas.nativeCanvas.drawText("KEEP MOVING. STAY ALIVE.", 180f, 450f, labelPaint)
                    labelPaint.alpha = 255
                }
            }
        }
    }
}

private fun DrawScope.glow(center: Offset, radius: Float, color: Color, alpha: Float) {
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center, radius), radius, center)
}
private fun DrawScope.polygon(center: Offset, radius: Float, sides: Int, color: Color, rotation: Float) {
    val path = Path()
    repeat(sides) { i ->
        val angle = (i * 2 * PI / sides + rotation * PI / 180 - PI / 2).toFloat()
        val x = center.x + cos(angle) * radius
        val y = center.y + sin(angle) * radius
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color.copy(alpha = .13f))
    drawPath(path, color.copy(alpha = .1f), style = Stroke(6f))
    drawPath(path, color, style = Stroke(1.5f))
}
internal fun DrawScope.ship(center: Offset, time: Float, shield: Boolean) {
    glow(center, 44f, Cyan, .22f)
    val flame = Path().apply {
        moveTo(center.x - 5f, center.y + 9f)
        lineTo(center.x, center.y + 28f + sin(time * 30f) * 6f)
        lineTo(center.x + 5f, center.y + 9f); close()
    }
    drawPath(flame, Brush.verticalGradient(listOf(Cyan.copy(alpha = .85f), Violet.copy(alpha = 0f)), center.y, center.y + 35f))
    val path = Path().apply {
        moveTo(center.x, center.y - 16f); lineTo(center.x + 13f, center.y + 12f)
        lineTo(center.x, center.y + 6f); lineTo(center.x - 13f, center.y + 12f); close()
    }
    drawPath(path, Cyan.copy(alpha = .14f))
    drawPath(path, Cyan.copy(alpha = .12f), style = Stroke(7f))
    drawPath(path, Cyan, style = Stroke(1.6f))
    drawLine(Color.White, center - Offset(0f, 8f), center + Offset(0f, 3f), 2f)
    if (shield) {
        drawCircle(Cyan.copy(alpha = .08f), 27f, center)
        drawCircle(Cyan.copy(alpha = .65f + sin(time * 5) * .2f), 27f, center, style = Stroke(1.5f))
    }
}
internal fun DrawScope.backdrop(time: Float) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF101127), Ink, Color(0xFF100D24))))
    glow(Offset(size.width * .85f, size.height * .2f), size.width * .9f, Violet, .08f)
    repeat(40) { i ->
        val x = ((i * 137.5f) % 997) / 997 * size.width
        val y = ((i * 79.3f + time * 10) % 991) / 991 * size.height
        drawCircle(Cyan.copy(alpha = .08f + (i % 3) * .06f), if (i % 3 == 0) 1.5f else .7f, Offset(x, y))
    }
}
private fun DrawScope.arenaBackground(time: Float) {
    drawRect(Color(0xFF090F21), size = Size(WORLD_WIDTH, WORLD_HEIGHT))
    glow(Offset(180f, 70f), 240f, Violet, .13f)
    // Vertical city silhouettes and flowing perspective grid use only Canvas primitives.
    repeat(14) { i ->
        val h = 35f + ((i * 47) % 100)
        drawRect(Color(0xFF12162F), Offset(i * 28f, 175f - h), Size(22f, h))
        repeat(3) { j -> drawRect(Violet.copy(alpha = .13f), Offset(i * 28f + 5, 180f - h + j * 14), Size(3f, 5f)) }
    }
    drawLine(Violet.copy(alpha = .25f), Offset(0f, 177f), Offset(360f, 177f), 1f)
    for (i in -4..4) drawLine(Violet.copy(alpha = .12f), Offset(180f + i * 15, 177f), Offset(180f + i * 130, 640f), .6f)
    repeat(15) { i ->
        val progress = ((i / 15f + time * .035f) % 1f)
        val y = 177f + progress.pow(2) * 463f
        drawLine(Violet.copy(alpha = .08f + progress * .08f), Offset(0f, y), Offset(360f, y), .6f)
    }
    repeat(24) { i ->
        val x = (i * 137f) % 360
        val y = (i * 73f + time * 16f) % 640
        drawCircle(Cyan.copy(alpha = .22f), .8f, Offset(x, y))
    }
    drawLine(Cyan.copy(alpha = .22f), Offset(10f, 510f), Offset(10f, 605f), 2f)
    drawLine(Cyan.copy(alpha = .22f), Offset(350f, 510f), Offset(350f, 605f), 2f)
    drawLine(Cyan.copy(alpha = .08f), Offset(20f, PLAYER_Y + 25), Offset(340f, PLAYER_Y + 25), 1f)
}


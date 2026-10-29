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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import com.jrustam.neonrush.R
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

private const val NANOSECONDS_PER_SECOND = 1_000_000_000f
private const val SCORE_DIGITS = 6
private const val SECTOR_DIGITS = 2
private const val MENU_SHIP_PERIOD_MILLIS = 4000
private const val MENU_SHIP_CYCLE_RADIANS = 6.283185f
private const val VOLUME_PERCENT_SCALE = 100

@Composable
fun NeonRushApp(vm: GameViewModel) {
    val screen by vm.screen.collectAsStateWithLifecycle()
    val preferences by vm.preferences.collectAsStateWithLifecycle()
    val saveError by vm.saveError.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    BackHandler(screen != Screen.Menu) {
        when (screen) { Screen.Playing -> vm.pause(); Screen.Paused -> vm.resume(); else -> vm.menu() }
    }
    LaunchedEffect(screen) {
        if (screen == Screen.Playing) {
            var last = withFrameNanos { it }
            while (true) {
                withFrameNanos { now -> vm.tick((now - last) / NANOSECONDS_PER_SECOND); last = now }
            }
        }
    }
    LaunchedEffect(preferences.haptics) {
        var lastOrbs = vm.frame.value.orbs
        var wasEnded = vm.frame.value.ended
        vm.frame.map { it.orbs to it.ended }.distinctUntilChanged().collect { (orbs, ended) ->
            if (preferences.haptics && (orbs > lastOrbs || (ended && !wasEnded)))
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            lastOrbs = orbs; wasEnded = ended
        }
    }
    Surface(Modifier.fillMaxSize(), color = Ink) {
        Box(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize()) { backdrop(0f) }
            Column(Modifier.fillMaxSize().safeDrawingPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.weight(1f).widthIn(max = 520.dp).fillMaxWidth()) {
                    when (screen) {
                        Screen.Menu -> Menu(preferences.highScore, vm::start, vm::settings)
                        Screen.Settings -> Settings(preferences.particles, preferences.haptics, vm::particles, vm::haptics, vm::menu, preferences.musicVolume, preferences.sfxVolume, vm::musicVolume, vm::sfxVolume)
                        else -> {
                            GameScreen(vm, preferences.particles, screen == Screen.Playing)
                            if (screen == Screen.Paused) Overlay(stringResource(R.string.signal_paused), stringResource(R.string.pause_title), stringResource(R.string.pause_subtitle), stringResource(R.string.resume_run), vm::resume, vm::menu)
                            if (screen == Screen.GameOver) {
                                val result by vm.frame.collectAsStateWithLifecycle()
                                Result(result, result.score > vm.previousBest, maxOf(preferences.highScore, result.score), vm::start, vm::menu)
                            }
                        }
                    }
                }
                saveError?.let { Text(stringResource(it), color = Pink, fontSize = 12.sp, modifier = Modifier.padding(12.dp)) }
            }
        }
    }
}

@Composable
private fun Eyebrow(text: String, color: Color = Cyan) {
    Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.5.sp, fontFamily = FontFamily.Monospace)
}

@Composable
private fun Menu(best: Int, start: () -> Unit, settings: () -> Unit) {
    val motion = rememberInfiniteTransition(label = "ship idle")
    val phase = motion.animateFloat(0f, MENU_SHIP_CYCLE_RADIANS, infiniteRepeatable(tween(MENU_SHIP_PERIOD_MILLIS, easing = LinearEasing)), label = "ship pulse")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(stringResource(R.string.arcade_brand))
            Text(stringResource(R.string.menu_number), color = Muted, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
        Spacer(Modifier.height(24.dp))
        Eyebrow(stringResource(R.string.menu_eyebrow), Violet)
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.title_neon), style = TextStyle(color = Cyan, fontSize = 64.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, letterSpacing = 5.sp, shadow = Shadow(Cyan.copy(alpha = .6f), Offset.Zero, 28f)))
        Text(stringResource(R.string.title_rush), style = TextStyle(color = Color.White, fontSize = 64.sp, lineHeight = 64.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, letterSpacing = 7.sp, shadow = Shadow(Pink.copy(alpha = .7f), Offset.Zero, 28f)))
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.menu_tagline), color = Muted, fontSize = 14.sp)
        Canvas(Modifier.fillMaxWidth().height(116.dp)) {
            val center = Offset(size.width / 2, size.height / 2 + sin(phase.value) * 6f)
            drawCircle(Brush.radialGradient(listOf(Violet.copy(alpha = .18f), Color.Transparent), center, 140f), 140f, center)
            drawOval(Cyan.copy(alpha = .16f), Offset(center.x - 85, center.y + 38), Size(170f, 30f), style = Stroke(1f))
            translate(center.x, center.y) { scale(2.2f, Offset.Zero) { ship(Offset.Zero, phase.value, false) } }
            drawLine(Cyan.copy(alpha = .3f), Offset(20f, center.y), Offset(center.x - 75f, center.y), 1f)
            drawLine(Pink.copy(alpha = .3f), Offset(center.x + 75f, center.y), Offset(size.width - 20f, center.y), 1f)
        }
        Panel {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column { Eyebrow(stringResource(R.string.personal_best), Muted); Spacer(Modifier.height(6.dp)); Text(stringResource(R.string.high_score), color = Muted, fontSize = 10.sp, letterSpacing = 1.sp) }
                Text(best.toString().padStart(SCORE_DIGITS, '0'), color = Cyan, fontSize = 28.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(22.dp))
        Action(stringResource(R.string.start_run), start)
        TextButton(onClick = settings, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(stringResource(R.string.settings), color = Muted, letterSpacing = 2.sp, fontSize = 12.sp) }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Hint("◇", stringResource(R.string.collect), Cyan); Hint("⬡", stringResource(R.string.dodge), Pink); Hint("✦", stringResource(R.string.power_up), Violet)
        }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.menu_controls_hint), color = Muted.copy(alpha = .7f), fontSize = 9.sp, letterSpacing = 1.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun Hint(icon: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, color = color, fontSize = 22.sp)
        Spacer(Modifier.height(5.dp))
        Text(label, color = Muted, fontSize = 9.sp, letterSpacing = 1.sp)
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xE611182B)).border(1.dp, Violet.copy(alpha = .18f), RoundedCornerShape(16.dp)).padding(20.dp), content = content)
}

@Composable
private fun Action(label: String, action: () -> Unit) {
    Button(onClick = action, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Ink)) {
        Text(label, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, letterSpacing = 2.sp)
    }
}

@Composable
private fun GameScreen(vm: GameViewModel, particles: Boolean, playing: Boolean) {
    var arenaWidth by remember { mutableFloatStateOf(0f) }
    Column(Modifier.fillMaxSize()
        .pointerInput(playing, arenaWidth) {
            if (playing && arenaWidth > 0f) {
                // The arena is centered; gestures also work over the HUD and outer padding.
                fun movePlayer(fingerX: Float) {
                    val arenaLeft = (size.width - arenaWidth) / 2f
                    vm.moveTo((fingerX - arenaLeft) / arenaWidth * WORLD_WIDTH)
                }
                detectDragGestures(
                    onDragStart = { movePlayer(it.x) },
                    onDrag = { change, _ ->
                        change.consume()
                        movePlayer(change.position.x)
                    }
                )
            }
        }
        .padding(horizontal = 16.dp, vertical = 10.dp)) {
        GameHud(vm, playing)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val boardWidth = minOf(maxWidth, maxHeight * (WORLD_WIDTH / WORLD_HEIGHT))
                Arena(vm, particles, Modifier.width(boardWidth)
                    .aspectRatio(WORLD_WIDTH / WORLD_HEIGHT)
                    .onSizeChanged { arenaWidth = it.width.toFloat() })
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Eyebrow(stringResource(R.string.game_brand), Muted)
            Text(stringResource(R.string.game_controls_hint), color = Muted, fontSize = 9.sp, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun GameHud(vm: GameViewModel, playing: Boolean) {
    val hud by vm.hud.collectAsStateWithLifecycle()
    val pauseDescription = stringResource(R.string.pause_game)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column { Eyebrow(stringResource(R.string.score), Muted); Text(hud.score.toString().padStart(SCORE_DIGITS, '0'), color = Cyan, fontSize = 28.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) { Eyebrow(stringResource(R.string.sector), Muted); Text(hud.level.toString().padStart(SECTOR_DIGITS, '0'), color = Color.White, fontSize = 22.sp, fontFamily = FontFamily.Monospace) }
        OutlinedButton(onClick = { vm.pause() }, enabled = playing, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(12.dp)) { Text("Ⅱ", fontSize = 20.sp, modifier = Modifier.semantics { contentDescription = pauseDescription }) }
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PowerStatus(stringResource(R.string.shield), hud.shield, 12f, Cyan, Modifier.weight(1f))
        PowerStatus(stringResource(R.string.slow), hud.slow, 6f, Violet, Modifier.weight(1f))
        PowerStatus(stringResource(R.string.score_multiplier), hud.multiplier, 8f, Pink, Modifier.weight(1f))
    }
}

@Composable
private fun PowerStatus(name: String, seconds: Float, duration: Float, color: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = if (seconds > 0) .13f else .035f)).padding(8.dp)) {
        Text(if (seconds > 0) stringResource(R.string.power_up_countdown, name, ceil(seconds).toInt().toString()) else name, color = if (seconds > 0) color else Muted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(5.dp))
        LinearProgressIndicator(progress = { seconds / duration }, color = color, trackColor = color.copy(alpha = .1f), modifier = Modifier.fillMaxWidth().height(2.dp))
    }
}

@Composable
private fun Overlay(tag: String, title: String, subtitle: String, primary: String, action: () -> Unit, menu: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Ink.copy(alpha = .92f)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Eyebrow(tag, Violet)
            Spacer(Modifier.height(20.dp))
            Text(title, fontSize = 36.sp, fontWeight = FontWeight.Black, color = Color.White)
            Spacer(Modifier.height(10.dp))
            Text(subtitle, color = Muted)
            Spacer(Modifier.height(40.dp))
            Action(primary, action)
            TextButton(onClick = menu, modifier = Modifier.padding(top = 10.dp)) { Text(stringResource(R.string.end_run_return), color = Muted, fontSize = 11.sp, letterSpacing = 1.sp) }
        }
    }
}

@Composable
private fun Result(frame: GameFrame, record: Boolean, best: Int, retry: () -> Unit, menu: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Ink.copy(alpha = .94f)), contentAlignment = Alignment.Center) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Eyebrow(stringResource(R.string.run_complete), Pink)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.signal_lost), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
            Text(stringResource(R.string.game_over_subtitle), color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
            Spacer(Modifier.height(30.dp))
            Eyebrow(if (record) stringResource(R.string.new_personal_best) else stringResource(R.string.final_score), if (record) Cyan else Muted)
            Text(frame.score.toString().padStart(SCORE_DIGITS, '0'), color = Cyan, fontSize = 48.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 12.dp))
            Panel {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat(stringResource(R.string.survived), stringResource(R.string.duration_seconds, frame.elapsed.toInt().toString())); Stat(stringResource(R.string.energy), "${frame.orbs}"); Stat(stringResource(R.string.sector), "${frame.level}")
                }
                HorizontalDivider(Modifier.padding(vertical = 16.dp), color = Violet.copy(alpha = .15f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.all_time_best), fontSize = 10.sp, color = Muted, letterSpacing = 1.sp)
                    Text(best.toString(), color = Cyan, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }
            Spacer(Modifier.height(28.dp))
            Action(stringResource(R.string.retry_run), retry)
            TextButton(onClick = menu, modifier = Modifier.padding(top = 10.dp)) { Text(stringResource(R.string.main_menu), color = Muted, letterSpacing = 1.sp) }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontSize = 22.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(6.dp)); Text(label, color = Muted, fontSize = 9.sp, letterSpacing = 1.sp)
    }
}

@Composable
private fun Settings(particles: Boolean, haptics: Boolean, setParticles: (Boolean) -> Unit, setHaptics: (Boolean) -> Unit, back: () -> Unit, musicVolume: Float, sfxVolume: Float, setMusicVolume: (Float) -> Unit, setSfxVolume: (Float) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp)) {
        TextButton(onClick = back, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.back_main_menu), color = Cyan, fontSize = 11.sp, letterSpacing = 1.sp) }
        Spacer(Modifier.height(24.dp)); Eyebrow(stringResource(R.string.system_configuration), Violet)
        Text(stringResource(R.string.settings), fontSize = 36.sp, fontWeight = FontWeight.Black, color = Color.White, modifier = Modifier.padding(top = 12.dp, bottom = 32.dp))
        Panel {
            VolumeControl(stringResource(R.string.music), musicVolume, setMusicVolume)
            VolumeControl(stringResource(R.string.sound_effects), sfxVolume, setSfxVolume)
            HorizontalDivider(Modifier.padding(vertical = 16.dp), color = Violet.copy(alpha = .15f))
            Toggle(stringResource(R.string.particle_effects), stringResource(R.string.particle_effects_description), particles, setParticles)
            HorizontalDivider(Modifier.padding(vertical = 16.dp), color = Violet.copy(alpha = .15f))
            Toggle(stringResource(R.string.haptic_feedback), stringResource(R.string.haptic_feedback_description), haptics, setHaptics)
        }
        Spacer(Modifier.height(28.dp)); Eyebrow(stringResource(R.string.how_to_survive))
        Text(stringResource(R.string.gameplay_instructions), color = Muted, fontSize = 14.sp, lineHeight = 23.sp, modifier = Modifier.padding(vertical = 16.dp))
        Panel {
            Text(stringResource(R.string.shield_heading), color = Cyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(stringResource(R.string.shield_description), color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
            Text(stringResource(R.string.slow_heading), color = Violet, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(stringResource(R.string.slow_description), color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
            Text(stringResource(R.string.multiplier_heading), color = Pink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(stringResource(R.string.multiplier_description), color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.save_and_pause_description), color = Muted, fontSize = 11.sp, lineHeight = 18.sp)
        Spacer(Modifier.height(20.dp)); Text(stringResource(R.string.version_label), color = Violet.copy(alpha = .6f), fontSize = 10.sp, letterSpacing = 2.sp)
    }
}

@Composable
private fun Toggle(title: String, subtitle: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, color = Color.White, fontSize = 15.sp); Text(subtitle, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp)) }
        Switch(checked = checked, onCheckedChange = change, modifier = Modifier.semantics { contentDescription = title })
    }
}

@Composable
private fun VolumeControl(label: String, saved: Float, save: (Float) -> Unit) {
    var value by remember(saved) { mutableFloatStateOf(saved) }
    val volumeDescription = stringResource(R.string.volume_description, label)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.White, fontSize = 15.sp)
        Text(if (value == 0f) stringResource(R.string.muted) else stringResource(R.string.volume_percentage, (value * VOLUME_PERCENT_SCALE).roundToInt().toString()), color = Cyan, fontSize = 12.sp)
    }
    Slider(value = value, onValueChange = { value = it }, onValueChangeFinished = { save(value) },
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = volumeDescription })
}

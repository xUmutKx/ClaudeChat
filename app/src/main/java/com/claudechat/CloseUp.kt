package com.claudechat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlin.math.sin

private val doneGreen = Color(0xFF4CAF50)
private val errorPink = Color(0xFFFF4D9D)
private val idleGrey = Color(0xFF5B6270)
private val blueScreens = listOf(0xFF2196F3, 0xFF4D8DFF, 0xFF1E88E5, 0xFF5AA9E6, 0xFF3F51B5, 0xFF42A5F5).map { Color(it) }

/** Wall, floor and window light for each room index: soft gradients instead of the old pixel sprites, which looked like blocks when scaled up. */
private val roomPalettes = listOf(
    listOf(0xFFE9DFD2, 0xFFC9B49A, 0xFFBFD9EA),   // warm plaster, oak floor, daylight
    listOf(0xFFD9E2EA, 0xFF9AA5B1, 0xFFCFE6F5),   // cool grey wall, concrete, cold window
    listOf(0xFFEADBC8, 0xFF8C6A4F, 0xFFFFE7B8),   // amber wall, dark wood, lamp light
    listOf(0xFFC9D8C6, 0xFF7E8F7A, 0xFFE1F2D8),   // sage wall, stone, soft green light
    listOf(0xFF2E3A4A, 0xFF1B222C, 0xFF8FB8E8),   // night room, dark floor, blue screen glow
)

/** A room drawn with gradients: wall, floor with a soft horizon, a window with light, and vignette. Picked by index, no image files. */
@Composable
private fun RoomBackdrop(index: Int, modifier: Modifier) {
    val p = roomPalettes[Math.floorMod(index, roomPalettes.size)].map { Color(it) }
    Canvas(modifier) {
        val w = size.width; val h = size.height; val horizon = h * .62f
        drawRect(Brush.verticalGradient(listOf(p[0], p[0].copy(alpha = .9f)), 0f, horizon))
        drawRect(Brush.verticalGradient(listOf(p[1].copy(alpha = .85f), p[1]), horizon, h), topLeft = Offset(0f, horizon), size = androidx.compose.ui.geometry.Size(w, h - horizon))
        // window: a pale panel on the wall with a soft glow around it
        val wx = w * .66f; val wy = h * .14f; val ww = w * .22f; val wh = h * .38f
        drawRect(Brush.radialGradient(listOf(p[2].copy(alpha = .55f), Color.Transparent), Offset(wx + ww / 2, wy + wh / 2), ww * 1.4f))
        drawRect(p[2], Offset(wx, wy), androidx.compose.ui.geometry.Size(ww, wh))
        drawRect(p[0].copy(alpha = .9f), Offset(wx + ww / 2 - 1.5f, wy), androidx.compose.ui.geometry.Size(3f, wh))
        // floor light falling from the window
        drawRect(Brush.radialGradient(listOf(p[2].copy(alpha = .25f), Color.Transparent), Offset(wx, horizon), w * .6f), Offset(0f, horizon), androidx.compose.ui.geometry.Size(w, h - horizon))
        // vignette so the edges fall off, like a real webcam
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0x66000000)), Offset(w / 2, h / 2), w * .75f))
    }
}

/**
 * The agent as a webcam frame: the head only (hat, ears, eyes), the room behind it. The mascot pictures are 20x18 units; the frame
 * shows units 1..19 across and the top 9 rows, so the laptop lid (row 9 down) is cut off. The screen light is laid over the face.
 */
@Composable
fun CloseUpCall(agent: AgentInfo, speaking: Boolean, roomIndex: Int, modifier: Modifier) {
    val clock = rememberInfiniteTransition(label = "closeup")
    val pulseT by clock.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "pulse")

    val idle = !agent.done && agent.activity.isEmpty() && agent.tools == 0 && agent.say.isEmpty()
    val working = !agent.done && !idle
    val screen = when {
        agent.done && agent.err -> errorPink
        agent.done -> doneGreen
        idle -> idleGrey
        else -> blueScreens[Math.floorMod(agent.id.hashCode(), blueScreens.size)]
    }
    val glow = 0.75f + 0.25f * sin(2 * Math.PI * pulseT).toFloat()

    BoxWithConstraints(modifier.aspectRatio(2f).clipToBounds()) {
        RoomBackdrop(roomIndex, Modifier.fillMaxSize())
        val u = maxWidth / 18f
        val face = Modifier.requiredSize(u * 20, u * 18).offset(x = -u, y = 0.dp)
        when {
            agent.done -> Mascot(false, face, sleeping = true)
            agent.err -> Mascot(false, face, error = true)
            working && !speaking -> Mascot(true, face, computer = true) // laptop frames alternate: typing
            else -> Mascot(false, face, idle = idle)
        }
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.radialGradient(listOf(screen.copy(alpha = 0.4f * (if (working) glow else 0.6f)), Color.Transparent), Offset(size.width * 0.5f, size.height * 0.5f), size.width * 0.4f))
        }
    }
}

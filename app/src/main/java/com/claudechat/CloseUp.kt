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

/** room_01..room_20 by index; 0 when the drawable is missing. getIdentifier, not R.drawable: the room is picked at runtime. */
@Composable
private fun roomDrawable(index: Int): Int {
    val ctx = LocalContext.current
    return remember(index) { ctx.resources.getIdentifier("room_%02d".format(index), "drawable", ctx.packageName) }
}

/**
 * The agent as a webcam frame: the head only (hat, ears, eyes), the room behind it. The mascot pictures are 20x18 units; the frame
 * shows units 1..19 across and the top 9 rows, so the laptop lid (row 9 down) is cut off. The screen light is laid over the face.
 */
@Composable
fun CloseUpCall(agent: AgentInfo, speaking: Boolean, roomIndex: Int, modifier: Modifier) {
    val room = roomDrawable(roomIndex)
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
        if (room != 0) Image(painterResource(room), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
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

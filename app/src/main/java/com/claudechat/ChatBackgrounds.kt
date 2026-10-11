package com.claudechat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/** Ten more chat backgrounds, each a small loop drawn on a Canvas. Picked in Customize like the others. */
val EXTRA_CHAT_BGS = listOf("snow", "rain", "fireflies", "bubbles", "sunset", "clouds", "lava", "confetti", "sakura", "matrix")

/** A stable pseudo-random value in 0..1 for particle [i] and channel [k]: the same particle is always in the same place. */
private fun rnd(i: Int, k: Int): Float = ((i * 7919 + k * 104729) % 1000) / 1000f

@Composable
fun ExtraChatBackground(style: String, modifier: Modifier, dark: Boolean) {
    val clock = rememberInfiniteTransition(label = "bg_$style")
    val t by clock.animateFloat(0f, 1f, infiniteRepeatable(tween(20000, easing = LinearEasing)), label = "bgt")
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        when (style) {
            "snow" -> for (i in 0 until 70) {
                val x = ((rnd(i, 1) + 0.03f * sin(t * 2.0 * PI + i).toFloat()) % 1f + 1f) % 1f * w
                val y = ((rnd(i, 2) + t * (0.3f + rnd(i, 3) * 0.5f)) % 1f) * h
                drawCircle(Color.White.copy(alpha = if (dark) 0.85f else 0.7f), (1f + rnd(i, 4) * 2.2f).dp.toPx(), Offset(x, y))
            }
            "rain" -> for (i in 0 until 60) {
                val x = rnd(i, 1) * w
                val y = ((rnd(i, 2) + t * (2f + rnd(i, 3))) % 1f) * h
                drawLine(Color(0xFF9EC9FF).copy(alpha = if (dark) 0.45f else 0.35f), Offset(x, y), Offset(x - 4.dp.toPx(), y + 14.dp.toPx()), strokeWidth = 1.2.dp.toPx())
            }
            "fireflies" -> for (i in 0 until 26) {
                val x = (rnd(i, 1) + 0.08f * sin(t * 2.0 * PI + i).toFloat()) * w
                val y = (rnd(i, 2) + 0.06f * sin(t * 2.0 * PI * 0.7 + i * 1.3).toFloat()) * h
                val glow = 0.5f + 0.5f * sin(t * 2.0 * PI * (1 + i % 3) + i).toFloat()
                drawCircle(Color(0xFFFFE27A).copy(alpha = 0.15f + 0.8f * glow), (1.5f + rnd(i, 4) * 1.5f).dp.toPx(), Offset(x, y))
            }
            "bubbles" -> for (i in 0 until 22) {
                val x = rnd(i, 1) * w + 6f * sin(t * 2.0 * PI + i).toFloat()
                val y = h - ((rnd(i, 2) + t * (0.5f + rnd(i, 3) * 0.5f)) % 1f) * h
                drawCircle(Color(0xFF7FD8FF).copy(alpha = 0.22f), (4f + rnd(i, 4) * 10f).dp.toPx(), Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(1.2.dp.toPx()))
            }
            "sunset" -> {
                val k = 0.5f + 0.5f * sin(t * 2.0 * PI).toFloat()
                drawRect(Brush.verticalGradient(listOf(Color(0xFF2B1B4A).copy(alpha = if (dark) 0.9f else 0.35f), Color(0xFFFF7A59).copy(alpha = if (dark) 0.5f else 0.25f + 0.1f * k), Color.Transparent)))
                drawCircle(Color(0xFFFFC36B).copy(alpha = 0.4f), h * 0.12f, Offset(w * (0.2f + 0.6f * t), h * 0.72f))
            }
            "clouds" -> for (i in 0 until 5) {
                val x = ((rnd(i, 1) + t * (0.2f + rnd(i, 2) * 0.3f)) % 1.4f - 0.2f) * w
                val y = (0.15f + rnd(i, 3) * 0.6f) * h
                drawCircle(Color.White.copy(alpha = if (dark) 0.12f else 0.22f), (40f + rnd(i, 4) * 40f).dp.toPx(), Offset(x, y))
            }
            "lava" -> {
                val c = Offset(w * 0.5f, h * (0.6f + 0.1f * sin(t * 2.0 * PI).toFloat()))
                drawCircle(Brush.radialGradient(listOf(Color(0x66FF6A00), Color.Transparent), c, h * 0.7f), h * 0.7f, c)
                for (i in 0 until 12) {
                    val y = h - ((rnd(i, 1) + t * (0.4f + rnd(i, 2) * 0.4f)) % 1f) * h * 0.5f
                    drawCircle(Color(0xFFFF9A3C).copy(alpha = 0.35f), (3f + rnd(i, 3) * 5f).dp.toPx(), Offset(rnd(i, 4) * w, y))
                }
            }
            "confetti" -> for (i in 0 until 50) {
                val x = rnd(i, 1) * w + 10f * sin(t * 2.0 * PI + i).toFloat()
                val y = ((rnd(i, 2) + t * (0.5f + rnd(i, 3) * 0.6f)) % 1f) * h
                val col = listOf(Color(0xFFFF5D8F), Color(0xFF5DE08A), Color(0xFF7CC4FF), Color(0xFFFFD166))[i % 4]
                drawRect(col.copy(alpha = if (dark) 0.8f else 0.6f), Offset(x, y), Size(4.dp.toPx(), 7.dp.toPx()))
            }
            "sakura" -> for (i in 0 until 26) {
                val x = ((rnd(i, 1) + 0.05f * sin(t * 2.0 * PI + i).toFloat()) % 1f) * w
                val y = ((rnd(i, 2) + t * (0.25f + rnd(i, 3) * 0.3f)) % 1f) * h
                drawCircle(Color(0xFFFFB7D5).copy(alpha = if (dark) 0.7f else 0.55f), (2f + rnd(i, 4) * 2.5f).dp.toPx(), Offset(x, y))
            }
            "matrix" -> for (i in 0 until 18) {
                val x = (i + 0.5f) / 18f * w
                val y = ((rnd(i, 2) + t * (1f + rnd(i, 3))) % 1f) * h
                for (j in 0 until 6) drawCircle(Color(0xFF3DDC97).copy(alpha = (1f - j / 6f) * 0.5f), 2.dp.toPx(), Offset(x, y - j * 12.dp.toPx()))
            }
        }
    }
}

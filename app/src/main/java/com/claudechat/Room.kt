package com.claudechat

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

/** What the mascot does in its room: where it walks to, how long it stays. */
private class Act(val name: String, val x: Float, val y: Float, val dur: Long)

private val ACTS = listOf(
    Act("read", 7f, 14f, 6000), Act("paint", 11f, 19f, 6000), Act("exercise", 15f, 25f, 6000), Act("eat", 19f, 19f, 5000),
    Act("stars", 26f, 12f, 5000), Act("pc", 30f, 19f, 7000), Act("toilet", 37f, 22f, 4000), Act("sleep", 6f, 21f, 9000),
    Act("tv", 4f, 23f, 6000), Act("guitar", 16f, 14f, 6000), Act("cook", 15f, 16f, 5000), Act("dance", 20f, 25f, 6000),
)
private const val WALK = 1500L
private const val COLS = 40f
private const val ROWS = 30f

/** Two blues: light (default) and dark. A tap on the room switches them. */
private class Pal(val wall: Color, val floor: Color, val base: Color, val rug: Color, val rug2: Color, val wood: Color,
                  val sky: Color, val star: Color, val bed: Color, val screen: Color, val food: Color, val book: Color)
private val LIGHT = Pal(Color(0xFFBFE3FA), Color(0xFF8CC4EA), Color(0xFF6AA8D6), Color(0xFF3F7FC0), Color(0xFF2C5E93), Color(0xFF5B8DB8),
    Color(0xFFE9F7FF), Color(0xFFFFFFFF), Color(0xFF2F6FB0), Color(0xFF7CF0FF), Color(0xFFFFE08A), Color(0xFF1F4E82))
private val DARK = Pal(Color(0xFF1C2E4D), Color(0xFF14233D), Color(0xFF0E1A2E), Color(0xFF2B4F80), Color(0xFF1E3A60), Color(0xFF2E4A6E),
    Color(0xFF0B1528), Color(0xFFBFD8FF), Color(0xFF3A6AA0), Color(0xFF3CC8E0), Color(0xFFD6B25A), Color(0xFF4A78B0))

/** The act at time [t] (ms), the mascot's cell position, and how far into the act it is. */
private fun place(t: Long): Triple<Act, Pair<Float, Float>, Long> {
    val total = ACTS.sumOf { WALK + it.dur }
    var ph = t % total
    for (i in ACTS.indices) {
        val a = ACTS[i]; val from = ACTS[(i + ACTS.size - 1) % ACTS.size]
        if (ph < WALK) { val f = ph.toFloat() / WALK; return Triple(a, (from.x + (a.x - from.x) * f) to (from.y + (a.y - from.y) * f), 0L) }
        ph -= WALK
        if (ph < a.dur) return Triple(a, a.x to a.y, ph)
        ph -= a.dur
    }
    return Triple(ACTS[0], ACTS[0].x to ACTS[0].y, 0L)
}

private fun DrawScope.px(c: Float, x: Float, y: Float, w: Float, h: Float, col: Color) =
    drawRect(col, Offset(x * c, y * c), Size(w * c, h * c))

/** A small pixel room with a mascot that walks between things and does what they are for. */
@Composable
fun MascotRoom() {
    val ctx = LocalContext.current
    var dark by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(0L) }
    val start = remember { System.currentTimeMillis() }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis() - start; delay(60) } }
    val p = if (dark) DARK else LIGHT
    val (act, pos, _) = place(now)
    // decorations and the code fragments: one fragment each time a new activity starts (not on the first one)
    val rug by Prefs.roomRug.flow.collectAsState(); val art by Prefs.roomArt.flow.collectAsState(); val plant by Prefs.roomPlant.flow.collectAsState()
    val fragSeen = remember { BooleanArray(1) }
    LaunchedEffect(act.name) { if (fragSeen[0]) Prefs.roomFrag.value = ((Prefs.roomFrag.value.toIntOrNull() ?: 0) + 1).toString(); fragSeen[0] = true }
    Canvas(Modifier.fillMaxWidth().aspectRatio(COLS / ROWS).clickable { dark = !dark }) {
        val c = size.width / COLS
        // walls, floor, baseboard
        px(c, 0f, 0f, COLS, 20f, p.wall); px(c, 0f, 20f, COLS, ROWS - 20f, p.floor); px(c, 0f, 19f, COLS, 1f, p.base)
        // window with twinkling stars
        px(c, 22f, 3f, 8f, 8f, p.wood); px(c, 23f, 4f, 6f, 6f, p.sky)
        for (i in 0..2) if ((now / 450 + i) % 3 != 0L) px(c, 24f + i * 2f, 5f + (i % 2), 1f, 1f, p.star)
        // bookshelf, bed, plant, toilet
        px(c, 1f, 4f, 5f, 9f, p.wood); px(c, 2f, 5f, 1f, 2f, p.book); px(c, 4f, 6f, 1f, 2f, p.rug); px(c, 2f, 9f, 3f, 2f, p.book)
        px(c, 2f, 17f, 9f, 4f, p.wood); px(c, 2f, 17f, 9f, 2f, p.bed); px(c, 2f, 17f, 2f, 1f, p.star)
        when (plant) {
            "cactus" -> { px(c, 37f, 13f, 2f, 6f, p.screen); px(c, 35f, 15f, 2f, 2f, p.screen); px(c, 39f, 16f, 2f, 2f, p.screen); px(c, 36f, 19f, 4f, 2f, p.wood) }
            "tree" -> { px(c, 37f, 6f, 2f, 13f, p.wood); px(c, 34f, 3f, 8f, 5f, p.screen); px(c, 36f, 2f, 4f, 2f, p.screen); px(c, 36f, 19f, 4f, 2f, p.wood) }
            "bonsai" -> { px(c, 37f, 12f, 2f, 3f, p.rug); px(c, 36f, 15f, 4f, 2f, p.wood) }
        }
        px(c, 35f, 16f, 3f, 2f, p.star); px(c, 36f, 19f, 2f, 2f, p.star)
        // desk with the computer, the screen blinks while it works
        px(c, 26f, 16f, 10f, 1f, p.wood); px(c, 26f, 17f, 1f, 3f, p.wood); px(c, 35f, 17f, 1f, 3f, p.wood)
        px(c, 30f, 11f, 5f, 5f, p.base); px(c, 31f, 12f, 3f, 3f, if ((now / 300) % 2 == 0L) p.screen else p.sky)
        // table with food, easel with a picture growing on it, rug and mat
        px(c, 14f, 18f, 7f, 1f, p.wood); px(c, 14f, 19f, 1f, 2f, p.wood); px(c, 20f, 19f, 1f, 2f, p.wood); px(c, 16f, 17f, 3f, 1f, p.food)
        px(c, 6f, 11f, 1f, 8f, p.wood); px(c, 10f, 11f, 1f, 8f, p.wood); px(c, 6f, 11f, 5f, 4f, p.star)
        val painted = ((now / 500) % 10).toInt()
        for (i in 0 until painted) px(c, 7f + i % 4, 12f + i / 4, 1f, 1f, if (i % 2 == 0) p.food else p.screen)
        // the rug: plain, checker, stripes or stars
        for (x in 12 until 28) for (y in 22 until 27) {
            val alt = when (rug) { "checker" -> (x + y) % 2 == 0; "stripes" -> x % 2 == 0; "star" -> x % 4 == 2 && y % 2 == 0; else -> false }
            px(c, x.toFloat(), y.toFloat(), 1f, 1f, if (alt) p.rug2 else p.rug)
        }
        if (rug == "plain") px(c, 14f, 23f, 12f, 3f, p.rug2)
        // the wall picture
        when (art) {
            "poster" -> { px(c, 31f, 2f, 5f, 6f, p.wood); px(c, 32f, 3f, 3f, 4f, p.sky); px(c, 33f, 4f, 1f, 2f, p.food) }
            "painting" -> { px(c, 31f, 2f, 6f, 5f, p.wood); px(c, 32f, 3f, 4f, 3f, p.sky); px(c, 32f, 5f, 2f, 1f, p.bed); px(c, 34f, 4f, 1f, 1f, p.food) }
            "clock" -> { px(c, 32f, 2f, 4f, 4f, p.base); px(c, 33f, 3f, 2f, 2f, p.star); px(c, 34f, 3f, 1f, 1f, p.screen) }
        }
        // sofa and TV, guitar on the wall, kitchen counter with stove
        px(c, 1f, 24f, 8f, 3f, p.wood); px(c, 1f, 22f, 8f, 2f, p.bed)
        px(c, 13f, 2f, 6f, 4f, p.base); px(c, 14f, 3f, 4f, 2f, if ((now / 700) % 2 == 0L) p.screen else p.sky); px(c, 15f, 6f, 2f, 1f, p.wood)
        px(c, 19f, 9f, 1f, 5f, p.wood); px(c, 18f, 13f, 3f, 2f, p.rug); px(c, 18f, 12f, 3f, 1f, p.rug2)
        px(c, 12f, 13f, 6f, 3f, p.wood); px(c, 13f, 12f, 2f, 1f, p.base); px(c, 15f, 12f, 2f, 1f, p.food)
        // the disco lights for the dance
        if (act.name == "dance") for (i in 0 until 8) if ((now / 250 + i) % 3 == 0L) px(c, 2f + i * 4.5f, 0f, 1f, 1f, p.food)

        // the mascot: pose by what it is doing; exercise jumps
        val ids = when (act.name) {
            "sleep" -> listOf(Mascots.r(R.drawable.ic_mascot_sleep18))
            "pc" -> Mascots.pc((now / 500) % 2 == 1L)
            "eat", "dance" -> listOf(Mascots.r(R.drawable.ic_mascot_happy18))
            else -> listOf(Mascots.r(R.drawable.ic_mascot18))
        }
        val lift = if (act.name == "exercise" || act.name == "dance") kotlin.math.abs(kotlin.math.sin(now / 300.0)).toFloat() * 3f else 0f
        val l = (pos.first - 3f) * c; val t = (pos.second - 6f - lift) * c
        drawIntoCanvas { cv ->
            ids.forEach { id ->
                val d: Drawable = androidx.core.content.ContextCompat.getDrawable(ctx, id)!!
                d.setBounds(l.toInt(), t.toInt(), (l + 6f * c).toInt(), (t + 6f * c * 0.9f).toInt())
                d.draw(cv.nativeCanvas)
            }
        }
    }
}

/** The room's shop: each slot has its own items; a bought item stays owned. Code fragments pay for them. */
internal val ROOM_SHOP = mapOf(
    "rug" to listOf("plain" to 0, "checker" to 5, "stripes" to 8, "star" to 10),
    "art" to listOf("none" to 0, "poster" to 6, "painting" to 9, "clock" to 12),
    "plant" to listOf("bonsai" to 0, "none" to 0, "cactus" to 5, "tree" to 14),
)

@Composable
fun RoomShop() {
    val frag by Prefs.roomFrag.flow.collectAsState()
    val owned by Prefs.roomOwned.flow.collectAsState()
    val rug by Prefs.roomRug.flow.collectAsState(); val art by Prefs.roomArt.flow.collectAsState(); val plant by Prefs.roomPlant.flow.collectAsState()
    val chosen = mapOf("rug" to rug, "art" to art, "plant" to plant)
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("◆ ${frag.toIntOrNull() ?: 0}", color = Color.White, fontSize = 14.sp)
        ROOM_SHOP.forEach { (slot, items) ->
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEach { (name, price) ->
                    val id = "$slot:$name"
                    val has = owned.split(",").contains(id) || price == 0
                    val on = chosen[slot] == name
                    Column(Modifier.width(78.dp).clip(RoundedCornerShape(12.dp))
                        .border(if (on) 2.dp else 1.dp, if (on) Color(0xFF7CC4FF) else Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                        .clickable {
                            val fragNow = frag.toIntOrNull() ?: 0
                            if (!has && fragNow >= price) {
                                Prefs.roomFrag.value = (fragNow - price).toString()
                                Prefs.roomOwned.value = (owned.split(",") + id).filter { it.isNotEmpty() }.joinToString(",")
                            }
                            if (has || fragNow >= price) when (slot) { "rug" -> Prefs.roomRug.value = name; "art" -> Prefs.roomArt.value = name; else -> Prefs.roomPlant.value = name }
                        }
                        .padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(name, color = Color.White, fontSize = 12.sp)
                        Text(if (has) (if (on) "on" else "owned") else "◆ $price", color = Color(0xFFB0B0B0), fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

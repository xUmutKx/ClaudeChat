package com.claudechat

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Small helper for the newer screens: English by default, Turkish when the app language is Turkish. */
fun tr(en: String, trText: String) = if (Prefs.lang.value == "tr") trText else en

// ---------------------------------------------------------------- sub-agents

/** Strip above the composer: every sub-agent (Task/Agent call) of the running turn, with what it is doing right now. */
@Composable
fun AgentsBar() {
    val agents by Engine.agents.collectAsState()
    if (agents.isEmpty()) return
    var open by rememberSaveable { mutableStateOf(false) }
    val running = agents.count { !it.done }
    val cs = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), shape = RoundedCornerShape(16.dp), color = cs.surfaceContainer) {
        Column {
            Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (running > 0) PulseDot(Color(0xFF2196F3)) else Icon(Icons.Filled.CheckCircle, null, Modifier.size(14.dp), tint = Color(0xFF4CAF50))
                Spacer(Modifier.width(10.dp))
                Text(
                    if (running > 0) tr("$running of ${agents.size} agents working", "${agents.size} ajandan $running tanesi çalışıyor") else tr("${agents.size} agents finished", "${agents.size} ajan bitti"),
                    Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Medium
                )
                Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = cs.onSurfaceVariant)
            }
            if (open) Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                agents.forEach { a ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 5.dp)) { if (a.done) Icon(Icons.Filled.Check, null, Modifier.size(14.dp), tint = Color(0xFF4CAF50)) else PulseDot(Color(0xFF2196F3)) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.title, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val line = listOf(a.kind, if (a.tools > 0) tr("${a.tools} tools", "${a.tools} araç") else "", a.activity).filter { it.isNotBlank() }.joinToString(" · ")
                            Text(line, fontSize = 11.5.sp, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PulseDot(c: Color) {
    val a by rememberInfiniteTransition(label = "pd").animateFloat(0.3f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a")
    Box(Modifier.size(10.dp).alpha(a).clip(CircleShape).background(c))
}

// ---------------------------------------------------------------- customize (tap the header mascot)

val CHAT_BGS = listOf("none", "stars", "galaxy", "aurora", "waves")

/** Chat-screen-only background; drawn behind the messages, never on the other screens. */
@Composable
fun ChatBackground(modifier: Modifier = Modifier, style: String? = null) {
    val bg = style ?: Prefs.chatBg.flow.collectAsState().value
    val dark = MaterialTheme.colorScheme.background.let { (it.red + it.green + it.blue) / 3f < 0.5f }
    when (bg) {
        "galaxy" -> {
            // a slowly turning spiral of stars with a soft nebula glow behind it
            val clock = androidx.compose.animation.core.rememberInfiniteTransition(label = "galaxy")
            val tm by clock.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(60000, easing = androidx.compose.animation.core.LinearEasing)), label = "galaxyT")
            Canvas(modifier) {
                val c = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.42f)
                val R = size.width.coerceAtLeast(size.height) * 0.9f
                drawCircle(Brush.radialGradient(listOf(Color(if (dark) 0x553D2A8A else 0x33A88BFF), Color.Transparent), c, R * 0.6f), R * 0.6f, c)
                for (i in 0 until 260) {
                    val arm = i % 2
                    val t = (i / 2f) / 130f                     // 0 at the centre, 1 at the rim
                    val ang = arm * Math.PI + t * 4.2 * Math.PI + tm * 2 * Math.PI
                    val rad = t * R * 0.5f * (0.85f + 0.3f * ((i * 37) % 100) / 100f)
                    val x = c.x + (rad * Math.cos(ang)).toFloat(); val y = c.y + (rad * 0.55f * Math.sin(ang)).toFloat()
                    val tw = 0.5f + 0.5f * kotlin.math.sin(2.0 * Math.PI * (tm * (1 + i % 3) + (i % 7) / 7f)).toFloat()
                    val col = if (i % 4 == 0) Color(0xFFFFE8A3) else if (i % 3 == 0) Color(0xFF9EC9FF) else Color(0xFFD9C8FF)
                    drawCircle(col.copy(alpha = 0.25f + 0.7f * tw), (0.8f + 1.4f * ((i * 13) % 10) / 10f).dp.toPx(), androidx.compose.ui.geometry.Offset(x, y))
                }
                drawCircle(Color(0xFFFFF4D6).copy(alpha = 0.9f), 3.dp.toPx(), c)
            }
        }
        "aurora" -> {
            // soft bands of light that slide and bend
            val clock = androidx.compose.animation.core.rememberInfiniteTransition(label = "aurora")
            val tm by clock.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(14000, easing = androidx.compose.animation.core.LinearEasing)), label = "auroraT")
            Canvas(modifier) {
                val cols = listOf(Color(0xFF3DDC97), Color(0xFF5B8CFF), Color(0xFFB36BFF))
                for (b in 0 until 3) {
                    val path = androidx.compose.ui.graphics.Path()
                    val base = size.height * (0.18f + 0.22f * b)
                    path.moveTo(0f, base)
                    for (x in 0..40) {
                        val fx = x / 40f
                        val y = base + size.height * 0.07f * kotlin.math.sin(2 * Math.PI * (fx + tm + b * 0.3f)).toFloat() + size.height * 0.03f * kotlin.math.sin(2 * Math.PI * (fx * 2 - tm)).toFloat()
                        path.lineTo(fx * size.width, y)
                    }
                    path.lineTo(size.width, 0f); path.lineTo(0f, 0f); path.close()
                    drawPath(path, Brush.verticalGradient(listOf(cols[b].copy(alpha = if (dark) 0.35f else 0.25f), Color.Transparent), 0f, base + size.height * 0.1f))
                }
            }
        }
        "waves" -> {
            // three rolling lines of water
            val clock = androidx.compose.animation.core.rememberInfiniteTransition(label = "waves")
            val tm by clock.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(8000, easing = androidx.compose.animation.core.LinearEasing)), label = "wavesT")
            Canvas(modifier) {
                val cols = listOf(Color(0xFF4FC3F7), Color(0xFF29B6F6), Color(0xFF0288D1))
                for (b in 0 until 3) {
                    val path = androidx.compose.ui.graphics.Path()
                    val base = size.height * (0.55f + 0.14f * b)
                    path.moveTo(0f, size.height)
                    for (x in 0..60) {
                        val fx = x / 60f
                        val y = base + size.height * 0.035f * kotlin.math.sin(2 * Math.PI * (fx * (2 + b) + tm * (1 + b * 0.5f) + b * 0.2f)).toFloat()
                        path.lineTo(fx * size.width, y)
                    }
                    path.lineTo(size.width, size.height); path.close()
                    drawPath(path, cols[b].copy(alpha = if (dark) 0.16f else 0.12f))
                }
            }
        }
        "stars" -> {
            // sharp four-pointed stars, yellow and white, each twinkling at its own pace
            val clock = androidx.compose.animation.core.rememberInfiniteTransition(label = "stars")
            val tm by clock.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(9000, easing = androidx.compose.animation.core.LinearEasing)), label = "twinkle")
            Canvas(modifier) {
                for (i in 0 until 40) {
                    val fx = ((i * 7919) % 1000) / 1000f; val fy = ((i * 104729) % 1000) / 1000f
                    val phase = ((i * 37) % 100) / 100f; val speed = 1 + i % 3
                    val tw = 0.5f + 0.5f * kotlin.math.sin(2.0 * Math.PI * (tm * speed + phase).toDouble()).toFloat()
                    val col = if (i % 3 != 0) Color(if (dark) 0xFFFFD54F else 0xFFE0A100) else (if (dark) Color.White else Color(0xFFB8C4D0))
                    val r = (2.5f + (i % 4) * 1.6f).dp.toPx() * (0.75f + 0.25f * tw)
                    val cx = fx * size.width; val cy = fy * size.height
                    val star = androidx.compose.ui.graphics.Path().apply {
                        for (k in 0 until 8) {
                            val ang = Math.PI / 4 * k - Math.PI / 2
                            val rad = if (k % 2 == 0) r else r * 0.28f
                            val x = cx + (rad * Math.cos(ang)).toFloat(); val y = cy + (rad * Math.sin(ang)).toFloat()
                            if (k == 0) moveTo(x, y) else lineTo(x, y)
                        }
                        close()
                    }
                    drawPath(star, col.copy(alpha = 0.15f + 0.75f * tw))
                }
            }
        }
        else -> {}
    }
}

/** Opens when the header mascot is tapped: outfit, dance style, pill colour, chat background. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CustomizeSheet(onDismiss: () -> Unit) {
    val dance by Prefs.danceMode.flow.collectAsState()
    val pill by Prefs.pillColor.flow.collectAsState()
    val bg by Prefs.chatBg.flow.collectAsState()
    val bubble by Prefs.bubbleStyle.flow.collectAsState()
    val skin by Prefs.mascotSkin.flow.collectAsState()
    val pose by Prefs.mascotPose.flow.collectAsState()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr("Customize", "Özelleştir"), fontFamily = FontFamily.Serif, fontSize = 24.sp)
            Loadout()
            // the tiles are pictures only, so the picked motion's name is written once here
            Text(tr(Poses.label(pose).first, Poses.label(pose).second), fontSize = 13.sp)
            Chips(listOf("steps" to tr("Frame by frame", "Kare kare"), "smooth" to tr("Smooth", "Akıcı")), dance) { Prefs.danceMode.value = it }
            Text(if (dance == "steps") tr("Each motion jumps between 8 frames.", "Her hareket 8 kare halinde oynar.") else tr("Each motion glides smoothly.", "Her hareket akıcı süzülür."), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // every state side by side, so the blue "shell is running" look can be checked without waiting for one
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                StatePreview(tr("Idle", "Boşta")) { Mascot(false, Modifier.size(54.dp, 39.dp), idle = true) }
                StatePreview(tr("Working", "Çalışıyor")) { Mascot(true, Modifier.size(54.dp, 39.dp)) }
                StatePreview(tr("Shell", "Komut")) { Mascot(true, Modifier.size(54.dp, 39.dp), computer = true) }
                StatePreview(tr("Sleep", "Uyku")) { Mascot(false, Modifier.size(54.dp, 39.dp), sleeping = true) }
            }
            CharacterPicker()
            Label(tr("Scene", "Ortam"))
            ScenePicker()
            run {
                val dimStr by Prefs.sceneDim.flow.collectAsState()
                var dim by remember(dimStr) { mutableFloatStateOf((dimStr.toIntOrNull() ?: 35).toFloat()) }
                Text(tr("Darken the scene: ${dim.toInt()}%", "Ortamı karart: %${dim.toInt()}"), fontSize = 13.sp)
                Slider(dim, { dim = it }, valueRange = 0f..90f, onValueChangeFinished = { Prefs.sceneDim.value = dim.toInt().toString() })
            }
            Label(tr("Body colour", "Gövde rengi"))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Outfit.SKINS.forEach { k ->
                    Box(Modifier.size(34.dp).clip(CircleShape).background(Color(Outfit.SKIN_SWATCH[k] ?: 0xFFD97757))
                        .then(if (skin == k) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                        .clickable { Mascots.setSkin(Mascots.current(), k) })
                }
            }
            // previews only, no captions: each tile is the thing itself
            val glow by Prefs.pillGlow.flow.collectAsState()
            Label(tr("Glow behind the mascot", "Maskotun arkasındaki ışık"))
            PreviewTiles(listOf("none" to tr("None", "Yok"), "cyan" to "Cyan", "pink" to tr("Pink", "Pembe"), "gold" to tr("Gold", "Altın"), "green" to tr("Green", "Yeşil")), glow, { Prefs.pillGlow.value = it }, 46, 46) { k ->
                Box(Modifier.size(26.dp).clip(CircleShape).background(if (glowArgb(k) == 0) Color(0xFF111114) else Color(glowArgb(k))))
            }
            // one colour per state for the pill and the header; "rainbow" cycles through the hues
            val buildC by Prefs.buildColor.flow.collectAsState(); val bashC by Prefs.bashColor.flow.collectAsState()
            val thinkC by Prefs.thinkColor.flow.collectAsState(); val doneC by Prefs.doneColor.flow.collectAsState()
            // collapsed by default: the four rows are only shown when opened
            var colourOpen by rememberSaveable { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().clickable { colourOpen = !colourOpen }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Colours by state", "Duruma göre renkler"), Modifier.weight(1f), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(if (colourOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null)
            }
            if (colourOpen) listOf(
                Triple(tr("Build", "Derleme"), buildC, Prefs.buildColor), Triple("Bash", bashC, Prefs.bashColor),
                Triple(tr("Thinking", "Düşünme"), thinkC, Prefs.thinkColor), Triple(tr("Done", "Bitti"), doneC, Prefs.doneColor),
            ).forEach { (name, cur, pref) ->
                Label(name)
                PreviewTiles(StateColors.CHOICES.map { it.first to tr(it.second, it.third) }, cur, { pref.value = it }, 34, 34) { k ->
                    Box(Modifier.size(20.dp).clip(CircleShape).background(stateBrush(k)))
                }
            }
            PreviewTiles(listOf("black" to tr("Black", "Siyah"), "white" to tr("White", "Beyaz")), pill, { Prefs.pillColor.value = it }) { k ->
                Box(Modifier.size(54.dp, 20.dp).clip(RoundedCornerShape(10.dp)).background(if (k == "white") Color(0xFFF2F2F5) else Color(0xFF111114)), contentAlignment = Alignment.Center) {
                    Mascot(false, Modifier.size(24.dp, 16.dp), idle = true)
                }
            }
            PreviewTiles(listOf("none" to tr("None", "Yok"), "stars" to tr("Stars", "Yıldızlar"), "galaxy" to tr("Galaxy", "Galaksi"), "aurora" to tr("Aurora", "Kuzey ışığı"), "waves" to tr("Waves", "Dalgalar")), bg, { Prefs.chatBg.value = it }, 46, 46) { k ->
                Box(Modifier.fillMaxSize()) {
                    ChatBackground(Modifier.fillMaxSize(), style = k)
                    Box(Modifier.padding(start = 26.dp, top = 12.dp).size(24.dp, 7.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .35f)))
                    Box(Modifier.padding(start = 10.dp, top = 24.dp).size(24.dp, 9.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.primaryContainer))
                }
            }
            PreviewTiles(listOf("soft" to tr("Soft", "Yumuşak"), "round" to tr("Round", "Yuvarlak"), "square" to tr("Square", "Köşeli"), "outline" to tr("Outline", "Çerçeve")), bubble, { Prefs.bubbleStyle.value = it }) { k ->
                val shape = RoundedCornerShape(when (k) { "round" -> 16.dp; "square" -> 3.dp; else -> 10.dp })
                Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(30.dp, 6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .4f)))
                    Box(Modifier.align(Alignment.End).size(40.dp, 14.dp).clip(shape)
                        .background(when (k) { "round" -> MaterialTheme.colorScheme.primaryContainer; "outline" -> Color.Transparent; else -> MaterialTheme.colorScheme.surfaceContainerHigh })
                        .then(if (k == "outline") Modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape) else Modifier))
                }
            }
        }
    }
}

/** TF2-style loadout: the big mascot in the middle, slots on its left and right. Tap a slot and its items show in the row below. */
@Composable
private fun Loadout() {
    var slot by rememberSaveable { mutableStateOf("hat") }
    val pose by Prefs.mascotPose.flow.collectAsState()
    val hat by Prefs.pillOutfit.flow.collectAsState(); val face by Prefs.wearFace.flow.collectAsState()
    val eyes by Prefs.eyeColor.flow.collectAsState(); val body by Prefs.wearBody.flow.collectAsState()
    val legs by Prefs.wearLegs.flow.collectAsState(); val feet by Prefs.wearFeet.flow.collectAsState()
    val cur = mapOf("hat" to hat, "face" to face, "eyes" to eyes, "body" to body, "legs" to legs, "feet" to feet, "anim" to pose)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { listOf("hat", "face", "eyes").forEach { s -> SlotButton(s, cur.getValue(s), slot) { slot = s } } }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { PosedMascot(pose, Modifier.size(170.dp, 140.dp)) }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { listOf("body", "legs", "feet", "anim").forEach { s -> SlotButton(s, cur.getValue(s), slot) { slot = s } } }
    }
    Label(slotName(slot))
    PreviewTiles(slotItems(slot).map { it to it }, cur.getValue(slot), { pick(slot, it) }, 56, 60) { k -> SlotTile(slot, k) }
    // says what the picked item is, right under the row (the tiles are pictures only)
    Text(itemLabel(slot, cur.getValue(slot)), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SlotButton(slot: String, current: String, selected: String, onClick: () -> Unit) {
    Column(Modifier.size(56.dp, 60.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
        .then(if (slot == selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)) else Modifier)
        .clickable(onClick = onClick)
        .semantics { contentDescription = slotName(slot) }, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(slotShort(slot), fontSize = 9.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) { SlotTile(slot, current) }
    }
}

/** A short name for the box, shown on top of it. */
private fun slotShort(slot: String) = when (slot) {
    "hat" -> tr("Hat", "Şapka"); "face" -> tr("Face", "Yüz"); "eyes" -> tr("Eyes", "Göz"); "body" -> tr("Body", "Gövde")
    "legs" -> tr("Pants", "Pantolon"); "anim" -> tr("Idle", "Boşta"); else -> tr("Feet", "Ayak")
}

/** The hat slot previews on the bare mascot; the other slots use the same tile as before. */
@Composable
private fun SlotTile(slot: String, k: String) {
    // the item itself, no mascot around it; the idle animation keeps its mascot preview
    if (slot == "anim") { PosedMascot(k, Modifier.fillMaxSize().padding(6.dp)); return }
    val ch = Mascots.current()
    val res = when (slot) { "hat" -> Outfit.hat(k); "face" -> Wear.faceRes(k, ch); "eyes" -> Wear.eyeRes(k, ch); else -> Wear.res(k, ch) }
    when {
        k == "none" -> Text("—", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center))
        res != 0 -> Image(painterResource(res), null, Modifier.fillMaxSize().padding(4.dp))
        else -> Text(k.take(6), fontSize = 9.sp, modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center))
    }
}

private fun slotItems(slot: String): List<String> = when (slot) {
    "anim" -> Poses.ALL; "hat" -> Outfit.ALL; "face" -> Wear.FACE; "eyes" -> Wear.EYES; "body" -> Wear.BODY; "legs" -> Wear.LEGS; else -> Wear.FEET
}

private fun slotName(slot: String) = when (slot) {
    "hat" -> tr("Hat", "Şapka"); "face" -> tr("Face", "Yüz"); "eyes" -> tr("Eyes", "Gözler")
    "body" -> tr("Body", "Gövde"); "legs" -> tr("Trousers", "Pantolon"); "anim" -> tr("Idle animation", "Boşta animasyonu")
    else -> tr("Shoes and socks", "Ayakkabı ve çorap")
}

private fun itemLabel(slot: String, k: String): String = when {
    slot == "anim" -> Poses.label(k).let { tr(it.first, it.second) }
    k == "none" -> tr("None", "Yok")
    else -> k.replaceFirstChar { it.uppercase() }
}

private fun pick(slot: String, k: String) {
    when (slot) {
        "anim" -> Prefs.mascotPose.value = k
        "hat" -> Prefs.pillOutfit.value = k; "face" -> Prefs.wearFace.value = k; "eyes" -> Prefs.eyeColor.value = k
        "body" -> Prefs.wearBody.value = k; "legs" -> Prefs.wearLegs.value = k; else -> Prefs.wearFeet.value = k
    }
    Wear.saveCurrent()
}

/** One tile: the mascot wearing just this piece. */
@Composable
private fun WearTile(style: String) {
    val skin by Prefs.mascotSkin.flow.collectAsState()
    val tint = remember(skin) { Outfit.skinMatrix(skin)?.let { androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(it)) } }
    val ch = Mascots.current()
    Box(Modifier.fillMaxSize().padding(5.dp)) {
        Image(painterResource(Mascots.preview(ch)), null, Modifier.fillMaxSize(), colorFilter = tint)
        Wear.res(style, ch).let { if (it != 0) Image(painterResource(it), null, Modifier.fillMaxSize()) }
        Wear.layers("none", "none", "none", style.takeIf { it == "glasses" || it == "mustache" } ?: "none", style.takeIf { it in Wear.EYES } ?: "dark", ch).forEach { Image(painterResource(it), null, Modifier.fillMaxSize()) }
    }
}

/** A colour chip for the Customize picker: one colour, or a rainbow sweep for "rainbow". */
private fun stateBrush(k: String): androidx.compose.ui.graphics.Brush =
    if (k == "rainbow") androidx.compose.ui.graphics.Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red))
    else androidx.compose.ui.graphics.SolidColor(Color(StateColors.tileColor(k)))

/** Tiles that show the option itself (no caption). The name is only read out by screen readers. */
@Composable
private fun PreviewTiles(items: List<Pair<String, String>>, current: String, onPick: (String) -> Unit, tileW: Int = 64, tileH: Int = 52, tile: @Composable (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (k, name) ->
            val on = current == k
            // the tile grows a little while it is pressed: the tap is visible, and small tiles are easier to hit
            val src = remember { MutableInteractionSource() }
            val pressed by src.collectIsPressedAsState()
            val grow by animateFloatAsState(if (pressed) 1.12f else 1f, label = "tilePress")
            Box(Modifier.size(tileW.dp, tileH.dp).graphicsLayer { scaleX = grow; scaleY = grow }.clip(RoundedCornerShape(14.dp))
                .then(if (on) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)) else Modifier)
                .clickable(interactionSource = src, indication = null) { onPick(k) }
                .semantics { contentDescription = name }, contentAlignment = Alignment.Center) { tile(k) }
        }
    }
}

/** The characters, shown as pictures (no names, no explanations), each in its own colour. The first one, "A", follows whichever AI answers. */
@Composable
fun CharacterPicker() {
    val chosen by Prefs.mascotChar.flow.collectAsState()
    Prefs.provider.flow.collectAsState().value; Prefs.mascotSkins.flow.collectAsState().value   // redraw when the AI or a colour changes
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (listOf("auto") + Mascots.IDS).forEach { c ->
            val on = chosen == c
            val who = if (c == "auto") Mascots.current() else c
            val tint = remember(who, Prefs.mascotSkins.value) { Mascots.tint(who)?.let { androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(it)) } }
            Box(Modifier.size(58.dp, 54.dp).clip(RoundedCornerShape(14.dp))
                .background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest)
                .then(if (on) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)) else Modifier)
                .clickable { Mascots.pick(c) }, contentAlignment = Alignment.Center) {
                Image(painterResource(Mascots.preview(who)), null, Modifier.size(44.dp, 40.dp), colorFilter = tint)
                if (c == "auto") Icon(Icons.Filled.AutoAwesome, null, Modifier.align(Alignment.TopEnd).padding(3.dp).size(13.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun StatePreview(label: String, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(70.dp, 58.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) { content() }
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun Label(t: String) = Text(t, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(items: List<Pair<String, String>>, current: String, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { (k, l) -> FilterChip(current == k, { onPick(k) }, { Text(l) }) }
    }
}

// ---------------------------------------------------------------- folder picker (instead of typing a path)

/** Browse the folders inside Ubuntu through the bridge and pick one. */
@Composable
fun FolderPickerDialog(start: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var path by remember { mutableStateOf(start.ifBlank { "/root" }) }
    var parent by remember { mutableStateOf("/") }
    var dirs by remember { mutableStateOf<List<String>?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(path) {
        dirs = null; failed = false
        val r = Engine.ls(path)
        if (r == null) failed = true else { path = r.first; parent = r.second; dirs = r.third }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Choose a folder", "Klasör seç")) },
        text = {
            Column {
                Text(path, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                Spacer(Modifier.height(6.dp))
                when {
                    failed -> Text(tr("Can't reach Claude's bridge. Start it from the main screen first.", "Köprüye ulaşılamıyor. Önce ana ekrandan başlat."))
                    dirs == null -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    else -> LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        if (path != "/") item { FolderRow("..", Icons.AutoMirrored.Filled.ArrowBack) { path = parent } }
                        items(dirs!!) { d -> FolderRow(d, Icons.Filled.Folder) { path = path.trimEnd('/') + "/" + d } }
                        if (dirs!!.isEmpty()) item { Text(tr("No sub-folders", "Alt klasör yok"), Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        },
        confirmButton = { TextButton({ onPick(path) }, enabled = !failed) { Text(tr("Use this folder", "Bu klasörü kullan")) } },
        dismissButton = { TextButton(onDismiss) { Text(tr("Cancel", "İptal")) } },
    )
}

@Composable
private fun FolderRow(name: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------------------------------------------------------------- first-time setup (no Termux yet)

private const val FULL_SETUP =
    "pkg update -y && pkg install -y proot-distro && proot-distro install ubuntu && " +
        "proot-distro login ubuntu -- bash -lc 'apt update && apt install -y curl nodejs npm git && npm install -g @anthropic-ai/claude-code'"

/**
 * Step by step for someone who never installed Termux: get Termux (downloaded in here), one short paste that installs Ubuntu + Claude Code
 * and starts the connection, sign in through the phone's browser (no codes to copy), optionally GitHub, then pick the project folder.
 */
@Composable
fun SetupScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val bridge by Engine.bridge.collectAsState()
    val tick = remember { mutableStateOf(0) }
    var auth by remember { mutableStateOf<Engine.Auth?>(null) }
    var pickFolder by remember { mutableStateOf(false) }
    var folderChosen by rememberSaveable { mutableStateOf(false) }
    var showFull by remember { mutableStateOf(false) }
    var cmd by remember { mutableStateOf(SetupServer.command().orEmpty()) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(2000); tick.value++; if (Termux.installed(ctx)) Engine.checkBridge() } }
    LaunchedEffect(bridge) { while (bridge == 200) { auth = Engine.authStatus(); kotlinx.coroutines.delay(6000) } }
    val installed = remember(tick.value) { Termux.installed(ctx) }
    val granted = remember(tick.value) { Termux.granted(ctx) }
    // the system asks once whether Claude Chat may run commands in Termux; Termux opens right after, whatever the answer
    val perm = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { Termux.openTermux(ctx) }
    val cs = MaterialTheme.colorScheme
    val up = bridge == 200

    Column(Modifier.fillMaxSize().background(cs.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            Text(tr("Set up Claude on this phone", "Claude'u bu telefona kur"), fontFamily = FontFamily.Serif, fontSize = 22.sp)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr("Once, in a few steps. Each step turns green when it is done.", "Bir kez, birkaç adımda. Biten adım yeşile döner."), color = cs.onSurfaceVariant)
            val dl by TermuxInstall.state.collectAsState()
            Step(1, tr("Get Termux (downloaded inside this app)", "Termux'u al (uygulamanın içinden iner)"), installed,
                tr("Termux is open source. Claude Chat downloads it from its GitHub release and opens Android's installer. You may be asked to allow installing from this app once.", "Termux açık kaynaklıdır. Claude Chat onu GitHub sürümünden indirir ve Android'in kurucusunu açar. Bir kez bu uygulamadan kurmaya izin vermen istenebilir.")) {
                when (val d = dl) {
                    is TermuxInstall.State.Idle, is TermuxInstall.State.Failed -> {
                        Button({ TermuxInstall.start(ctx) }, enabled = !installed) { Text(tr("Download Termux", "Termux'u indir")) }
                        if (d is TermuxInstall.State.Failed) Text(d.why, fontSize = 12.sp, color = cs.error)
                    }
                    is TermuxInstall.State.Looking -> Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text(tr("Finding the latest release…", "Son sürüm aranıyor…")) }
                    is TermuxInstall.State.Downloading -> Column {
                        LinearProgressIndicator({ d.percent / 100f }, Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)))
                        Text("${d.percent}%", fontSize = 12.sp, color = cs.onSurfaceVariant)
                    }
                    is TermuxInstall.State.Ready -> Button({ TermuxInstall.install(ctx, d.file) }, enabled = !installed) { Text(tr("Install Termux", "Termux'u kur")) }
                }
            }
            Step(2, tr("One paste sets everything up", "Tek yapıştırma her şeyi kurar"), up,
                tr("Tap the button: it copies one short line and opens Termux. Long-press in Termux, choose Paste, press Enter. It installs Ubuntu and Claude Code, then starts the connection. The first time takes a few minutes; later it only repairs.",
                    "Düğmeye bas: kısa bir satırı kopyalar ve Termux'u açar. Termux'ta basılı tut, Yapıştır'ı seç, Enter'a bas. Ubuntu'yu ve Claude Code'u kurar, sonra bağlantıyı başlatır. İlk seferde birkaç dakika sürer; sonra sadece onarır.")) {
                if (cmd.isNotEmpty()) Cmd(cmd, clip) else Text(tr("Could not prepare the line.", "Satır hazırlanamadı."), color = cs.error, fontSize = 12.sp)
                Button({
                    cmd = SetupServer.command().orEmpty()   // a fresh code each time: the line only works for a few minutes
                    clip.setText(AnnotatedString(cmd))
                    if (installed && !granted) perm.launch(Termux.PERM) else Termux.openTermux(ctx)
                }, enabled = installed && cmd.isNotEmpty()) { Text(tr("Copy and open Termux", "Kopyala ve Termux'u aç")) }
                if (up) Text(tr("Connected.", "Bağlandı."), color = Color(0xFF4CAF50), fontSize = 13.sp)
                else Text(tr("Waiting for the connection… (this screen notices by itself)", "Bağlantı bekleniyor… (bu ekran kendiliğinden fark eder)"), color = cs.onSurfaceVariant, fontSize = 12.sp)
                TextButton({ showFull = !showFull }) { Text(if (showFull) tr("Hide the long command", "Uzun komutu gizle") else tr("Show the long command instead", "Bunun yerine uzun komutu göster")) }
                if (showFull) Cmd(ALL_IN_ONE, clip)
            }
            SignInStep(3, "claude", tr("Sign in to Claude", "Claude'a giriş yap"),
                tr("Your browser opens Claude's sign-in page. Approve there and come back: nothing to copy.", "Tarayıcın Claude'un giriş sayfasını açar. Orada onayla ve geri dön: kopyalanacak bir şey yok."),
                auth?.claudeIn == true, auth?.email.orEmpty(), up, tr("Sign in with the browser", "Tarayıcıyla giriş yap")) { scopeAuth -> auth = scopeAuth }
            SignInStep(4, "gh", tr("GitHub (optional)", "GitHub (isteğe bağlı)"),
                tr("Lets Claude push and pull for you. GitHub shows a short code: it is copied for you, paste it on the page that opens.", "Claude'un senin için push/pull yapmasını sağlar. GitHub kısa bir kod gösterir: senin için kopyalanır, açılan sayfaya yapıştır."),
                auth?.ghIn == true, auth?.ghUser.orEmpty(), up && auth?.ghInstalled != false, tr("Sign in to GitHub", "GitHub'a giriş yap")) { scopeAuth -> auth = scopeAuth }
            Step(5, tr("Pick your project folder", "Proje klasörünü seç"), folderChosen,
                tr("Claude works inside this folder. You can change it later in Settings.", "Claude bu klasörün içinde çalışır. Sonra Ayarlar'dan değiştirebilirsin.")) {
                Text(Prefs.cwd.flow.collectAsState().value, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                OutlinedButton({ pickFolder = true }, enabled = up) { Text(tr("Choose folder", "Klasör seç")) }
                Button(onBack, enabled = up && auth?.claudeIn == true) { Text(tr("Start chatting", "Sohbete başla")) }
            }
            if (up && auth?.claudeIn == true) Text(tr("All set. Say hi!", "Hazır. Merhaba de!"), color = Color(0xFF4CAF50), fontWeight = FontWeight.Medium)
        }
    }
    if (pickFolder) FolderPickerDialog(Prefs.cwd.value, { Prefs.cwd.value = it; folderChosen = true; pickFolder = false }, { pickFolder = false })
}

/** Opens a web page on the phone. */
private fun openUrl(ctx: android.content.Context, url: String) {
    try { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
}

/**
 * A sign-in step: the bridge runs `claude auth login` / `gh auth login`, which open their page through $BROWSER; the link comes here and opens in the
 * phone's browser. Claude's answer returns over localhost by itself; GitHub shows a short code, which is copied for the user.
 */
@Composable
private fun SignInStep(n: Int, what: String, title: String, body: String, signedIn: Boolean, who: String, enabled: Boolean, button: String, onAuth: (Engine.Auth?) -> Unit) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val cs = MaterialTheme.colorScheme
    var run by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<Engine.Login?>(null) }
    var opened by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(run) {
        if (!run) return@LaunchedEffect
        opened = ""; copied = ""; info = Engine.login(what, "start")
        val t0 = System.currentTimeMillis()
        while (System.currentTimeMillis() - t0 < 5 * 60_000L) {
            kotlinx.coroutines.delay(1000)
            val v = Engine.login(what, "state") ?: continue
            info = v
            if (v.code.isNotEmpty() && copied != v.code) { copied = v.code; clip.setText(AnnotatedString(v.code)) }
            if (v.url.isNotEmpty() && opened != v.url) { opened = v.url; openUrl(ctx, v.url) }
            if (v.state == "done" || v.state == "error" || v.state == "cancelled") break
        }
        run = false
        onAuth(Engine.authStatus())
    }
    Step(n, title, signedIn, body) {
        if (signedIn) {
            Text(tr("Signed in", "Giriş yapıldı") + if (who.isNotEmpty()) " · $who" else "", color = Color(0xFF4CAF50), fontSize = 13.sp)
            OutlinedButton({ run = true }, enabled = enabled && !run) { Text(tr("Sign in again", "Yeniden giriş yap")) }
        } else if (!run) {
            Button({ run = true }, enabled = enabled) { Text(button) }
            info?.takeIf { it.state == "error" }?.let { Text(it.out.takeLast(200), fontSize = 12.sp, color = cs.error) }
            if (!enabled) Text(tr("Needs the connection from step 2 first.", "Önce 2. adımdaki bağlantı gerekir."), fontSize = 12.sp, color = cs.onSurfaceVariant)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp))
                Text(if (info?.url.isNullOrEmpty()) tr("Getting the sign-in page…", "Giriş sayfası hazırlanıyor…") else tr("Finish in the browser, then come back.", "Tarayıcıda bitir, sonra geri dön."))
            }
            info?.code?.takeIf { it.isNotEmpty() }?.let { c ->
                Surface(shape = RoundedCornerShape(12.dp), color = cs.surfaceContainerHighest) {
                    Row(Modifier.padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(c, Modifier.weight(1f).padding(vertical = 10.dp), fontFamily = FontFamily.Monospace, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        IconButton({ clip.setText(AnnotatedString(c)) }) { Icon(Icons.Filled.ContentCopy, tr("Copy", "Kopyala")) }
                    }
                }
                Text(tr("Copied. Paste it on the GitHub page.", "Kopyalandı. GitHub sayfasına yapıştır."), fontSize = 12.sp, color = cs.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ info?.url?.takeIf { it.isNotEmpty() }?.let { openUrl(ctx, it) } }, enabled = !info?.url.isNullOrEmpty()) { Text(tr("Open the page again", "Sayfayı yeniden aç")) }
                TextButton({ run = false; scope.launch { Engine.login(what, "cancel") } }) { Text(tr("Cancel", "İptal")) }
            }
        }
    }
}

private const val ALL_IN_ONE = Termux.SETUP_CMD + "; " + FULL_SETUP

private fun android.content.Context.requestTermuxPermission() {
    val a = this as? android.app.Activity ?: return
    a.requestPermissions(arrayOf(Termux.PERM), 77)
}

@Composable
private fun Step(n: Int, title: String, done: Boolean, body: String, content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(18.dp), color = if (done) Color(0xFF4CAF50).copy(alpha = .14f) else cs.surfaceContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(if (done) Color(0xFF4CAF50) else cs.primary), contentAlignment = Alignment.Center) {
                    if (done) Icon(Icons.Filled.Check, null, Modifier.size(16.dp), tint = Color.White) else Text("$n", color = cs.onPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                Text(title, fontWeight = FontWeight.Medium, fontSize = 16.sp)
            }
            Text(body, fontSize = 13.sp, color = cs.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun Cmd(cmd: String, clip: androidx.compose.ui.platform.ClipboardManager) {
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Row(Modifier.padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(cmd, Modifier.weight(1f).padding(vertical = 8.dp), fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            IconButton({ clip.setText(AnnotatedString(cmd)) }) { Icon(Icons.Filled.ContentCopy, tr("Copy", "Kopyala")) }
        }
    }
}

/** Scenes behind the mascot, shown as pictures with the mascot in them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScenePicker() {
    val sel by Prefs.mascotScene.flow.collectAsState()
    val skin by Prefs.mascotSkin.flow.collectAsState()
    val outfit by Prefs.pillOutfit.flow.collectAsState()
    val tint = remember(skin) { Outfit.skinMatrix(skin)?.let { androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(it)) } }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Outfit.SCENES.forEach { k ->
            val on = sel == k
            Box(Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .then(if (on) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)) else Modifier)
                .clickable { Prefs.mascotScene.value = k }, contentAlignment = Alignment.Center) {
                val sc = Outfit.scene(k)
                if (sc != 0) {
                    Image(painterResource(sc), null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = (Prefs.sceneDim.flow.collectAsState().value.toIntOrNull() ?: 35) / 100f)))
                }
                Box(Modifier.size(50.dp, 45.dp)) {
                    Image(painterResource(Mascots.r(R.drawable.ic_mascot18)), null, Modifier.fillMaxSize(), colorFilter = tint)
                    val h = Outfit.hat(outfit); if (h != 0) Image(painterResource(h), null, Modifier.fillMaxSize().hatFit())
                }
            }
        }
    }
}

/** Settings > About: the mascot dances in a glow with twinkling sparkles; author, version, GitHub. */
@Composable
fun AboutCard() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val cs = MaterialTheme.colorScheme
    val ver = remember { try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (e: Exception) { "" } }
    val code = remember { try { androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(ctx.packageManager.getPackageInfo(ctx.packageName, 0)) } catch (e: Exception) { 0L } }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        var taps by remember { mutableIntStateOf(0) }
        var room by remember { mutableStateOf(false) }
        PlayMascot(Modifier.fillMaxWidth().height(280.dp), onTap = { taps++; if (taps >= 10) { taps = 0; room = true } })
        if (room) androidx.compose.ui.window.Dialog({ room = false }, androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(color = Color.Black) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) { MascotRoom(); RoomShop() } }
        }
        Text("Claude Chat", fontFamily = FontFamily.Serif, fontSize = 28.sp)
        Text("by UmutK", fontSize = 16.sp, color = cs.primary, fontWeight = FontWeight.Medium)
        Surface(shape = RoundedCornerShape(16.dp), color = cs.surfaceContainer) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(tr("Version", "Sürüm") to "$ver ($code)", tr("Package", "Paket") to ctx.packageName, tr("Android", "Android") to "${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})",
                    tr("Device", "Cihaz") to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}").forEach { (k, v) ->
                    Row { Text(k, Modifier.weight(1f), color = cs.onSurfaceVariant, fontSize = 13.sp); Text(v, fontSize = 13.sp) }
                }
            }
        }
        OutlinedButton({ ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/xUmutKx")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }, Modifier.fillMaxWidth()) { Text("GitHub · xUmutKx") }
        for ((label, url) in listOf("Phone control adapted from buddy-android (Apache-2.0)" to "https://github.com/ghorbelhamdi/buddy-android")) {
            OutlinedButton({ ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text(label) }
        }
        Text(tr("Claude Code on your phone, through a local bridge in Termux. Nothing is sent anywhere except to Claude itself.", "Telefonda Claude Code, Termux'taki yerel bir köprü üzerinden. Claude'un kendisi dışında hiçbir yere bir şey gönderilmez."), fontSize = 12.sp, color = cs.onSurfaceVariant)
    }
}

/** Easter egg: the mascot dances in a glow; hold it and it follows the finger like a jelly toy (spring physics), let go and it flies, spins and bounces off the walls. */
@Composable
private fun PlayMascot(modifier: Modifier, onTap: () -> Unit = {}) {
    val cs = MaterialTheme.colorScheme
    val dens = androidx.compose.ui.platform.LocalDensity.current
    val mw = with(dens) { 104.dp.toPx() }; val mh = with(dens) { 94.dp.toPx() }
    val t = rememberInfiniteTransition(label = "about")
    val pulse by t.animateFloat(0.55f, 1f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow")
    val spin by t.animateFloat(0f, 1f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "spin")
    var area by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var pos by remember { mutableStateOf(Offset.Zero) }
    var vel by remember { mutableStateOf(Offset.Zero) }
    var ang by remember { mutableStateOf(0f) }      // degrees
    var av by remember { mutableStateOf(0f) }       // degrees / s
    var held by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(area) {
        if (area.width == 0) return@LaunchedEffect
        val center = Offset(area.width / 2f, area.height / 2f)
        if (pos == Offset.Zero) pos = center
        var last = 0L
        while (true) {
            val now = withFrameNanos { it }
            val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceAtMost(0.033f); last = now
            var p = pos; var v = vel
            val acc: Offset
            if (held) {
                val k = 260f; val c = 2f * kotlin.math.sqrt(k) * 0.5f      // slightly under-damped: wobbly
                acc = (target - p) * k - v * c
            } else {
                acc = (center - p) * 2.2f - v * 0.7f                        // a soft pull home, a little drag
            }
            v += acc * dt; p += v * dt
            // walls
            val minX = mw / 2f; val maxX = area.width - mw / 2f; val minY = mh / 2f; val maxY = area.height - mh / 2f
            var a2 = av
            if (p.x < minX) { p = Offset(minX, p.y); if (v.x < 0) { a2 += v.y * 0.05f; v = Offset(-v.x * 0.8f, v.y) } }
            if (p.x > maxX) { p = Offset(maxX, p.y); if (v.x > 0) { a2 -= v.y * 0.05f; v = Offset(-v.x * 0.8f, v.y) } }
            if (p.y < minY) { p = Offset(p.x, minY); if (v.y < 0) { a2 -= v.x * 0.05f; v = Offset(v.x, -v.y * 0.8f) } }
            if (p.y > maxY) { p = Offset(p.x, maxY); if (v.y > 0) { a2 += v.x * 0.05f; v = Offset(v.x, -v.y * 0.8f) } }
            // rotation: leans against acceleration while held, spins freely after a throw, then unwinds to upright
            val err = ang - 360f * kotlin.math.round(ang / 360f)
            val rest = if (held) 70f else 5f
            val damp = if (held) 5f else 0.9f
            a2 += (-err * rest - a2 * damp - (if (held) acc.x * 0.05f else 0f)) * dt
            ang += a2 * dt
            av = a2; pos = p; vel = v
        }
    }
    val glow = cs.primary
    Box(modifier.onSizeChanged { area = it }
        .pointerInput(Unit) { detectTapGestures(onTap = { av += if (vel.x >= 0) 720f else -720f; onTap() }) }
        .pointerInput(Unit) {
            detectDragGesturesAfterLongPress(
                onDragStart = { target = it; held = true },
                onDrag = { ch, _ -> target = ch.position },
                onDragEnd = { held = false; av += vel.x * 0.15f },
                onDragCancel = { held = false },
            )
        }) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val c = pos
            val r = 110.dp.toPx()
            drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = .55f * pulse), glow.copy(alpha = .18f * pulse), Color.Transparent), c, r), r, c)
            for (i in 0 until 12) {
                val an = (i / 12f + spin * (if (i % 2 == 0) 1f else -1f)) * 6.2831855f
                val rr = r * (if (i % 2 == 0) 0.80f else 0.60f)
                val q = Offset(c.x + rr * kotlin.math.cos(an), c.y + rr * kotlin.math.sin(an))
                val tw = (0.5f + 0.5f * kotlin.math.sin((spin * 6.2831855f * 3f) + i * 1.7f)).coerceIn(0f, 1f)
                val sz = (3f + 5f * tw) * density
                val col = (if (i % 3 == 0) Color(0xFFFFE082) else Color.White).copy(alpha = .25f + .75f * tw)
                drawLine(col, Offset(q.x - sz, q.y), Offset(q.x + sz, q.y), density)
                drawLine(col, Offset(q.x, q.y - sz), Offset(q.x, q.y + sz), density)
                drawCircle(col, sz / 3f, q)
            }
        }
        val speed = kotlin.math.sqrt(vel.x * vel.x + vel.y * vel.y)
        val stretch = 1f + (speed / 5000f).coerceAtMost(0.18f)
        Box(Modifier.size(104.dp, 94.dp).graphicsLayer {
            translationX = pos.x - mw / 2f; translationY = pos.y - mh / 2f
            rotationZ = ang; scaleX = stretch; scaleY = 2f - stretch
        }) { Mascot(true, Modifier.fillMaxSize()) }
    }
}

package com.claudechat

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
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

val CHAT_BGS = listOf("none", "dusk", "mint", "rose", "sand", "grid", "stars")

/** Chat-screen-only background; drawn behind the messages, never on the other screens. */
@Composable
fun ChatBackground(modifier: Modifier = Modifier) {
    val bg by Prefs.chatBg.flow.collectAsState()
    val dark = MaterialTheme.colorScheme.background.let { (it.red + it.green + it.blue) / 3f < 0.5f }
    when (bg) {
        "dusk" -> Box(modifier.background(Brush.verticalGradient(if (dark) listOf(Color(0xFF1B1633), Color(0xFF0E0B1C)) else listOf(Color(0xFFE6DFFF), Color(0xFFFFE3EC)))))
        "mint" -> Box(modifier.background(Brush.verticalGradient(if (dark) listOf(Color(0xFF0F2A25), Color(0xFF0A1715)) else listOf(Color(0xFFD9F5EA), Color(0xFFF1FBF6)))))
        "rose" -> Box(modifier.background(Brush.verticalGradient(if (dark) listOf(Color(0xFF2E1520), Color(0xFF170A10)) else listOf(Color(0xFFFFE0E8), Color(0xFFFFF4F1)))))
        "sand" -> Box(modifier.background(Brush.verticalGradient(if (dark) listOf(Color(0xFF2A2218), Color(0xFF16120B)) else listOf(Color(0xFFF3E7D3), Color(0xFFFBF5EA)))))
        "grid" -> Canvas(modifier) {
            val step = 28.dp.toPx(); val c = (if (dark) Color.White else Color.Black).copy(alpha = .05f)
            var x = 0f; while (x < size.width) { drawLine(c, Offset(x, 0f), Offset(x, size.height)); x += step }
            var y = 0f; while (y < size.height) { drawLine(c, Offset(0f, y), Offset(size.width, y)); y += step }
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
    val outfit by Prefs.pillOutfit.flow.collectAsState()
    val dance by Prefs.danceMode.flow.collectAsState()
    val pill by Prefs.pillColor.flow.collectAsState()
    val bg by Prefs.chatBg.flow.collectAsState()
    val bubble by Prefs.bubbleStyle.flow.collectAsState()
    val skin by Prefs.mascotSkin.flow.collectAsState()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr("Customize", "Özelleştir"), fontFamily = FontFamily.Serif, fontSize = 24.sp)
            // every state side by side, so the blue "shell is running" look can be checked without waiting for one
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                StatePreview(tr("Idle", "Boşta")) { Mascot(false, Modifier.size(64.dp, 46.dp), sleeping = true) }
                StatePreview(tr("Working", "Çalışıyor")) { Mascot(true, Modifier.size(64.dp, 46.dp)) }
                StatePreview(tr("Shell", "Komut")) { Mascot(true, Modifier.size(64.dp, 46.dp), computer = true) }
            }
            Label(tr("Outfit", "Kıyafet"))
            OutfitPicker(outfit) { Prefs.pillOutfit.value = it }
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
                        .clickable { Prefs.mascotSkin.value = k })
                }
            }
            Label(tr("Animation", "Animasyon"))
            Chips(listOf("steps" to tr("Frame by frame", "Kare kare"), "smooth" to tr("Smooth", "Akıcı")), dance) { Prefs.danceMode.value = it }
            Label(tr("Pill colour", "Pill rengi"))
            Chips(listOf("black" to tr("Black", "Siyah"), "white" to tr("White", "Beyaz")), pill) { Prefs.pillColor.value = it }
            Label(tr("Chat background (main chat screen only)", "Sohbet arka planı (yalnızca ana sohbet ekranı)"))
            Chips(listOf("none" to tr("None", "Yok"), "dusk" to tr("Dusk", "Alacakaranlık"), "mint" to tr("Mint", "Nane"), "rose" to tr("Rose", "Gül"), "sand" to tr("Sand", "Kum"), "grid" to tr("Grid", "Izgara"), "stars" to tr("Stars", "Yıldızlar")), bg) { Prefs.chatBg.value = it }
            Label(tr("Message bubbles", "Mesaj balonları"))
            Chips(listOf("soft" to tr("Soft", "Yumuşak"), "round" to tr("Round", "Yuvarlak"), "square" to tr("Square", "Köşeli"), "outline" to tr("Outline", "Çerçeve")), bubble) { Prefs.bubbleStyle.value = it }
        }
    }
}

@Composable
private fun StatePreview(label: String, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(78.dp, 62.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) { content() }
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

/** Step-by-step setup for someone who never installed Termux: get Termux, allow Claude Chat, install Ubuntu + Claude, log in. */
@Composable
fun SetupScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val bridge by Engine.bridge.collectAsState()
    val tick = remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(2000); tick.value++; if (Termux.installed(ctx)) Engine.checkBridge() } }
    val installed = remember(tick.value) { Termux.installed(ctx) }
    val granted = remember(tick.value) { Termux.granted(ctx) }
    val cs = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().background(cs.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            Text(tr("Set up Claude on this phone", "Claude'u bu telefona kur"), fontFamily = FontFamily.Serif, fontSize = 22.sp)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr("Three steps, once. Each step turns green when it is done.", "Üç adım, bir kez. Biten adım yeşile döner."), color = cs.onSurfaceVariant)
            val dl by TermuxInstall.state.collectAsState()
            Step(1, tr("Get Termux (downloaded inside this app)", "Termux'u al (uygulamanın içinden iner)"), installed,
                tr("Termux is open source. Claude Chat downloads it from its GitHub release and opens Android's installer. You may be asked to allow installing from this app once.", "Termux açık kaynaklıdır. Claude Chat onu GitHub sürümünden indirir ve Android'in kurucusunu açar. Bir kez bu uygulamadan kurulum iznini isteyebilir.")) {
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
            Step(2, tr("Set everything up with one paste", "Tek yapıştırmayla her şeyi kur"), bridge == 200,
                tr("This one command lets Claude Chat control Termux, installs Ubuntu and installs Claude Code. It takes a few minutes and needs internet. Tap the button, then long-press in Termux and choose Paste.", "Bu tek komut Claude Chat'in Termux'u yönetmesine izin verir, Ubuntu'yu ve Claude Code'u kurar. Birkaç dakika sürer, internet ister. Düğmeye dokun, sonra Termux'ta basılı tutup Yapıştır'ı seç.")) {
                Cmd(ALL_IN_ONE, clip)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ clip.setText(AnnotatedString(ALL_IN_ONE)); Termux.openTermux(ctx) }, enabled = installed) { Text(tr("Copy and open Termux", "Kopyala ve Termux'u aç")) }
                    OutlinedButton({ ctx.requestTermuxPermission() }, enabled = installed && !granted) { Text(tr("Grant permission", "İzin ver")) }
                }
            }
            Step(3, tr("Log in to Claude", "Claude'a giriş yap"), bridge == 200,
                tr("In Termux run the line below, follow the login link it prints, then close it. After this the chat works.", "Termux'ta aşağıdaki satırı çalıştır, çıkan giriş bağlantısını izle, sonra kapat. Bundan sonra sohbet çalışır.")) {
                Cmd("proot-distro login ubuntu -- claude", clip)
                Button({ Engine.connect() }, enabled = installed) { Text(tr("Start Claude", "Claude'u başlat")) }
            }
            if (bridge == 200) Text(tr("All set — go back and say hi.", "Hazır — geri dön ve merhaba de."), color = Color(0xFF4CAF50), fontWeight = FontWeight.Medium)
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

/** Outfit choices drawn on the mascot itself instead of named in text. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OutfitPicker(selected: String, onPick: (String) -> Unit) {
    val skin by Prefs.mascotSkin.flow.collectAsState()
    val tint = remember(skin) { Outfit.skinMatrix(skin)?.let { androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(it)) } }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Outfit.ALL.forEach { k ->
            val on = selected == k
            Box(Modifier.size(64.dp, 58.dp).clip(RoundedCornerShape(14.dp))
                .background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest)
                .then(if (on) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)) else Modifier)
                .clickable { onPick(k) }, contentAlignment = Alignment.Center) {
                Box(Modifier.size(50.dp, 45.dp)) {
                    Image(painterResource(R.drawable.ic_mascot18), null, Modifier.fillMaxSize(), colorFilter = tint)
                    val h = Outfit.hat(k); if (h != 0) Image(painterResource(h), null, Modifier.fillMaxSize())
                }
            }
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
                    Image(painterResource(R.drawable.ic_mascot18), null, Modifier.fillMaxSize(), colorFilter = tint)
                    val h = Outfit.hat(outfit); if (h != 0) Image(painterResource(h), null, Modifier.fillMaxSize())
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
        PlayMascot(Modifier.fillMaxWidth().height(280.dp))
        Text(tr("Press and hold the mascot, then drag and fling it", "Maskota basılı tut, sonra sürükle ve fırlat"), fontSize = 11.sp, color = cs.onSurfaceVariant)
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
        Text(tr("Claude Code on your phone, through a local bridge in Termux. Nothing is sent anywhere except to Claude itself.", "Telefonda Claude Code, Termux'taki yerel bir köprü üzerinden. Claude'un kendisi dışında hiçbir yere bir şey gönderilmez."), fontSize = 12.sp, color = cs.onSurfaceVariant)
    }
}

/** Easter egg: the mascot dances in a glow; hold it and it follows the finger like a jelly toy (spring physics), let go and it flies, spins and bounces off the walls. */
@Composable
private fun PlayMascot(modifier: Modifier) {
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
        .pointerInput(Unit) { detectTapGestures(onTap = { av += if (vel.x >= 0) 720f else -720f }) }
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

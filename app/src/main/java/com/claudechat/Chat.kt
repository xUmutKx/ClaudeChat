package com.claudechat

import android.graphics.Bitmap
import android.text.format.DateFormat
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.ui.Alignment
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import java.util.Date

fun statusColor(s: Status) = when (s) {
    Status.Working -> Color(0xFFFF9800)
    Status.Background -> Color(0xFF2196F3)
    Status.Done -> Color(0xFF4CAF50)
    Status.Error -> Color(0xFFF44336)
    Status.Offline -> Color(0xFF9E9E9E)
    Status.Idle -> Color(0xFF757575)
}

@Composable
fun statusText(s: Status, det: String) = when (s) {
    Status.Working -> det.ifEmpty { stringResource(R.string.status_working) }
    Status.Background -> det.ifEmpty { stringResource(R.string.status_bg) }
    Status.Done -> stringResource(R.string.status_done)
    Status.Error -> stringResource(R.string.status_error)
    Status.Offline -> stringResource(R.string.status_offline)
    Status.Idle -> stringResource(R.string.status_idle)
}

/** Unsent text; lives outside the composable so it survives opening the chat list or settings. */
val Draft = mutableStateOf("")

@Composable
fun ChatScreen(onSettings: () -> Unit, onChats: () -> Unit, onSetup: () -> Unit = {}, onTasks: () -> Unit = {}) {
    val msgs by Engine.messages.collectAsState()
    val ask by Engine.approval.collectAsState()
    ask?.let { a ->
        AlertDialog(onDismissRequest = { a.answer.complete(0) },
            title = { Text(tr("Allow this?", "İzin veriyor musun?")) },
            text = { Column { Text(a.title, fontWeight = FontWeight.Bold); Text(a.detail.take(900), Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), fontFamily = FontFamily.Monospace, fontSize = 12.sp) } },
            confirmButton = { Row { TextButton({ a.answer.complete(2) }) { Text(tr("Always (this chat)", "Hep (bu sohbet)")) }; TextButton({ a.answer.complete(1) }) { Text(tr("Allow", "İzin ver")) } } },
            dismissButton = { TextButton({ a.answer.complete(0) }) { Text(tr("Deny", "Reddet")) } })
    }
    val st by Engine.status.collectAsState()
    val det by Engine.detail.collectAsState()
    var input by Draft
    val atts = remember { mutableStateListOf<Att>() }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        scope.launch { uris.forEach { u -> withContext(Dispatchers.IO) { Attach.save(ctx, u) }?.let { atts.add(it) } } }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        scope.launch { uris.forEach { u -> withContext(Dispatchers.IO) { Attach.save(ctx, u) }?.let { atts.add(it) } } }
    }
    val curId by Engine.currentId.collectAsState()
    val modelTick by Prefs.chatModelTick.collectAsState()
    val model = remember(curId, modelTick) { Prefs.chatModel(curId) }
    val modelSeen by Engine.modelName.collectAsState()
    val chats by Engine.chats.collectAsState()
    val title = chats.firstOrNull { it.id == curId }?.title.orEmpty()
    var replyTo by remember { mutableStateOf<Msg?>(null) }
    var sheet by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf(false) }
    val realShell by Engine.shell.collectAsState()
    val demoShell by Engine.demoShell.collectAsState()
    val shell = realShell || demoShell
    // the header says "Compiling" while a Gradle build runs (the pill polls too, but it is not always on screen)
    LaunchedEffect(Unit) { while (true) { withContext(Dispatchers.IO) { Engine.buildLog() }; kotlinx.coroutines.delay(3000) } }
    val items = remember(msgs) { groupMsgs(msgs) }
    val working = st == Status.Working
    val slash = if (input.startsWith("/") && !input.contains(' ')) Cmds.matching(input.drop(1)) else emptyList()
    val showTyping = working && (msgs.lastOrNull()?.role.let { it == null || it == Role.User || it == Role.Tool })

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding().imePadding()) {
        // this chat looks finished but another chat still works in the background: say so (blue) instead of "Done"
        TopBar(title, st, det, model.ifEmpty { shortModel(modelSeen) }, { sheet = true }, onChats, onSettings, shell, onTasks) { custom = true }
        if (sheet) OptionsSheet { sheet = false }
        if (custom) CustomizeSheet { custom = false }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            ChatBackground(Modifier.fillMaxSize())
            if (msgs.isEmpty() && !working) EmptyState(onSetup)
            val list = rememberLazyListState()
            // the list stays where you scrolled while Claude writes; the arrow brings you back to the newest message
            val atBottom by remember { derivedStateOf { list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset < 40 } }
            LazyColumn(
                Modifier.fillMaxSize(), state = list, reverseLayout = true,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                if (showTyping) item(key = "typing") { TypingBubble() }
                itemsIndexed(items.asReversed(), key = { _, g -> g.first().id }) { ri, g ->
                    val m = g.first()
                    val next = items.getOrNull(items.size - 1 - ri + 1)?.first()
                    val lastInGroup = next == null || next.role != m.role
                    if (m.role == Role.Tool) ToolGroup(g) else Message(m, lastInGroup, onSettings) { replyTo = it }
                }
            }
            val scope = rememberCoroutineScope()
            if (!atBottom) IconButton({ scope.launch { list.animateScrollToItem(0) } },
                Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp).size(40.dp).background(Color.Black.copy(alpha = .6f), CircleShape)) {
                Icon(Icons.Filled.KeyboardArrowDown, tr("Newest message", "En yeni mesaj"), tint = Color.White)
            }
        }
        AgentsBar()
        if (slash.isNotEmpty()) Palette(slash) { input = "/${it.name} " }
        replyTo?.let { r ->
            val cs = MaterialTheme.colorScheme
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp).clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainerHigh).height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(4.dp).fillMaxHeight().background(cs.primary))
                Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(if (r.role == Role.Claude) "Claude" else tr("You", "Sen"), fontSize = 12.sp, color = cs.primary, fontWeight = FontWeight.Medium)
                    Text(r.text.lineSequence().filter { it.isNotBlank() }.joinToString(" "), fontSize = 13.sp, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                IconButton({ replyTo = null }, Modifier.size(36.dp)) { Icon(Icons.Filled.Close, stringResource(R.string.remove), Modifier.size(18.dp)) }
            }
        }
        if (atts.isNotEmpty()) LazyRow(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(atts.toList(), key = { it.uri.toString() }) { a -> AttChip(a) { atts.remove(a) } }
        }
        BuildBar(onTasks)
        val ctxUsed by Engine.context.collectAsState()
        Composer(input, { input = it }, working, atts.isNotEmpty(), { galleryPicker.launch("image/*") }, { picker.launch("*/*") },
            onSend = {
                if (!Cmds.runLocal(input.trim(), { sheet = true }, onSettings)) Engine.send(input, atts.toList(), replyTo?.let { it.role to it.text })
                input = ""; atts.clear(); replyTo = null
            }, onStop = { Engine.stop() })
        // under the box: the model and how much of its context window this chat uses
        val window = if ((model + modelSeen).contains("1m", true)) 1_000_000 else 200_000
        Text("◆ ${model.ifEmpty { shortModel(modelSeen) }.ifEmpty { "Auto" }} · ${(ctxUsed * 100 / window).coerceAtMost(100)}% context",
            Modifier.padding(start = 22.dp, bottom = 6.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TopBar(title: String, st: Status, det: String, model: String, onModel: () -> Unit, onChats: () -> Unit, onSettings: () -> Unit, shell: Boolean, onTasks: () -> Unit, onMascot: () -> Unit) {
    val building by Engine.buildRunning.collectAsState()
    val buildKey by Prefs.buildColor.flow.collectAsState()
    val doneKey by Prefs.doneColor.flow.collectAsState()
    val bashKey by Prefs.bashColor.flow.collectAsState()
    val thinkKey by Prefs.thinkColor.flow.collectAsState()
    val hue by rememberInfiniteTransition(label = "rbow").animateFloat(0f, 360f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "rb")
    val toBlue by rememberInfiniteTransition(label = "both").animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing), androidx.compose.animation.core.RepeatMode.Reverse), label = "bb")
    // the same colours as the pill: build colour while a build runs, done colour when done, blue for the shell
    val thinkingNow = st == Status.Working && !shell && det == appStr(R.string.thinking)
    // a build while Bash runs or while Claude thinks: the build colour and the other one blend
    val second = if (shell) bashKey else thinkKey
    val accent = when {
        building && (shell || thinkingNow) -> androidx.compose.ui.graphics.lerp(Color(StateColors.argb(buildKey)), Color(StateColors.argb(second)), toBlue)
        building -> Color(StateColors.argb(buildKey, hue))
        shell -> Color(StateColors.argb(bashKey, hue))
        st == Status.Working && det == appStr(R.string.thinking) -> Color(StateColors.argb(thinkKey, hue))
        st == Status.Done -> Color(StateColors.argb(doneKey, hue))
        else -> statusColor(st)
    }
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(Modifier.statusBarsPadding().fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (Prefs.showHeaderMascot.flow.collectAsState().value == "1") {
                // the same coloured loading ring as around the camera (the pill is hidden while the chat is open)
                val ringColor = accent
                val spin by rememberInfiniteTransition(label = "hring").animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "hr")
                Box(Modifier.size(66.dp), contentAlignment = Alignment.Center) {
                if (st != Status.Idle || building) androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    val w = 3.dp.toPx(); val inset = w / 2f
                    val arcSize = androidx.compose.ui.geometry.Size(size.width - w, size.height - w)
                    val tl = androidx.compose.ui.geometry.Offset(inset, inset)
                    drawArc(ringColor.copy(alpha = .25f), 0f, 360f, false, tl, arcSize, style = androidx.compose.ui.graphics.drawscope.Stroke(w))
                    if (building && (shell || thinkingNow)) rotate(spin, center) { // a build with Bash or thinking: the ring turns through both colours
                        drawArc(androidx.compose.ui.graphics.Brush.sweepGradient(listOf(Color(StateColors.argb(buildKey)), Color(StateColors.argb(second)), Color(StateColors.argb(buildKey))), center), 0f, 360f, false, tl, arcSize, style = androidx.compose.ui.graphics.drawscope.Stroke(w))
                    } else if (st.running || building) drawArc(ringColor, spin - 90f, 100f, false, tl, arcSize, style = androidx.compose.ui.graphics.drawscope.Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    else drawArc(ringColor, 0f, 360f, false, tl, arcSize, style = androidx.compose.ui.graphics.drawscope.Stroke(w))
                }
                Box(Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest).clickable(onClick = onMascot), contentAlignment = Alignment.Center) {
                    val sc = Outfit.scene(Prefs.mascotScene.flow.collectAsState().value)
                    if (sc != 0) androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(sc), null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    if (sc != 0) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = (Prefs.sceneDim.flow.collectAsState().value.toIntOrNull() ?: 35) / 100f)))
                    val pc = st == Status.Background || shell
                    Mascot(st.running || building, Modifier.size(48.dp, 35.dp), sleeping = st == Status.Idle && !building, computer = pc, error = st == Status.Error || st == Status.Offline)
                }
                }
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable(onClick = onModel)) {
                Text(title.ifEmpty { modelTitle(model) }, fontFamily = FontFamily.Serif, fontSize = 20.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                    Spacer(Modifier.width(6.dp))
                    // thinking: the same empty thought bubble as in the pill
                    if (st == Status.Working && !shell && det == appStr(R.string.thinking)) Icon(painterResource(R.drawable.ic_thought), null, Modifier.size(12.dp), tint = Color(StateColors.argb(thinkKey)))
                    Text((if (title.isNotEmpty()) modelTitle(model) + " · " else "") + (if (building) tr("Compiling", "Derleniyor") else statusText(st, det)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            // one small mascot per running agent (at most six), overlapping, the front one on top
            val agentsNow by Engine.agents.collectAsState()
            val running = agentsNow.count { !it.done }.coerceAtMost(6)
            if (running > 0) Box(Modifier.size(18.dp + 8.dp * (running - 1), 14.dp)) {
                repeat(running) { i -> Mascot(true, Modifier.size(18.dp, 14.dp).offset(x = 8.dp * i).zIndex(i.toFloat()), computer = i % 2 == 1) }
            }
            IconButton({ Prefs.black.value = !Prefs.black.value }) { Icon(Icons.Outlined.DarkMode, stringResource(R.string.black_mode)) }
            IconButton(onChats) { Icon(Icons.Outlined.FormatListBulleted, stringResource(R.string.chats_title)) }
            IconButton(onSettings) { Icon(Icons.Outlined.Settings, stringResource(R.string.settings)) }
        }
    }
}

/** One shared dance for the notification, island, pill, AOD and chat: slow stepped frames, tilts left/right, then straight hops. */
object Dance {
    const val FRAME_MS = 280L
    /** 4 frames, even on both sides: centre (up), tilt left (low), centre (up), tilt right (low) */
    val ROT = floatArrayOf(0f, -12f, 0f, 12f)
    /** 0 = down, 1 = up (scaled by each place's hop height) */
    val UP = floatArrayOf(1f, 0f, 1f, 0f)
}

/** Mascot picture with the chosen outfit's hat on top (same pixel canvas as the pill's). */
@Composable
private fun MascotImg(sleeping: Boolean, modifier: Modifier, computer: Boolean = false, error: Boolean = false) {
    val hat = Outfit.hat(Prefs.pillOutfit.flow.collectAsState().value)
    val skin = Prefs.mascotSkin.flow.collectAsState().value
    Prefs.mascotChar.flow.collectAsState().value; Prefs.provider.flow.collectAsState().value // follow the character
    val tint = remember(skin) { Outfit.skinMatrix(skin)?.let { androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(it)) } }
    val wFeet by Prefs.wearFeet.flow.collectAsState(); val wLegs by Prefs.wearLegs.flow.collectAsState(); val wBody by Prefs.wearBody.flow.collectAsState()
    val wFace by Prefs.wearFace.flow.collectAsState(); val wEyes by Prefs.eyeColor.flow.collectAsState()
    val wear = Wear.layers(wFeet, wLegs, wBody, wFace, wEyes)
    if (computer) { // a shell runs: a small laptop (back cover with an apple-like logo) in front of the mascot, its screen lighting the face green
        var alt by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(450); alt = !alt } }
        Box(modifier) {
            Mascots.pc(alt).forEach { Image(painterResource(it), null, Modifier.fillMaxSize(), colorFilter = tint) }
            if (hat != 0) Image(painterResource(hat), null, Modifier.fillMaxSize().hatFit())
        }
        return
    }
    if (error) { // crossed-out eyes
        Box(modifier) {
            Image(painterResource(R.drawable.ic_mascot_x18), null, Modifier.fillMaxSize(), colorFilter = tint)
            wear.forEach { Image(painterResource(it), null, Modifier.fillMaxSize()) }
            if (hat != 0) Image(painterResource(hat), null, Modifier.fillMaxSize().hatFit())
        }
        return
    }
    val blanketOn = sleeping && Prefs.buddyBlanket.flow.collectAsState().value == "1"
    if (hat == 0 && !blanketOn && wear.isEmpty()) { Image(painterResource(if (sleeping) Mascots.r(R.drawable.ic_mascot_sleep) else Mascots.r(R.drawable.ic_mascot)), null, modifier, colorFilter = tint); return }
    // falling asleep: the blanket is pulled up over the body
    var pulled by remember { mutableStateOf(false) }
    LaunchedEffect(sleeping) { pulled = false; if (sleeping) { kotlinx.coroutines.delay(60); pulled = true } }
    val pull by animateFloatAsState(if (pulled) 1f else 0f, tween(900), label = "blanket")
    Box(modifier) {
        Image(painterResource(if (sleeping) Mascots.r(R.drawable.ic_mascot_sleep18) else Mascots.r(R.drawable.ic_mascot18)), null, Modifier.fillMaxSize(), colorFilter = tint)
        wear.forEach { Image(painterResource(it), null, Modifier.fillMaxSize()) }
        if (blanketOn) Image(painterResource(R.drawable.ic_blanket), null, Modifier.fillMaxSize().graphicsLayer { translationY = (1f - pull) * size.height * 0.45f; alpha = pull })
        if (hat != 0) Image(painterResource(hat), null, Modifier.fillMaxSize().hatFit())
    }
}

/** Waiting poses: the mascot jumps on a trampoline, reads a scroll (tilting over it) or sits at the laptop. */
@Composable
fun PosedMascot(pose: String, modifier: Modifier) {
    if (pose == "pc") { MascotImg(false, modifier, computer = true); return }
    val k = rememberInfiniteTransition(label = "pose").animateFloat(0f, 1f, infiniteRepeatable(tween(Poses.period(pose), easing = LinearEasing), RepeatMode.Restart), label = "poseK")
    // the motion is applied while drawing (graphicsLayer reads k in the draw phase), so the mascot is not recomposed every frame
    val prop = Poses.prop(pose)
    // "Frame by frame" in Customize: the motion jumps between 8 poses per cycle instead of gliding
    Box(modifier.graphicsLayer { Poses.apply(this, pose, if (Prefs.danceMode.value == "steps") kotlin.math.floor(k.value * 8) / 8 else k.value) }) {
        MascotImg(false, Modifier.fillMaxSize())
        if (prop != 0) Image(painterResource(prop), null, Modifier.fillMaxSize())
    }
}

/** Waiting poses for the mascot: each is one motion of the drawn mascot (the pixel art itself does not change). */
object Poses {
    val ALL = listOf(
        "idle", "jump", "hop", "dance", "spin", "squash", "nod", "sway", "bounce", "flip", "read", "pulse", "breath",
        "shiver", "swing", "slide", "zoom", "fade", "wobble", "rocket", "swim", "parachute", "bow", "whistle", "yawn", "backflip", "moonwalk", "pc")
    fun period(p: String): Int = when (p) { "jump", "squash", "hop", "nod" -> 620; "spin", "flip", "backflip" -> 1400; "rocket" -> 900; "dance" -> 900; "shiver" -> 500; else -> 1300 }
    fun label(p: String): Pair<String, String> = when (p) {
        "idle" -> "Idle" to "Boşta"; "jump" -> "Jump" to "Zıplama"; "hop" -> "Hop" to "Sekme"; "dance" -> "Dance" to "Dans"
        "spin" -> "Spin" to "Dönme"; "squash" -> "Squash" to "Esneme"; "nod" -> "Hello" to "Selam"; "sway" -> "Sway" to "Sallanma"
        "bounce" -> "Bounce" to "Zıplayış"; "flip" -> "Flip" to "Takla"; "read" -> "Read" to "Okuma"; "pulse" -> "Pulse" to "Atım"
        "breath" -> "Breathe" to "Nefes"; "shiver" -> "Shiver" to "Titreme"; "swing" -> "Swing" to "Salıncak"; "slide" -> "Slide" to "Kayma"
        "zoom" -> "Zoom" to "Büyüme"; "fade" -> "Glow" to "Parıltı"; "wobble" -> "Wobble" to "Sallantı"; "rocket" -> "Rocket" to "Roket"
        "swim" -> "Swim" to "Yüzme"; "parachute" -> "Parachute" to "Paraşüt"; "bow" -> "Bow" to "Eğilme"; "whistle" -> "Whistle" to "Islık"
        "yawn" -> "Yawn" to "Esneme 2"; "backflip" -> "Backflip" to "Geri takla"; "moonwalk" -> "Moonwalk" to "Ay yürüyüşü"
        "pc" -> "Laptop" to "Bilgisayar"; else -> p to p
    }
    /** Small objects drawn in the pose: the scroll for reading, the whistle, the parachute, the swim ring. */
    fun prop(p: String): Int = when (p) {
        "read" -> R.drawable.prop_papyrus
        "whistle" -> R.drawable.prop_whistle
        "parachute" -> R.drawable.prop_parachute
        "swim" -> R.drawable.prop_ring
        else -> 0
    }

    fun apply(l: androidx.compose.ui.graphics.GraphicsLayerScope, p: String, t: Float) {
        val s = kotlin.math.sin(2 * Math.PI * t).toFloat(); val c = kotlin.math.cos(2 * Math.PI * t).toFloat()
        val a = kotlin.math.abs(s); val half = kotlin.math.abs(kotlin.math.sin(Math.PI * t)).toFloat()
        val px = { v: Float -> v * l.density }
        when (p) {
            "jump" -> l.translationY = -a * px(14f)
            "hop" -> { l.translationY = -half * px(10f);}
            "dance" -> { l.translationX = s * px(8f); l.translationY = -a * px(6f); l.rotationZ = s * 8f }
            "spin" -> l.rotationY = t * 360f
            "squash" -> { l.translationY = px(4f) * a }
            "nod" -> { l.translationY = s * px(3f);}
            "sway" -> { l.translationX = s * px(6f); l.rotationZ = s * 3f }
            "bounce" -> { l.translationY = -a * px(22f);}
            "flip" -> l.rotationX = t * 360f
            "read" -> { l.rotationZ = s * 4f;}
            "pulse" -> {}
            "breath" -> {}
            "shiver" -> l.translationX = kotlin.math.sin(2 * Math.PI * t * 6).toFloat() * px(2f)
            "swing" -> { l.transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f); l.rotationZ = s * 15f }
            "slide" -> l.translationX = s * px(16f)
            "zoom" -> {}
            "fade" -> l.alpha = 0.4f + 0.6f * a
            "wobble" -> { l.rotationZ = s * 6f;}
            "rocket" -> { l.translationY = -t * px(20f); l.translationX = kotlin.math.sin(2 * Math.PI * t * 8).toFloat() * px(1.5f) }
            "swim" -> { l.translationY = s * px(4f); l.translationX = s * px(4f); l.rotationZ = c * 6f }
            "parachute" -> { l.translationY = -half * px(6f); l.rotationZ = s * 5f }
            "bow" -> { l.translationY = px(2f) * kotlin.math.max(0f, s) }
            "whistle" -> { l.translationY = -a * px(3f) }
            "yawn" -> { l.translationY = -a * px(2f) }
            "backflip" -> { l.translationY = -half * px(18f); l.rotationX = -t * 360f }
            "moonwalk" -> l.translationX = ((2 * t) % 1f - 0.5f) * px(8f)
            else -> {}
        }
    }
}

/** The mascot; while Claude works it dances in the same stepped frames as the notification. */
@Composable
fun Mascot(working: Boolean, modifier: Modifier, sleeping: Boolean = false, computer: Boolean = false, error: Boolean = false, idle: Boolean = false) {
    val pose by Prefs.mascotPose.flow.collectAsState()
    if (idle && !working && !sleeping && !error && !computer && pose != "idle") { PosedMascot(pose, modifier); return }
    if (computer) { MascotImg(false, modifier, computer = true); return }
    if (idle && !working && !sleeping && !error) { // awake and doing nothing: blinks now and then
        var closed by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(2200L + (0..3800).random()); closed = true; kotlinx.coroutines.delay(140); closed = false } }
        MascotImg(closed, modifier); return
    }
    if (error && !working) { MascotImg(false, modifier, error = true); return }
    if (sleeping && !working) { // idle: eyes closed and a drifting "z"
        val t = rememberInfiniteTransition(label = "zzz")
        val a by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "z")
        val a2 by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(1400, delayMillis = 700), RepeatMode.Reverse), label = "z2")
        Box(contentAlignment = Alignment.TopEnd) {
            MascotImg(true, modifier)
            // two z's, the second one bigger and half a beat later
            Text("z", Modifier.offset(x = 3.dp, y = (-4).dp).graphicsLayer { alpha = a }, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            Text("Z", Modifier.offset(x = 10.dp, y = (-10).dp).graphicsLayer { alpha = a2 }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        }
        return
    }
    if (!working) { MascotImg(false, modifier); return }
    val n = Dance.ROT.size
    if (Prefs.danceMode.flow.collectAsState().value == "smooth") {
        // glide between the same 4 poses
        val p by rememberInfiniteTransition(label = "dance").animateFloat(0f, n.toFloat(), infiniteRepeatable(tween((Dance.FRAME_MS * n).toInt(), easing = LinearEasing)), label = "p")
        MascotImg(false, modifier.graphicsLayer {
            val i = p.toInt().coerceIn(0, n - 1); val f = p - i; val j = (i + 1) % n
            rotationZ = Dance.ROT[i] + (Dance.ROT[j] - Dance.ROT[i]) * f
            translationY = -(Dance.UP[i] + (Dance.UP[j] - Dance.UP[i]) * f) * 3.dp.toPx()
        })
    } else {
        var frame by remember { mutableStateOf(0) }
        LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(Dance.FRAME_MS); frame = (frame + 1) % n } }
        MascotImg(false, modifier.graphicsLayer { rotationZ = Dance.ROT[frame]; translationY = -Dance.UP[frame] * 3.dp.toPx() })
    }
}

@Composable
private fun EmptyState(onSetup: () -> Unit = {}) {
    val ctx = LocalContext.current
    val bridge by Engine.bridge.collectAsState()
    val err by Engine.bridgeError.collectAsState()
    val note by Termux.note.collectAsState()
    val clip = LocalClipboardManager.current
    var auth by remember { mutableStateOf<Engine.Auth?>(null) }
    // one tap also grants Claude Chat the right to run commands in Termux (a system dialog, once), then connects
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { Engine.connect() }
    LaunchedEffect(Unit) { Engine.checkBridge() }
    LaunchedEffect(bridge) { auth = if (bridge == 200) Engine.authStatus() else null }
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Image(painterResource(Mascots.r(R.drawable.ic_mascot)), null, Modifier.size(120.dp, 84.dp))
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.empty_title), fontFamily = FontFamily.Serif, fontSize = 28.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (bridge != 200) FirstRunGuide()
        // one tap: starts the bridge with the current token (also done by itself when auto-start is on)
        if (bridge != 200) {
            Spacer(Modifier.height(20.dp))
            if (bridge == 0 || bridge == -2) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(if (bridge == 0) R.string.starting_bridge else R.string.checking), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (note.isNotEmpty()) Text(note, Modifier.padding(top = 6.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                if (!Termux.installed(ctx)) Button(onSetup) { Text(tr("Set up from scratch", "Sıfırdan kur")) }
                else {
                    Button({ if (!Termux.granted(ctx)) perm.launch(Termux.PERM) else Engine.connect() }) { Text(stringResource(R.string.start_claude)) }
                    // when starting from here does not work (Termux not allowed to take orders yet): one paste in Termux fixes it for good
                    OutlinedButton({ SetupServer.command()?.let { clip.setText(androidx.compose.ui.text.AnnotatedString(it)); Termux.openTermux(ctx) } }) { Text(tr("Repair the connection (paste once)", "Bağlantıyı onar (bir kez yapıştır)")) }
                }
                if (Termux.installed(ctx)) TextButton(onSetup) { Text(tr("Setup guide", "Kurulum rehberi")) }
                if (err.isNotBlank()) Text(err.take(300), Modifier.padding(top = 10.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        } else if (auth != null && auth?.claudeIn == false) {
            // connected, but Claude does not know who you are yet: the setup screen signs in through the browser
            Spacer(Modifier.height(20.dp))
            Text(tr("Claude is not signed in yet.", "Claude'a henüz giriş yapılmadı."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onSetup, Modifier.padding(top = 8.dp)) { Text(tr("Sign in", "Giriş yap")) }
        }
    }
}

/** Swipe a message to the right to answer it (like in a messenger). */
@Composable
private fun SwipeToReply(onReply: () -> Unit, content: @Composable () -> Unit) {
    val dens = androidx.compose.ui.platform.LocalDensity.current
    val trigger = with(dens) { 64.dp.toPx() }
    var dx by remember { mutableStateOf(0f) }
    var held by remember { mutableStateOf(false) }
    val shown by animateFloatAsState(if (held) dx else 0f, label = "swipe")
    Box(Modifier.fillMaxWidth().pointerInput(Unit) {
        detectHorizontalDragGestures(
            onDragStart = { held = true; dx = 0f },
            onDragEnd = { if (dx >= trigger) onReply(); held = false; dx = 0f },
            onDragCancel = { held = false; dx = 0f },
        ) { _, d -> dx = (dx + d).coerceIn(0f, trigger * 1.4f) }
    }) {
        if (shown > 8f) Icon(Icons.AutoMirrored.Filled.Reply, null, Modifier.align(Alignment.CenterStart).padding(start = 6.dp).size(22.dp).alpha((shown / trigger).coerceIn(0f, 1f)), tint = MaterialTheme.colorScheme.primary)
        Box(Modifier.offset { androidx.compose.ui.unit.IntOffset(shown.toInt(), 0) }) { content() }
    }
}

@Composable
private fun Message(m: Msg, lastInGroup: Boolean, onSettings: () -> Unit, onReply: (Msg) -> Unit) {
    if (m.role == Role.User || m.role == Role.Claude) SwipeToReply({ onReply(m) }) { MessageBody(m, lastInGroup, onSettings) } else MessageBody(m, lastInGroup, onSettings)
}

@Composable
private fun MessageBody(m: Msg, lastInGroup: Boolean, onSettings: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val maxW = (LocalConfiguration.current.screenWidthDp * 0.86f).dp
    val ctx = LocalContext.current
    when (m.role) {
        Role.Note -> Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
            Surface(shape = RoundedCornerShape(14.dp), color = cs.surfaceContainer) {
                Text(m.text, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 12.5.sp, color = cs.onSurfaceVariant)
            }
        }
        Role.Tool -> ToolGroup(listOf(m))
        else -> {
            val user = m.role == Role.User
            val err = m.role == Role.Error
            if (user) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                    val bs = Prefs.bubbleStyle.flow.collectAsState().value
                    Surface(
                        shape = RoundedCornerShape(when (bs) { "round" -> 28.dp; "square" -> 6.dp; else -> 20.dp }),
                        color = when (bs) { "round" -> cs.primaryContainer; "outline" -> Color.Transparent; else -> cs.surfaceContainerHigh },
                        border = if (bs == "outline") BorderStroke(1.dp, cs.outline) else null,
                        modifier = Modifier.widthIn(max = maxW)
                    ) {
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            val quote = if (m.text.startsWith("↩ ")) m.text.removePrefix("↩ ").substringBefore("\n\n") else null
                            val body = if (quote != null) m.text.substringAfter("\n\n", "") else m.text
                            Column {
                                if (quote != null) Row(Modifier.padding(bottom = 6.dp).height(IntrinsicSize.Min)) {
                                    Box(Modifier.width(3.dp).fillMaxHeight().background(cs.primary))
                                    Text(quote, Modifier.padding(start = 8.dp), fontSize = 12.sp, color = cs.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                }
                                if (body.isNotBlank() || quote == null) SelectionContainer { MarkdownText(body, cs.onSurface) }
                            }
                        }
                    }
                    CopyBtn(if (m.text.startsWith("↩ ")) m.text.substringAfter("\n\n", "") else m.text)
                }
            } else {
                val fg = if (err) cs.onErrorContainer else cs.onSurface
                val body: @Composable () -> Unit = {
                    if (err) Icon(Icons.Filled.WarningAmber, null, Modifier.size(18.dp), tint = fg)
                    SelectionContainer { MarkdownText(m.text, fg, serif = !err) }
                    if (m.action) TextButton(onSettings, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.sec_method)) }
                }
                if (err) Surface(shape = RoundedCornerShape(16.dp), color = cs.errorContainer) { Column(Modifier.padding(14.dp)) { body() } }
                else Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) { body(); FileChips(m.text); CopyBtn(m.text) }
            }
        }
    }
}

/** Small copy button under a message: one tap puts the whole message on the clipboard, the icon turns into a tick for a moment. */
@Composable
private fun CopyBtn(text: String) {
    if (text.isBlank()) return
    val clip = LocalClipboardManager.current
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(done) { if (done) { kotlinx.coroutines.delay(1500); done = false } }
    IconButton({ clip.setText(AnnotatedString(text)); done = true }, Modifier.size(30.dp)) {
        Icon(if (done) Icons.Filled.Check else Icons.Filled.ContentCopy, stringResource(R.string.copy), Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .8f))
    }
}

@Composable
private fun AttChip(a: Att, onRemove: () -> Unit) {
    val ctx = LocalContext.current
    val thumb by produceState<Bitmap?>(null, a.uri) {
        value = withContext(Dispatchers.IO) { try { ctx.contentResolver.loadThumbnail(a.uri, Size(120, 120), null) } catch (e: Exception) { null } }
    }
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(start = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            val b = thumb
            if (b != null) Image(b.asImageBitmap(), null, Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            else Icon(Icons.Filled.InsertDriveFile, null, Modifier.size(40.dp).padding(6.dp))
            Spacer(Modifier.width(8.dp))
            Text(a.name, Modifier.widthIn(max = 140.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
            IconButton(onRemove, Modifier.size(32.dp)) { Icon(Icons.Filled.Close, stringResource(R.string.remove), Modifier.size(16.dp)) }
        }
    }
}

@Composable
private fun TypingBubble() {
    // like Claude Code's status line: a turning star and a random orange word that changes now and then, with the seconds so far
    var word by remember { mutableStateOf(WORKING_WORDS.random()) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        var n = 0
        while (true) {
            kotlinx.coroutines.delay(250); n++; tick = n
            if (n % 12 == 0) word = WORKING_WORDS.filter { it != word }.random()
        }
    }
    val orange = Color(0xFFD97757)
    Row(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        // drawn, not a text glyph: phones turn "✳" and friends into colour emoji
        androidx.compose.foundation.Canvas(Modifier.size(14.dp)) {
            val r = size.minDimension / 2f * (0.7f + 0.3f * ((tick % 6) / 5f))
            rotate(tick * 22.5f, center) {
                for (i in 0 until 4) {
                    val a = Math.PI * i / 4
                    val dx = (r * Math.cos(a)).toFloat(); val dy = (r * Math.sin(a)).toFloat()
                    drawLine(orange, androidx.compose.ui.geometry.Offset(center.x - dx, center.y - dy), androidx.compose.ui.geometry.Offset(center.x + dx, center.y + dy), strokeWidth = 2.4f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        Text("$word…", color = orange, fontSize = 12.sp)
        Text("  ${tick / 4}s", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
    }
}

private val WORKING_WORDS = listOf(
    "Pondering", "Cooking", "Brewing", "Churning", "Conjuring", "Noodling", "Percolating", "Simmering", "Spelunking", "Wibbling", "Zigzagging", "Harmonizing",
    "Ruminating", "Cogitating", "Marinating", "Moseying", "Puzzling", "Reticulating", "Tinkering", "Vibing", "Whirring", "Combobulating", "Incubating", "Manifesting",
    "Musing", "Orchestrating", "Philosophising", "Sautéing", "Smooshing", "Unravelling", "Crafting", "Finagling", "Gallivanting", "Hatching", "Jiving", "Pontificating",
    "Flummoxing", "Frolicking", "Herding", "Julienning", "Lollygagging", "Nebulizing", "Scheming", "Whisking",
)

@Composable
private fun Composer(value: String, onChange: (String) -> Unit, working: Boolean, hasAtt: Boolean, onGallery: () -> Unit, onFiles: () -> Unit, onSend: () -> Unit, onStop: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), shape = RoundedCornerShape(28.dp), color = cs.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, cs.outlineVariant), shadowElevation = 2.dp) {
        Column(Modifier.padding(start = 6.dp, end = 8.dp, top = 4.dp, bottom = 6.dp)) {
            TextField(
                value, onChange, Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.hint_message), color = cs.onSurfaceVariant) }, maxLines = 6,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                ),
            )
            Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                // the plus opens two choices: photos from the gallery, or any file
                var menu by remember { mutableStateOf(false) }
                Box {
                    IconButton({ menu = true }, Modifier.size(40.dp)) { Icon(Icons.Filled.Add, stringResource(R.string.attach), tint = cs.onSurfaceVariant) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(tr("Gallery", "Galeri")) }, leadingIcon = { Icon(Icons.Filled.PhotoLibrary, null) }, onClick = { menu = false; onGallery() })
                        DropdownMenuItem(text = { Text(tr("Files", "Dosyalar")) }, leadingIcon = { Icon(Icons.Filled.InsertDriveFile, null) }, onClick = { menu = false; onFiles() })
                    }
                }
                val studio by Prefs.studio.flow.collectAsState()
                IconButton({ Prefs.studio.value = !studio }, Modifier.size(40.dp)) { Icon(Icons.Filled.Brush, "Studio", tint = if (studio) cs.primary else cs.onSurfaceVariant) }
                val pv by Prefs.provider.flow.collectAsState()
                if (pv != "claude") Text(Providers.byId(pv).name, fontSize = 12.sp, color = cs.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                if (working) {
                    FilledIconButton(onClick = onStop, modifier = Modifier.size(40.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = cs.surfaceContainerHighest, contentColor = cs.onSurface),
                    ) { Icon(Icons.Filled.Stop, stringResource(R.string.stop)) }
                    Spacer(Modifier.width(8.dp))
                }
                FilledIconButton(
                    onClick = onSend, enabled = value.isNotBlank() || hasAtt, modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = cs.primary, contentColor = cs.onPrimary,
                        disabledContainerColor = cs.primary.copy(alpha = 0.35f), disabledContentColor = cs.onPrimary.copy(alpha = 0.7f)),
                ) { Icon(Icons.Filled.ArrowUpward, stringResource(R.string.send)) }
            }
        }
    }
}

// ---- lightweight markdown ----

@Composable
fun MarkdownText(text: String, color: Color, serif: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    val clip = LocalClipboardManager.current
    val parts = remember(text) { splitFences(text) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        parts.forEach { (isCode, body) ->
            if (isCode) {
                // an SVG shows as a picture, with a switch to its code and back
                val isSvg = body.contains("<svg") && body.contains("</svg>")
                var showCode by remember(body) { mutableStateOf(false) }
                if (isSvg) {
                    TextButton(onClick = { showCode = !showCode }) { Text(if (showCode) tr("Picture", "Resim") else tr("Code", "Kod"), fontSize = 13.sp) }
                    if (!showCode) SvgCard(body.substring(body.indexOf("<svg"), body.lastIndexOf("</svg>") + 6))
                }
                if (!isSvg || showCode) Surface(shape = RoundedCornerShape(10.dp), color = cs.surfaceContainerLowest) {
                    Box {
                        Text(
                            body, Modifier.horizontalScroll(rememberScrollState()).padding(start = 10.dp, end = 34.dp, top = 8.dp, bottom = 8.dp),
                            fontFamily = FontFamily.Monospace, fontSize = 12.5.sp, color = cs.onSurface,
                        )
                        IconButton({ clip.setText(AnnotatedString(body)) }, Modifier.align(Alignment.TopEnd).size(30.dp)) {
                            Icon(Icons.Filled.ContentCopy, stringResource(R.string.copy), Modifier.size(15.dp), tint = cs.onSurfaceVariant)
                        }
                    }
                }
            } else if (body.isNotBlank()) {
                Text(inline(body.trim('\n'), color, cs.surfaceContainerHigh), color = color, fontSize = if (serif) 16.5.sp else 15.5.sp, lineHeight = if (serif) 25.sp else 21.sp, fontFamily = if (serif) FontFamily.Serif else FontFamily.Default)
            }
        }
    }
}

private fun splitFences(s: String): List<Pair<Boolean, String>> {
    val out = mutableListOf<Pair<Boolean, String>>()
    val buf = StringBuilder()
    var code = false
    for (line in s.lines()) {
        if (line.trimStart().startsWith("```")) {
            if (buf.isNotEmpty() || code) out += code to buf.toString().trimEnd('\n')
            buf.clear(); code = !code
        } else buf.append(line).append('\n')
    }
    if (buf.isNotEmpty()) out += code to buf.toString().trimEnd('\n')
    return out
}

private val inlineRe = Regex("\\*\\*(.+?)\\*\\*|`([^`]+)`")

private fun inline(s: String, fg: Color, codeBg: Color): AnnotatedString = buildAnnotatedString {
    s.lines().forEachIndexed { i, raw ->
        if (i > 0) append('\n')
        var line = raw
        val heading = Regex("^#{1,6}\\s+").find(line)
        val bullet = Regex("^(\\s*)[-*]\\s+").find(line)
        if (bullet != null) line = bullet.groupValues[1] + "•  " + line.substring(bullet.range.last + 1)
        val base = if (heading != null) { line = line.substring(heading.range.last + 1); SpanStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp) } else SpanStyle()
        withStyle(base) {
            var pos = 0
            for (m in inlineRe.findAll(line)) {
                append(line.substring(pos, m.range.first))
                if (m.groupValues[1].isNotEmpty()) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(m.groupValues[1]) }
                else withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg, fontSize = 14.sp)) { append(m.groupValues[2]) }
                pos = m.range.last + 1
            }
            append(line.substring(pos))
        }
    }
}


// ---- tool groups (small, expandable), palette, options ----

private fun groupMsgs(l: List<Msg>): List<List<Msg>> {
    val out = mutableListOf<MutableList<Msg>>()
    for (m in l) {
        val last = out.lastOrNull()
        if (m.role == Role.Tool && last != null && last.first().role == Role.Tool) last.add(m) else out.add(mutableListOf(m))
    }
    return out
}

fun shortModel(id: String) = id.removePrefix("claude-").replace(Regex("-\\d{8}$"), "")

/** Title for header: only the model family (Sonnet, Opus, etc.), or "Claude" if unknown. */
fun modelTitle(id: String): String {
    if (id.isEmpty()) return "Claude"
    val m = shortModel(id)
    val fam = listOf("fable", "opus", "sonnet", "haiku").firstOrNull { m.contains(it, true) }
        ?: return if (m.length > 16) m.take(12) + "…" else m
    return fam.replaceFirstChar { it.uppercase() }
}

@Composable
private fun ToolGroup(g: List<Msg>) {
    val cs = MaterialTheme.colorScheme
    var open by rememberSaveable(g.first().id) { mutableStateOf(false) }
    val title = if (g.size == 1) g[0].text
    else g.map { it.text.substringBefore(" ·") }.distinct().take(3).joinToString(", ") + " · " + stringResource(R.string.steps_n, g.size)
    Column(Modifier.fillMaxWidth().padding(start = 6.dp)) {
        Surface(shape = CircleShape, color = cs.surfaceContainer, modifier = Modifier.clip(CircleShape).clickable { open = !open }) {
            Row(Modifier.padding(start = 10.dp, end = 6.dp, top = 3.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Terminal, null, Modifier.size(13.dp), tint = cs.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(title, Modifier.widthIn(max = 260.dp), fontSize = 11.5.sp, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, Modifier.size(16.dp), tint = cs.onSurfaceVariant)
            }
        }
        if (open) Column(Modifier.padding(start = 8.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { g.forEach { ToolItem(it) } }
    }
}

@Composable
private fun ToolItem(m: Msg) {
    val cs = MaterialTheme.colorScheme
    var show by rememberSaveable(m.id) { mutableStateOf(false) }
    Column(Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = m.detail.isNotBlank()) { show = !show }) {
        Text(m.text, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = cs.onSurfaceVariant, maxLines = if (show) 4 else 1, overflow = TextOverflow.Ellipsis)
        if (show && m.detail.isNotBlank()) Surface(shape = RoundedCornerShape(8.dp), color = cs.surfaceContainerLowest, modifier = Modifier.padding(top = 3.dp)) {
            Text(
                buildAnnotatedString {
                    m.detail.lines().forEachIndexed { i, l ->
                        if (i > 0) append('\n')
                        val c = when { l.startsWith("+ ") -> Color(0xFF4CAF50); l.startsWith("- ") -> Color(0xFFE57373); else -> cs.onSurfaceVariant }
                        withStyle(SpanStyle(color = c)) { append(l) }
                    }
                },
                Modifier.horizontalScroll(rememberScrollState()).padding(8.dp), fontFamily = FontFamily.Monospace, fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun Palette(cmds: List<Cmd>, onPick: (Cmd) -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 10.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        LazyColumn(Modifier.heightIn(max = 240.dp)) {
            items(cmds, key = { it.name }) { c ->
                Column(Modifier.fillMaxWidth().clickable { onPick(c) }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text("/" + c.name, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                    Text(stringResource(c.desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun OptionsSheet(onDismiss: () -> Unit) {
    val curId by Engine.currentId.collectAsState()
    val modelTick by Prefs.chatModelTick.collectAsState()
    val model = remember(curId, modelTick) { Prefs.chatModel(curId) }
    val effort by Prefs.effort.flow.collectAsState()
    val mode by Prefs.mode.flow.collectAsState()
    val models = listOf("" to stringResource(R.string.default_label),
        "fable" to "Fable 5.1", "opus" to "Opus 5.5", "sonnet" to "Sonnet 5.5", "claude-haiku-5-5" to "Haiku 5.5", "haiku" to "Haiku",
        "sonnet[1m]" to "Sonnet 1M", "opusplan" to "Opus plan")
    val efforts = listOf("" to stringResource(R.string.default_label), "low" to "Low", "medium" to "Medium", "high" to "High", "xhigh" to "X-High", "max" to "Max")
    ModalBottomSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.options_title), style = MaterialTheme.typography.titleLarge)
            // switch the agent right here, one tap; each one keeps its own key in Settings > AI
            val prov by Prefs.provider.flow.collectAsState()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("claude", "openai", "deepseek").forEach { id -> val p = Providers.byId(id); FilterChip(prov == id, { Providers.select(id) }, { Text(p.name) }) }
            }
            Text(stringResource(R.string.model_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                models.forEach { (v, l) -> FilterChip(model == v, { Prefs.setChatModel(curId, v) }, { Text(l) }) }
            }
            OutlinedTextField(model, { Prefs.setChatModel(curId, it.trim()) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.custom_model)) }, singleLine = true)
            Text(stringResource(R.string.effort_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                efforts.forEach { (v, l) -> FilterChip(effort == v, { Prefs.effort.value = v }, { Text(l) }) }
            }
            Text(stringResource(R.string.perm_mode), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Choices(listOf("default" to R.string.mode_default, "acceptEdits" to R.string.mode_edits, "plan" to R.string.mode_plan, "bypassPermissions" to R.string.mode_bypass), mode) { Prefs.mode.value = it }
        }
    }
}

/** The first-run guide: what Claude Chat talks to and the four steps to get there. Hidden for good once dismissed. */
@Composable
private fun FirstRunGuide() {
    val hidden by Prefs.guideHidden.flow.collectAsState()
    if (hidden == "1") return
    Surface(Modifier.padding(top = 20.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("How this works", "Nasıl çalışır"), style = MaterialTheme.typography.titleSmall)
            Text(tr("Claude Chat talks to a small server inside Termux on this phone only. Claude Code runs there with your own login. Nothing leaves the phone except what Claude itself sends.",
                "Claude Chat, bu telefondaki Termux içinde çalışan küçük bir sunucuyla konuşur. Claude Code orada senin kendi girişinle çalışır. Claude'un kendisi dışında telefondan hiçbir şey çıkmaz."),
                style = MaterialTheme.typography.bodySmall)
            listOf(
                tr("1. Install Termux (F-Droid or GitHub, not the old Play Store build).", "1. Termux'u kur (F-Droid ya da GitHub; eski Play Store sürümü değil)."),
                tr("2. Open Termux once and let it finish its first setup.", "2. Termux'u bir kez aç ve ilk kurulumun bitmesini bekle."),
                tr("3. Tap \"Start Claude\" below. Allow Termux commands when asked.", "3. Aşağıdaki \"Claude'u başlat\" düğmesine dokun. İstenirse Termux komutlarına izin ver."),
                tr("4. In Termux, run `claude` once and sign in with your Claude account.", "4. Termux'ta `claude` komutunu bir kez çalıştır ve Claude hesabınla giriş yap."),
            ).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            TextButton({ Prefs.guideHidden.value = "1" }) { Text(tr("Got it", "Anladım")) }
        }
    }
}

package com.claudechat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** What one running chat turn is doing right now (shown in the task manager). */
data class TaskInfo(val chatId: String, val title: String, val since: Long, val tool: String, val command: String, val agents: Int, val agentsDone: Int, val queued: Int)

/** A claude process kept alive by the bridge (one per chat, warm between turns). */
data class BridgeProc(val chat: String, val pid: Int, val busy: Boolean, val idleSec: Int)

private fun clock(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(s / 60, s % 60) }

/** Live view of everything running: chat turns, the shell command each one is on, sub-agents, and the bridge's claude processes. */
@Composable
fun TaskManagerScreen(onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var tasks by remember { mutableStateOf<List<TaskInfo>>(emptyList()) }
    var procs by remember { mutableStateOf<List<BridgeProc>?>(null) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        var n = 0
        while (true) {
            now = System.currentTimeMillis(); tasks = Engine.tasks()
            if (n % 3 == 0) procs = Engine.bridgeProcs() // every 3 s: a cheap local call
            n++; delay(1000)
        }
    }
    Column(Modifier.fillMaxSize().background(cs.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            Text(tr("Task manager", "Görev yöneticisi"), fontSize = 22.sp, fontFamily = FontFamily.Serif)
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { BuildCard() }
            item { UsageCard() }
            item { Text(if (tasks.isEmpty()) tr("Nothing is running.", "Çalışan bir şey yok.") else tr("${tasks.size} running", "${tasks.size} çalışıyor"), color = cs.onSurfaceVariant) }
            items(tasks, key = { it.chatId }) { t ->
                Surface(shape = RoundedCornerShape(16.dp), color = cs.surfaceContainer) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(if (t.command.isNotEmpty()) Color(0xFF2196F3) else Color(0xFFFF9800)))
                            Spacer(Modifier.width(10.dp))
                            Text(t.title.ifBlank { tr("New chat", "Yeni sohbet") }, Modifier.weight(1f), fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(clock(now - t.since), fontFamily = FontFamily.Monospace, color = cs.onSurfaceVariant)
                            IconButton({ Engine.stopChat(t.chatId) }) { Icon(Icons.Filled.Stop, tr("Stop", "Durdur"), tint = Color(0xFFF44336)) }
                        }
                        if (t.tool.isNotEmpty()) Text(t.tool, fontSize = 12.sp, color = cs.onSurfaceVariant)
                        if (t.command.isNotEmpty()) Surface(shape = RoundedCornerShape(8.dp), color = cs.surfaceContainerHighest) {
                            Text("$ " + t.command, Modifier.padding(8.dp).fillMaxWidth(), fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                        if (t.agents > 0) Text(tr("Agents: ${t.agentsDone}/${t.agents} done", "Ajanlar: ${t.agentsDone}/${t.agents} bitti"), fontSize = 12.sp, color = cs.primary)
                        if (t.queued > 0) Text(tr("${t.queued} message(s) waiting", "${t.queued} mesaj sırada"), fontSize = 12.sp, color = cs.onSurfaceVariant)
                    }
                }
            }
            item { Text(tr("Bridge processes", "Köprü süreçleri"), Modifier.padding(top = 8.dp), fontWeight = FontWeight.Medium) }
            val p = procs
            when {
                p == null -> item { Text(tr("Asking the bridge…", "Köprüye soruluyor…"), color = cs.onSurfaceVariant) }
                p.isEmpty() -> item { Text(tr("No claude process is alive (or the bridge is not running).", "Canlı claude süreci yok (ya da köprü çalışmıyor)."), color = cs.onSurfaceVariant, fontSize = 13.sp) }
                else -> items(p, key = { it.pid }) { x ->
                    Surface(shape = RoundedCornerShape(14.dp), color = cs.surfaceContainer) {
                        Row(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("claude · pid ${x.pid}", fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                                Text(if (x.busy) tr("working", "çalışıyor") else tr("idle for ${x.idleSec}s (kept warm)", "${x.idleSec} sn boşta (hazır bekliyor)"), fontSize = 12.sp, color = cs.onSurfaceVariant)
                            }
                            TextButton({ Engine.killProc(x.chat) }) { Text(tr("Kill", "Sonlandır")) }
                        }
                    }
                }
            }
        }
    }
}

/** Live CPU and memory use of Claude Chat itself and of the phone's RAM (task manager + Settings > Resources). */
@Composable
fun UsageCard() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val cs = MaterialTheme.colorScheme
    var cpu by remember { mutableStateOf(0f) }
    var sys by remember { mutableStateOf(-1f) }
    var pssMb by remember { mutableStateOf(0) }
    var heapMb by remember { mutableStateOf(0) }
    var usedMb by remember { mutableStateOf(0) }
    var totalMb by remember { mutableStateOf(1) }
    var hist by remember { mutableStateOf(listOf<Float>()) }
    LaunchedEffect(Unit) {
        val am = ctx.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        fun ticks(): Long = try { val f = java.io.File("/proc/self/stat").readText().substringAfterLast(')').trim().split(" "); f[11].toLong() + f[12].toLong() } catch (e: Exception) { 0L }
        fun sysTimes(): LongArray? = try { val f = java.io.File("/proc/stat").readLines().first().trim().split(Regex("\\s+")).drop(1).map { it.toLong() }; longArrayOf(f.sum() - f[3] - f.getOrElse(4) { 0 }, f.sum()) } catch (e: Exception) { null }
        var pt = ticks(); var pw = android.os.SystemClock.elapsedRealtime(); var ps = sysTimes()
        while (true) {
            delay(1000)
            val t = ticks(); val w = android.os.SystemClock.elapsedRealtime()
            cpu = if (w > pw) (t - pt) * 10f / (w - pw) * 100f else 0f   // clock ticks are 10 ms: % of one core
            pt = t; pw = w
            val s = sysTimes()
            sys = if (s != null && ps != null && s[1] > ps[1]) 100f * (s[0] - ps[0]) / (s[1] - ps[1]) else -1f
            ps = s
            hist = (hist + cpu).takeLast(60)
            val mi = android.app.ActivityManager.MemoryInfo(); am.getMemoryInfo(mi)
            totalMb = (mi.totalMem / 1048576).toInt().coerceAtLeast(1); usedMb = totalMb - (mi.availMem / 1048576).toInt()
            pssMb = am.getProcessMemoryInfo(intArrayOf(android.os.Process.myPid())).firstOrNull()?.totalPss?.div(1024) ?: 0
            heapMb = ((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576).toInt()
        }
    }
    Surface(shape = RoundedCornerShape(16.dp), color = cs.surfaceContainer) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("Resources", "Kaynak kullanımı"), fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column { Text(tr("Claude Chat CPU", "Claude Chat CPU"), fontSize = 11.sp, color = cs.onSurfaceVariant); Text("%.0f%%".format(cpu), fontSize = 22.sp) }
                Column { Text(tr("Claude Chat memory", "Claude Chat bellek"), fontSize = 11.sp, color = cs.onSurfaceVariant); Text("$pssMb MB", fontSize = 22.sp) }
                if (sys >= 0f) Column { Text(tr("Phone CPU", "Telefon CPU"), fontSize = 11.sp, color = cs.onSurfaceVariant); Text("%.0f%%".format(sys), fontSize = 22.sp) }
            }
            androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(8.dp)).background(cs.surfaceContainerHighest)) {
                val mx = maxOf(20f, hist.maxOrNull() ?: 0f)
                val p = androidx.compose.ui.graphics.Path()
                hist.forEachIndexed { i, v -> val x = size.width * (i + 60 - hist.size) / 59f; val y = size.height * (1 - v / mx); if (i == 0) p.moveTo(x, y) else p.lineTo(x, y) }
                drawPath(p, cs.primary, style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
            }
            Text(tr("Phone memory: $usedMb / $totalMb MB used", "Telefon belleği: $usedMb / $totalMb MB kullanımda"), fontSize = 13.sp)
            LinearProgressIndicator(progress = { usedMb.toFloat() / totalMb }, Modifier.fillMaxWidth())
            Text(tr("Java heap in use: $heapMb MB", "Java heap kullanımı: $heapMb MB"), fontSize = 12.sp, color = cs.onSurfaceVariant)
        }
    }
}

private val BUILD_STEPS = listOf(
    Triple("Configure", Regex("Configure project|:app:preBuild|checkKotlinGradlePluginConfigurationErrors"), 3),
    Triple("Resources", Regex(":(merge|process|generate|extract|parse|compile)\\w*(Resources|Manifest|Assets|Aapt|Rfile|Jni|Lint)\\w*"), 17),
    Triple("Kotlin", Regex(":compile\\w*Kotlin"), 38),
    Triple("Java", Regex(":compile\\w*JavaWithJavac|:kapt|:ksp"), 6),
    Triple("Shrink (R8)", Regex(":minify\\w*|:shrink\\w*|:l8DexDesugar"), 16),
    Triple("Dex", Regex(":dexBuilder|:mergeExtDex|:mergeDex|:mergeLibDex|:mergeProjectDex"), 9),
    Triple("Package", Regex(":package\\w*|:createReleaseApkListingFileRedirect|:validateSigning|:assemble"), 11),
)

/** Where a Gradle build is: the step it is in, how long it has been running (or took) and its name, worked out from the log text. No percentage: Gradle does not report one. */
class BuildState(val ok: Boolean, val failed: Boolean, val running: Boolean, val elapsed: Int, val took: String, val reached: Int, val last: String, val project: String)

/** The build's elapsed seconds, counting up every second between two polls of the bridge. */
@Composable
fun liveElapsed(b: BuildState?): Int {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    val fetched = remember(b) { System.currentTimeMillis() }
    LaunchedEffect(b?.running) { while (b?.running == true) { delay(1000); now = System.currentTimeMillis() } }
    return if (b == null || b.elapsed < 0) -1 else b.elapsed + ((now - fetched) / 1000).toInt().coerceAtLeast(0)
}

/** 83 -> "1:23", 3725 -> "1:02:05"; -1 (unknown) -> "". */
fun clockText(sec: Int): String = if (sec < 0) "" else if (sec >= 3600) "%d:%02d:%02d".format(sec / 3600, sec / 60 % 60, sec % 60) else "%d:%02d".format(sec / 60, sec % 60)

fun buildState(l: Triple<String, Int, String>): BuildState? {
    val text = l.third
    val ok = text.contains("BUILD SUCCESSFUL"); val failed = text.contains("BUILD FAILED")
    val running = !ok && !failed && (Engine.buildAlive || l.second < 90)   // a live Gradle process, or output in the last minute
    if (!ok && !failed && !running) return null
    val tasks = Regex("> Task (:\\S+)").findAll(text).map { it.groupValues[1] }.toList()
    val reached = BUILD_STEPS.indexOfLast { s -> tasks.any { s.second.containsMatchIn(it) } || (s.first == "Configure" && text.contains("Configure project")) }
    // Gradle's own last line says how long a finished build took ("BUILD SUCCESSFUL in 14m 35s")
    val took = Regex("BUILD (?:SUCCESSFUL|FAILED) in ([^\\n]+)").find(text)?.groupValues?.get(1)?.trim().orEmpty()
    return BuildState(ok, failed, running, if (running) Engine.buildElapsedFor(l.first) else -1, took, reached, tasks.lastOrNull().orEmpty(), l.first.substringAfterLast('/'))
}

/** Slim progress bar above the message box while a build runs: step name and percentage, tap opens the details. */
@Composable
fun BuildBar(onOpen: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var st by remember { mutableStateOf<BuildState?>(null) }
    LaunchedEffect(Unit) { while (true) { st = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Engine.buildLog()?.let { buildState(it) } }; delay(3000) } }
    val b = st ?: return
    if (!b.running) return
    val el = liveElapsed(b)
    Column(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 18.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Build", "Derleme") + " · " + b.project.removeSuffix(".log").removeSuffix("_build"), fontSize = 12.sp, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1)
            Text((BUILD_STEPS.getOrNull(b.reached)?.first ?: "…") + (if (el >= 0) "  ·  " + clockText(el) else ""), fontSize = 12.sp, color = cs.primary)
        }
        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 3.dp).height(3.dp).clip(RoundedCornerShape(2.dp)), color = cs.primary)
    }
}

/** Progress of the newest Gradle build the bridge sees: percentage plus the steps it goes through. */
@Composable
fun BuildCard() {
    val cs = MaterialTheme.colorScheme
    var log by remember { mutableStateOf<Triple<String, Int, String>?>(null) }
    LaunchedEffect(Unit) { while (true) { log = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Engine.buildLog() }; delay(2000) } }
    val l = log ?: return
    val b = buildState(l) ?: return
    val el = liveElapsed(b)
    val ok = b.ok; val failed = b.failed; val running = b.running; val reached = b.reached; val last = b.last; val proj = b.project; val text = l.third
    Surface(shape = RoundedCornerShape(16.dp), color = cs.surfaceContainer) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Build", "Derleme"), fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text(
                    if (failed) tr("failed", "başarısız") + (if (b.took.isNotEmpty()) " · ${b.took}" else "") else if (ok) tr("done", "bitti") + (if (b.took.isNotEmpty()) " · ${b.took}" else "") else clockText(el).ifEmpty { "…" },
                    fontSize = 22.sp, color = if (failed) Color(0xFFF44336) else if (ok) Color(0xFF4CAF50) else cs.primary)
            }
            if (running) LinearProgressIndicator(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = cs.primary)
            else LinearProgressIndicator({ 1f }, Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = if (failed) Color(0xFFF44336) else Color(0xFF4CAF50))
            BUILD_STEPS.forEachIndexed { i, s ->
                val state = if (ok || i < reached) 2 else if (i == reached) (if (failed) 3 else 1) else 0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(when (state) { 2 -> "✓"; 1 -> "▸"; 3 -> "✗"; else -> "·" }, Modifier.width(20.dp), color = when (state) { 2 -> Color(0xFF4CAF50); 1 -> cs.primary; 3 -> Color(0xFFF44336); else -> cs.onSurfaceVariant })
                    Text(s.first, fontSize = 13.sp, color = if (state == 0) cs.onSurfaceVariant else cs.onSurface)
                }
            }
            if (running && last.isNotEmpty()) Text(last, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (failed) {
                val errs = text.lines().filter { it.startsWith("e: ") || it.contains("error:") || it.contains("What went wrong") }.takeLast(4)
                errs.forEach { Text(it.take(160), fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFFF44336), maxLines = 2) }
            }
            Text(proj, fontSize = 10.sp, color = cs.onSurfaceVariant, maxLines = 1)
        }
    }
}

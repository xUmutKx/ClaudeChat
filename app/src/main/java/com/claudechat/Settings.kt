package com.claudechat

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun Page(title: Int, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding()) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(Modifier.statusBarsPadding().fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Column(verticalArrangement = Arrangement.spacedBy(14.dp), content = content) }
        }
    }
}

@Composable
private fun Sub(title: Int) {
    Text(stringResource(title), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Section(title: Int, content: @Composable ColumnScope.() -> Unit) {
    val icon = when (title) {
        R.string.sec_method -> Icons.Filled.Link
        R.string.sec_usage -> Icons.Filled.QueryStats
        R.string.sec_appearance -> Icons.Filled.Palette
        R.string.sec_overlay -> Icons.Filled.PhoneAndroid
        R.string.sec_black -> Icons.Filled.DarkMode
        R.string.sec_claude -> Icons.Filled.Tune
        R.string.sec_keep -> Icons.Filled.Bolt
        R.string.g_battery_title -> Icons.Filled.BatteryChargingFull
        R.string.g_wake_title -> Icons.Filled.Lock
        R.string.g_overlay_title -> Icons.Filled.Layers
        R.string.g_root_title -> Icons.Filled.Build
        R.string.g_phantom_title -> Icons.Filled.Shield
        else -> null
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Choices(options: List<Pair<String, Int>>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (v, label) -> FilterChip(selected == v, { onSelect(v) }, { Text(stringResource(label)) }) }
    }
}

/** Like [Choices], with ready-made labels (English or Turkish through tr()). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TextChoices(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (v, label) -> FilterChip(selected == v, { onSelect(v) }, { Text(label) }) }
    }
}

@Composable
private fun SwitchRow(title: Int, sub: Int?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            if (sub != null) Text(stringResource(sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}

@Composable
private fun Hint(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun CodeLine(cmd: String, shown: String = cmd) {
    val clip = LocalClipboardManager.current
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Row(Modifier.padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(shown, Modifier.weight(1f).padding(vertical = 8.dp), fontFamily = FontFamily.Monospace, fontSize = 11.5.sp)
            IconButton({ clip.setText(AnnotatedString(cmd)) }) { Icon(Icons.Filled.ContentCopy, stringResource(R.string.copy), Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun rememberTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val ob = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(ob)
        onDispose { owner.lifecycle.removeObserver(ob) }
    }
    return tick
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onGuide: () -> Unit, onLang: () -> Unit, onSetup: () -> Unit = {}, onTasks: () -> Unit = {}) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val tick = rememberTick()
    val theme by Prefs.theme.flow.collectAsState()
    val dyn by Prefs.dynamic.flow.collectAsState()
    val lang by Prefs.lang.flow.collectAsState()
    val overlay by Prefs.overlay.flow.collectAsState()
    val mode by Prefs.mode.flow.collectAsState()
    val keep by Prefs.keepAlive.flow.collectAsState()
    val auto by Prefs.autoStart.flow.collectAsState()
    val screenOn by Prefs.screenOn.flow.collectAsState()
    val limits by Prefs.limits.flow.collectAsState()
    var token by remember { mutableStateOf(Prefs.token.value) }
    var bridge by remember { mutableIntStateOf(-2) } // -2 unknown, -1 down, else http code
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val canOverlay = remember(tick) { Settings.canDrawOverlays(ctx) }
    val termuxPerm = remember(tick) { Termux.granted(ctx) }

    LaunchedEffect(tick) { bridge = Engine.ping() }

    var det by remember { mutableStateOf(false) }
    var cat by rememberSaveable { mutableStateOf("") } // "" = the list of categories
    var query by rememberSaveable { mutableStateOf("") }
    BackHandler(cat.isNotEmpty()) { cat = "" }
    val cats = listOf(
        SettingsCat("conn", Icons.Filled.Link, tr("Connection", "Bağlantı"), tr("Termux bridge, port, token", "Termux köprüsü, port, anahtar"), tr("Claude", "Claude"),
            "bridge termux port token connect köprü anahtar"),
        SettingsCat("claude", Icons.Filled.Build, tr("Claude", "Claude"), tr("Permissions and folders", "İzinler ve klasörler"), tr("Claude", "Claude"),
            "permission mode working folder attachment folder notes izin modu çalışma klasörü ek klasörü"),
        SettingsCat("ai", Icons.Filled.AutoAwesome, tr("Other AIs & Studio", "Diğer yapay zekâlar ve Stüdyo"), tr("Gemini, DeepSeek, Groq, OpenRouter; pictures and pixel art", "Gemini, DeepSeek, Groq, OpenRouter; resim ve pixel art"), tr("Claude", "Claude"),
            "ai gemini deepseek groq openrouter mistral openai key api studio svg pixel art picture image yapay zeka anahtar resim"),
        SettingsCat("usage", Icons.Filled.Memory, tr("Usage & limits", "Kullanım ve limitler"), tr("5-hour and weekly limits, builds, CPU and memory", "5 saatlik ve haftalık limit, derleme, CPU, bellek"), tr("Claude", "Claude"),
            "usage limits five hour weekly build progress cpu memory resources kullanım limit derleme kaynak"),
        SettingsCat("tasks", Icons.Filled.QueryStats, tr("Task manager", "Görev yöneticisi"), tr("Running chats and commands", "Çalışan sohbetler ve komutlar"), tr("Claude", "Claude"),
            "running chats shell commands stop tasks görev çalışan"),
        SettingsCat("look", Icons.Filled.Palette, tr("Look", "Görünüm"), tr("Theme and language", "Tema ve dil"), tr("Appearance", "Görünüm"),
            "theme dark mode language tema koyu mod dil"),
        SettingsCat("overlay", Icons.Filled.Layers, tr("Pill & overlay", "Pill ve overlay"), tr("Camera pill, bubble, line, curtain", "Kamera pill'i, balon, çizgi, perde"), tr("Appearance", "Görünüm"),
            "pill floating bubble line curtain length hide asleep event change balon çizgi perde uyku değişim"),
        SettingsCat("black", Icons.Filled.DarkMode, tr("Black screen", "Siyah ekran"), tr("AOD clock, brightness, widgets", "AOD saati, parlaklık, widget"), tr("Appearance", "Görünüm"),
            "aod clock brightness widgets date battery font saat parlaklık tarih pil yazı tipi"),
        SettingsCat("vibe", Icons.Filled.Vibration, tr("Vibration", "Titreşim"), tr("Your own pattern for each event", "Her olay için kendi şeklin"), tr("Appearance", "Görünüm"),
            "vibrate pattern answer error build titreşim cevap hata derleme"),
        SettingsCat("alive", Icons.Filled.BatteryChargingFull, tr("Stay alive", "Açık kalma"), tr("Battery, wake lock, setup wizard", "Pil, uyanık tutma, kurulum sihirbazı"), tr("Phone", "Telefon"),
            "battery wake lock keep alive setup wizard root tweaks pil uyanık kurulum"),
        SettingsCat("about", Icons.Filled.Info, tr("About", "Hakkında"), tr("Version, author, GitHub", "Sürüm, yapımcı, GitHub"), tr("About", "Hakkında"),
            "version author github sürüm yapımcı"),
    )
    Page(R.string.settings, { if (cat.isNotEmpty()) cat = "" else onBack() }) {
        AnimatedContent(cat, transitionSpec = { pageSlide(if (targetState.isNotEmpty() && initialState.isEmpty()) 1 else if (targetState.isEmpty()) -1 else 0).using(SizeTransform(clip = false) { _, _ -> snap() }) }, label = "settingsPage") { sc ->
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (sc.isEmpty()) {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text(tr("Search settings", "Ayarlarda ara")) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Filled.Close, tr("Clear", "Temizle")) } })
            val shown = cats.filter { query.isBlank() || it.title.contains(query.trim(), true) || it.sub.contains(query.trim(), true) || it.keywords.contains(query.trim(), true) }
            var lastGroup = ""
            shown.forEach { c ->
                if (query.isBlank() && c.group != lastGroup) {
                    lastGroup = c.group
                    Text(c.group.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 6.dp, top = 12.dp, bottom = 2.dp))
                }
                Surface(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { cat = c.key }, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(c.icon, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.title, style = MaterialTheme.typography.titleMedium)
                            Text(c.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (shown.isEmpty()) Hint(tr("Nothing matches.", "Eşleşen ayar yok."))
        } else cats.firstOrNull { it.key == sc }?.let { c ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(c.icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(c.title, style = MaterialTheme.typography.titleLarge)
            }
        }
        if (sc == "conn") Section(R.string.sec_method) {
            val up = bridge == 200
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(if (up) statusColor(Status.Done) else statusColor(Status.Error)))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (bridge == -2) R.string.checking else if (up) R.string.bridge_up else R.string.bridge_down))
            }
            Button({
                scope.launch {
                    bridge = -2
                    // one tap does everything: asks the system once for the right to run commands in Termux, grants it through root when there is root, then starts the bridge
                    if (Termux.installed(ctx) && !Termux.granted(ctx)) permLauncher.launch(Termux.PERM)
                    withContext(Dispatchers.IO) { Termux.rootSetup() }
                    Termux.startBridge(ctx)?.let { bridge = -1; det = true; return@launch }
                    repeat(75) { kotlinx.coroutines.delay(1000); val p = Engine.ping(); if (p == 200) { bridge = p; return@launch } }
                    bridge = -1; det = true
                }
            }, Modifier.fillMaxWidth().height(52.dp)) { Text(stringResource(if (up) R.string.start_bridge else R.string.connect_now)) }
            OutlinedButton({
                SetupServer.command()?.let { clip.setText(AnnotatedString(it)); Termux.openTermux(ctx) }
            }, Modifier.fillMaxWidth()) { Text(tr("Repair the connection (paste once in Termux)", "Bağlantıyı onar (Termux'a bir kez yapıştır)")) }
            Hint(tr("If starting from here does not work, this copies one short line: paste it in Termux and everything is set up again.", "Buradan başlatma çalışmazsa bu, kısa bir satır kopyalar: Termux'a yapıştır, her şey yeniden kurulur."))
            if (bridge == 401) Hint(stringResource(R.string.bridge_401))
            if (!up && Termux.log.isNotBlank()) Hint(Termux.log.trim().takeLast(400))

            TextButton({ det = !det }) { Text(stringResource(if (det) R.string.adv_hide else R.string.conn_details)) }
            if (det) {
            Sub(R.string.sec_termux)
            run {
            val inst = remember(tick) { Termux.installed(ctx) }
            Hint(stringResource(if (inst) R.string.termux_installed else R.string.termux_not_installed))
            Hint(stringResource(if (termuxPerm) R.string.termux_perm_granted else R.string.termux_perm_missing))
            if (!termuxPerm) Button({ permLauncher.launch(Termux.PERM) }) { Text(stringResource(R.string.grant)) }
            Button({
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { Termux.rootSetup() }
                    android.widget.Toast.makeText(ctx, if (ok) R.string.root_setup_ok else R.string.root_setup_fail, android.widget.Toast.LENGTH_LONG).show()
                }
            }) { Text(stringResource(R.string.root_setup)) }
            Hint(stringResource(R.string.termux_setup))
            CodeLine(Termux.SETUP_CMD)
        }

            Sub(R.string.sec_manual)
            run {
            Hint(stringResource(R.string.manual_sub))
            val cmd = remember(token) { Termux.manualCmd(ctx) }
            CodeLine(cmd, cmd.take(90) + "…")
            Button({ Termux.openTermux(ctx) }) { Text(stringResource(R.string.open_termux)) }
            OutlinedButton({ scope.launch { bridge = -2; bridge = Engine.ping() } }) { Text(stringResource(R.string.test_conn)) }
            }
            Sub(R.string.sec_connection)
            OutlinedTextField(Prefs.port.value, { Prefs.port.value = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.port)) }, singleLine = true)
            OutlinedTextField(Prefs.distro.value, { Prefs.distro.value = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.distro)) }, singleLine = true)
            OutlinedTextField(token, { token = it; Prefs.token.value = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.token)) }, singleLine = true,
                trailingIcon = { TextButton({ token = Prefs.randomToken(); Prefs.token.value = token }) { Text(stringResource(R.string.regenerate)) } })
            }
            SwitchRow(R.string.auto_start, null, auto) { Prefs.autoStart.value = it }
        }

        if (sc == "usage") Section(R.string.sec_usage) {
            val p = limits.split("|")
            val rows = listOf(R.string.lim_five to 0, R.string.lim_week to 2)
            if (p.size < 4 || p[0].isEmpty() && p[2].isEmpty()) Hint(stringResource(R.string.lim_none))
            else rows.forEach { (title, i) ->
                val u = (p.getOrNull(i)?.toFloatOrNull() ?: return@forEach).coerceIn(0f, 1f)
                val reset = p.getOrNull(i + 1)?.toLongOrNull() ?: 0L
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(title), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text("${(u * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                }
                LinearProgressIndicator({ u }, Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)))
                if (reset > 0) Hint(stringResource(R.string.lim_reset, java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(reset * 1000))))
                Spacer(Modifier.height(6.dp))
            }
        }


        if (sc == "about") AboutCard()

        if (sc == "vibe") VibeSettings()
        if (sc == "tasks") { LaunchedEffect(Unit) { cat = ""; onTasks() } }

        if (sc == "usage") { BuildCard(); UsageCard() }

        if (sc == "look") Section(R.string.sec_appearance) {
            Choices(listOf("system" to R.string.theme_system, "light" to R.string.theme_light, "dark" to R.string.theme_dark, "amoled" to R.string.theme_amoled), theme) { Prefs.theme.value = it }
            Choices(listOf("en" to R.string.lang_en, "tr" to R.string.lang_tr), lang) { if (it != lang) { Prefs.lang.value = it; onLang() } }
        }

        if (sc == "overlay") Section(R.string.sec_overlay) {
            val sleepHide by Prefs.pillSleepHide.flow.collectAsState()
            val headerOn by Prefs.showHeaderMascot.flow.collectAsState()
            val scenePill by Prefs.sceneInPill.flow.collectAsState()
            Row(verticalAlignment = Alignment.CenterVertically) { Text(tr("Scene behind the mascot in the pill", "Pill'deki maskotun arkasında ortam"), Modifier.weight(1f)); Switch(scenePill == "1", { Prefs.sceneInPill.value = if (it) "1" else "0" }) }
            val notifOn by Prefs.showNotifMascot.flow.collectAsState()
            Text(tr("Where the mascot shows", "Maskot nerede görünsün"), style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) { Text(tr("Chat screen, top left", "Sohbet ekranı, sol üst"), Modifier.weight(1f)); Switch(headerOn == "1", { Prefs.showHeaderMascot.value = if (it) "1" else "0" }) }
            Row(verticalAlignment = Alignment.CenterVertically) { Text(tr("Notification", "Bildirim"), Modifier.weight(1f)); Switch(notifOn == "1", { Prefs.showNotifMascot.value = if (it) "1" else "0" }) }
            Hint(tr("The camera pill is chosen below (Off hides it). Each place can be on or off on its own.", "Kamera pill'i aşağıdan seçilir (Kapalı gizler). Her yer ayrı ayrı açılıp kapatılabilir."))
            val hideOn = sleepHide != "0"
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Hide the pill after it has been asleep", "Uyuduktan sonra pill gizlensin"), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(hideOn, { Prefs.pillSleepHide.value = if (it) "120" else "0" })
            }
            if (hideOn) {
                var secs by remember { mutableFloatStateOf((sleepHide.toFloatOrNull() ?: 120f).coerceIn(10f, 600f)) }
                val shown = secs.toInt()
                Text(tr("After ", "Süre: ") + if (shown >= 60) "${shown / 60} " + tr("min", "dk") + (if (shown % 60 != 0) " ${shown % 60} " + tr("s", "sn") else "") else "$shown " + tr("s", "sn"), style = MaterialTheme.typography.bodyMedium)
                Slider(secs, { secs = (Math.round(it / 5f) * 5f).coerceIn(10f, 600f) }, valueRange = 10f..600f, steps = 117, onValueChangeFinished = { Prefs.pillSleepHide.value = secs.toInt().toString() })
            }
            Choices(listOf("off" to R.string.ov_style_off, "pill" to R.string.ov_style_pill, "bubble" to R.string.ov_style_bubble, "line" to R.string.ov_style_line, "curtain" to R.string.ov_style_curtain), overlay) { Prefs.overlay.value = it }
            if (overlay == "pill") {
                val ev by Prefs.pillEvents.flow.collectAsState()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Show only when something changes", "Sadece bir değişimde çıksın"), style = MaterialTheme.typography.bodyLarge)
                        Hint(tr("The ring spins around the camera, the pill grows out to both sides, stays a while, then shrinks back into the camera.", "Halka kameranın etrafında döner, pill iki yana açılır, bir süre kalır, sonra kameraya doğru daralıp gider."))
                    }
                    Switch(ev == "1", { Prefs.pillEvents.value = if (it) "1" else "0" })
                }
                if (ev == "1") {
                    val pHold by Prefs.pillHold.flow.collectAsState()
                    var hold by remember { mutableFloatStateOf((pHold.toFloatOrNull() ?: 4f).coerceIn(1f, 15f)) }
                    Text(tr("Stays open ${hold.toInt()} s", "Açık kalma süresi ${hold.toInt()} sn"), style = MaterialTheme.typography.bodyLarge)
                    Slider(hold, { hold = it }, valueRange = 1f..15f, steps = 13, onValueChangeFinished = { Prefs.pillHold.value = hold.toInt().toString() })
                }
                val pBg by Prefs.pillBg.flow.collectAsState()
                var bg by remember { mutableFloatStateOf((pBg.toFloatOrNull() ?: 100f).coerceIn(0f, 100f)) }
                Text(tr("Pill background: ${bg.toInt()}% opaque", "Pill arka planı: %${bg.toInt()} opak"), style = MaterialTheme.typography.bodyLarge)
                Slider(bg, { bg = it; Prefs.pillBg.value = it.toInt().toString() }, valueRange = 0f..100f)
                val pAll by Prefs.pillAlpha.flow.collectAsState()
                var all by remember { mutableFloatStateOf((pAll.toFloatOrNull() ?: 100f).coerceIn(10f, 100f)) }
                Text(tr("Whole pill: ${all.toInt()}% opaque", "Tüm pill: %${all.toInt()} opak"), style = MaterialTheme.typography.bodyLarge)
                Slider(all, { all = it; Prefs.pillAlpha.value = it.toInt().toString() }, valueRange = 10f..100f)
                val pGap by Prefs.pillGap.flow.collectAsState()
                var gap by remember { mutableFloatStateOf((pGap.toFloatOrNull() ?: 1f).coerceIn(0f, 8f)) }
                Text(stringResource(R.string.pill_gap, gap.toInt()), style = MaterialTheme.typography.bodyLarge)
                Slider(gap, { gap = it }, valueRange = 0f..8f, steps = 7, onValueChangeFinished = { Prefs.pillGap.value = gap.toInt().toString() })
                val pExt by Prefs.pillExtra.flow.collectAsState()
                var ext by remember { mutableFloatStateOf((pExt.toFloatOrNull() ?: 0f).coerceIn(0f, 200f)) }
                Text(tr("Extra pill length at each end: ${ext.toInt()} px", "Pill ekstra uzatma (her uç): ${ext.toInt()} px"), style = MaterialTheme.typography.bodyLarge)
                Slider(ext, { ext = it }, valueRange = 0f..200f, onValueChangeFinished = { Prefs.pillExtra.value = ext.toInt().toString() })
                val pBub by Prefs.pillBubble.flow.collectAsState()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Show pill messages as a speech bubble", "Pill mesajlarını konuşma balonu olarak göster"), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(pBub == "1", { Prefs.pillBubble.value = if (it) "1" else "0" })
                }
                Hint(tr("The bubble opens under the pill. Its size, hold time and opacity are the buddy bubble settings.", "Balon pill'in altında açılır. Boyutu, kalma süresi ve saydamlığı buddy balonu ayarlarıdır."))
                val strip by Prefs.pillHandle.flow.collectAsState()
                Text(tr("Touch strip under the pill", "Pill'in altındaki dokunma şeridi"), style = MaterialTheme.typography.bodyLarge)
                TextChoices(listOf("1" to tr("On, invisible", "Açık, görünmez"), "2" to tr("On, show it", "Açık, göster"), "0" to tr("Off", "Kapalı")), strip) { Prefs.pillHandle.value = it }
                Hint(tr("The pill sits over the status bar, where touches often never arrive. This strip right under it takes a tap (opens the chat bubble) and lets you pull the mascot out of the pill with a drag. It covers a thin band at the top of the app below, so turn it off if it gets in the way.",
                    "Pill durum çubuğunun üstünde durur ve dokunma çoğu zaman oraya ulaşmaz. Hemen altındaki bu şerit dokunmayı alır (sohbet balonunu açar) ve maskotu sürükleyerek pill'den çıkarmanı sağlar. Altındaki uygulamanın üstünden ince bir bant kaplar; engel olursa kapat."))
            }
            run {
                val buddyOn by Prefs.buddyOn.flow.collectAsState()
                val home by Prefs.mascotHome.flow.collectAsState()
                Text(tr("Floating buddy", "Yüzen buddy"), style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Show the buddy", "Buddy'yi göster"), style = MaterialTheme.typography.bodyLarge)
                        Hint(tr("The mascot walks around on top of every app. It works next to the pill.", "Maskot her uygulamanın üstünde dolaşır. Pill ile birlikte çalışır."))
                    }
                    Switch(buddyOn, { Prefs.buddyOn.value = it; if (!it) Prefs.mascotHome.value = "pill" })
                }
                if (buddyOn && overlay == "pill") {
                    Text(tr("The mascot lives in", "Maskot şurada yaşar"), style = MaterialTheme.typography.bodyLarge)
                    TextChoices(listOf("pill" to tr("The pill", "Pill"), "buddy" to tr("The buddy", "Buddy"), "both" to tr("Both", "İkisi birlikte")), home) { Prefs.mascotHome.value = it }
                    Hint(tr("There is one mascot: it is either in the pill or walking around. Pull it out of the pill with a drag from the strip under it; let the buddy go on the pill or the strip to put it back.",
                        "Tek maskot var: ya pill'in içinde ya da dolaşıyor. Altındaki şeritten sürükleyerek pill'den çıkar; buddy'yi pill'in ya da şeridin üstünde bırakınca geri girer."))
                }
            }
            if (Prefs.buddyOn.flow.collectAsState().value) BuddySettings()
            if (!canOverlay && overlay != "off") {
                Hint(stringResource(R.string.overlay_perm_sub))
                Button({ ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))) }) { Text(stringResource(R.string.grant)) }
            }
            val accOn = remember(tick, PillAccess.instance) { PillAccess.enabled(ctx) }
            Text(tr("Above everything: " + if (accOn) "on" else "off", "Her şeyin üstünde: " + if (accOn) "açık" else "kapalı"), style = MaterialTheme.typography.bodyLarge)
            Hint(tr("With the accessibility layer on, the pill and the buddy stay above the notification shade and over Settings screens. It reads nothing on your screen.", "Erişilebilirlik katmanı açıkken pill ve buddy bildirim panelinin ve Ayarlar ekranlarının üstünde kalır. Ekranındaki hiçbir şeyi okumaz."))
            if (!accOn) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ Thread { PillAccess.enableWithRoot() }.start() }) { Text(tr("Turn on (root)", "Aç (root)")) }
                OutlinedButton({ ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text(tr("Open settings", "Ayarları aç")) }
            }
        }

        if (sc == "black") Section(R.string.sec_black) {
            val dim by Prefs.blackDim.flow.collectAsState()
            val bClock by Prefs.blackClock.flow.collectAsState()
            val bDate by Prefs.blackDate.flow.collectAsState()
            val bStatus by Prefs.blackStatus.flow.collectAsState()
            val bLast by Prefs.blackLast.flow.collectAsState()
            val bBatt by Prefs.blackBattery.flow.collectAsState()
            val bMascot by Prefs.blackMascot.flow.collectAsState()
            val bText by Prefs.blackText.flow.collectAsState()
            var level by remember { mutableFloatStateOf((dim.toFloatOrNull() ?: 15f).coerceIn(1f, 100f)) }
            SwitchRow(R.string.black_on, R.string.black_on_sub, Prefs.black.flow.collectAsState().value) { Prefs.black.value = it }
            Text(stringResource(R.string.black_dim, level.toInt()), style = MaterialTheme.typography.bodyLarge)
            Slider(level, { level = it }, valueRange = 1f..100f, onValueChangeFinished = { Prefs.blackDim.value = level.toInt().toString() })
            SwitchRow(R.string.black_clock, null, bClock) { Prefs.blackClock.value = it }
            if (bClock) {
                val font by Prefs.blackFont.flow.collectAsState()
                val size by Prefs.blackSize.flow.collectAsState()
                var sz by remember { mutableFloatStateOf((size.toFloatOrNull() ?: 72f).coerceIn(32f, 120f)) }
                val style by Prefs.blackStyle.flow.collectAsState()
                Choices(listOf("digital" to R.string.cs_digital, "stacked" to R.string.cs_stacked, "analog" to R.string.cs_analog, "ticks" to R.string.cs_ticks), style) { Prefs.blackStyle.value = it }
                FontPreviews(if (font == "thin") "outfit" else font) { Prefs.blackFont.value = it }
                Text(stringResource(R.string.black_size, sz.toInt()), style = MaterialTheme.typography.bodyLarge)
                Slider(sz, { sz = it }, valueRange = 32f..120f, onValueChangeFinished = { Prefs.blackSize.value = sz.toInt().toString() })
            }
            SwitchRow(R.string.black_date, null, bDate) { Prefs.blackDate.value = it }
            SwitchRow(R.string.black_status, R.string.black_status_sub, bStatus) { Prefs.blackStatus.value = it }
            SwitchRow(R.string.black_last, R.string.black_last_sub, bLast) { Prefs.blackLast.value = it }
            SwitchRow(R.string.black_battery, null, bBatt) { Prefs.blackBattery.value = it }
            SwitchRow(R.string.black_mascot, null, bMascot) { Prefs.blackMascot.value = it }
            OutlinedTextField(bText, { Prefs.blackText.value = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.black_text)) }, maxLines = 2)
        }

        run {
            if (sc == "claude") Section(R.string.sec_claude) {
                Text(stringResource(R.string.perm_mode))
                Choices(listOf("default" to R.string.mode_default, "acceptEdits" to R.string.mode_edits, "plan" to R.string.mode_plan, "bypassPermissions" to R.string.mode_bypass), mode) { Prefs.mode.value = it }
                var pick by remember { mutableStateOf("") } // "cwd" / "attach": which field the folder picker fills
                OutlinedTextField(Prefs.cwd.value, { Prefs.cwd.value = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.workdir)) }, singleLine = true,
                    trailingIcon = { IconButton({ pick = "cwd" }) { Icon(Icons.Filled.Folder, tr("Browse", "Gözat")) } })
                OutlinedTextField(Prefs.attachDir.value, { Prefs.attachDir.value = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.attach_dir)) }, singleLine = true,
                    trailingIcon = { IconButton({ pick = "attach" }) { Icon(Icons.Filled.Folder, tr("Browse", "Gözat")) } })
                SwitchRow(R.string.chat_notes, R.string.chat_notes_sub, Prefs.chatNotes.flow.collectAsState().value) { Prefs.chatNotes.value = it }
                if (pick.isNotEmpty()) FolderPickerDialog(if (pick == "cwd") Prefs.cwd.value else Prefs.attachDir.value, { if (pick == "cwd") Prefs.cwd.value = it else Prefs.attachDir.value = it; pick = "" }, { pick = "" })
            }
            if (sc == "ai") Section(R.string.sec_ai) {
                val prov by Prefs.provider.flow.collectAsState()
                val cfgTick by Prefs.provCfg.flow.collectAsState()
                Text(tr("Who answers in new messages", "Yeni mesajlara kim cevap versin"), style = MaterialTheme.typography.bodyLarge)
                TextChoices(Providers.all.map { it.id to it.name }, prov) { Providers.select(it) }
                val p = Providers.byId(prov)
                Hint(tr(p.note, p.noteTr))
                if (p.id != "claude") {
                    androidx.compose.runtime.key(p.id) {
                        var k by remember(p.id) { mutableStateOf(Providers.key(p.id)) }
                        var m by remember(p.id) { mutableStateOf(Providers.model(p)) }
                        var b by remember(p.id) { mutableStateOf(Providers.base(p)) }
                        if (p.needsKey || p.id == "custom" || p.id == "ollama") OutlinedTextField(k, { k = it; Providers.set(p.id, "key", it) }, Modifier.fillMaxWidth(), label = { Text(tr("API key", "API anahtarı")) }, singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                        if (p.auth == "gh") {
                            var who by remember { mutableStateOf<Engine.Auth?>(null) }
                            LaunchedEffect(Unit) { who = Engine.authStatus() }
                            Hint(if (who?.ghIn == true) tr("Signed in to GitHub as ${who?.ghUser}.", "GitHub'a giriş yapıldı: ${who?.ghUser}.") else tr("Not signed in to GitHub yet.", "GitHub'a henüz giriş yapılmadı."))
                            if (who?.ghIn != true) Button(onSetup) { Text(tr("Sign in to GitHub", "GitHub'a giriş yap")) }
                        }
                        OutlinedTextField(m, { m = it; Providers.set(p.id, "model", it) }, Modifier.fillMaxWidth(), label = { Text(tr("Model", "Model")) }, singleLine = true)
                        if (p.id == "custom" || p.id == "ollama" || p.base.isEmpty()) OutlinedTextField(b, { b = it; Providers.set(p.id, "base", it) }, Modifier.fillMaxWidth(), label = { Text(tr("Server address (…/v1)", "Sunucu adresi (…/v1)")) }, singleLine = true)
                        if (p.id != "claude" && p.id != "custom" || Providers.base(p).isNotEmpty()) {
                            var browse by remember { mutableStateOf(false) }
                            OutlinedButton({ browse = true }, Modifier.fillMaxWidth()) { Text(tr("Browse all models", "Tüm modellere göz at")) }
                            if (browse) ModelBrowser(p, { browse = false }) { m = it; Providers.set(p.id, "model", it); browse = false }
                        }
                        if (p.models.isNotEmpty()) {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                p.models.forEach { mn -> FilterChip(m == mn, { m = mn; Providers.set(p.id, "model", mn) }, { Text(mn) }) }
                            }
                        }
                        if (p.id == "ollama") {
                            val scope = rememberCoroutineScope()
                            var installed by remember { mutableStateOf<List<String>?>(null) }
                            var status by remember { mutableStateOf("") }
                            var busy by remember { mutableStateOf(false) }
                            val http = remember { okhttp3.OkHttpClient.Builder().connectTimeout(4, java.util.concurrent.TimeUnit.SECONDS).readTimeout(0, java.util.concurrent.TimeUnit.SECONDS).build() }
                            fun refresh() { scope.launch(kotlinx.coroutines.Dispatchers.IO) { installed = Providers.ollamaModels(http); if (installed == null) status = tr("Ollama is not reachable at that address.", "Ollama bu adreste yok.") } }
                            LaunchedEffect(Unit) { refresh() }
                            Text(tr("Installed models (tap to use)", "Yüklü modeller (kullanmak için dokun)"), style = MaterialTheme.typography.bodyLarge)
                            val inst = installed
                            if (inst != null && inst.isEmpty()) Hint(tr("Nothing installed yet. Pick one below and download it.", "Henüz yüklü model yok. Aşağıdan seçip indir."))
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                inst.orEmpty().forEach { mn -> FilterChip(m == mn, { m = mn; Providers.set(p.id, "model", mn) }, { Text(mn) }) }
                            }
                            Button({
                                busy = true; status = tr("Starting…", "Başlıyor…")
                                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    val err = Providers.ollamaPull(http, m) { status = it }
                                    status = err ?: tr("Downloaded: $m", "İndirildi: $m"); busy = false; refresh()
                                }
                            }, enabled = !busy && m.isNotBlank()) { Text(tr("Download $m", "$m indir")) }
                            if (status.isNotEmpty()) Hint(status)
                            Hint(tr("Ollama must be running: in Termux/Ubuntu install it and run: ollama serve. Small models (1–4B) fit a phone; bigger ones belong on a computer: set its address above.", "Ollama çalışıyor olmalı: Termux/Ubuntu'da kur ve şunu çalıştır: ollama serve. Küçük modeller (1–4B) telefona sığar; büyükleri bilgisayarda çalıştırıp adresini yukarıya yaz."))
                        }
                        if (p.keyUrl.isNotEmpty()) TextButton({ ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(p.keyUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text(tr("Get a key", "Anahtar al")) }
                    }
                    Hint(tr("Other AIs only chat: they cannot touch files or run commands on your phone. The key stays on this phone and goes only to that provider. The mascot changes colour so you can see who is answering.", "Diğer yapay zekâlar sadece sohbet eder: telefonunda dosyaya dokunamaz, komut çalıştıramaz. Anahtar bu telefonda kalır ve sadece o sağlayıcıya gider. Kimin cevap verdiğini görmen için maskot renk değiştirir."))
                }
                Text(tr("Mascot", "Maskot"), style = MaterialTheme.typography.bodyLarge)
                CharacterPicker()
                if (prov != "claude") {
                    SwitchRow(R.string.agent_on, R.string.agent_on_sub, Prefs.agentTools.flow.collectAsState().value) { Prefs.agentTools.value = it }
                    if (Prefs.agentTools.flow.collectAsState().value) {
                        OutlinedTextField(Prefs.agentDir.value, { Prefs.agentDir.value = it }, Modifier.fillMaxWidth(), label = { Text(tr("Folder the AI may work in", "Yapay zekânın çalışabileceği klasör")) }, singleLine = true)
                        SwitchRow(R.string.agent_root, R.string.agent_root_sub, Prefs.agentRoot.flow.collectAsState().value) { Prefs.agentRoot.value = it }
                        if (!android.os.Environment.isExternalStorageManager()) {
                            Hint(tr("Files on shared storage need 'All files access'.", "Ortak depolamadaki dosyalar için 'Tüm dosyalara erişim' gerekir."))
                            Button({ ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text(tr("Allow all files", "Tüm dosyalara izin ver")) }
                        }
                        Hint(tr("It works inside this folder only, with no bridge: reading and searching are free; writing asks you in the default mode, commands always ask unless the mode is 'bypass'. Plan mode is read-only.", "Sadece bu klasörde çalışır, köprü gerekmez: okuma ve arama serbest; yazma varsayılan modda sorar, komutlar 'bypass' modu değilse her zaman sorar. Plan modu salt okunurdur."))
                    }
                }
                SwitchRow(R.string.studio_on, R.string.studio_on_sub, Prefs.studio.flow.collectAsState().value) { Prefs.studio.value = it }
                OutlinedButton({ ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://claude.ai/code")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }, Modifier.fillMaxWidth()) { Text(tr("Computer sessions (Remote Control)", "Bilgisayardaki oturumlar (Remote Control)")) }
                Hint(tr("On the computer run: claude remote-control. The session then shows up there and you can watch and steer it from the phone.", "Bilgisayarda şunu çalıştır: claude remote-control. Oturum orada görünür; telefondan izleyip yönlendirebilirsin."))
            }
            if (sc == "alive") Section(R.string.sec_keep) {
                Button(onSetup, Modifier.fillMaxWidth()) { Text(tr("Setup wizard (Termux, Ubuntu, Claude)", "Kurulum sihirbazı (Termux, Ubuntu, Claude)")) }
                if (Build.VERSION.SDK_INT >= 31) SwitchRow(R.string.dynamic_color, null, dyn) { Prefs.dynamic.value = it }
                SwitchRow(R.string.screen_on, null, screenOn) { Prefs.screenOn.value = it }
                SwitchRow(R.string.keep_service, null, keep) { Prefs.keepAlive.value = it; if (it) KeepAliveService.start(ctx) }
                Button(onGuide) { Text(stringResource(R.string.guide_title)) }
            }
        }
        }
        }
    }
}

@Composable
fun GuideScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val tick = rememberTick()
    val exempt = remember(tick) { ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName) }
    var msg by remember { mutableStateOf<Int?>(null) }

    Page(R.string.guide_title, onBack) {
        Hint(stringResource(R.string.guide_intro))
        Section(R.string.g_battery_title) {
            Hint(stringResource(R.string.g_battery_body))
            Button({
                if (!exempt) ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
            }, enabled = !exempt) { Text(stringResource(if (exempt) R.string.g_exempt_done else R.string.g_exempt_self)) }
            OutlinedButton({ ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }) { Text(stringResource(R.string.g_open_battery)) }
        }
        Section(R.string.g_wake_title) {
            Hint(stringResource(R.string.g_wake_body))
            Button({ msg = Termux.wakeLock(ctx) ?: R.string.g_wake_sent }) { Text(stringResource(R.string.g_run_wake)) }
            msg?.let { Hint(stringResource(it)) }
        }
        Section(R.string.g_overlay_title) { Hint(stringResource(R.string.g_overlay_body)) }
        Section(R.string.g_root_title) {
            Hint(stringResource(R.string.g_root_body))
            Button({ msg = Termux.rootTweaks(ctx) ?: R.string.g_root_sent }) { Text(stringResource(R.string.g_root_apply)) }
            OutlinedButton({ msg = Termux.rootDozeOff(ctx) ?: R.string.g_root_sent }) { Text(stringResource(R.string.g_root_doze)) }
        }
        if (Build.VERSION.SDK_INT >= 31) Section(R.string.g_phantom_title) {
            Hint(stringResource(R.string.g_phantom_body))
            Button({ ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }) { Text(stringResource(R.string.g_devopt)) }
            Hint(stringResource(R.string.g_phantom_adb))
            CodeLine("adb shell \"/system/bin/device_config set_sync_disabled_for_tests persistent\"")
            CodeLine("adb shell \"/system/bin/device_config put activity_manager max_phantom_processes 2147483647\"")
            CodeLine("adb shell settings put global settings_enable_monitor_phantom_procs false")
        }
    }
}

/** One entry of the settings menu: tap to open that category; the description doubles as the search text. */
private class SettingsCat(val key: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val title: String, val sub: String, val group: String, val keywords: String)

/** The clock fonts shown as what they look like (the time written in each font), not as names. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FontPreviews(current: String, onPick: (String) -> Unit) {
    val ctx = LocalContext.current
    val keys = listOf("outfit", "orbitron", "grotesk", "audiowide", "rajdhani", "chakra", "exo2i", "bungee", "sharetech", "majormono", "michroma", "regular", "mono")
    val fonts = remember { keys.associateWith { androidx.compose.ui.text.font.FontFamily(clockFont(ctx, it)) } }
    val cs = MaterialTheme.colorScheme
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.forEach { k ->
            val on = current == k
            Box(
                Modifier.width(104.dp).height(56.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black)
                    .border(if (on) 2.dp else 1.dp, if (on) cs.primary else cs.outlineVariant, RoundedCornerShape(12.dp))
                    .clickable { onPick(k) },
                contentAlignment = Alignment.Center
            ) { Text("09:05", color = Color.White, fontSize = 22.sp, fontFamily = fonts[k], maxLines = 1) }
        }
    }
}


/** Every model a provider offers, searchable; tap one to use it. "Free only" shows when the provider says which are free (OpenRouter). */
@Composable
private fun ModelBrowser(p: Providers.P, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val http = remember { okhttp3.OkHttpClient.Builder().connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS).readTimeout(20, java.util.concurrent.TimeUnit.SECONDS).build() }
    var list by remember { mutableStateOf<List<Pair<String, Boolean>>?>(null) }
    var err by remember { mutableStateOf("") }
    var q by remember { mutableStateOf("") }
    var freeOnly by remember { mutableStateOf(false) }
    LaunchedEffect(p.id) { list = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Providers.listModels(p, http) { err = it } } }
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onDismiss) { Text(tr("Close", "Kapat")) } },
        title = { Text(p.name) },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text(tr("Search models", "Model ara")) })
                val all = list
                if (all != null && all.any { it.second }) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Free only", "Sadece ücretsiz"), Modifier.weight(1f)); Switch(freeOnly, { freeOnly = it })
                }
                when {
                    all == null && err.isEmpty() -> Text(tr("Loading…", "Yükleniyor…"), Modifier.padding(top = 12.dp))
                    all == null -> Text(err, Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.error)
                    else -> {
                        val shown = all.filter { (!freeOnly || it.second) && it.first.contains(q, true) }
                        Text("${shown.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 360.dp)) {
                            items(shown.size) { i ->
                                val (id, free) = shown[i]
                                Row(Modifier.fillMaxWidth().clickable { onPick(id) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(id, Modifier.weight(1f), fontSize = 14.sp)
                                    if (free) Text(tr("free", "ücretsiz"), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        })
}

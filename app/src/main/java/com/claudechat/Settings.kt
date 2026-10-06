package com.claudechat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
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
fun SettingsScreen(onBack: () -> Unit, onGuide: () -> Unit, onLang: () -> Unit, onSetup: () -> Unit = {}) {
    val ctx = LocalContext.current
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
    var cat by rememberSaveable { mutableStateOf("conn") }
    Page(R.string.settings, onBack) {
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("conn" to tr("Connection", "Bağlantı"), "look" to tr("Look", "Görünüm"), "overlay" to tr("Overlay", "Overlay"), "black" to tr("Black screen", "Siyah ekran"), "claude" to "Claude", "alive" to tr("Stay alive", "Açık kalma"))) { (k, l) ->
                FilterChip(cat == k, { cat = k }, { Text(l) })
            }
        }
        if (cat == "conn") Section(R.string.sec_method) {
            val up = bridge == 200
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(if (up) statusColor(Status.Done) else statusColor(Status.Error)))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (bridge == -2) R.string.checking else if (up) R.string.bridge_up else R.string.bridge_down))
            }
            Button({
                scope.launch {
                    bridge = -2
                    // one tap does everything: grants Termux permission through root (silently ignored without root), then starts the bridge
                    withContext(Dispatchers.IO) { Termux.rootSetup() }
                    Termux.startBridge(ctx)?.let { bridge = -1; det = true; return@launch }
                    repeat(75) { kotlinx.coroutines.delay(1000); val p = Engine.ping(); if (p == 200) { bridge = p; return@launch } }
                    bridge = -1; det = true
                }
            }, Modifier.fillMaxWidth().height(52.dp)) { Text(stringResource(if (up) R.string.start_bridge else R.string.connect_now)) }
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

        if (cat == "conn") Section(R.string.sec_usage) {
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
                if (reset > 0) Hint(stringResource(R.string.lim_reset, java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(reset * 1000))))
                Spacer(Modifier.height(6.dp))
            }
        }


        if (cat == "look") Section(R.string.sec_appearance) {
            Choices(listOf("system" to R.string.theme_system, "light" to R.string.theme_light, "dark" to R.string.theme_dark, "amoled" to R.string.theme_amoled), theme) { Prefs.theme.value = it }
            Choices(listOf("en" to R.string.lang_en, "tr" to R.string.lang_tr), lang) { if (it != lang) { Prefs.lang.value = it; onLang() } }
        }

        if (cat == "overlay") Section(R.string.sec_overlay) {
            Choices(listOf("off" to R.string.ov_style_off, "pill" to R.string.ov_style_pill, "bubble" to R.string.ov_style_bubble, "line" to R.string.ov_style_line, "curtain" to R.string.ov_style_curtain), overlay) { Prefs.overlay.value = it }
            val dance by Prefs.danceMode.flow.collectAsState()
            Text(stringResource(R.string.dance_mode), style = MaterialTheme.typography.bodyLarge)
            Choices(listOf("steps" to R.string.dm_steps, "smooth" to R.string.dm_smooth), dance) { Prefs.danceMode.value = it }
            if (overlay == "pill") {
                val pColor by Prefs.pillColor.flow.collectAsState()
                val pGap by Prefs.pillGap.flow.collectAsState()
                val pOutfit by Prefs.pillOutfit.flow.collectAsState()
                var gap by remember { mutableFloatStateOf((pGap.toFloatOrNull() ?: 1f).coerceIn(0f, 8f)) }
                Text(stringResource(R.string.pill_color), style = MaterialTheme.typography.bodyLarge)
                Choices(listOf("black" to R.string.pc_black, "white" to R.string.pc_white), pColor) { Prefs.pillColor.value = it }
                Text(stringResource(R.string.pill_gap, gap.toInt()), style = MaterialTheme.typography.bodyLarge)
                Slider(gap, { gap = it }, valueRange = 0f..8f, steps = 7, onValueChangeFinished = { Prefs.pillGap.value = gap.toInt().toString() })
                Text(stringResource(R.string.pill_outfit), style = MaterialTheme.typography.bodyLarge)
                Choices(listOf("none" to R.string.po_none, "wizard" to R.string.po_wizard, "crown" to R.string.po_crown, "party" to R.string.po_party, "bow" to R.string.po_bow), pOutfit) { Prefs.pillOutfit.value = it }
            }
            if (!canOverlay && overlay != "off") {
                Hint(stringResource(R.string.overlay_perm_sub))
                Button({ ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))) }) { Text(stringResource(R.string.grant)) }
            }
        }

        if (cat == "black") Section(R.string.sec_black) {
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
                Choices(listOf("outfit" to R.string.font_outfit, "orbitron" to R.string.font_orbitron, "grotesk" to R.string.font_grotesk, "audiowide" to R.string.font_audiowide, "rajdhani" to R.string.font_rajdhani, "chakra" to R.string.font_chakra, "exo2i" to R.string.font_exo2i, "bungee" to R.string.font_bungee, "sharetech" to R.string.font_sharetech, "majormono" to R.string.font_majormono, "michroma" to R.string.font_michroma, "regular" to R.string.font_regular, "mono" to R.string.font_mono), if (font == "thin") "outfit" else font) { Prefs.blackFont.value = it }
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
            if (cat == "claude") Section(R.string.sec_claude) {
                Text(stringResource(R.string.perm_mode))
                Choices(listOf("default" to R.string.mode_default, "acceptEdits" to R.string.mode_edits, "plan" to R.string.mode_plan, "bypassPermissions" to R.string.mode_bypass), mode) { Prefs.mode.value = it }
                var pick by remember { mutableStateOf("") } // "cwd" / "attach": which field the folder picker fills
                OutlinedTextField(Prefs.cwd.value, { Prefs.cwd.value = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.workdir)) }, singleLine = true,
                    trailingIcon = { IconButton({ pick = "cwd" }) { Icon(Icons.Filled.Folder, tr("Browse", "Gözat")) } })
                OutlinedTextField(Prefs.attachDir.value, { Prefs.attachDir.value = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.attach_dir)) }, singleLine = true,
                    trailingIcon = { IconButton({ pick = "attach" }) { Icon(Icons.Filled.Folder, tr("Browse", "Gözat")) } })
                if (pick.isNotEmpty()) FolderPickerDialog(if (pick == "cwd") Prefs.cwd.value else Prefs.attachDir.value, { if (pick == "cwd") Prefs.cwd.value = it else Prefs.attachDir.value = it; pick = "" }, { pick = "" })
            }
            if (cat == "alive") Section(R.string.sec_keep) {
                Button(onSetup, Modifier.fillMaxWidth()) { Text(tr("Setup wizard (Termux, Ubuntu, Claude)", "Kurulum sihirbazı (Termux, Ubuntu, Claude)")) }
                if (Build.VERSION.SDK_INT >= 31) SwitchRow(R.string.dynamic_color, null, dyn) { Prefs.dynamic.value = it }
                SwitchRow(R.string.screen_on, null, screenOn) { Prefs.screenOn.value = it }
                SwitchRow(R.string.keep_service, null, keep) { Prefs.keepAlive.value = it; if (it) KeepAliveService.start(ctx) }
                Button(onGuide) { Text(stringResource(R.string.guide_title)) }
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

package com.claudechat

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.io.File

/** Anthropic's official Claude Code plugin directory. */
private const val MARKETPLACE = "anthropics/claude-plugins-official"
private const val MARKETPLACE_NAME = "claude-plugins-official"
private val LOG_DIR = "/sdcard/Download/projects/_logs"
private val INSTALL_LOG = File("$LOG_DIR/plugins.log")
private val INSTALLED_FILE = File("$LOG_DIR/plugins-installed.txt")
private const val CLAUDE_DIR = "/root/.claude"

private val NAME_RE = Regex("([A-Za-z0-9._-]+)@$MARKETPLACE_NAME")
private val VERSION_RE = Regex("[Vv]ersion:?\\s*v?([0-9][^\\s]*)")

/** Plugins already installed in Claude Code inside the Ubuntu distro; shown even before the list file exists. */
val LOCAL_PLUGINS = listOf("effortless", "ponytail")

data class InstalledPlugin(val name: String, val version: String)

/** Parses `claude plugin list` output (written by Termux into [INSTALLED_FILE]); falls back to [LOCAL_PLUGINS]. */
private fun readInstalled(): List<InstalledPlugin> {
    val out = mutableListOf<InstalledPlugin>()
    try {
        INSTALLED_FILE.takeIf { it.isFile }?.readLines()?.forEach { line ->
            NAME_RE.find(line)?.let { out += InstalledPlugin(it.groupValues[1], "") }
            VERSION_RE.find(line)?.let { v -> if (out.isNotEmpty()) out[out.lastIndex] = out.last().copy(version = v.groupValues[1]) }
        }
    } catch (e: Exception) { }
    if (out.isEmpty()) out += LOCAL_PLUGINS.map { InstalledPlugin(it, "") }
    return out.distinctBy { it.name }
}

/** Installed plugins; the settings lists read this and it is re-read after each Termux command. */
val installedPlugins = mutableStateOf(readInstalled())

fun refreshInstalled() { installedPlugins.value = readInstalled() }

/** Settings subtitle for a plugin entry. */
fun pluginSub(p: InstalledPlugin) = when (p.name) {
    "effortless" -> tr("Judge and effort", "Yargıç ve çaba")
    "ponytail" -> tr("Level: lite, full, ultra, off", "Seviye: lite, full, ultra, off")
    else -> "Version ${p.version.ifEmpty { "?" }}"
}

/** Runs a shell command inside the Ubuntu distro, where Claude Code and its plugins live. */
private fun inDistro(cmd: String) = "${Termux.BIN}/proot-distro login ${Termux.distroName()} -- bash -lc '" + cmd.replace("'", "'\\''") + "'"

/** Runs [cmd] in Termux with its output in [logFile]; the exit code is written last so the app can tell success from failure. */
fun runInTermux(ctx: Context, cmd: String, logFile: File, label: String): String {
    val script = "mkdir -p $LOG_DIR; : > ${logFile.path}; ( $cmd ) 2>&1 | tee -a ${logFile.path}; echo \"--- exit \${PIPESTATUS[0]}\" >> ${logFile.path}"
    return if (Termux.run(ctx, script) == null) "$label started in Termux." else "Termux is not ready: open Termux once, then try again."
}

private fun logText() = try { INSTALL_LOG.takeIf { it.isFile }?.readText().orEmpty() } catch (e: Exception) { "" }

/** Waits for the running Termux command to end, re-reading the installed list meanwhile. */
private suspend fun waitForTermux() {
    for (i in 0 until 40) {
        delay(1500)
        refreshInstalled()
        if (logText().contains("--- exit")) break
    }
}

@Composable
private fun ChoiceRow(options: List<String>, selected: String, onPick: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { o ->
            val pick = { onPick(o) }
            if (o == selected) Button(pick, Modifier.weight(1f)) { Text(o) }
            else OutlinedButton(pick, Modifier.weight(1f)) { Text(o) }
        }
    }
}

@Composable
fun EffortlessSettings() {
    val ctx = LocalContext.current
    val judge by Prefs.effortJudge.flow.collectAsState()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("effortless", style = MaterialTheme.typography.titleMedium)
        Text(tr("Judge: who picks the effort for each prompt. auto uses Haiku, and Jev too when a key is set.",
            "Yargıç: her istemin çabasını kim seçer. auto Haiku'yu kullanır, anahtar varsa Jev de devreye girer."), style = MaterialTheme.typography.bodySmall)
        ChoiceRow(listOf("auto", "haiku", "jev"), judge) { v ->
            Prefs.effortJudge.value = v
            Termux.run(ctx, inDistro("python3 -c 'import json,os;p=\"$CLAUDE_DIR/settings.json\";d=json.load(open(p)) if os.path.exists(p) else {};d[\"effortless.judge\"]=\"$v\";json.dump(d,open(p,\"w\"),indent=2)'"))
        }
    }
}

@Composable
fun PonytailSettings() {
    val ctx = LocalContext.current
    val level by Prefs.ponytailLevel.flow.collectAsState()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("ponytail", style = MaterialTheme.typography.titleMedium)
        Text(tr("How lazy the code gets: lite, full (default), ultra, or off.",
            "Kod ne kadar tembel olsun: lite, full (varsayılan), ultra ya da off."), style = MaterialTheme.typography.bodySmall)
        ChoiceRow(listOf("lite", "full", "ultra", "off"), level) { v ->
            Prefs.ponytailLevel.value = v
            val flag = "$CLAUDE_DIR/.ponytail-active"
            Termux.run(ctx, inDistro(if (v == "off") "rm -f $flag" else "mkdir -p $CLAUDE_DIR && echo $v > $flag"))
        }
    }
}

/** Icon button that updates one plugin in Termux, then re-reads its installed version. */
@Composable
private fun UpdateButton(name: String) {
    val ctx = LocalContext.current
    var started by remember { mutableIntStateOf(0) }
    LaunchedEffect(started) { if (started > 0) waitForTermux() }
    IconButton({
        val msg = runInTermux(ctx, "claude plugin marketplace update $MARKETPLACE_NAME; claude plugin update $name@$MARKETPLACE_NAME; claude plugin list > ${INSTALLED_FILE.path}", INSTALL_LOG, "Updating $name")
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
        started++
    }) { Icon(Icons.Filled.SystemUpdate, tr("Update", "Güncelle")) }
}

/** Settings page of one installed plugin: its own panel (if any) and the version with an Update icon. */
@Composable
fun PluginPage(name: String) {
    val version = installedPlugins.value.firstOrNull { it.name == name }?.version.orEmpty()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (name) {
            "effortless" -> EffortlessSettings()
            "ponytail" -> PonytailSettings()
            else -> Text(name, style = MaterialTheme.typography.titleMedium)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Version", "Sürüm") + ": " + version.ifEmpty { "-" }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            UpdateButton(name)
        }
    }
}

/**
 * Plugin store: lists only the plugins installed in Claude Code inside Termux, with an Update icon on each.
 * Every command runs in Termux.
 */
@Composable
fun PluginStore() {
    val ctx = LocalContext.current
    val installed by installedPlugins
    var log by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    fun readFiles() {
        refreshInstalled()
        log = try { INSTALL_LOG.takeIf { it.isFile }?.readLines()?.takeLast(14)?.joinToString("\n").orEmpty() } catch (e: Exception) { "" }
    }

    fun start(cmd: String, label: String) { note = runInTermux(ctx, cmd, INSTALL_LOG, label) }

    LaunchedEffect(note) {
        // give the command a moment, then read what it printed
        if (note.endsWith("started in Termux.")) { for (i in 0 until 20) { delay(1500); readFiles(); if (log.contains("--- exit")) break } }
    }

    // no scroll of its own: the Settings page already scrolls, and a nested scroll crashes
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Plugin store", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton({ start("claude plugin list > ${INSTALLED_FILE.path}", "Checking installed plugins") }) { Icon(Icons.Filled.Refresh, tr("Refresh", "Yenile")) }
            IconButton({ start("claude plugin marketplace add $MARKETPLACE", "Adding the marketplace") }) { Icon(Icons.Filled.Add, tr("Add marketplace", "Mağazayı ekle")) }
            IconButton({ readFiles() }) { Icon(Icons.Filled.Description, tr("Show the last output", "Son çıktıyı göster")) }
        }
        if (note.isNotEmpty()) Text(note, style = MaterialTheme.typography.bodySmall)
        if (log.isNotEmpty()) Text(log, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        if (installed.isEmpty()) Text(tr("No plugins found yet. Tap refresh.", "Henüz eklenti yok. Yenile'ye dokunun."), style = MaterialTheme.typography.bodySmall)
        installed.forEach { p ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(p.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(p.version, style = MaterialTheme.typography.labelMedium)
                UpdateButton(p.name)
            }
        }
    }
}

package com.claudechat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/** Anthropic's official Claude Code plugin directory. */
private const val MARKETPLACE = "anthropics/claude-plugins-official"
private const val MARKETPLACE_NAME = "claude-plugins-official"
private const val MARKETPLACE_JSON = "https://raw.githubusercontent.com/anthropics/claude-plugins-official/main/.claude-plugin/marketplace.json"
private val LOG_DIR = "/sdcard/Download/projects/_logs"
private val INSTALL_LOG = File("$LOG_DIR/plugins.log")
private val INSTALLED_FILE = File("$LOG_DIR/plugins-installed.txt")

/** Plugins already installed in Claude Code inside the Ubuntu distro; listed here without the store. */
val LOCAL_PLUGINS = listOf("effortless", "ponytail")
private const val CLAUDE_DIR = "/root/.claude"

/** Runs a shell command inside the Ubuntu distro, where Claude Code and its plugins live. */
private fun inDistro(cmd: String) = "${Termux.BIN}/proot-distro login ${Termux.distroName()} -- bash -lc '" + cmd.replace("'", "'\\''") + "'"

private class PluginEntry(val name: String, val description: String)

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

/**
 * Settings: the official marketplace, its plugins, and the plugins already installed in Termux
 * (read from `claude plugin list`, which Termux writes to a shared file). Every command runs in Termux.
 */
@Composable
fun PluginStore() {
    val ctx = LocalContext.current
    var plugins by remember { mutableStateOf<List<PluginEntry>?>(null) }
    var installedText by remember { mutableStateOf("") }
    var log by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            val text = withContext(Dispatchers.IO) { java.net.URL(MARKETPLACE_JSON).openStream().bufferedReader().use { it.readText() } }
            val arr = JSONObject(text).getJSONArray("plugins")
            plugins = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                PluginEntry(o.optString("name"), o.optString("description").take(160))
            }.filter { it.name.isNotEmpty() }
        } catch (e: Exception) {
            error = "Could not read the plugin list: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    fun readFiles() {
        installedText = try { INSTALLED_FILE.takeIf { it.isFile }?.readLines()?.takeLast(80)?.joinToString("\n").orEmpty() } catch (e: Exception) { "" }
        log = try { INSTALL_LOG.takeIf { it.isFile }?.readLines()?.takeLast(14)?.joinToString("\n").orEmpty() } catch (e: Exception) { "" }
    }

    fun runInTermux(cmd: String, logFile: File, label: String) {
        // each run starts a fresh log; the exit code is written at the end so the app can tell success from failure
        val script = "mkdir -p $LOG_DIR; : > ${logFile.path}; ( $cmd ) 2>&1 | tee -a ${logFile.path}; echo \"--- exit \${PIPESTATUS[0]}\" >> ${logFile.path}"
        val err = Termux.run(ctx, script)
        note = if (err == null) "$label started in Termux." else "Termux is not ready: open Termux once, then try again."
    }

    LaunchedEffect(note) {
        // give the command a moment, then read what it printed
        if (note.endsWith("started in Termux.")) { for (i in 0 until 20) { delay(1500); readFiles(); if (log.contains("--- exit")) break } }
    }

    // no scroll of its own: the Settings page already scrolls, and a nested scroll crashes
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Plugin store", style = MaterialTheme.typography.titleMedium)
        Text("Adds Anthropic's official Claude Code plugins to Claude Code in Termux, and shows which ones are installed.",
            style = MaterialTheme.typography.bodySmall)
        Button({ runInTermux("claude plugin marketplace add $MARKETPLACE", INSTALL_LOG, "Adding the marketplace") }, Modifier.fillMaxWidth()) { Text("Add the official marketplace") }
        OutlinedButton({
            runInTermux("claude plugin list", INSTALLED_FILE, "Checking installed plugins")
        }, Modifier.fillMaxWidth()) { Text("Refresh installed plugins") }
        OutlinedButton({ readFiles() }, Modifier.fillMaxWidth()) { Text("Show the last output") }
        if (note.isNotEmpty()) Text(note, style = MaterialTheme.typography.bodySmall)
        if (log.isNotEmpty()) Text(log, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        if (installedText.isNotEmpty()) {
            Text("Installed (from claude plugin list)", style = MaterialTheme.typography.titleSmall)
            Text(installedText, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        LOCAL_PLUGINS.forEach { n ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(n, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("Installed", style = MaterialTheme.typography.labelMedium)
            }
        }
        plugins?.filter { it.name !in LOCAL_PLUGINS }?.take(60)?.forEach { p ->
            val installed = installedText.contains(p.name)
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    if (installed) Text("Installed", style = MaterialTheme.typography.labelMedium)
                    else OutlinedButton({ runInTermux("claude plugin install ${p.name}@$MARKETPLACE_NAME", INSTALL_LOG, "Installing ${p.name}") }) { Text("Install") }
                }
                Text(p.description, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

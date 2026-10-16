package com.claudechat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** What the app keeps on the phone, split by folder, so the user can see where the space goes. */
object Storage {
    class Row(val label: String, val bytes: Long, val path: String)

    private fun size(f: File): Long = when {
        !f.exists() -> 0L
        f.isFile -> f.length()
        else -> f.walkTopDown().filter { it.isFile }.sumOf { runCatching { it.length() }.getOrDefault(0L) }
    }

    /** Folders inside the built-in Linux that are big in practice; the rest of it is shown as "other". */
    fun breakdown(ctx: android.content.Context): List<Row> {
        val root = Linux.root()
        val rows = mutableListOf<Row>()
        val parts = listOf(
            Triple(tr("Claude Code", "Claude Code"), "usr/local/lib/node_modules/@anthropic-ai", "usr/local/lib/node_modules/@anthropic-ai"),
            Triple(tr("npm cache", "npm önbelleği"), "root/.npm", "root/.npm"),
            Triple(tr("Atomic Agent", "Atomic Agent"), "opt/atomic", "opt/atomic"),
            Triple(tr("Atomic download", "Atomic indirmesi"), "root/.atomic-dl", "root/.atomic-dl"),
            Triple(tr("Ollama models", "Ollama modelleri"), "root/.ollama", "root/.ollama"),
            Triple(tr("Ollama install", "Ollama kurulumu"), "usr/local/lib/ollama", "usr/local/lib/ollama"),
            Triple(tr("apt packages", "apt paketleri"), "var/cache/apt", "var/cache/apt"),
        )
        var counted = 0L
        for ((label, rel, p) in parts) {
            val b = size(File(root, p)); counted += b
            if (b > 0) rows += Row(label, b, rel)
        }
        val linux = size(root)
        if (linux - counted > 0) rows += Row(tr("Built-in Linux, other", "Yerleşik Linux, diğer"), linux - counted, "linux/ubuntu")
        rows += Row(tr("Chats", "Sohbetler"), size(File(ctx.filesDir, "chats")), "files/chats")
        rows += Row(tr("App cache (downloads, APKs)", "Uygulama önbelleği (indirmeler, APK)"), size(ctx.cacheDir), "cache")
        return rows.filter { it.bytes > 0 }.sortedByDescending { it.bytes }
    }

    /** Deletes only re-downloadable things: the caches and the Atomic download. Models and installs stay. */
    fun clearCaches(ctx: android.content.Context) {
        ctx.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        listOf("root/.npm", "root/.atomic-dl", "var/cache/apt/archives").forEach { File(Linux.root(), it).deleteRecursively() }
    }

    fun human(b: Long): String = when {
        b >= 1L shl 30 -> String.format("%.2f GB", b / (1024.0 * 1024 * 1024))
        b >= 1L shl 20 -> String.format("%.1f MB", b / (1024.0 * 1024))
        b >= 1L shl 10 -> String.format("%.0f KB", b / 1024.0)
        else -> "$b B"
    }
}

/** A card in Settings > Usage: sizes per folder, refresh, and a clear-caches button. */
@Composable
fun StorageCard() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<Storage.Row>?>(null) }
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(tick) { rows = withContext(Dispatchers.IO) { Storage.breakdown(ctx) } }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(tr("Storage used by the app", "Uygulamanın kullandığı alan"), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        val list = rows
        if (list == null) Text(tr("Counting…", "Sayılıyor…"), style = MaterialTheme.typography.bodySmall)
        else {
            val total = list.sumOf { it.bytes }
            Text(tr("Total ${Storage.human(total)}", "Toplam ${Storage.human(total)}"), style = MaterialTheme.typography.bodyMedium)
            list.forEach { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(r.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(Storage.human(r.bytes), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ rows = null; tick++ }) { Text(tr("Recount", "Yeniden say")) }
            OutlinedButton({
                scope.launch {
                    withContext(Dispatchers.IO) { Storage.clearCaches(ctx) }
                    rows = null; tick++
                }
            }) { Text(tr("Clear caches", "Önbellekleri temizle")) }
        }
    }
}

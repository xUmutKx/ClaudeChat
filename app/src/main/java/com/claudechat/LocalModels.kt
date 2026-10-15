package com.claudechat

import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/**
 * Small ("lite") models for the local agent. Tags and download sizes are from ollama.com/library (2026-10).
 * [tools] = the model template supports tool calling, which the file/command harness needs; Gemma 3 and the others here without it only chat.
 */
object LocalModels {
    class M(val tag: String, val name: String, val mb: Int, val tools: Boolean, val logo: Int)

    val all = listOf(
        M("qwen3:0.6b", "Qwen3 0.6B", 523, true, R.drawable.ic_logo_qwen),
        M("qwen3:1.7b", "Qwen3 1.7B", 1434, true, R.drawable.ic_logo_qwen),
        M("qwen3:4b", "Qwen3 4B", 2560, true, R.drawable.ic_logo_qwen),
        M("qwen2.5:0.5b", "Qwen2.5 0.5B", 398, true, R.drawable.ic_logo_qwen),
        M("qwen2.5:1.5b", "Qwen2.5 1.5B", 986, true, R.drawable.ic_logo_qwen),
        M("qwen2.5:3b", "Qwen2.5 3B", 1946, true, R.drawable.ic_logo_qwen),
        M("llama3.2:1b", "Llama 3.2 1B", 1331, true, R.drawable.ic_logo_llama),
        M("llama3.2:3b", "Llama 3.2 3B", 2048, true, R.drawable.ic_logo_llama),
        M("gemma3:1b", "Gemma 3 1B", 806, false, R.drawable.ic_logo_gemma),
        M("gemma3:4b", "Gemma 3 4B", 3482, false, R.drawable.ic_logo_gemma),
    )

    fun byTag(t: String) = all.firstOrNull { it.tag == t }
    fun size(m: M) = if (m.mb >= 1000) "%.1f GB".format(m.mb / 1024.0) else "${m.mb} MB"

    fun totalRamMb(c: Context): Int {
        val i = ActivityManager.MemoryInfo(); (c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(i)
        return (i.totalMem shr 20).toInt()
    }
    /** Weights plus the context cache need about twice the download size; the phone itself needs the rest. */
    fun fits(m: M, ramMb: Int) = ramMb >= m.mb * 2.2
    /** The biggest model that can use tools and still fits this phone. */
    fun recommended(ramMb: Int) = all.filter { it.tools && fits(it, ramMb) }.maxByOrNull { it.mb }
}

/** Cards for the local models: logo, name, size, whether it fits this phone and whether it can use tools; download or pick. [model] is the one in use. */
@Composable
fun LocalModelCards(model: String, installed: List<String>?, status: String, pulling: String, onPick: (String) -> Unit, onPull: (String) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val ram = remember { LocalModels.totalRamMb(ctx) }
    val rec = remember { LocalModels.recommended(ram) }
    Text(tr("Light models for this phone (${ram / 1024 + 1} GB RAM)", "Bu telefon için hafif modeller (${ram / 1024 + 1} GB RAM)"), style = MaterialTheme.typography.bodyLarge)
    LocalModels.all.forEach { m ->
        val have = installed?.any { it == m.tag || it == m.tag + ":latest" } == true
        val fits = LocalModels.fits(m, ram)
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(painterResource(m.logo), null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurface)  // logos are white vectors: untinted they vanish on light cards
                Column(Modifier.weight(1f)) {
                    Text(m.name, style = MaterialTheme.typography.bodyLarge)
                    Text(LocalModels.size(m) + " · " + (if (m.tools) tr("tools", "araç") else tr("chat only, no tools", "sadece sohbet, araç yok")) +
                        " · " + (if (fits) tr("fits", "sığar") else tr("too big for this phone", "bu telefona büyük")) + (if (m == rec) " · " + tr("recommended", "önerilen") else ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (pulling == m.tag && status.isNotEmpty()) Text(status, style = MaterialTheme.typography.labelSmall)
                }
                if (have) { if (model == m.tag) Text(tr("In use", "Kullanımda"), style = MaterialTheme.typography.labelMedium) else OutlinedButton({ onPick(m.tag) }) { Text(tr("Use", "Kullan")) } }
                else OutlinedButton({ onPull(m.tag) }, enabled = pulling.isEmpty()) { Text(tr("Download", "İndir")) }
            }
        }
    }
    Text(tr("Only 'tools' models can read files and run commands in the agent. Gemma 3 has no tool calling in Ollama: it answers but cannot act.", "Dosya okuyup komut çalıştırabilmek için 'araç' işaretli model gerekir. Gemma 3 Ollama'da araç çağırmayı desteklemez: cevap verir ama iş yapamaz."),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** DeepSeek build: set up the app's own Ubuntu so the Bash tool has a real shell without Termux. */
@Composable
fun BashLinuxControl() {
    if (!App.dsOnly || !Linux.supported()) return
    val busy by Linux.busy.collectAsState()
    val status by Linux.status.collectAsState()
    val ready = remember(busy) { Linux.ready() }
    if (ready) { Text(tr("Bash runs in the built-in Linux.", "Bash yerleşik Linux'ta çalışır."), style = MaterialTheme.typography.bodySmall); return }
    OutlinedButton({ linuxJob(tr("Starting…", "Başlıyor…")) { Linux.install() } }, Modifier.fillMaxWidth(), enabled = !busy) { Text(tr("Set up Linux for Bash (about 250 MB)", "Bash için Linux kur (yaklaşık 250 MB)")) }
    if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
}

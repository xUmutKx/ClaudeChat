package com.claudechat

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun linuxJob(label: String, block: () -> String?) {
    if (Linux.busy.value) return
    Linux.busy.value = true; Linux.status.value = label
    Thread {
        val err = try { block() } catch (e: Exception) { e.message ?: "error" }
        Linux.status.value = err ?: tr("Done", "Bitti"); Linux.busy.value = false
    }.start()
}

private fun startClaude(ctx: Context) {
    Prefs.builtinLinux.value = true
    Termux.startBridge(ctx.applicationContext as android.app.Application, kill = true)
}

/** Connection screen, arm64 phones: one button sets up the app's own Ubuntu with Claude Code, or restarts it. [onState] gets the ping result. */
@Composable
fun LinuxSection(onState: (Int) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val busy by Linux.busy.collectAsState()
    val status by Linux.status.collectAsState()
    val log by Linux.log.collectAsState()
    val ready = remember(busy) { Linux.ready() }
    Button({
        if (!ready) linuxJob(tr("Starting…", "Başlıyor…")) { Linux.install()?.also { return@linuxJob it }; startClaude(ctx); null }
        else startClaude(ctx)
        scope.launch { onState(-2); repeat(90) { delay(1000); val p = Engine.ping(); if (p == 200) { onState(p); return@launch } }; onState(-1) }
    }, Modifier.fillMaxWidth().height(52.dp), enabled = !busy) {
        Text(if (!ready) tr("Set up (about 400 MB)", "Kur (yaklaşık 400 MB)") else tr("Start / restart", "Başlat / yeniden başlat"))
    }
    if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
    if (busy) Text(log.takeLast(400), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Installs Atomic Agent or Ollama into the built-in Linux and starts it. Cancel with [Linux.cancel]; the provider selection is untouched either way. */
fun installService(id: String) {
    if (Linux.busy.value) return
    Linux.installing.value = id
    linuxJob(tr("Installing…", "Kuruluyor…")) {
        try {
            val atomic = id == "atomic"
            val c = Linux.exec(if (atomic) Linux.ATOMIC_INSTALL else Linux.OLLAMA_INSTALL)
            if (c != 0) return@linuxJob tr("Not installed (exit $c)", "Kurulmadı (çıkış $c)")
            if (atomic) {
                val key = Providers.field("atomic", "key").ifEmpty { java.util.UUID.randomUUID().toString() }
                Providers.set("atomic", "key", key); Linux.service("atomic", Linux.atomicServe(key))
            } else Linux.service("ollama", Linux.OLLAMA_SERVE)
            null
        } finally { Linux.installing.value = "" }
    }
}

fun removeService(id: String) {
    Linux.stop(id)
    Linux.installing.value = id
    linuxJob(tr("Removing…", "Kaldırılıyor…")) {
        try { Linux.exec(if (id == "atomic") Linux.ATOMIC_REMOVE else Linux.OLLAMA_REMOVE); null } finally { Linux.installing.value = "" }
    }
}

/** Install and start buttons for Atomic Agent or Ollama inside the built-in Linux, shown under their provider cards. */
@Composable
fun LinuxServiceControl(id: String) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val busy by Linux.busy.collectAsState()
    val status by Linux.status.collectAsState()
    var tick by remember { mutableIntStateOf(0) }
    if (!Linux.supported() || App.dsOnly) return
    if (!Linux.ready()) { Text(tr("Set up Claude Code first (Connection).", "Önce Claude Code'u kur (Bağlantı)."), style = MaterialTheme.typography.bodySmall); return }
    val atomic = id == "atomic"
    val running = remember(tick, busy) { Linux.running(id) }
    val installed = remember(tick, busy) { Linux.exec("command -v ${if (atomic) "atomic-agent" else "ollama"} >/dev/null") == 0 }
    val mine = Linux.installing.collectAsState().value == id
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (busy && mine) OutlinedButton({ Linux.cancel() }) { Text(tr("Cancel", "İptal")) }
        else if (!installed) Button({ installService(id) }, enabled = !busy) { Text(tr("Install", "Kur")) }
        else OutlinedButton({ removeService(id); tick++ }, enabled = !busy) { Text(tr("Remove", "Kaldır")) }
        Button({
            if (running) Linux.stop(id) else if (atomic) {
                val key = Providers.field("atomic", "key").ifEmpty { java.util.UUID.randomUUID().toString() }
                Providers.set("atomic", "key", key); Linux.service("atomic", Linux.atomicServe(key))
            } else Linux.service("ollama", Linux.OLLAMA_SERVE)
            tick++
        }, enabled = !busy && installed) { Text(if (running) tr("Stop", "Durdur") else tr("Start", "Başlat")) }
    }
    if (busy && mine) Text(Linux.log.collectAsState().value.trimEnd().substringAfterLast('\n').substringAfterLast('\r').takeLast(120).ifBlank { status }, style = MaterialTheme.typography.bodySmall)
    else if (status.isNotBlank() && busy) Text(status, style = MaterialTheme.typography.bodySmall)
}

/** A site's chat inside the app, cookies kept. Slots 1-3 run in their own process with their own cookie store, so a second account of the same site stays separate. */
open class WebLoginActivity : ComponentActivity() {
    open val slot = 0
    override fun attachBaseContext(b: Context) = super.attachBaseContext(b.localized())

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (slot > 0) try { WebView.setDataDirectorySuffix("web$slot") } catch (e: Exception) { }
        val url = WebAccounts.site(intent.getStringExtra("site") ?: "chatgpt").url
        setContent { ClaudeTheme {
            var wv by remember { mutableStateOf<WebView?>(null) }
            BackHandler { if (wv?.canGoBack() == true) wv?.goBack() else finish() }
            // no frame of our own: the site's phone layout fills the screen
            AndroidView({ c ->
                WebView(c).apply {
                    layoutParams = ViewGroup.LayoutParams(-1, -1)
                    settings.javaScriptEnabled = true; settings.domStorageEnabled = true
                    // the "; wv" marker makes some sites (Google sign-in) refuse the login page
                    settings.userAgentString = settings.userAgentString.replace("; wv", "")
                    CookieManager.getInstance().setAcceptCookie(true); CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = WebViewClient(); webChromeClient = WebChromeClient()
                    loadUrl(url); wv = this
                }
            }, Modifier.fillMaxSize().statusBarsPadding().imePadding())
        } }
    }

    override fun onPause() { super.onPause(); CookieManager.getInstance().flush() }
}
class WebLoginActivity1 : WebLoginActivity() { override val slot = 1 }
class WebLoginActivity2 : WebLoginActivity() { override val slot = 2 }
class WebLoginActivity3 : WebLoginActivity() { override val slot = 3 }

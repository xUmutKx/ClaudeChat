package com.claudechat

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

/** Chat with a website account (ChatGPT, Claude, Gemini, Grok) inside the app. Up to 4 accounts per site, each with its own cookies. */
object WebAccounts {
    class Site(val id: String, val name: String, val url: String, val logo: Int)
    val sites = listOf(
        Site("chatgpt", "ChatGPT", "https://chatgpt.com/", R.drawable.ic_logo_openai),
        Site("claude", "Claude", "https://claude.ai/", R.drawable.ic_logo_claude),
        Site("gemini", "Gemini", "https://gemini.google.com/", R.drawable.ic_logo_gemini),
        Site("grok", "Grok", "https://grok.com/", R.drawable.ic_logo_grok))
    fun site(id: String) = sites.firstOrNull { it.id == id } ?: sites[0]

    class Acct(val site: Site, val slot: Int) { val name get() = site.name + if (slot > 0) " ${slot + 1}" else "" }
    private val cls = listOf(WebLoginActivity::class.java, WebLoginActivity1::class.java, WebLoginActivity2::class.java, WebLoginActivity3::class.java)

    private fun raw() = try { JSONArray(Providers.field("web", "list", "[]")) } catch (e: Exception) { JSONArray() }
    fun list(): List<Acct> = (0 until raw().length()).map { raw().getJSONObject(it) }.map { Acct(site(it.optString("site")), it.optInt("slot")) }
    private fun save(l: List<Acct>) = Providers.set("web", "list", JSONArray(l.map { JSONObject().put("site", it.site.id).put("slot", it.slot) }).toString())
    /** Adds the site's next free account slot and returns it, or null when all four are taken. */
    fun add(s: Site): Acct? {
        val l = list(); val slot = (0..3).firstOrNull { n -> l.none { it.site == s && it.slot == n } } ?: return null
        return Acct(s, slot).also { save(l + it) }
    }
    fun remove(a: Acct) = save(list().filterNot { it.site == a.site && it.slot == a.slot })
    fun open(c: Context, a: Acct) = c.startActivity(Intent(c, cls[a.slot]).putExtra("site", a.site.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun providerLogo(id: String): Int? = when (id) {
    "claude" -> R.drawable.ic_logo_claude
    "openai" -> R.drawable.ic_logo_openai
    "gemini" -> R.drawable.ic_logo_gemini
    "grok" -> R.drawable.ic_logo_grok
    "deepseek", "llm7" -> R.drawable.ic_logo_deepseek
    "ollama" -> R.drawable.ic_logo_ollama
    "atomic" -> R.drawable.ic_logo_atomic
    "github" -> R.drawable.ic_logo_github
    "groq" -> R.drawable.ic_logo_groq
    "mistral" -> R.drawable.ic_logo_mistral
    "openrouter" -> R.drawable.ic_logo_openrouter
    else -> null
}

@Composable
private fun ProvCard(selected: Boolean, onClick: () -> Unit, icon: @Composable () -> Unit, title: String, status: String?, on: Boolean, trailing: @Composable () -> Unit = {}) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (selected) cs.primaryContainer else cs.surfaceVariant.copy(alpha = .45f))
        .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(cs.surface), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            if (status != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (on) Color(0xFF3FB950) else cs.outline))
                Spacer(Modifier.width(6.dp))
                Text(status, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1)
            }
        }
        if (selected) Icon(Icons.Filled.CheckCircle, null, tint = cs.primary)
        trailing()
    }
}

@Composable
private fun Logo(res: Int?, fallback: androidx.compose.ui.graphics.vector.ImageVector) {
    if (res != null) Icon(painterResource(res), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface)
    else Icon(fallback, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface)
}

/** Provider cards grouped by kind, each with its logo and a connected / not connected line, plus the website accounts. Tapping a card selects it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProviderCards(selected: String) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val cfg by Prefs.provCfg.flow.collectAsState()
    var claudeUp by remember { mutableStateOf(false) }
    var ghIn by remember { mutableStateOf(false) }
    var ollamaUp by remember { mutableStateOf(false) }
    val busy by Linux.busy.collectAsState()
    val installing by Linux.installing.collectAsState()
    val lstatus by Linux.status.collectAsState()
    var have by remember { mutableStateOf(mapOf("atomic" to true, "ollama" to true)) }
    var ask by remember { mutableStateOf<String?>(null) }
    val canManage = Linux.supported() && !App.dsOnly && Linux.ready()
    LaunchedEffect(cfg, busy) {
        if (canManage && !busy) have = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            mapOf("atomic" to (Linux.exec("command -v atomic-agent >/dev/null") == 0), "ollama" to (Linux.exec("command -v ollama >/dev/null") == 0)) }
        claudeUp = Engine.ping() == 200
        if (!App.dsOnly) {
            ghIn = Engine.authStatus()?.ghIn == true
            ollamaUp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                Providers.ollamaModels(okhttp3.OkHttpClient.Builder().connectTimeout(1500, java.util.concurrent.TimeUnit.MILLISECONDS).build()) != null }
        }
    }
    fun connected(p: Providers.P): Boolean = when (p.id) {
        "claude" -> claudeUp
        "github" -> ghIn
        "ollama" -> ollamaUp
        "atomic" -> Linux.running("atomic")
        "custom" -> Providers.base(p).isNotEmpty()
        else -> !p.needsKey || Providers.key(p.id).isNotEmpty()
    }
    val groups = listOf(
        tr("Claude Code", "Claude Code") to listOf("claude"),
        tr("Free", "Ücretsiz") to listOf("pollinations", "llm7", "github"),
        tr("API key", "API anahtarı") to listOf("openai", "gemini", "grok", "deepseek", "groq", "openrouter", "mistral"),
        tr("Other AI", "Diğer yapay zekâ") to listOf("atomic", "ollama", "custom"))
    ask?.let { id ->
        AlertDialog({ ask = null }, title = { Text(Providers.byId(id).name) },
            text = { Text(tr("Not installed yet. Download it now?", "Henüz kurulu değil. Şimdi indirilsin mi?")) },
            confirmButton = { TextButton({ ask = null; installService(id) }) { Text(tr("Download", "İndir")) } },
            dismissButton = { TextButton({ ask = null }) { Text(tr("Not now", "Şimdi değil")) } })
    }
    groups.forEach { (title, ids) ->
        val ps = Providers.all.filter { it.id in ids }
        if (ps.isEmpty()) return@forEach
        Text(title, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        ps.forEach { p ->
            val on = connected(p)
            val svc = canManage && (p.id == "atomic" || p.id == "ollama")
            val here = have[p.id] != false || on
            val st = when { svc && installing == p.id -> lstatus.ifBlank { tr("Downloading…", "İndiriliyor…") }; svc && !here -> tr("Not installed", "Kurulu değil")
                p.id == "atomic" || p.id == "ollama" -> if (on) tr("Running", "Çalışıyor") else if (svc) tr("Installed", "Kurulu") else tr("Not running", "Çalışmıyor"); on -> tr("Connected", "Bağlı"); else -> tr("Not connected", "Bağlı değil") }
            ProvCard(selected == p.id, { Providers.select(p.id); if (svc && !here && installing.isEmpty()) ask = p.id }, { Logo(providerLogo(p.id), Icons.Filled.Tune) }, p.name, st, on && !(svc && installing == p.id)) {
                if (svc && installing == p.id && busy) IconButton({ Linux.cancel() }) { Icon(Icons.Filled.Close, tr("Cancel", "İptal")) }
                else if (svc && here && !busy) IconButton({ removeService(p.id) }) { Icon(Icons.Filled.Delete, tr("Remove", "Kaldır")) }
            }
        }
    }
    if (App.dsOnly) return
    val accts = remember(cfg) { WebAccounts.list() }
    Text(tr("Website accounts", "Site hesapları"), Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    accts.forEach { a ->
        val wid = "web:${a.site.id}:${a.slot}"
        ProvCard(selected == wid, { Providers.select(wid) }, { Logo(a.site.logo, Icons.Filled.Language) }, a.name, tr("Signed in on the website", "Sitede giriş"), true) {
            IconButton({ WebAccounts.open(ctx, a) }) { Icon(Icons.Filled.Language, null) }
            IconButton({ WebAccounts.remove(a); if (selected == wid) Providers.select("claude"); Providers.set("web", "ts", System.nanoTime().toString()) }) { Icon(Icons.Filled.Delete, null) }
        }
    }
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WebAccounts.sites.forEach { s ->
            AssistChip({ WebAccounts.add(s)?.let { WebAccounts.open(ctx, it) }; Providers.set("web", "ts", System.nanoTime().toString()) }, { Text(s.name) },
                leadingIcon = { Icon(painterResource(s.logo), null, Modifier.size(18.dp)) })
        }
    }
    Text(tr("Sign in with e-mail and password. Google may refuse its own sign-in here: then use the ChatGPT, Claude or Grok login, or an API key.",
        "E-posta ve parolayla giriş yap. Google kendi girişini burada reddedebilir: o zaman ChatGPT, Claude veya Grok girişini ya da API anahtarını kullan."),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

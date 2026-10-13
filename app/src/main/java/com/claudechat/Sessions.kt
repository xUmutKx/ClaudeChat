package com.claudechat

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** A Claude Code session transcript found by the bridge (sessions run in Termux, read only here). */
class Session(val f: String, val title: String, val last: String, val time: Long, val size: Long)
class SessMsg(val user: Boolean, val text: String)

private val sessHttp = OkHttpClient.Builder().connectTimeout(4, TimeUnit.SECONDS).callTimeout(8, TimeUnit.SECONDS).build()

/** GET on the bridge with the same port and token as Engine (its helper is private). Null when unreachable. */
private suspend fun bridgeGet(path: String): String? = withContext(Dispatchers.IO) {
    try {
        sessHttp.newCall(Request.Builder()
            .url("http://127.0.0.1:${Prefs.port.value.filter(Char::isDigit).ifEmpty { "8787" }}$path")
            .header("X-Token", Termux.cleanToken()).build())
            .execute().use { if (it.isSuccessful) it.body!!.string() else null }
    } catch (e: Exception) { null }
}

@Composable
fun SessionsScreen(onBack: () -> Unit) {
    var loaded by remember { mutableStateOf(false) }
    var reachable by remember { mutableStateOf(true) }
    var list by remember { mutableStateOf<List<Session>>(emptyList()) }
    var open by remember { mutableStateOf<Session?>(null) }
    LaunchedEffect(Unit) {
        val body = bridgeGet("/sessions")
        reachable = body != null
        list = body?.let { b ->
            val a = JSONObject(b).getJSONArray("sessions")
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                Session(o.getString("f"), o.optString("title"), o.optString("last"), o.optDouble("time").toLong(), o.optLong("size"))
            }
        } ?: emptyList()
        loaded = true
    }
    BackHandler(open != null) { open = null }
    open?.let { SessionDetail(it) { open = null }; return }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding()) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(Modifier.statusBarsPadding().fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                Text("Sessions", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            }
        }
        val msg = when {
            !loaded -> null
            !reachable -> "Bridge not reachable"
            list.isEmpty() -> "No sessions"
            else -> null
        }
        if (msg != null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(msg, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(list, key = { it.f }) { s ->
                Column(Modifier.fillMaxWidth().clickable { open = s }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(s.title.ifEmpty { "Untitled session" }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge)
                    if (s.last.isNotEmpty()) Text(s.last, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        DateUtils.getRelativeTimeSpanString(s.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString() +
                            "  ·  " + "%.1f KB".format(s.size / 1024.0),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** One transcript, read only: user and assistant text in order. */
@Composable
fun SessionDetail(s: Session, onBack: () -> Unit) {
    var loaded by remember { mutableStateOf(false) }
    var msgs by remember { mutableStateOf<List<SessMsg>>(emptyList()) }
    LaunchedEffect(s.f) {
        val body = bridgeGet("/sessions/one?f=" + URLEncoder.encode(s.f, "UTF-8"))
        msgs = body?.let { b ->
            val a = JSONObject(b).getJSONArray("messages")
            (0 until a.length()).map { i -> val o = a.getJSONObject(i); SessMsg(o.getString("role") == "user", o.getString("text")) }
        } ?: emptyList()
        loaded = true
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding()) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(Modifier.statusBarsPadding().fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                Text(s.title.ifEmpty { "Untitled session" }, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium)
            }
        }
        if (loaded && msgs.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Could not load this session", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(msgs) { m ->
                Column {
                    Text(if (m.user) "You" else "Claude", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(m.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

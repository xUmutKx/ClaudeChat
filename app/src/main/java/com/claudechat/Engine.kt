package com.claudechat

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.util.concurrent.TimeUnit

enum class Status { Idle, Working, Done, Error, Offline }
enum class Role { User, Claude, Tool, Error, Note }

data class Msg(
    val id: Long,
    val role: Role,
    val text: String,
    val time: Long = System.currentTimeMillis(),
    val action: Boolean = false, // error bubble offers a shortcut to settings
    val detail: String = "",     // expandable tool details (command, diff...)
)

data class Chat(val id: String, val title: String, val session: String, val cost: Double, val updated: Long)

object Engine {
    val messages = MutableStateFlow<List<Msg>>(emptyList())
    val status = MutableStateFlow(Status.Idle)
    val detail = MutableStateFlow("")
    val modelName = MutableStateFlow("")   // model the CLI reported last
    val cost = MutableStateFlow(0.0)         // accumulated cost of this chat, USD

    @Volatile var appVisible = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    @Volatile private var call: Call? = null
    private var nextId = 1L
    private lateinit var file: File
    private val http = OkHttpClient.Builder().connectTimeout(4, TimeUnit.SECONDS).readTimeout(0, TimeUnit.SECONDS).build()

    // per-turn streaming state
    private var curId: Long? = null
    private var streamed = false
    private var anyText = false
    private var gotResult = false

    val chats = MutableStateFlow<List<Chat>>(emptyList())
    val currentId = MutableStateFlow("")
    private lateinit var dir: File

    private fun msgFile(id: String) = File(dir, "$id.json")

    private fun readMsgs(id: String): List<Msg> = try {
        val arr = JSONArray(msgFile(id).readText())
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Msg(o.getLong("i"), Role.valueOf(o.getString("r")), o.getString("t"), o.optLong("ts"), o.optBoolean("a"), o.optString("d"))
        }
    } catch (e: Exception) { emptyList() }

    private fun writeMsgs(id: String, list: List<Msg>) = try {
        val arr = JSONArray()
        list.takeLast(400).forEach {
            arr.put(JSONObject().put("i", it.id).put("r", it.role.name).put("t", it.text).put("ts", it.time).put("a", it.action).put("d", it.detail))
        }
        msgFile(id).writeText(arr.toString())
    } catch (e: Exception) { }

    private fun writeIndex() = try {
        val arr = JSONArray()
        chats.value.forEach { arr.put(JSONObject().put("id", it.id).put("t", it.title).put("s", it.session).put("c", it.cost).put("u", it.updated)) }
        File(dir, "index.json").writeText(arr.toString())
    } catch (e: Exception) { }

    private fun newId() = java.lang.Long.toString(System.currentTimeMillis(), 36)

    fun load(c: Context) {
        dir = File(c.filesDir, "chats").apply { mkdirs() }
        try {
            val arr = JSONArray(File(dir, "index.json").readText())
            chats.value = (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Chat(o.getString("id"), o.optString("t"), o.optString("s"), o.optDouble("c"), o.optLong("u"))
            }
        } catch (e: Exception) { }
        // migrate the single-chat file of the first version
        val legacy = File(c.filesDir, "chat.json")
        if (chats.value.isEmpty() && legacy.exists()) {
            try {
                val id = newId()
                legacy.copyTo(msgFile(id), overwrite = true)
                val first = readMsgs(id).firstOrNull { it.role == Role.User }?.text.orEmpty()
                chats.value = listOf(Chat(id, first.take(48).ifEmpty { "Chat" }, Prefs.session.value, 0.0, System.currentTimeMillis()))
                writeIndex(); legacy.delete()
            } catch (e: Exception) { }
        }
        val last = chats.value.filter { it.title.isNotEmpty() }.maxByOrNull { it.updated }
        if (last != null) open(last.id) else startFresh()
    }

    private fun startFresh() {
        val id = newId()
        chats.update { it.filter { c -> c.title.isNotEmpty() } + Chat(id, "", "", 0.0, System.currentTimeMillis()) }
        currentId.value = id
        messages.value = emptyList(); cost.value = 0.0
    }

    private fun open(id: String) {
        currentId.value = id
        messages.value = readMsgs(id)
        cost.value = chats.value.firstOrNull { it.id == id }?.cost ?: 0.0
        nextId = (messages.value.maxOfOrNull { it.id } ?: 0) + 1
    }

    private fun update(f: (Chat) -> Chat) = chats.update { l -> l.map { if (it.id == currentId.value) f(it) else it } }

    private var session: String
        get() = chats.value.firstOrNull { it.id == currentId.value }?.session.orEmpty()
        set(v) { update { it.copy(session = v) }; writeIndex() }

    private fun save() {
        update { it.copy(cost = cost.value, updated = System.currentTimeMillis()) }
        writeMsgs(currentId.value, messages.value)
        writeIndex()
    }

    private fun halt() { job?.cancel(); call?.cancel(); status.value = Status.Idle; detail.value = "" }

    fun openChat(id: String) {
        if (id == currentId.value) return
        halt(); save()
        open(id)
    }

    fun deleteChat(id: String) {
        msgFile(id).delete()
        val wasCurrent = id == currentId.value
        if (wasCurrent) halt()
        chats.update { l -> l.filter { it.id != id } }
        writeIndex()
        if (wasCurrent) {
            val next = chats.value.filter { it.title.isNotEmpty() }.maxByOrNull { it.updated }
            if (next != null) open(next.id) else startFresh()
        }
    }

    private fun add(role: Role, text: String, action: Boolean = false, detail: String = ""): Long {
        val id = synchronized(this) { nextId++ }
        messages.update { it + Msg(id, role, text, action = action, detail = detail) }
        return id
    }

    private fun appendTo(id: Long, text: String) =
        messages.update { l -> l.map { if (it.id == id) it.copy(text = it.text + text) else it } }

    fun send(text: String, atts: List<Att> = emptyList()) {
        val t = text.trim()
        if (t.isEmpty() && atts.isEmpty()) return
        val shown = (t + "\n" + atts.joinToString("\n") { "📎 ${it.name}" }).trim()
        val prompt = if (atts.isEmpty()) t else
            (t.ifEmpty { "Please look at the attached file(s)." } + "\n\n[Attached files, read them as needed]\n" + atts.joinToString("\n") { it.path })
        if (chats.value.firstOrNull { it.id == currentId.value }?.title.isNullOrEmpty())
            update { it.copy(title = shown.lineSequence().first().take(48)) }
        add(Role.User, shown); save()
        if (status.value == Status.Working) { pending.add(prompt); return } // queued: runs when the current turn ends
        start(prompt)
    }

    private val pending = ArrayDeque<String>()

    private fun start(prompt: String) {
        status.value = Status.Working
        detail.value = ""
        KeepAliveService.start(App.ctx)
        job = scope.launch { run(prompt) }
    }

    private fun drain() { pending.removeFirstOrNull()?.let { start(it) } }

    private fun badModel(s: String) = Prefs.model.value.isNotEmpty() &&
        (s.contains("model", true) && (s.contains("not found", true) || s.contains("invalid", true) || s.contains("issue with the selected", true) || s.contains("may not exist", true)))

    fun stop() {
        if (status.value != Status.Working) return
        job?.cancel(); call?.cancel()
        status.value = Status.Idle; detail.value = ""
        add(Role.Tool, appStr(R.string.stopped)); save()
        drain()
    }

    fun newChat() {
        halt()
        if (messages.value.isEmpty()) return
        save(); startFresh(); writeIndex()
    }

    /** Local, app-side message (command feedback); not sent to Claude. */
    fun note(text: String) { add(Role.Note, text); save() }

    private fun url(path: String) = "http://127.0.0.1:${Prefs.port.value.filter(Char::isDigit).ifEmpty { "8787" }}$path"

    /** HTTP status of the bridge, or -1 when unreachable. */
    suspend fun ping(): Int = withContext(Dispatchers.IO) {
        try {
            http.newBuilder().callTimeout(2, TimeUnit.SECONDS).build()
                .newCall(Request.Builder().url(url("/ping")).header("X-Token", Termux.cleanToken()).build())
                .execute().use { it.code }
        } catch (e: IOException) { -1 }
    }

    /** Starts the bridge through Termux and waits for it. Returns an error string or null. */
    suspend fun startBridgeAndWait(): String? {
        detail.value = appStr(R.string.starting_bridge)
        Termux.startBridge(App.ctx)?.let { return appStr(it) }
        repeat(75) {
            delay(1000)
            if (ping() == 200) return null
        }
        return appStr(R.string.err_offline, Prefs.port.value) + Termux.log.trim().takeIf { it.isNotEmpty() }?.let { "\n\n" + it.takeLast(500) }.orEmpty()
    }

    private suspend fun run(prompt: String) {
        curId = null; streamed = false; anyText = false; gotResult = false
        try {
            val p = ping()
            if (p != 200) {
                if (!Prefs.autoStart.value) return fail(appStr(R.string.err_offline, Prefs.port.value), Status.Offline)
                val err = startBridgeAndWait()
                if (err != null) return fail(err, Status.Offline)
            }
            detail.value = ""
            stream(prompt, canRetry = true)
            if (status.value == Status.Working) finish(Status.Done)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!currentCoroutineContext().isActive) return
            if (e is ConnectException) fail(appStr(R.string.err_offline, Prefs.port.value), Status.Offline)
            else fail(e.message ?: e.javaClass.simpleName)
        }
    }

    private suspend fun stream(prompt: String, canRetry: Boolean) {
        val body = JSONObject().put("prompt", prompt).put("cwd", Prefs.cwd.value).put("mode", Prefs.mode.value).put("addDir", Prefs.attachDir.value).put("model", Prefs.model.value).put("effort", Prefs.effort.value)
        if (session.isNotEmpty()) body.put("session", session)
        val req = Request.Builder().url(url("/chat")).header("X-Token", Termux.cleanToken())
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        val c = http.newCall(req)
        call = c
        var retry = false
        c.execute().use { resp ->
            if (resp.code == 401) return fail(appStr(R.string.err_token), Status.Offline, action = true)
            val src = resp.body!!.source()
            while (currentCoroutineContext().isActive) {
                val line = src.readUtf8Line() ?: break
                if (line.isNotBlank() && handle(line, canRetry)) { retry = true }
            }
        }
        if (retry) {
            session = ""
            curId = null; streamed = false
            stream(prompt, canRetry = false)
        }
    }

    /** Returns true when the turn should be retried without a stale session id. */
    private fun handle(line: String, canRetry: Boolean): Boolean {
        val o = try { JSONObject(line) } catch (e: Exception) { return false }
        val sub = !o.isNull("parent_tool_use_id") && o.has("parent_tool_use_id")
        when (o.optString("type")) {
            "system" -> if (o.optString("subtype") == "init") {
                o.optString("session_id").takeIf { it.isNotEmpty() }?.let { session = it }
                o.optString("model").takeIf { it.isNotEmpty() }?.let { modelName.value = it }
                o.optJSONArray("slash_commands")?.let { a -> Prefs.slash.value = (0 until a.length()).joinToString(",") { a.getString(it) } }
            }
            "stream_event" -> if (!sub) {
                val e = o.getJSONObject("event")
                when (e.optString("type")) {
                    "message_start" -> { curId = null; streamed = false }
                    "content_block_start" -> when (e.optJSONObject("content_block")?.optString("type")) {
                        "tool_use" -> detail.value = appStr(R.string.using_tool, e.getJSONObject("content_block").optString("name"))
                        "thinking" -> detail.value = appStr(R.string.thinking)
                        "text" -> detail.value = appStr(R.string.writing)
                    }
                    "content_block_delta" -> {
                        val d = e.getJSONObject("delta")
                        if (d.optString("type") == "text_delta") appendClaude(d.optString("text"))
                    }
                }
            }
            "assistant" -> if (!sub) {
                val content = o.getJSONObject("message").optJSONArray("content") ?: JSONArray()
                for (i in 0 until content.length()) {
                    val b = content.getJSONObject(i)
                    when (b.optString("type")) {
                        "text" -> if (!streamed && b.optString("text").isNotBlank()) appendClaude(b.getString("text"))
                        "tool_use" -> {
                            val name = b.optString("name")
                            add(Role.Tool, toolSummary(name, b.optJSONObject("input")), detail = toolDetail(b.optJSONObject("input")))
                            detail.value = appStr(R.string.using_tool, name)
                        }
                    }
                }
                curId = null
            }
            "result" -> {
                gotResult = true
                val turnCost = o.optDouble("total_cost_usd", 0.0)
                cost.value += turnCost
                o.optString("session_id").takeIf { it.isNotEmpty() }?.let { session = it }
                val res = o.optString("result")
                if (o.optBoolean("is_error")) {
                    if (canRetry && res.contains("No conversation found", true)) return true
                    if (canRetry && badModel(res)) { add(Role.Note, "Model '${Prefs.model.value}' failed, using default."); Prefs.model.value = ""; return true }
                    fail(res.ifBlank { appStr(R.string.status_error) })
                } else {
                    if (!anyText && res.isNotBlank()) appendClaude(res)
                    finish(Status.Done)
                }
            }
            "rate_limit_event" -> o.optJSONObject("rate_limit_info")?.optJSONObject("unifiedWindows")?.let { w ->
                val f = w.optJSONObject("five_hour"); val s = w.optJSONObject("seven_day")
                val p = Prefs.limits.value.split("|").toMutableList().also { while (it.size < 4) it.add("") }
                if (f != null) { p[0] = f.optDouble("utilization", 0.0).toString(); p[1] = f.optLong("resetsAt", 0).toString() }
                if (s != null) { p[2] = s.optDouble("utilization", 0.0).toString(); p[3] = s.optLong("resetsAt", 0).toString() }
                Prefs.limits.value = p.joinToString("|")
            }
            "bridge_exit" -> {
                val code = o.optInt("code")
                val err = o.optString("stderr")
                if (!gotResult && code != 0) {
                    if (canRetry && err.contains("No conversation found", true)) return true
                    if (canRetry && badModel(err)) { add(Role.Note, "Model '${Prefs.model.value}' failed, using default."); Prefs.model.value = ""; return true }
                    fail(appStr(R.string.err_exit, code, err))
                }
            }
        }
        return false
    }

    private fun appendClaude(text: String) {
        if (text.isEmpty()) return
        streamed = true; anyText = true
        val id = curId ?: add(Role.Claude, "").also { curId = it }
        appendTo(id, text)
    }

    private fun toolSummary(name: String, input: JSONObject?): String {
        val k = listOf("command", "file_path", "path", "pattern", "url", "query", "description", "prompt")
            .firstOrNull { input?.has(it) == true }
        val v = k?.let { input!!.optString(it).lineSequence().firstOrNull()?.take(90) }
        return if (v.isNullOrBlank()) name else "$name · $v"
    }

    private fun toolDetail(input: JSONObject?): String {
        if (input == null) return ""
        fun str(k: String) = input.optString(k)
        val sb = StringBuilder()
        when {
            input.has("old_string") -> {
                sb.append(str("file_path")).append('\n')
                str("old_string").lines().take(15).forEach { sb.append("- ").append(it).append('\n') }
                str("new_string").lines().take(15).forEach { sb.append("+ ").append(it).append('\n') }
            }
            input.has("file_path") && input.has("content") -> sb.append(str("file_path")).append('\n').append(str("content").take(600))
            input.has("command") -> { if (str("description").isNotBlank()) sb.append("# ").append(str("description")).append('\n'); sb.append(str("command")) }
            else -> try { sb.append(input.toString(2)) } catch (e: Exception) { }
        }
        return sb.toString().take(1500)
    }

    private fun finish(st: Status) {
        status.value = st; detail.value = ""
        save()
        // the island/overlay already shows "done"; a second notification would just duplicate it
        if (!appVisible && Prefs.overlay.value == "off") Notifier.done(App.ctx, false, messages.value.lastOrNull { it.role == Role.Claude }?.text.orEmpty())
        drain()
    }

    private fun fail(text: String, st: Status = Status.Error, action: Boolean = st == Status.Offline) {
        add(Role.Error, text, action)
        status.value = st; detail.value = ""
        save()
        if (!appVisible) Notifier.done(App.ctx, true, text)
    }
}

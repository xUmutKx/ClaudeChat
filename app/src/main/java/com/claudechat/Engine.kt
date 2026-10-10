package com.claudechat

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Background = a chat is still working but it is not the one on screen (shown in blue). Only used for the overall status. */
enum class Status { Idle, Working, Done, Error, Offline, Background }

val Status.running get() = this == Status.Working || this == Status.Background
enum class Role { User, Claude, Tool, Error, Note }

data class Msg(
    val id: Long,
    val role: Role,
    val text: String,
    val time: Long = System.currentTimeMillis(),
    val action: Boolean = false, // error bubble offers a shortcut to settings
    val detail: String = "",     // expandable tool details (command, diff...)
)

/** One sub-agent (Task/Agent tool call) of the running turn: what it is doing right now and whether it finished. */
data class AgentInfo(val id: String, val title: String, val kind: String, val activity: String = "", val tools: Int = 0, val done: Boolean = false, val bg: Boolean = false)

data class Chat(val id: String, val title: String, val session: String, val cost: Double, val updated: Long)

/** Everything one chat needs to keep running while another chat is on screen. */
private class Run(val id: String) {
    val messages = MutableStateFlow<List<Msg>>(emptyList())
    val status = MutableStateFlow(Status.Idle)
    val detail = MutableStateFlow("")
    val cost = MutableStateFlow(0.0)   // accumulated cost of this chat, USD
    val ctx = MutableStateFlow(0)      // tokens in the context window after the last answer
    val agents = MutableStateFlow<List<AgentInfo>>(emptyList())
    val shell = MutableStateFlow(false) // a Bash command is running right now
    @Volatile var since = 0L       // when the running turn started (task manager)
    @Volatile var tool = ""        // tool the turn is in right now
    @Volatile var cmd = ""         // the Bash command line, while one runs
    var nextId = 1L
    var job: Job? = null
    @Volatile var call: Call? = null
    val pending = ArrayDeque<String>() // queued prompts: run when the current turn ends

    // per-turn streaming state
    var curId: Long? = null
    var streamed = false
    var anyText = false
    var gotResult = false
    // the claude process was ended by a signal (143 = SIGTERM, 137 = SIGKILL...): that is an interruption, not a failure, so the turn goes on by itself
    @Volatile var interrupted = false
    var resumes = 0
}

@OptIn(ExperimentalCoroutinesApi::class)
object Engine {
    val modelName = MutableStateFlow("")   // model the CLI reported last

    @Volatile var appVisible = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val http = OkHttpClient.Builder().connectTimeout(4, TimeUnit.SECONDS).readTimeout(0, TimeUnit.SECONDS).build()

    val chats = MutableStateFlow<List<Chat>>(emptyList())
    val currentId = MutableStateFlow("")
    private lateinit var dir: File
    private val runs = ConcurrentHashMap<String, Run>()

    /** Chats whose turn is running right now (they keep going when another chat is opened). */
    val busy = MutableStateFlow<Set<String>>(emptySet())

    /** Status for the overlay / notification: working while any chat works, else the latest change. */
    val overall = MutableStateFlow(Status.Idle)
    /** The last state of every chat (working, done, error ...), so the chat list can light each one up. */
    val chatState = MutableStateFlow<Map<String, Status>>(emptyMap())
    val overallDetail = MutableStateFlow("")

    private fun <T> current(empty: T, f: (Run) -> StateFlow<T>): StateFlow<T> =
        currentId.flatMapLatest { if (it.isEmpty()) flowOf(empty) else f(chatRun(it)) }.stateIn(uiScope, SharingStarted.Eagerly, empty)

    // the chat on screen
    val messages: StateFlow<List<Msg>> = current(emptyList()) { it.messages }
    val status: StateFlow<Status> = current(Status.Idle) { it.status }
    val detail: StateFlow<String> = current("") { it.detail }
    val cost: StateFlow<Double> = current(0.0) { it.cost }
    val agents: StateFlow<List<AgentInfo>> = current(emptyList()) { it.agents }
    val context: StateFlow<Int> = current(0) { it.ctx }
    val shell: StateFlow<Boolean> = current(false) { it.shell }

    /** "Show me the blue shell state": the header, pill and bubble pretend a command runs for a few seconds (customize sheet preview). */
    val demoShell = MutableStateFlow(false)
    private fun tr0(en: String, trText: String) = if (Prefs.lang.value == "tr") trText else en

    fun previewShell() { demoShell.value = true; uiScope.launch { delay(10_000); demoShell.value = false } }

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

    @Synchronized private fun writeIndex() = try {
        val arr = JSONArray()
        chats.value.forEach { arr.put(JSONObject().put("id", it.id).put("t", it.title).put("s", it.session).put("c", it.cost).put("u", it.updated)) }
        File(dir, "index.json").writeText(arr.toString())
    } catch (e: Exception) { }

    private fun newId() = java.lang.Long.toString(System.currentTimeMillis(), 36)

    /** The run of a chat, created (and its messages read from disk) on first use. */
    private fun chatRun(id: String): Run = runs.getOrPut(id) {
        Run(id).also { r ->
            // a turn that was running when the app was closed keeps its start time, so the counter does not reset
            r.since = App.ctx.getSharedPreferences("runs", 0).getLong(id, 0L)
            r.messages.value = readMsgs(id)
            r.nextId = (r.messages.value.maxOfOrNull { it.id } ?: 0) + 1
            r.cost.value = chats.value.firstOrNull { it.id == id }?.cost ?: 0.0
        }
    }

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
        chatRun(id)
        Wear.switch(currentId.value, id)
        currentId.value = id
        updateOverall()
    }

    private fun open(id: String) {
        val r = chatRun(id)
        if (r.status.value != Status.Working) setStatus(r, Status.Idle)
        Wear.switch(currentId.value, id)
        currentId.value = id
        updateOverall()
    }

    private fun updateChat(id: String, f: (Chat) -> Chat) = chats.update { l -> l.map { if (it.id == id) f(it) else it } }

    private fun sessionOf(id: String) = chats.value.firstOrNull { it.id == id }?.session.orEmpty()

    private fun setSession(id: String, v: String) { updateChat(id) { it.copy(session = v) }; writeIndex() }

    private fun save(r: Run) {
        updateChat(r.id) { it.copy(cost = r.cost.value, updated = System.currentTimeMillis()) }
        writeMsgs(r.id, r.messages.value)
        writeIndex()
    }

    private fun setStatus(r: Run, st: Status) {
        r.status.value = st
        chatState.update { it + (r.id to st) }
        if (st != Status.Working) r.detail.value = ""
        busy.update { if (st == Status.Working) it + r.id else it - r.id }
        updateOverall(st)
        if (busy.value.isEmpty()) overallDetail.value = ""
    }

    /** Orange while the open chat works, blue when only other (background) chats work. */
    private fun updateOverall(st: Status? = null) {
        overall.value = if (busy.value.isNotEmpty()) (if (currentId.value in busy.value) Status.Working else Status.Background)
                        else (st ?: overall.value)
    }

    private fun setDetail(r: Run, d: String) {
        r.detail.value = d
        if (r.status.value == Status.Working) overallDetail.value = d
    }

    private fun halt(r: Run) { r.job?.cancel(); r.call?.cancel(); r.pending.clear(); setStatus(r, Status.Idle) }

    fun openChat(id: String) {
        if (id == currentId.value) return
        runs[currentId.value]?.let { save(it) }
        open(id)
    }

    fun deleteChat(id: String) {
        runs.remove(id)?.let { halt(it) }
        msgFile(id).delete()
        val wasCurrent = id == currentId.value
        chats.update { l -> l.filter { it.id != id } }
        writeIndex()
        if (wasCurrent) {
            val next = chats.value.filter { it.title.isNotEmpty() }.maxByOrNull { it.updated }
            if (next != null) open(next.id) else startFresh()
        }
    }

    private fun add(r: Run, role: Role, text: String, action: Boolean = false, detail: String = ""): Long {
        val id = synchronized(r) { r.nextId++ }
        r.messages.update { it + Msg(id, role, text, action = action, detail = detail) }
        return id
    }

    private fun appendTo(r: Run, id: Long, text: String) =
        r.messages.update { l -> l.map { if (it.id == id) it.copy(text = it.text + text) else it } }

    private fun firstLine(shown: String) = shown.lineSequence().first().take(48)

    /** [reply]: the earlier message (author, text) the user swiped to answer; Claude is told which one. */
    fun send(text: String, atts: List<Att> = emptyList(), reply: Pair<Role, String>? = null) {
        val t = text.trim()
        if (t.isEmpty() && atts.isEmpty()) return
        val r = chatRun(currentId.value)
        val quote = reply?.second?.trim().orEmpty()
        val shownQuote = if (quote.isEmpty()) "" else "↩ " + quote.lineSequence().filter { it.isNotBlank() }.joinToString(" ").take(160) + "\n\n"
        val shown = (shownQuote + t + "\n" + atts.joinToString("\n") { "📎 ${it.name}" }).trim()
        val who = if (reply?.first == Role.Claude) "you (Claude)" else "the user"
        val head = if (quote.isEmpty()) "" else "[The user is replying to this earlier message from $who. Answer about that message specifically:\n\"${quote.take(1500)}\"]\n\n"
        val prompt = head + if (atts.isEmpty()) t else
            (t.ifEmpty { "Please look at the attached file(s)." } + "\n\n[Attached files, read them as needed]\n" + atts.joinToString("\n") { it.path })
        if (chats.value.firstOrNull { it.id == r.id }?.title.isNullOrEmpty())
            updateChat(r.id) { it.copy(title = firstLine((t + "\n" + atts.joinToString("\n") { a -> "📎 ${a.name}" }).trim().ifEmpty { shown })) }
        add(r, Role.User, shown); save(r)
        if (r.status.value == Status.Working) { r.pending.add(prompt); return }
        start(r, prompt)
    }

    private fun start(r: Run, prompt: String) {
        r.since = System.currentTimeMillis(); r.tool = ""; r.cmd = ""
        App.ctx.getSharedPreferences("runs", 0).edit().putLong(r.id, r.since).apply()
        setStatus(r, Status.Working)
        KeepAliveService.start(App.ctx)
        r.job = scope.launch { execute(r, prompt) }
    }

    private fun drain(r: Run) { r.pending.removeFirstOrNull()?.let { start(r, it) } }

    private fun badModel(id: String, s: String) = Prefs.chatModel(id).isNotEmpty() &&
        (s.contains("model", true) && (s.contains("not found", true) || s.contains("invalid", true) || s.contains("issue with the selected", true) || s.contains("may not exist", true)))

    /** Stops the chat on screen; other chats keep running. */
    fun stop() = runs[currentId.value]?.let { stopRun(it) } ?: Unit

    /** Stops one running chat by id (task manager). */
    fun stopChat(id: String) { runs[id]?.let { stopRun(it) } }

    /** Everything that is running right now: one entry per working chat. */
    fun tasks(): List<TaskInfo> = runs.values.filter { it.status.value == Status.Working }.map { r ->
        TaskInfo(r.id, chats.value.firstOrNull { it.id == r.id }?.title.orEmpty(), r.since, r.tool, if (r.shell.value) r.cmd else "",
            r.agents.value.size, r.agents.value.count { it.done }, r.pending.size)
    }.sortedBy { it.since }

    /** The claude processes the bridge keeps alive (one per chat). */
    fun bridgeProcs(): List<BridgeProc>? = try {
        http.newBuilder().callTimeout(3, TimeUnit.SECONDS).build().newCall(Request.Builder().url(url("/ps")).header("X-Token", Termux.cleanToken()).build()).execute().use { resp ->
            if (!resp.isSuccessful) emptyList() else {
                val a = JSONObject(resp.body!!.string()).getJSONArray("procs")
                (0 until a.length()).map { val o = a.getJSONObject(it); BridgeProc(o.getString("chat"), o.optInt("pid"), o.optBoolean("busy"), o.optInt("idle")) }
            }
        }
    } catch (e: Exception) { emptyList() }

    /** Newest Gradle build log the bridge can find: file, seconds since its last write, and its tail. */
    @Volatile var buildAlive = false
    /** The same build state as a flow, so the chat header can show "Compiling" while it runs. */
    val buildRunning = MutableStateFlow(false)
    /** Seconds the running Gradle build has been going (read from its process by the bridge), or -1 when the bridge could not tell. */
    @Volatile var buildElapsed = -1
    private val buildSeen = HashMap<String, Long>()   // log file -> when this app first saw it running (fallback for the elapsed time)
    fun buildLog(): Triple<String, Int, String>? = try {
        http.newBuilder().callTimeout(4, TimeUnit.SECONDS).build().newCall(Request.Builder().url(url("/build")).header("X-Token", Termux.cleanToken()).build()).execute().use { resp ->
            if (!resp.isSuccessful) null else JSONObject(resp.body!!.string()).let { o ->
                buildAlive = o.optBoolean("alive"); buildRunning.value = buildAlive; buildElapsed = o.optInt("elapsed", -1)
                if (o.optString("file").isEmpty()) null else {
                    val f = o.getString("file"); val text = o.optString("text")
                    // a new run of the same log file starts again from a short log: forget the old first-seen time
                    synchronized(buildSeen) { if (buildSeen[f] == null || (text.length < 600 && !text.contains("BUILD "))) buildSeen[f] = System.currentTimeMillis() - o.optInt("age") * 1000L }
                    Triple(f, o.optInt("age"), text)
                }
            }
        }
    } catch (e: Exception) { null }

    /** Seconds since the build of [file] started: the process time from the bridge, else since this app first saw its log. */
    fun buildElapsedFor(file: String): Int =
        if (buildElapsed >= 0) buildElapsed else synchronized(buildSeen) { buildSeen[file]?.let { ((System.currentTimeMillis() - it) / 1000).toInt() } ?: -1 }

    /** Ends the bridge's claude process of a chat (it is started again on the next message). */
    fun killProc(chat: String) {
        scope.launch {
            try {
                http.newBuilder().callTimeout(3, TimeUnit.SECONDS).build().newCall(Request.Builder().url(url("/kill")).header("X-Token", Termux.cleanToken())
                    .post(JSONObject().put("chat", chat).toString().toRequestBody("application/json".toMediaType())).build()).execute().close()
            } catch (_: Exception) { }
            stopChat(chat)
        }
    }

    /** Stops every running chat (notification's stop button). */
    fun stopAll() { runs.values.forEach { stopRun(it) } }

    private fun stopRun(r: Run) {
        if (r.status.value != Status.Working) return
        r.job?.cancel(); r.call?.cancel()
        setStatus(r, Status.Idle)
        add(r, Role.Tool, appStr(R.string.stopped)); save(r)
        drain(r)
    }

    /** Opens a blank chat; a running chat is left alone and keeps working in the background. */
    fun newChat() {
        if (messages.value.isEmpty()) return
        runs[currentId.value]?.let { save(it) }
        startFresh(); writeIndex()
    }

    /** Local, app-side message (command feedback); not sent to Claude. */
    fun note(text: String) { val r = chatRun(currentId.value); add(r, Role.Note, text); save(r) }

    private fun url(path: String) = "http://127.0.0.1:${Prefs.port.value.filter(Char::isDigit).ifEmpty { "8787" }}$path"

    /** HTTP status of the bridge, or -1 when unreachable. */
    suspend fun ping(): Int = withContext(Dispatchers.IO) {
        try {
            http.newBuilder().callTimeout(4, TimeUnit.SECONDS).build()
                .newCall(Request.Builder().url(url("/ping")).header("X-Token", Termux.cleanToken()).build())
                .execute().use { it.code }
        } catch (e: IOException) { -1 }
    }

    /** Sub-folders of [path] inside Ubuntu (through the bridge): (resolved path, parent, names), or null when unreachable. */
    suspend fun ls(path: String): Triple<String, String, List<String>>? = withContext(Dispatchers.IO) {
        try {
            http.newBuilder().callTimeout(4, TimeUnit.SECONDS).build()
                .newCall(Request.Builder().url(url("/ls?p=" + java.net.URLEncoder.encode(path, "UTF-8"))).header("X-Token", Termux.cleanToken()).build())
                .execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val o = JSONObject(resp.body!!.string())
                    val a = o.getJSONArray("dirs")
                    Triple(o.getString("path"), o.getString("parent"), (0 until a.length()).map { a.getString(it) })
                }
        } catch (e: Exception) { null }
    }

    // ---- sign-in through the bridge: Claude opens in the phone's browser, GitHub shows a device code ------------------------

    class Auth(val claudeIn: Boolean, val email: String, val ghInstalled: Boolean, val ghIn: Boolean, val ghUser: String)
    class Login(val state: String, val url: String, val code: String, val out: String)

    /** Who is signed in (the bridge asks `claude auth status` and `gh auth status` in Ubuntu), or null when the bridge cannot be reached. */
    suspend fun authStatus(): Auth? = withContext(Dispatchers.IO) {
        try {
            http.newBuilder().callTimeout(20, TimeUnit.SECONDS).build().newCall(Request.Builder().url(url("/auth")).header("X-Token", Termux.cleanToken()).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val o = JSONObject(resp.body!!.string()); val c = o.optJSONObject("claude"); val g = o.optJSONObject("gh")
                Auth(c?.optBoolean("loggedIn") == true, c?.optString("email").orEmpty(), g?.optBoolean("installed") == true, g?.optBoolean("loggedIn") == true, g?.optString("user").orEmpty())
            }
        } catch (e: Exception) { null }
    }

    /** The GitHub login's token (the bridge asks `gh auth token`), or null when not signed in or the bridge is down. Blocking: call it off the main thread. */
    fun ghTokenBlocking(): String? = try {
        http.newBuilder().callTimeout(8, TimeUnit.SECONDS).build().newCall(Request.Builder().url(url("/gh/token")).header("X-Token", Termux.cleanToken()).build()).execute().use { r ->
            if (r.isSuccessful) r.body!!.string().trim().takeIf { it.isNotEmpty() } else null
        }
    } catch (e: Exception) { null }

    /** [what] = "claude" or "gh"; [action] = start / state / cancel / code. */
    suspend fun login(what: String, action: String, code: String = ""): Login? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().header("X-Token", Termux.cleanToken())
            val call = if (action == "state") req.url(url("/login/state?what=$what")).get()
                else req.url(url("/login/$action")).post(JSONObject().put("what", what).put("code", code).toString().toRequestBody("application/json".toMediaType()))
            http.newBuilder().callTimeout(10, TimeUnit.SECONDS).build().newCall(call.build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val o = JSONObject(resp.body!!.string())
                Login(o.optString("state"), o.optString("url"), o.optString("code"), o.optString("out"))
            }
        } catch (e: Exception) { null }
    }

    /** Starts the bridge through Termux and waits for it. Returns an error string or null. */
    suspend fun startBridgeAndWait(onDetail: (String) -> Unit = {}): String? {
        onDetail(appStr(R.string.starting_bridge))
        Termux.startBridge(App.ctx, kill = false)?.let { return appStr(it) }
        repeat(130) {
            delay(1000)
            if (ping() == 200) return null
        }
        return appStr(R.string.err_offline, Prefs.port.value) + Termux.log.trim().takeIf { it.isNotEmpty() }?.let { "\n\n" + it.takeLast(500) }.orEmpty()
    }

    /** Bridge state for the one-tap start: -2 checking, -1 down, 0 starting, 200 up. */
    val bridge = MutableStateFlow(-2)
    val bridgeError = MutableStateFlow("")

    /** Looks at the bridge and, when it is down and auto-start is on, brings it up without any tap. */
    fun checkBridge() {
        if (bridge.value == 0) return
        scope.launch {
            bridge.value = -2
            // a busy bridge (scanning /proc, big file lists) can miss one ping: only a few misses in a row mean it is gone
            var up = false
            repeat(3) { if (!up) { up = ping() == 200; if (!up) delay(1500) } }
            if (up) bridge.value = 200
            else { bridge.value = -1; if (Prefs.autoStart.value) connect() }
        }
    }

    /** One tap: (re)starts the bridge with the current token and waits until it answers. */
    fun connect() {
        if (bridge.value == 0) return
        bridge.value = 0; bridgeError.value = ""
        scope.launch {
            val err = startBridgeAndWait()
            if (err == null) bridge.value = 200 else { bridge.value = -1; bridgeError.value = err }
        }
    }

    private suspend fun execute(r: Run, prompt: String) {
        r.curId = null; r.streamed = false; r.anyText = false; r.gotResult = false; r.interrupted = false; r.resumes = 0
        r.agents.value = emptyList(); r.shell.value = false
        try {
            val pv = Providers.current()
            if (pv.id != "claude") { direct(r, pv); return }
            val p = ping()
            if (p != 200) {
                if (!Prefs.autoStart.value) return fail(r, appStr(R.string.err_offline, Prefs.port.value), Status.Offline)
                val err = startBridgeAndWait { setDetail(r, it) }
                if (err != null) return fail(r, err, Status.Offline)
            }
            bridge.value = 200
            setDetail(r, "")
            stream(r, prompt, canRetry = true)
            if (r.status.value == Status.Working) finish(r, Status.Done)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!currentCoroutineContext().isActive) return
            // the answer already arrived (or the user pressed stop): a late socket/stream error is not worth showing
            if (r.gotResult || !r.status.value.running) return
            if (e is IOException && e !is ConnectException) {
                fail(r, appStr(R.string.err_dropped), Status.Offline); return
            }
            if (e is ConnectException) fail(r, appStr(R.string.err_offline, Prefs.port.value), Status.Offline)
            else fail(r, e.message ?: e.javaClass.simpleName)
        }
    }

    /** A question to the user before a tool changes something: shown as a dialog in the chat. */
    class Ask(val title: String, val detail: String) { val answer = CompletableDeferred<Int>() } // 0 no, 1 yes, 2 yes and do not ask again in this chat
    val approval = MutableStateFlow<Ask?>(null)
    private val alwaysOk = ConcurrentHashMap.newKeySet<String>()

    private suspend fun ask(r: Run, tool: String, a: Ask): Boolean {
        if (alwaysOk.contains(r.id + tool)) return true
        approval.value = a
        val v = try { a.answer.await() } finally { approval.value = null }
        if (v == 2) alwaysOk.add(r.id + tool)
        return v != 0
    }

    /** Another AI answers directly over the internet; with tools on it can read and change files in the agent folder and run commands, in the app itself. */
    private suspend fun direct(r: Run, pv: Providers.P) {
        setDetail(r, pv.name)
        val msgs = JSONArray()
        val system = (if (Prefs.studio.value) Providers.STUDIO + "\n\n" else "") + (if (Prefs.agentTools.value)
            "You are a coding agent on the user's Android phone. Your project folder is ${AgentTools.root().path}; relative paths start there. Use the tools to read, search, edit and run things instead of guessing, and keep answers short." else "")
        if (system.isNotEmpty()) msgs.put(JSONObject().put("role", "system").put("content", system))
        r.messages.value.filter { (it.role == Role.User || it.role == Role.Claude) && it.text.isNotBlank() }.takeLast(40)
            .forEach { msgs.put(JSONObject().put("role", if (it.role == Role.User) "user" else "assistant").put("content", it.text)) }
        val mode = Prefs.mode.value
        var tools: JSONArray? = if (Prefs.agentTools.value) AgentTools.specs(mode) else null
        val client = http.newBuilder().connectTimeout(20, TimeUnit.SECONDS).build()
        var steps = 0
        while (steps++ < 30) {
            r.curId = null
            val turn = try {
                withContext(Dispatchers.IO) { Providers.completion(pv, msgs, tools, client, { r.call = it }) { appendClaude(r, it) } }
            } catch (e: IOException) {
                if (tools != null && steps == 1 && Regex("\\b(400|404|422)\\b").containsMatchIn(e.message.orEmpty())) { tools = null; add(r, Role.Note, tr0("This model cannot use tools: answering without files and commands.", "Bu model araç kullanamıyor: dosya ve komut olmadan cevaplıyorum.")); continue }
                throw e
            }
            if (turn.calls.isEmpty()) break
            val am = JSONObject().put("role", "assistant").put("content", if (turn.text.isEmpty()) JSONObject.NULL else turn.text)
            am.put("tool_calls", JSONArray(turn.calls.map { JSONObject().put("id", it.id).put("type", "function").put("function", JSONObject().put("name", it.name).put("arguments", it.args.ifBlank { "{}" })) }))
            msgs.put(am)
            for (c in turn.calls) {
                val a = try { JSONObject(c.args.ifBlank { "{}" }) } catch (e: Exception) { JSONObject() }
                r.tool = c.name; r.shell.value = c.name == "Bash"; r.cmd = if (c.name == "Bash") a.optString("command").lineSequence().firstOrNull().orEmpty().take(300) else ""
                setDetail(r, appStr(R.string.using_tool, c.name))
                add(r, Role.Tool, toolSummary(c.name, a), detail = toolDetail(a))
                val out = if (AgentTools.needsAsk(c.name, mode) && !ask(r, c.name, Ask(toolSummary(c.name, a), toolDetail(a)))) "The user declined this action."
                    else withContext(Dispatchers.IO) { AgentTools.run(c.name, a, mode) }
                msgs.put(JSONObject().put("role", "tool").put("tool_call_id", c.id).put("content", out))
            }
            r.shell.value = false
        }
        if (steps > 30) add(r, Role.Note, tr0("Stopped after 30 steps.", "30 adımdan sonra durdum."))
        if (r.status.value == Status.Working) finish(r, Status.Done)
    }

    /** Short notes on the latest other chats (title, first question, last answer) so a fresh session knows the context without replaying history. */
    private fun oldChatNotes(id: String): String {
        val sb = StringBuilder()
        chats.value.filter { it.id != id && it.title.isNotEmpty() }.sortedByDescending { it.updated }.take(6).forEach { c ->
            val m = readMsgs(c.id)
            val q = m.firstOrNull { it.role == Role.User }?.text.orEmpty().replace('\n', ' ').take(100)
            val a = m.lastOrNull { it.role == Role.Claude }?.text.orEmpty().replace('\n', ' ').take(160)
            sb.append("- ").append(c.title).append(" | Q: ").append(q).append(" | A: ").append(a).append('\n')
        }
        return if (sb.isEmpty()) "" else "[Notes on earlier chats, for context only; do not mention unless relevant]\n$sb[End of notes]\n\n"
    }

    private suspend fun stream(r: Run, prompt: String, canRetry: Boolean) {
        val notes = if (Prefs.chatNotes.value && sessionOf(r.id).isEmpty() && r.messages.value.count { it.role == Role.User } <= 1) oldChatNotes(r.id) else ""
        val body = JSONObject().put("prompt", (if (Prefs.studio.value) "[" + Providers.STUDIO + "]\n\n" else "") + notes + prompt).put("chat", r.id).put("cwd", Prefs.cwd.value).put("mode", Prefs.mode.value).put("addDir", Prefs.attachDir.value).put("model", Prefs.chatModel(r.id)).put("effort", Prefs.effort.value)
        val sess = sessionOf(r.id)
        if (sess.isNotEmpty()) body.put("session", sess)
        // the bridge passes this to claude as the claudechat-phone MCP server (only while the phone tools are on)
        if (Prefs.phoneTools.value) body.put("phoneToken", com.claudechat.phone.PhoneControl.token(App.ctx))
        val req = Request.Builder().url(url("/chat")).header("X-Token", Termux.cleanToken())
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        val c = http.newCall(req)
        r.call = c
        var retry = false
        c.execute().use { resp ->
            if (resp.code == 401) return fail(r, appStr(R.string.err_token), Status.Offline, action = true)
            val src = resp.body!!.source()
            while (currentCoroutineContext().isActive) {
                val line = src.readUtf8Line() ?: break
                // a single odd line must never abort the turn (JSONException used to surface as a raw error)
                if (line.isNotBlank() && try { handle(r, line, canRetry) } catch (e: org.json.JSONException) { false }) { retry = true }
            }
        }
        if (retry) {
            setSession(r.id, "")
            r.curId = null; r.streamed = false
            stream(r, prompt, canRetry = false)
        } else if (r.interrupted && !r.gotResult && currentCoroutineContext().isActive) {
            r.interrupted = false
            if (r.resumes < 3) {
                // same session, nothing shown: the bridge starts a new claude that picks the conversation up where the old one stopped
                r.resumes++
                r.curId = null; r.streamed = false
                delay(1000)
                stream(r, "Your process was interrupted. Carry on from where you stopped; do not start over.", canRetry = false)
            } else {
                // it keeps getting ended (Android killing it?): say so quietly, without the red error
                add(r, Role.Note, tr0("Claude was interrupted several times. Send a message and it carries on.", "Claude birkaç kez yarıda kesildi. Bir mesaj yaz, devam eder."))
                setStatus(r, Status.Idle); save(r); drain(r)
            }
        }
    }

    /** Returns true when the turn should be retried without a stale session id. */
    private fun handle(r: Run, line: String, canRetry: Boolean): Boolean {
        val o = try { JSONObject(line) } catch (e: Exception) { return false }
        val sub = !o.isNull("parent_tool_use_id") && o.has("parent_tool_use_id")
        when (o.optString("type")) {
            "system" -> if (o.optString("subtype") == "init") {
                o.optString("session_id").takeIf { it.isNotEmpty() }?.let { setSession(r.id, it) }
                o.optString("model").takeIf { it.isNotEmpty() }?.let { modelName.value = it }
                o.optJSONArray("slash_commands")?.let { a -> Prefs.slash.value = (0 until a.length()).joinToString(",") { a.getString(it) } }
            }
            "stream_event" -> if (!sub) {
                val e = o.getJSONObject("event")
                when (e.optString("type")) {
                    "message_start" -> { r.curId = null; r.streamed = false }
                    "content_block_start" -> when (e.optJSONObject("content_block")?.optString("type")) {
                        "tool_use" -> { val n = e.getJSONObject("content_block").optString("name"); r.shell.value = n == "Bash"; r.tool = n; if (n != "Bash") r.cmd = ""; setDetail(r, appStr(R.string.using_tool, n)) }
                        "thinking" -> { r.shell.value = false; setDetail(r, appStr(R.string.thinking)) }
                        "text" -> { r.shell.value = false; setDetail(r, appStr(R.string.writing)) }
                    }
                    "content_block_delta" -> {
                        val d = e.getJSONObject("delta")
                        if (d.optString("type") == "text_delta") appendClaude(r, d.optString("text"))
                    }
                }
            }
            "assistant" -> if (!sub) {
                // what the context window holds now: fresh input plus cached input for this answer
                o.getJSONObject("message").optJSONObject("usage")?.let { u -> r.ctx.value = u.optInt("input_tokens") + u.optInt("cache_read_input_tokens") + u.optInt("cache_creation_input_tokens") }
                val content = o.getJSONObject("message").optJSONArray("content") ?: JSONArray()
                for (i in 0 until content.length()) {
                    val b = content.getJSONObject(i)
                    when (b.optString("type")) {
                        "text" -> if (!r.streamed && b.optString("text").isNotBlank()) appendClaude(r, b.getString("text"))
                        "tool_use" -> {
                            val name = b.optString("name")
                            val inp = b.optJSONObject("input")
                            if (name == "Task" || name == "Agent") {
                                val title = inp?.optString("description").orEmpty().ifBlank { inp?.optString("prompt").orEmpty().lineSequence().firstOrNull().orEmpty() }.take(80).ifBlank { name }
                                // a background agent's tool result arrives at once ("launched"): it stays working until the turn ends
                                r.agents.value = r.agents.value + AgentInfo(b.optString("id"), title, inp?.optString("subagent_type").orEmpty().ifBlank { "agent" }, bg = inp?.optBoolean("run_in_background") == true)
                            }
                            r.shell.value = name == "Bash"; r.tool = name
                            r.cmd = if (name == "Bash") inp?.optString("command").orEmpty().lineSequence().firstOrNull().orEmpty().take(300) else ""
                            add(r, Role.Tool, toolSummary(name, inp), detail = toolDetail(inp))
                            setDetail(r, appStr(R.string.using_tool, name))
                        }
                    }
                }
                r.curId = null
            } else {
                // a sub-agent is talking: keep its card up to date
                val pid = o.optString("parent_tool_use_id")
                val content = o.optJSONObject("message")?.optJSONArray("content") ?: JSONArray()
                var act = ""; var used = 0
                for (i in 0 until content.length()) {
                    val b = content.getJSONObject(i)
                    when (b.optString("type")) {
                        "tool_use" -> { act = toolSummary(b.optString("name"), b.optJSONObject("input")); used++ }
                        "text" -> if (act.isEmpty()) act = b.optString("text").lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().take(90)
                    }
                }
                if (act.isNotEmpty() || used > 0) r.agents.value = r.agents.value.map { if (it.id == pid) it.copy(activity = act.ifEmpty { it.activity }, tools = it.tools + used) else it }
            }
            "user" -> if (!sub) { // tool results: a finished Task/Agent call = that agent is done
                r.shell.value = false
                val content = o.optJSONObject("message")?.optJSONArray("content")
                if (content != null) for (i in 0 until content.length()) {
                    val b = content.optJSONObject(i) ?: continue
                    if (b.optString("type") == "tool_result") {
                        val id = b.optString("tool_use_id")
                        r.agents.value = r.agents.value.map { if (it.id == id && !it.bg) it.copy(done = true) else it }
                    }
                }
            }
            "result" -> {
                r.gotResult = true
                val turnCost = o.optDouble("total_cost_usd", 0.0)
                r.cost.value += turnCost
                o.optString("session_id").takeIf { it.isNotEmpty() }?.let { setSession(r.id, it) }
                val res = o.optString("result")
                if (o.optBoolean("is_error")) {
                    if (canRetry && res.contains("No conversation found", true)) return true
                    if (canRetry && badModel(r.id, res)) { add(r, Role.Note, "Model '${Prefs.chatModel(r.id)}' failed, using default."); Prefs.setChatModel(r.id, ""); return true }
                    fail(r, res.ifBlank { appStr(R.string.status_error) })
                } else {
                    // the final answer must always show: when nothing from this turn contains it (text streamed earlier was only a preface, or the stream lost it), add it as its own message
                    if (res.isNotBlank()) {
                        val turn = r.messages.value.takeLastWhile { it.role != Role.User }.filter { it.role == Role.Claude }
                        val norm = { t: String -> t.filter { !it.isWhitespace() } }
                        if (!r.anyText || turn.none { norm(it.text).contains(norm(res)) || norm(res).contains(norm(it.text)) && it.text.length > res.length / 2 }) { r.curId = null; appendClaude(r, res) }
                    }
                    finish(r, Status.Done)
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
                // ended by a signal (not by an error of its own): the turn is simply carried on
                if (!r.gotResult && code in SIGNAL_EXITS) { r.interrupted = true; return false }
                if (!r.gotResult && code != 0) {
                    if (canRetry && err.contains("No conversation found", true)) return true
                    if (canRetry && badModel(r.id, err)) { add(r, Role.Note, "Model '${Prefs.chatModel(r.id)}' failed, using default."); Prefs.setChatModel(r.id, ""); return true }
                    fail(r, appStr(R.string.err_exit, code, err))
                }
            }
        }
        return false
    }

    /** Exit codes of a process ended from outside: 129 hangup, 130 interrupt, 131 quit, 137 killed, 143 terminated (and the same as plain signal numbers). */
    private val SIGNAL_EXITS = setOf(129, 130, 131, 137, 143, 9, 15)   // exit code 1 and the like are real errors and stay errors

    private fun appendClaude(r: Run, text: String) {
        if (text.isEmpty()) return
        r.streamed = true; r.anyText = true
        val id = r.curId ?: add(r, Role.Claude, "").also { r.curId = it }
        appendTo(r, id, text)
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

    private fun finish(r: Run, st: Status) {
        r.shell.value = false; r.agents.value = r.agents.value.map { it.copy(done = true) }
        App.ctx.getSharedPreferences("runs", 0).edit().remove(r.id).apply()
        setStatus(r, st)
        save(r)
        // the island/overlay already shows "done"; a second notification would just duplicate it
        if (!appVisible && Prefs.overlay.value == "off") Notifier.done(App.ctx, false, r.messages.value.lastOrNull { it.role == Role.Claude }?.text.orEmpty())
        if (st == Status.Done) { Vib.event("done"); retitle(r) }
        drain(r)
    }

    private fun fail(r: Run, text: String, st: Status = Status.Error, action: Boolean = st == Status.Offline) {
        r.shell.value = false
        add(r, Role.Error, text, action)
        setStatus(r, st)
        save(r)
        Vib.event("error")
        if (!appVisible) Notifier.done(App.ctx, true, text)
    }

    /** After the first answer, replaces the "first line of my message" title with a short topic title made by Claude (haiku). */
    private fun retitle(r: Run) {
        val users = r.messages.value.filter { it.role == Role.User }
        if (users.size > 3) return   // the bridge may fail once; a later answer tries again while the title is still the first line
        val chat = chats.value.firstOrNull { it.id == r.id } ?: return
        if (chat.title != firstLine(users[0].text)) return // already renamed
        val reply = r.messages.value.lastOrNull { it.role == Role.Claude }?.text.orEmpty()
        if (reply.isBlank()) return
        scope.launch {
            try {
                val body = JSONObject().put("user", users[0].text.take(600)).put("reply", reply.take(600))
                val req = Request.Builder().url(url("/title")).header("X-Token", Termux.cleanToken())
                    .post(body.toString().toRequestBody("application/json".toMediaType())).build()
                val txt = http.newBuilder().callTimeout(60, TimeUnit.SECONDS).build().newCall(req).execute().use { if (it.code == 200) it.body?.string() else null } ?: return@launch
                val t = JSONObject(txt).optString("title").trim().trim('"', '\'', '.', '*', '#', ' ').take(60)
                if (t.isNotEmpty() && runs.containsKey(r.id)) { updateChat(r.id) { it.copy(title = t) }; writeIndex() }
            } catch (e: Exception) { }
        }
    }
}

package com.claudechat

import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Other AIs next to Claude Code. Every one of these speaks the OpenAI chat format (Gemini has an OpenAI-compatible endpoint too),
 * so a single streaming client covers them. The key stays on the phone and is sent only to that provider.
 */
object Providers {
    class P(val id: String, val name: String, val base: String, val model: String, val note: String, val noteTr: String, val skin: String, val keyUrl: String,
            val needsKey: Boolean = true, val ch: String = "claude", val models: List<String> = emptyList(), val auth: String = "")

    val all = listOf(
        P("claude", "Claude Code", "", "", "Through the Termux bridge (your Claude login)", "Termux köprüsü üzerinden (Claude girişin)", "orange", "", false, "claude"),
        // no key at all: a free shared service, and the GitHub login you already have
        P("pollinations", "Free, no account", "https://text.pollinations.ai/openai", "openai", "No account and no key: a free shared service, it can be slow", "Hesap ve anahtar yok: ücretsiz ortak servis, yavaş olabilir", "teal", "", false, "star",
            listOf("openai", "openai-fast")),
        P("llm7", "Free: DeepSeek, GPT-OSS, Gemma", "https://api.llm7.io/v1", "DeepSeek-V4-Flash-0731", "No account and no key (LLM7 free tier, rate limited). Gemini and ChatGPT need their own key or login", "Hesap ve anahtar yok (LLM7 ücretsiz katman, sınırlı). Gemini ve ChatGPT kendi anahtarını ya da girişini ister", "blue", "https://token.llm7.io/", false, "whale",
            listOf("DeepSeek-V4-Flash-0731", "gpt-oss:20b", "gemma4:31b", "glm-5.2", "GLM-5.3-Flash", "minimax-m2.7", "mistral-Nemo-Instruct-2407", "nemotron-3-nano:30b", "codestral-latest")),
        P("github", "GitHub Models", "https://models.github.ai/inference", "openai/gpt-4.1-mini", "Free with your GitHub login: nothing to copy", "GitHub girişinle ücretsiz: kopyalanacak bir şey yok", "gray", "", false, "cat",
            listOf("openai/gpt-4.1-mini", "openai/gpt-4.1", "openai/gpt-4o-mini", "meta/llama-3.3-70b-instruct", "deepseek/deepseek-r1", "microsoft/phi-4", "mistral-ai/mistral-small-2503", "xai/grok-3-mini"), "gh"),
        P("deepseek", "DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat", "Paid API key (very cheap)", "Ücretli API anahtarı (çok ucuz)", "blue", "https://platform.deepseek.com/api_keys", true, "whale",
            listOf("deepseek-chat", "deepseek-reasoner")),
        P("gemini", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash", "Free key from Google AI Studio", "Google AI Studio'dan ücretsiz anahtar", "purple", "https://aistudio.google.com/apikey", true, "star",
            listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.5-flash-lite")),
        P("ollama", "Ollama (local)", "http://127.0.0.1:11434/v1", "llama3.2:3b", "Models that run on your own phone or computer: no key, no internet once downloaded", "Kendi telefonunda ya da bilgisayarında çalışan modeller: anahtar yok, indirdikten sonra internet yok", "gray", "", false, "llama",
            listOf("llama3.2:3b", "qwen2.5:3b", "gemma3:4b", "deepseek-r1:1.5b", "phi3:mini")),
        P("groq", "Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", "Free key, very fast", "Ücretsiz anahtar, çok hızlı", "teal", "https://console.groq.com/keys", true, "bolt",
            listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant")),
        P("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", "deepseek/deepseek-chat-v3-0324:free", "Many models, some free (':free')", "Çok model, bazıları ücretsiz (':free')", "green", "https://openrouter.ai/keys", true, "rabbit"),
        P("mistral", "Mistral", "https://api.mistral.ai/v1", "mistral-small-latest", "Free tier key", "Ücretsiz katman anahtarı", "red", "https://console.mistral.ai/api-keys", true, "cat",
            listOf("mistral-small-latest", "mistral-large-latest")),
        P("openai", "OpenAI", "https://api.openai.com/v1", "gpt-4o-mini", "Paid API key", "Ücretli API anahtarı", "gray", "https://platform.openai.com/api-keys", true, "owl",
            listOf("gpt-4o-mini", "gpt-4o")),
        P("custom", "Custom", "", "", "Any OpenAI-compatible server (set address and model)", "OpenAI uyumlu herhangi bir sunucu (adres ve model gir)", "yellow", "", false, "ghost"),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id } ?: all[0]
    fun current() = byId(Prefs.provider.value)

    private fun cfg() = try { JSONObject(Prefs.provCfg.value) } catch (e: Exception) { JSONObject() }
    private fun field(id: String, k: String, def: String = "") = cfg().optJSONObject(id)?.optString(k, def)?.takeIf { it.isNotEmpty() } ?: def
    fun key(id: String) = field(id, "key")
    fun model(p: P) = field(p.id, "model", p.model)
    fun base(p: P) = field(p.id, "base", p.base).trimEnd('/')
    fun set(id: String, k: String, v: String) {
        val c = cfg(); val o = c.optJSONObject(id) ?: JSONObject()
        o.put(k, v.trim()); c.put(id, o); Prefs.provCfg.value = c.toString()
    }

    /** The mascot takes the provider's colour, so a glance at the pill tells who is answering. Claude's own colour comes back with Claude. */
    fun select(id: String) {
        val p = byId(id)
        Prefs.provider.value = p.id
        Mascots.syncSkin()          // each character keeps its own colour; picking an AI does not repaint it
    }

    const val STUDIO = "You are in Studio mode, a small image workshop. When the user asks for a picture, icon, sprite, logo or pixel art, answer with ONE complete SVG inside a ```svg code block " +
        "(<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 W H\" ...>, no scripts, no external files). For pixel art use a small grid (16x16, 32x32 or 64x64) of <rect> cells with " +
        "shape-rendering=\"crispEdges\" and one rect per coloured pixel run. Keep the text around it to one or two short sentences."

    class ToolCall(val id: String, val name: String, val args: String)
    class Turn(val text: String, val calls: List<ToolCall>)

    /** One model turn, streamed. [msgs] is the whole conversation in OpenAI format; with [tools] the model may answer with tool calls instead of (or after) text. */
    fun completion(p: P, msgs: JSONArray, tools: JSONArray?, client: OkHttpClient, onCall: (Call) -> Unit, onDelta: (String) -> Unit): Turn {
        // GitHub Models takes the GitHub login's token, which the bridge hands over; the others use the key typed in Settings
        val k = if (p.auth == "gh") Engine.ghTokenBlocking().orEmpty() else key(p.id)
        if (p.auth == "gh" && k.isEmpty()) throw IOException("Not signed in to GitHub. Sign in under Setup > GitHub (or Settings > AI), with the Termux connection running.")
        if (p.needsKey && k.isEmpty()) throw IOException("No API key for ${p.name}. Add it in Settings > AI.")
        val base = base(p)
        if (base.isEmpty()) throw IOException("No server address for ${p.name}. Add it in Settings > AI.")
        val model = model(p)
        if (model.isEmpty()) throw IOException("No model name for ${p.name}. Add it in Settings > AI.")
        val body = JSONObject().put("model", model).put("stream", true).put("messages", msgs)
        if (tools != null && tools.length() > 0) body.put("tools", tools)
        val rb = Request.Builder().url("$base/chat/completions").post(body.toString().toRequestBody("application/json".toMediaType()))
        if (k.isNotEmpty()) rb.header("Authorization", "Bearer $k")
        if (p.auth == "gh") { rb.header("Accept", "application/vnd.github+json"); rb.header("X-GitHub-Api-Version", "2022-11-28") }
        val call = client.newCall(rb.build())
        onCall(call)
        val text = StringBuilder()
        val ids = HashMap<Int, String>(); val names = HashMap<Int, String>(); val args = HashMap<Int, StringBuilder>()
        call.execute().use { resp ->
            if (!resp.isSuccessful) {
                val raw = resp.body?.string().orEmpty()
                val msg = try { JSONObject(raw).optJSONObject("error")?.optString("message").orEmpty().ifEmpty { raw.take(200) } } catch (e: Exception) {
                    try { JSONArray(raw).optJSONObject(0)?.optJSONObject("error")?.optString("message").orEmpty().ifEmpty { raw.take(200) } } catch (e2: Exception) { raw.take(200) }
                }
                throw IOException("${p.name} ${resp.code}: $msg")
            }
            val src = resp.body!!.source()
            while (true) {
                val line = src.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val d = line.removePrefix("data:").trim()
                if (d == "[DONE]") break
                try {
                    val delta = JSONObject(d).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta") ?: continue
                    val t = delta.optString("content")
                    if (t.isNotEmpty() && t != "null") { text.append(t); onDelta(t) }
                    val tc = delta.optJSONArray("tool_calls")
                    if (tc != null) for (n in 0 until tc.length()) {
                        val c = tc.getJSONObject(n); val idx = c.optInt("index", n)
                        c.optString("id").takeIf { it.isNotEmpty() && it != "null" }?.let { ids[idx] = it }
                        val f = c.optJSONObject("function")
                        f?.optString("name")?.takeIf { it.isNotEmpty() && it != "null" }?.let { names[idx] = it }
                        f?.optString("arguments")?.takeIf { it != "null" }?.let { args.getOrPut(idx) { StringBuilder() }.append(it) }
                    }
                } catch (e: org.json.JSONException) { }
            }
        }
        val calls = names.keys.sorted().map { ToolCall(ids[it] ?: ("call_" + it), names[it]!!, args[it]?.toString().orEmpty()) }
        return Turn(text.toString(), calls)
    }

    // ---- Ollama: list what is installed and download more ------------------------------------------------

    private fun ollamaRoot(): String = base(byId("ollama")).removeSuffix("/v1")

    /** Names of the models installed in Ollama, or null when it cannot be reached. */
    fun ollamaModels(client: OkHttpClient): List<String>? = try {
        client.newCall(Request.Builder().url(ollamaRoot() + "/api/tags").build()).execute().use { r ->
            if (!r.isSuccessful) return null
            val a = JSONObject(r.body!!.string()).optJSONArray("models") ?: return emptyList()
            (0 until a.length()).map { a.getJSONObject(it).optString("name") }.filter { it.isNotEmpty() }
        }
    } catch (e: Exception) { null }

    /** Downloads a model; [onStatus] gets lines like "pulling 4f2a… 37%". Returns an error text or null on success. */
    fun ollamaPull(client: OkHttpClient, name: String, onStatus: (String) -> Unit): String? = try {
        val body = JSONObject().put("name", name).put("stream", true).toString().toRequestBody("application/json".toMediaType())
        client.newCall(Request.Builder().url(ollamaRoot() + "/api/pull").post(body).build()).execute().use { r ->
            if (!r.isSuccessful) return "Ollama ${r.code}: ${r.body?.string().orEmpty().take(160)}"
            val src = r.body!!.source()
            var err: String? = null
            while (true) {
                val line = src.readUtf8Line() ?: break
                val o = try { JSONObject(line) } catch (e: Exception) { continue }
                if (o.has("error")) { err = o.optString("error"); break }
                val total = o.optLong("total"); val done = o.optLong("completed")
                onStatus(o.optString("status") + if (total > 0) " ${done * 100 / total}%" else "")
            }
            err
        }
    } catch (e: Exception) { "Cannot reach Ollama (${e.message}). Is it running?" }

    /** Every model the provider offers (OpenAI-style GET /models; GitHub Models has its own catalog): id to "free". Null on failure, with the reason in [err]. */
    fun listModels(p: P, client: OkHttpClient, err: (String) -> Unit): List<Pair<String, Boolean>>? = try {
        val gh = p.auth == "gh"
        val k = if (gh) Engine.ghTokenBlocking().orEmpty() else key(p.id)
        if (gh && k.isEmpty()) { err("Sign in to GitHub first."); null }
        else if (p.needsKey && k.isEmpty()) { err("Add the API key first."); null } else {
            val rb = Request.Builder().url(if (gh) "https://models.github.ai/catalog/models" else base(p) + "/models")
            if (k.isNotEmpty()) rb.header("Authorization", "Bearer $k")
            if (gh) { rb.header("Accept", "application/vnd.github+json"); rb.header("X-GitHub-Api-Version", "2022-11-28") }
            client.newCall(rb.build()).execute().use { r ->
                if (!r.isSuccessful) { err("${p.name} ${r.code}: ${r.body?.string().orEmpty().take(160)}"); null }
                else if (gh) {
                    val a = JSONArray(r.body!!.string())
                    (0 until a.length()).map { a.getJSONObject(it).optString("id") }.filter { it.isNotEmpty() }.sorted().map { it to true }   // every GitHub model is free within its rate limits
                } else {
                    val a = JSONObject(r.body!!.string()).optJSONArray("data") ?: JSONArray()
                    (0 until a.length()).map { a.getJSONObject(it) }.map { o ->
                        val free = o.optJSONObject("pricing")?.let { it.optString("prompt") == "0" && it.optString("completion") == "0" } ?: false
                        o.optString("id").removePrefix("models/") to free
                    }.filter { it.first.isNotEmpty() }.sortedBy { it.first }
                }
            }
        }
    } catch (e: Exception) { err(e.message ?: "failed"); null }
}

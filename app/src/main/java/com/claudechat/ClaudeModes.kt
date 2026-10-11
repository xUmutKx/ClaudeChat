package com.claudechat

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** The three ways the setup wizard can run Claude. Stored in Prefs.setupMode. */
object SetupMode {
    const val TERMUX = "termux"   // Claude Code inside Termux, through the bridge (the original way)
    const val ROOT = "root"       // the claude binary run directly through su
    const val API = "api"         // the Anthropic Messages API with the user's key
}

/** Root mode: checked live every time (no stored flag), with the same `su -c` calls Termux and PillAccess use. */
object RootMode {
    const val BIN = "/usr/local/lib/node_modules/@anthropic-ai/claude-code/bin/claude.exe"

    /** Output of one command as root, or null when su is missing or refused. Blocking: call off the main thread. */
    fun su(cmd: String): String? = try {
        val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        if (p.waitFor() == 0) out.trim() else null
    } catch (e: Exception) { null }

    /** (root granted right now, claude binary present). Blocking. */
    fun check(): Pair<Boolean, Boolean> = Pair(su("id -u") == "0", su("test -f '$BIN' && echo yes") == "yes")

    /** Blocking. The chat command needs root and the binary: the wizard shows which one is missing. */
    fun ready(): Boolean = check().let { it.first && it.second }
}

/** API mode: the key lives in Providers' settings store (Prefs.provCfg, like the other providers) under id "anthropic". */
object AnthropicApi {
    const val MODEL = "claude-opus-5-5"
    private const val ID = "anthropic"
    private const val URL = "https://api.anthropic.com/v1/messages"
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).build()

    fun key(): String = Providers.key(ID)

    /** A new key clears the earlier test: only a key that answered is "verified". */
    fun setKey(v: String) { Providers.set(ID, "key", v); Providers.set(ID, "tested", "") }

    /** True when the stored key answered the last test. */
    fun verified(): Boolean = key().isNotEmpty() && Providers.field(ID, "tested") == "1"

    private fun request(body: JSONObject): Request = Request.Builder().url(URL)
        .header("x-api-key", key()).header("anthropic-version", "2023-06-01")
        .post(body.toString().toRequestBody("application/json".toMediaType())).build()

    /** The API's own error text, never the key. */
    private fun errText(code: Int, raw: String): String {
        val msg = try { JSONObject(raw).optJSONObject("error")?.optString("message").orEmpty() } catch (e: Exception) { "" }
        return "Anthropic $code: ${msg.ifEmpty { raw.take(200) }}"
    }

    /** One short request with the stored key. Returns null when it answered, else the reason. Blocking. */
    fun test(): String? {
        if (key().isEmpty()) return "No key saved."
        val msgs = JSONArray().put(JSONObject().put("role", "user").put("content", "Reply with one word: OK"))
        // effort low and a small cap: the test costs a fraction of a cent
        val body = JSONObject().put("model", MODEL).put("max_tokens", 256).put("messages", msgs)
            .put("output_config", JSONObject().put("effort", "low"))
        return try {
            client.newCall(request(body)).execute().use { r ->
                val raw = r.body?.string().orEmpty()
                if (!r.isSuccessful) return errText(r.code, raw)
                val content = JSONObject(raw).optJSONArray("content") ?: JSONArray()
                val answered = (0 until content.length()).any { content.getJSONObject(it).optString("type") == "text" }
                if (!answered) return "Anthropic answered without text."
                Providers.set(ID, "tested", "1")
                null
            }
        } catch (e: Exception) { "Cannot reach Anthropic (${e.javaClass.simpleName})." }
    }

    /** Streams one answer from the conversation [msgs] (role/content, Anthropic format). Blocking; throws IOException with the API's message. */
    fun stream(msgs: JSONArray, onDelta: (String) -> Unit) {
        if (key().isEmpty()) throw IOException("No API key. Add it in Setup.")
        val body = JSONObject().put("model", MODEL).put("max_tokens", 16000).put("stream", true).put("messages", msgs)
        client.newCall(request(body)).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException(errText(resp.code, resp.body?.string().orEmpty()))
            val src = resp.body!!.source()
            while (true) {
                val line = src.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val o = try { JSONObject(line.removePrefix("data:").trim()) } catch (e: Exception) { continue }
                when (o.optString("type")) {
                    "content_block_delta" -> o.optJSONObject("delta")?.takeIf { it.optString("type") == "text_delta" }?.optString("text")?.let(onDelta)
                    "error" -> throw IOException("Anthropic: " + o.optJSONObject("error")?.optString("message").orEmpty())
                }
            }
        }
    }
}

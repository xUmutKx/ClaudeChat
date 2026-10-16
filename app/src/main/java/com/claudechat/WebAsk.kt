package com.claudechat

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.*
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Website accounts as chat providers. A hidden WebView sits in the process whose cookie store holds that account (slot 0 = main process,
 * slots 1-3 = their own ":webN" process, same as WebLoginActivity). It types the message into the site's input, clicks send and reads the newest answer back.
 * Messages: 1 = ask (site, prompt, chat; replyTo), 9 = cancel. Replies: 2 = text so far, 3 = done (final text), 4 = error ("login", "send", "timeout").
 */
open class WebAskService : Service() {
    open val slot = 0
    private val h = Handler(Looper.getMainLooper())
    private val me = Messenger(Handler(Looper.getMainLooper()) { m -> if (m.what == 1) ask(m) else if (m.what == 9) gen++; true })
    private var wv: WebView? = null
    @Volatile private var loaded = false
    private var key = ""
    private var gen = 0

    override fun onBind(i: Intent?): IBinder = me.binder

    private class Sel(val input: String, val send: String, val ans: String, val stop: String)
    private fun sel(site: String) = when (site) {
        "claude" -> Sel("div[contenteditable=\"true\"]", "button[aria-label=\"Send message\"],button[aria-label=\"Send Message\"]", ".font-claude-response", "button[aria-label=\"Stop response\"],button[aria-label=\"Stop Response\"]")
        "gemini" -> Sel("rich-textarea .ql-editor,div[contenteditable=\"true\"]", "button.send-button,button[aria-label*=\"Send\"]", "message-content", "button[aria-label*=\"Stop\"]")
        "grok" -> Sel("textarea,div[contenteditable=\"true\"]", "button[type=\"submit\"],button[aria-label=\"Submit\"]", ".response-content-markdown", "button[aria-label*=\"Stop\"]")
        else -> Sel("#prompt-textarea", "button[data-testid=\"send-button\"]", "[data-message-author-role=\"assistant\"]", "button[data-testid=\"stop-button\"]")
    }

    private fun web(): WebView = wv ?: WebView(applicationContext).apply {
        settings.javaScriptEnabled = true; settings.domStorageEnabled = true
        settings.userAgentString = settings.userAgentString.replace("; wv", "")
        CookieManager.getInstance().setAcceptCookie(true); CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        webViewClient = object : WebViewClient() { override fun onPageFinished(v: WebView?, u: String?) { loaded = true } }
        // never attached to a window: give it a size so the site lays out and its scripts run
        measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY)); layout(0, 0, 1080, 1920)
        onResume(); wv = this
    }

    override fun onCreate() { super.onCreate(); if (slot > 0) try { WebView.setDataDirectorySuffix("web$slot") } catch (e: Exception) { } }

    private fun ask(m: Message) {
        val to = m.replyTo ?: return
        val d = m.data
        val site = WebAccounts.site(d.getString("site").orEmpty()); val s = sel(site.id)
        val prompt = d.getString("prompt").orEmpty(); val k = site.id + d.getString("chat").orEmpty()
        val my = ++gen
        fun reply(what: Int, t: String) { try { to.send(Message.obtain(null, what).apply { data = Bundle().apply { putString("t", t) } }) } catch (e: Exception) { } }
        val w = web()
        if (k != key || w.url == null) { key = k; loaded = false; w.loadUrl(site.url) }
        val state = "(function(){var i=document.querySelector(${JSONObject.quote(s.input)});var a=document.querySelectorAll(${JSONObject.quote(s.ans)});" +
            "return JSON.stringify({i:!!i,n:a.length,t:a.length?a[a.length-1].innerText:'',g:!!document.querySelector(${JSONObject.quote(s.stop)}),u:location.href})})()"
        val type = "(function(p){var i=document.querySelector(${JSONObject.quote(s.input)});i.focus();if(i.tagName=='TEXTAREA'||i.tagName=='INPUT'){" +
            "Object.getOwnPropertyDescriptor(i.tagName=='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype,'value').set.call(i,p);i.dispatchEvent(new Event('input',{bubbles:true}))}" +
            "else{document.execCommand('selectAll');document.execCommand('insertText',false,p)}return 1})(${JSONObject.quote(prompt)})"
        val click = "(function(){var b=document.querySelector(${JSONObject.quote(s.send)});if(b&&!b.disabled){b.click();return 1}return 0})()"
        val t0 = SystemClock.uptimeMillis()
        var phase = 0; var before = 0; var sent = 0L; var last = ""; var stable = 0
        fun tick() {
            if (my != gen) return
            val el = SystemClock.uptimeMillis() - t0
            w.evaluateJavascript(state) { raw ->
                if (my != gen) return@evaluateJavascript
                val o = try { JSONObject(JSONTokener2.str(raw)) } catch (e: Exception) { null }
                val again = { h.postDelayed({ tick() }, 800) }
                when (phase) {
                    0 -> {
                        val u = o?.optString("u").orEmpty()
                        if (loaded && o?.optBoolean("i") != true && Regex("login|signin|sign-in|sign_in|/auth|accounts\\.google").containsMatchIn(u)) { reply(4, "login"); gen++ }
                        else if (loaded && o?.optBoolean("i") == true) {
                            before = o?.optInt("n") ?: 0; w.evaluateJavascript(type, null); phase = 1; sent = el; h.postDelayed({ tick() }, 700)
                        } else if (el > 40000) { reply(4, "login"); gen++ } else again()
                    }
                    1 -> {
                        if (o != null && (o.optBoolean("g") || o.optInt("n") > before)) { phase = 2; again() }
                        else if (el - sent > 20000) { reply(4, "send"); gen++ }
                        else { w.evaluateJavascript(click, null); again() }
                    }
                    else -> {
                        val t = if (o != null && o.optInt("n") > before) o.optString("t") else ""
                        if (t.isNotEmpty() && t != last) { last = t; stable = 0; reply(2, t) }
                        else if (t.isNotEmpty() && o?.optBoolean("g") != true && ++stable >= 3) { reply(3, last); gen++ }
                        else if (el > 240000) { if (last.isNotEmpty()) reply(3, last) else reply(4, "timeout"); gen++ }
                        else again()
                    }
                }
            }
        }
        tick()
    }
}
class WebAskService1 : WebAskService() { override val slot = 1 }
class WebAskService2 : WebAskService() { override val slot = 2 }
class WebAskService3 : WebAskService() { override val slot = 3 }

/** evaluateJavascript hands back a JSON-quoted string: unquote it. */
private object JSONTokener2 { fun str(raw: String?): String = org.json.JSONTokener(raw ?: "null").nextValue().let { if (it is String) it else "{}" } }

/** The app side: one bound connection per slot, kept for the life of the app so the hidden page (and its conversation) survives between messages. */
object WebAsk {
    private val conns = ConcurrentHashMap<Int, Messenger>()
    private val cls = listOf(WebAskService::class.java, WebAskService1::class.java, WebAskService2::class.java, WebAskService3::class.java)

    private suspend fun conn(slot: Int): Messenger = conns[slot] ?: suspendCancellableCoroutine { c ->
        val sc = object : android.content.ServiceConnection {
            override fun onServiceConnected(n: ComponentName?, b: IBinder?) { val m = Messenger(b); conns[slot] = m; if (c.isActive) c.resume(m) }
            override fun onServiceDisconnected(n: ComponentName?) { conns.remove(slot) }
        }
        if (!App.ctx.bindService(Intent(App.ctx, cls[slot.coerceIn(0, 3)]), sc, Context.BIND_AUTO_CREATE)) c.resumeWithException(IOException("Cannot start the website session"))
    }

    /** Sends [prompt] through the account; [onText] gets the whole answer so far. Returns null when done, else "login", "send" or "timeout". */
    suspend fun ask(site: String, slot: Int, chat: String, prompt: String, onText: (String) -> Unit): String? {
        val to = conn(slot)
        val done = CompletableDeferred<String?>()
        val back = Messenger(Handler(Looper.getMainLooper()) { m ->
            val t = m.data.getString("t").orEmpty()
            when (m.what) { 2 -> onText(t); 3 -> { onText(t); done.complete(null) }; 4 -> done.complete(t) }
            true
        })
        to.send(Message.obtain(null, 1).apply { replyTo = back; data = Bundle().apply { putString("site", site); putString("chat", chat); putString("prompt", prompt) } })
        try { return done.await() } finally { if (!done.isCompleted) try { to.send(Message.obtain(null, 9)) } catch (e: Exception) { } }
    }
}

package com.claudechat

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import kotlinx.coroutines.flow.MutableStateFlow
import java.security.SecureRandom
import java.util.Locale

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        ctx = this
        Prefs.init(this)
        Engine.load(this)
    }

    companion object {
        lateinit var ctx: Application
    }
}

/** Wraps a context so resources follow the in-app language (English by default). */
fun Context.localized(): Context {
    val c = Configuration(resources.configuration)
    c.setLocale(Locale(Prefs.lang.value))
    return createConfigurationContext(c)
}

fun appStr(id: Int, vararg args: Any): String = App.ctx.localized().getString(id, *args)

object Prefs {
    private lateinit var sp: SharedPreferences

    class S(private val key: String, private val def: String) {
        val flow = MutableStateFlow(sp.getString(key, def) ?: def)
        var value: String
            get() = flow.value
            set(v) { flow.value = v; sp.edit().putString(key, v).apply() }
    }

    class B(private val key: String, private val def: Boolean) {
        val flow = MutableStateFlow(sp.getBoolean(key, def))
        var value: Boolean
            get() = flow.value
            set(v) { flow.value = v; sp.edit().putBoolean(key, v).apply() }
    }

    lateinit var theme: S       // system | light | dark | amoled
    lateinit var lang: S        // en | tr
    lateinit var overlay: S     // off | pill | line | curtain
    lateinit var mode: S        // default | acceptEdits | plan | bypassPermissions
    lateinit var cwd: S
    lateinit var attachDir: S   // where Download/projects appears inside Ubuntu
    lateinit var model: S       // '' = CLI default; alias or full id
    lateinit var effort: S      // '' | low | medium | high | xhigh | max
    lateinit var slash: S       // comma list of slash commands reported by the CLI
    lateinit var port: S
    lateinit var distro: S
    lateinit var token: S
    lateinit var session: S
    lateinit var limits: S       // five-hour|weekly utilisation (0..1) and reset epochs: u5|r5|u7|r7
    lateinit var black: B        // full-screen black layer under the island; double-tap to leave
    lateinit var blackDim: S     // black mode screen brightness, percent (1..100)
    lateinit var blackClock: B   // black mode widgets
    lateinit var blackDate: B
    lateinit var blackStatus: B
    lateinit var blackLast: B
    lateinit var blackBattery: B
    lateinit var blackMascot: B
    lateinit var blackText: S    // free text widget
    lateinit var blackFont: S    // clock font: thin / regular / bold / mono / serif / cursive
    lateinit var blackStyle: S   // digital / stacked / analog / ticks
    lateinit var blackSize: S    // clock size, sp
    lateinit var dynamic: B
    lateinit var keepAlive: B
    lateinit var autoStart: B
    lateinit var screenOn: B    // keep the screen on while the curtain is showing

    fun init(c: Context) {
        sp = c.getSharedPreferences("cc", 0)
        theme = S("theme", "system")
        lang = S("lang", "en")
        overlay = S("overlay", "pill")
        mode = S("mode", "acceptEdits")
        cwd = S("cwd", "/root/projects")
        attachDir = S("attachDir", "/sdcard/Download/projects")
        model = S("model", "")
        effort = S("effort", "")
        slash = S("slash", "")
        port = S("port", "8787")
        distro = S("distro", "ubuntu")
        token = S("token", "")
        session = S("session", "")
        limits = S("limits", "")
        black = B("black", false)
        blackDim = S("blackDim", "15")
        blackClock = B("blackClock", true)
        blackDate = B("blackDate", true)
        blackStatus = B("blackStatus", true)
        blackLast = B("blackLast", true)
        blackBattery = B("blackBattery", true)
        blackMascot = B("blackMascot", false)
        blackText = S("blackText", "")
        blackFont = S("blackFont", "outfit")
        blackSize = S("blackSize", "72")
        blackStyle = S("blackStyle", "digital")
        dynamic = B("dynamic", false)
        keepAlive = B("keepAlive", true)
        autoStart = B("autoStart", true)
        screenOn = B("screenOn", true)
        if (token.value.isEmpty()) token.value = randomToken()
    }

    fun randomToken(): String {
        val chars = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val r = SecureRandom()
        return (1..20).map { chars[r.nextInt(chars.length)] }.joinToString("")
    }
}

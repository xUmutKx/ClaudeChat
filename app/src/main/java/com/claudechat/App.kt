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
    lateinit var model: S       // default for chats without their own choice; '' = CLI default; alias or full id
    /** Changes whenever one chat's model changes, so the screens that show it update. */
    val chatModelTick = MutableStateFlow(0)
    /** The model of one chat: its own choice, else the default from Options. */
    fun chatModel(id: String): String = sp.getString("cm_$id", null) ?: model.value
    fun setChatModel(id: String, v: String) { sp.edit().putString("cm_$id", v).apply(); chatModelTick.value++ }
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
    lateinit var danceMode: S    // mascot animation: steps (frame by frame) / smooth
    lateinit var pillColor: S   // pill background: black / white
    lateinit var agentTools: B   // let other AIs use files and commands
    lateinit var agentRoot: B    // run their commands as root
    lateinit var agentDir: S     // the folder they may work in
    lateinit var mascotChar: S   // auto (follows the AI) / claude / whale / rabbit / ...
    lateinit var provider: S     // claude / gemini / deepseek / ...
    lateinit var provCfg: S      // JSON: per provider key, model, base
    lateinit var studio: B       // Studio mode: pictures as SVG / pixel art
    lateinit var pillBubble: S   // show the pill's messages as a speech bubble under it
    lateinit var pillGap: S      // extra room around the camera ring, dp (0..8)
    lateinit var showHeaderMascot: S // "1"/"0": mascot at the top left of the chat screen
    lateinit var showNotifMascot: S  // "1"/"0": mascot inside the keep-alive notification
    lateinit var mascotScene: S      // background scene behind the mascot: none / beach / forest / home / library / cave / sea / plane
    lateinit var sceneDim: S         // percent of black drawn over the scene (behind the mascot), 0..90
    lateinit var pillEvents: S       // "1": the pill only appears when something changes
    lateinit var pillBg: S           // pill background opacity, percent (0..100)
    lateinit var pillAlpha: S        // whole pill opacity, percent (10..100)
    lateinit var pillHold: S         // seconds the event pill stays open (1..15)
    lateinit var sceneInPill: S      // "1": the chosen scene is also drawn behind the mascot in the pill
    lateinit var pillOutfit: S   // mascot outfit: none / wizard / crown / party / bow
    lateinit var pillSleepHide: S // seconds after finishing until the sleeping pill hides itself (0 = never, slider 10..600)
    lateinit var pillExtra: S    // extra pill length at each end, px
    lateinit var mascotSkin: S   // body colour of the character on screen now (Mascots.syncSkin keeps it in step)
    lateinit var mascotSkins: S  // every character's own body colour, as JSON {"whale":"blue",...}
    lateinit var bubbleX: S      // floating bubble position, px
    lateinit var bubbleY: S
    lateinit var chatBg: S       // chat-screen background: none / dusk / mint / rose / sand / grid / stars
    lateinit var buddySize: S
    lateinit var buddyOpacity: S
    lateinit var buddyBubble: S
    lateinit var buddyBubbleWork: S
    lateinit var buddyBubbleSize: S
    lateinit var buddyBubbleWidth: S
    lateinit var buddyBubbleLines: S
    lateinit var buddyBubbleHold: S
    lateinit var buddyBubbleOpacity: S
    lateinit var buddyBubbleShape: S
    lateinit var buddyBubbleTone: S
    lateinit var buddyTail: S
    lateinit var buddyGravity: S
    lateinit var buddyBounce: S
    lateinit var buddySleepAfter: S
    lateinit var buddyBlanket: S
    lateinit var buddyIdle: S
    lateinit var buddyTap: S
    lateinit var buddySnap: S
    lateinit var buddyX: S
    lateinit var buddyHideAsleep: S
    lateinit var buddyY: S
    lateinit var buddyOn: B       // the floating buddy, independent of the bar overlay (pill / line / curtain)
    lateinit var mascotHome: S    // where the mascot lives while both the pill and the buddy are on: pill / buddy
    lateinit var pillHandle: S    // touch strip under the pill: 0 off, 1 invisible, 2 faintly visible
    lateinit var bubbleStyle: S  // message bubbles: soft / round / square / outline
    lateinit var dynamic: B
    lateinit var keepAlive: B
    lateinit var autoStart: B
    lateinit var chatNotes: B
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
        blackMascot = B("blackMascot", true)
        blackText = S("blackText", "")
        blackFont = S("blackFont", "outfit")
        blackSize = S("blackSize", "72")
        blackStyle = S("blackStyle", "digital")
        danceMode = S("danceMode", "steps")
        pillColor = S("pillColor", "black")
        pillGap = S("pillGap", "1")
        pillBubble = S("pillBubble", "1")
        mascotChar = S("mascotChar", "auto")
        agentTools = B("agentTools", true)
        agentRoot = B("agentRoot", false)
        agentDir = S("agentDir", "/sdcard/Download/projects")
        provider = S("provider", "claude")
        provCfg = S("provCfg", "{}")
        studio = B("studio", false)
        showHeaderMascot = S("showHeaderMascot", "1")
        showNotifMascot = S("showNotifMascot", "1")
        mascotScene = S("mascotScene", "none")
        sceneDim = S("sceneDim", "35")
        pillEvents = S("pillEvents", "0")
        pillHold = S("pillHold", "4")
        pillBg = S("pillBg", "100")
        pillAlpha = S("pillAlpha", "100")
        sceneInPill = S("sceneInPill", "0")
        pillOutfit = S("pillOutfit", "none")
        pillSleepHide = S("pillSleepSecs", "120")
        pillExtra = S("pillExtra", "0")
        mascotSkin = S("mascotSkin", "orange")
        mascotSkins = S("mascotSkins", "")
        bubbleX = S("bubbleX", "")
        bubbleY = S("bubbleY", "")
        chatBg = S("chatBg", "none")
        bubbleStyle = S("bubbleStyle", "soft")
        buddySize = S("buddySize", "76")
        buddyOpacity = S("buddyOpacity", "100")
        buddyBubble = S("buddyBubble", "1")
        buddyBubbleWork = S("buddyBubbleWork", "1")
        buddyBubbleSize = S("buddyBubbleSize", "13")
        buddyBubbleWidth = S("buddyBubbleWidth", "230")
        buddyBubbleLines = S("buddyBubbleLines", "5")
        buddyBubbleHold = S("buddyBubbleHold", "12")
        buddyBubbleOpacity = S("buddyBubbleOpacity", "96")
        buddyBubbleShape = S("buddyBubbleShape", "pill")
        buddyBubbleTone = S("buddyBubbleTone", "light")
        buddyTail = S("buddyTail", "1")
        buddyGravity = S("buddyGravity", "0")
        buddyBounce = S("buddyBounce", "65")
        buddySleepAfter = S("buddySleepAfter", "60")
        buddyBlanket = S("buddyBlanket", "1")
        buddyIdle = S("buddyIdle", "1")
        buddyTap = S("buddyTap", "bubble")
        buddySnap = S("buddySnap", "0")
        buddyX = S("buddyX", "")
        buddyHideAsleep = S("buddyHideAsleep", "0")
        buddyY = S("buddyY", "")
        buddyOn = B("buddyOn", false)
        mascotHome = S("mascotHome", "pill")
        pillHandle = S("pillHandle", "1")
        dynamic = B("dynamic", false)
        keepAlive = B("keepAlive", true)
        autoStart = B("autoStart", true)
        chatNotes = B("chatNotes", true)
        screenOn = B("screenOn", true)
        if (token.value.isEmpty()) token.value = randomToken()
        // 0.9.45: the buddy became its own switch (it can run next to the pill), and tapping the mascot opens a small chat bubble instead of the app
        if (!sp.getBoolean("m_buddy_switch", false)) {
            if (overlay.value == "buddy") { overlay.value = "off"; buddyOn.value = true; mascotHome.value = "buddy" }
            if (buddyTap.value == "chat") buddyTap.value = "bubble"
            sp.edit().putBoolean("m_buddy_switch", true).apply()
        }
        // without the buddy the mascot can only live in the pill (a saved "buddy" with no buddy left the pill empty)
        if (!buddyOn.value && mascotHome.value != "pill") mascotHome.value = "pill"
        Mascots.syncSkin()
    }

    fun randomToken(): String {
        val chars = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val r = SecureRandom()
        return (1..20).map { chars[r.nextInt(chars.length)] }.joinToString("")
    }
}

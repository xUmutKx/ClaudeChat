package com.claudechat

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map

object Notifier {
    const val CH_KEEP = "keep_min"
    const val CH_DONE = "done_quiet"

    fun channels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_KEEP, c.getString(R.string.notif_channel_keep), NotificationManager.IMPORTANCE_MIN))
        nm.deleteNotificationChannel("done")
        nm.createNotificationChannel(NotificationChannel(CH_DONE, c.getString(R.string.notif_channel_done), NotificationManager.IMPORTANCE_LOW))
    }

    /** A Gradle build the bridge saw finished or failed. */
    fun build(c: Context, ok: Boolean, project: String) {
        val l = c.localized()
        val n = NotificationCompat.Builder(l, CH_DONE).setSmallIcon(Outfit.statIcon())
            .setContentTitle(if (ok) tr("Build finished", "Derleme bitti") else tr("Build failed", "Derleme başarısız")).setContentText(project)
            .setOnlyAlertOnce(true).setSilent(true).setContentIntent(open(c)).setAutoCancel(true).build()
        try { c.getSystemService(NotificationManager::class.java).notify(3, n) } catch (e: SecurityException) { }
    }

    fun open(c: Context): PendingIntent =
        PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun done(c: Context, error: Boolean, text: String) {
        if (Prefs.overlay.value != "off" && !error) return // the island already shows the result
        val l = c.localized()
        val n = NotificationCompat.Builder(l, CH_DONE)
            .setSmallIcon(Outfit.statIcon())
            .setContentTitle(l.getString(if (error) R.string.error_title else R.string.done_title))
            .setContentText(text.take(160))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.take(600)))
            .setOnlyAlertOnce(true).setSilent(true).setGroup("cc_done").setContentIntent(open(c)).setAutoCancel(true).build()
        try { c.getSystemService(NotificationManager::class.java).notify(2, n) } catch (e: SecurityException) { }
    }
}

class KeepAliveService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wl: PowerManager.WakeLock? = null
    internal lateinit var overlay: Overlay
    // (internal for relayout)
    private var stopJob: Job? = null

    override fun attachBaseContext(b: Context) = super.attachBaseContext(b.localized())
    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifier.channels(this)
        startFg(getString(R.string.status_idle))
        overlay = Overlay(applicationContext)
        live = this
        scope.launch(Dispatchers.IO) { // a Gradle build the bridge sees finishes or fails: vibrate in the user's pattern
            var seen = ""
            while (true) {
                delay(6_000)
                if (!Vib.anyBuild()) continue
                val l = Engine.buildLog() ?: continue
                val ok = l.third.contains("BUILD SUCCESSFUL"); val bad = l.third.contains("BUILD FAILED")
                if (!ok && !bad) continue
                val key = l.first + ok
                if (key == seen) continue
                val first = seen.isEmpty()
                seen = key
                if (first && l.second > 90) continue // an old log from before: do not announce it
                Vib.event(if (ok) "build_ok" else "build_fail")
                if (Vib.on(if (ok) "build_ok" else "build_fail")) Notifier.build(applicationContext, ok, l.first.substringAfterLast('/'))
            }
        }
        scope.launch {
            combine(Engine.overall, Engine.overallDetail, Engine.messages, Prefs.overlay.flow.combine(Prefs.black.flow) { s, b -> if (b) (if (s == "off") "pill" else s) + "+black" else s }, Prefs.keepAlive.flow) { st, det, msgs, style, keep ->
                Snap(st, det, msgs.lastOrNull { it.role == Role.Claude }?.text.orEmpty(), style, keep)
            }.collect { apply(it) }
        }
        scope.launch { // watchdog: if Termux was killed and the bridge died, bring it back
            delay(20_000)
            var fails = 0
            while (true) {
                if (Prefs.autoStart.value && !Engine.overall.value.running) {
                    if (Engine.ping() == 200) fails = 0
                    else if (++fails >= 4) { fails = 0; Termux.startBridge(applicationContext, kill = false) }
                }
                delay(20_000)
            }
        }
        scope.launch { // black-mode options changed: rebuild the black layer
            kotlinx.coroutines.flow.merge(
                Prefs.blackDim.flow.map { }, Prefs.blackText.flow.map { },
                Prefs.blackClock.flow.map { }, Prefs.blackDate.flow.map { }, Prefs.blackStatus.flow.map { },
                Prefs.blackLast.flow.map { }, Prefs.blackBattery.flow.map { }, Prefs.blackMascot.flow.map { },
                Prefs.blackFont.flow.map { }, Prefs.blackStyle.flow.map { }, Prefs.blackSize.flow.map { },
            ).collect { overlay.refreshBlack() }
        }
        scope.launch { // pill look changed (colour, gap, outfit): rebuild it
            kotlinx.coroutines.flow.merge(Prefs.pillColor.flow.map { }, Prefs.pillBg.flow.map { }, Prefs.pillGlow.flow.map { }, Prefs.mascotPose.flow.map { }, Prefs.pillGap.flow.map { }, Prefs.pillExtra.flow.map { }, Prefs.pillOutfit.flow.map { }, Prefs.danceMode.flow.map { }, Prefs.pillEvents.flow.map { }, Prefs.pillHold.flow.map { }, Prefs.pillHandle.flow.map { })
                .drop(7).collect { overlay.onRotate(); startFg(fgText, fgWorking ?: false) }
        }
        scope.launch { // the buddy was switched on or off, or the mascot moved house (pill <-> buddy): draw both again
            kotlinx.coroutines.flow.merge(Prefs.buddyOn.flow.map { }, Prefs.mascotHome.flow.map { }).drop(2).collect {
                if (Prefs.buddyOn.value && !Prefs.keepAlive.value) KeepAliveService.start(applicationContext)
                overlay.redraw()
            }
        }
        scope.launch { // opacity: no rebuild, just redraw
            kotlinx.coroutines.flow.merge(Prefs.pillBg.flow.map { }, Prefs.pillAlpha.flow.map { }).drop(2).collect { overlay.refreshOpacity() }
        }
        scope.launch { // buddy look changed: rebuild it with the same state
            kotlinx.coroutines.flow.merge(Prefs.buddySize.flow.map { }, Prefs.buddyOpacity.flow.map { }, Prefs.buddyBubble.flow.map { },
                Prefs.buddyBubbleSize.flow.map { }, Prefs.buddyBubbleWidth.flow.map { }, Prefs.buddyBubbleLines.flow.map { }, Prefs.buddyBubbleOpacity.flow.map { },
                Prefs.buddyBubbleShape.flow.map { }, Prefs.buddyBubbleTone.flow.map { }, Prefs.buddyTail.flow.map { }, Prefs.buddyGravity.flow.map { },
                Prefs.buddyBlanket.flow.map { }, Prefs.pillOutfit.flow.map { }, Prefs.mascotSkin.flow.map { }).drop(14).collect { overlay.buddy.refresh() }
        }
        scope.launch { Engine.demoShell.collect { overlay.redraw(); startFg(fgText, fgWorking ?: false) } }
        scope.launch { Engine.shell.collect { startFg(fgText, fgWorking ?: false) } }
        // a build starts or ends: the notification changes at once, instead of keeping the last "Done"
        scope.launch { Engine.buildRunning.collect { startFg(label(lastSt, ""), lastSt.running) } }
        scope.launch { Prefs.mascotSkin.flow.collect { overlay.redraw(); startFg(fgText, fgWorking ?: false) } }
        scope.launch { kotlinx.coroutines.flow.merge(Prefs.mascotChar.flow.map { }, Prefs.provider.flow.map { }).drop(2).collect { overlay.redraw(); overlay.buddy.refresh(); startFg(fgText, fgWorking ?: false) } }
        scope.launch { combine(AppState.foreground, Engine.demoShell) { fg, demo -> fg && !demo }.collect { overlay.setQuiet(it) } } // the shell preview also shows on the pill // chat open: its header mascot shows the status, so the pill hides
    }

    private data class Snap(val st: Status, val det: String, val last: String, val style: String, val keep: Boolean)

    // a running build is what the notification is about, whatever Claude's own status says
    private fun label(st: Status, det: String) = if (Engine.buildRunning.value) tr("Compiling", "Derleniyor") else when (st) {
        Status.Working, Status.Background -> det.ifEmpty { getString(R.string.status_working) }
        Status.Done -> getString(R.string.status_done)
        Status.Error -> getString(R.string.status_error)
        Status.Offline -> getString(R.string.status_offline)
        Status.Idle -> getString(R.string.status_idle)
    }

    private var lastSt = Status.Idle
    /** A shell command or background job runs: the notification shows the mascot at its laptop instead of dancing. */
    private fun atComputer() = lastSt == Status.Background || (lastSt == Status.Working && Engine.shell.value) || Engine.demoShell.value

    private fun apply(s: Snap) {
        lastSt = s.st
        startFg(label(s.st, s.det), s.st.running)
        overlay.render(s.style, s.st, s.det, s.last)
        // the CPU wake lock is held only while Claude works; the service itself can still stay up for Termux
        val hold = s.st.running
        if (hold && wl == null) {
            wl = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "claudechat:keep").apply { acquire() }
        } else if (!hold) {
            wl?.release(); wl = null
        }
        stopJob?.cancel()
        if (!s.keep && !s.st.running && s.style == "off" && !Prefs.buddyOn.value && !Vib.anyBuild()) {
            stopJob = scope.launch { delay(12_000); stopSelf() }
        }
    }

    private var fgText = ""
    private var fgWorking: Boolean? = null
    private var fgOutfit = ""

    // A plain notification with the mascot as its large icon: a custom RemoteViews layout crashed the app on start
    // (BadForegroundServiceNotificationException, "Couldn't inflate contentViews"), so no layout is inflated here.
    private fun picture(): android.graphics.Bitmap {
        val pc = atComputer()
        val asleep = lastSt == Status.Idle && !Engine.buildRunning.value
        val faces = if (pc) Mascots.pc(false)
            else listOf(if (asleep) Mascots.r(R.drawable.ic_mascot_sleep18) else Mascots.r(R.drawable.ic_mascot18))
        val blanket = asleep && Prefs.buddyBlanket.value == "1"
        val bm = Mascots.bitmap(this, faces, Outfit.hat(), blanket, if (pc) emptyList() else Wear.worn(asleep))
        if (!lastSt.running) return bm
        val m = android.graphics.Matrix().apply { postRotate(Dance.ROT[frame % Dance.ROT.size], bm.width / 2f, bm.height / 2f) }
        return android.graphics.Bitmap.createBitmap(bm, 0, 0, bm.width, bm.height, m, true)
    }

    private var frame = 0
    private var danceJob: Job? = null

    // while Claude works the large icon steps through the dance frames, re-posted in place under the same id
    private fun danceLoop(working: Boolean) {
        danceJob?.cancel(); danceJob = null
        if (!working || Prefs.showNotifMascot.value != "1") return
        danceJob = scope.launch {
            while (true) {
                delay(Dance.FRAME_MS)
                frame++
                NotificationManagerCompat.from(this@KeepAliveService).notify(1, note(fgText, fgWorking == true))
            }
        }
    }

    private fun note(text: String, working: Boolean): android.app.Notification =
        NotificationCompat.Builder(this, Notifier.CH_KEEP)
            .setSmallIcon(Outfit.statIcon())
            .setContentTitle(getString(R.string.notif_keep_title))
            .setContentText(text)
            .apply { if (Prefs.showNotifMascot.value == "1") setLargeIcon(picture()) }
            .setPriority(NotificationCompat.PRIORITY_MIN).setVisibility(NotificationCompat.VISIBILITY_SECRET).setOngoing(true).setOnlyAlertOnce(true).setContentIntent(Notifier.open(this))
            .addAction(0, getString(R.string.notif_stop),
                PendingIntent.getService(this, 1, Intent(this, KeepAliveService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .build()

    private fun startFg(text: String, working: Boolean = false) {
        val outfit = Prefs.pillOutfit.value + Prefs.showNotifMascot.value + atComputer() + (lastSt == Status.Idle) + Mascots.current() + Prefs.mascotSkin.value + Prefs.buddyBlanket.value
        if (text == fgText && fgWorking == working && fgOutfit == outfit) return // identical: do not re-post the notification
        fgText = text; fgWorking = working; fgOutfit = outfit
        val n = note(text, working)
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
        danceLoop(working)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            scope.cancel(); overlay.hide()
            Engine.stopAll()
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    // the camera moves to another edge when the screen rotates: re-place (and re-orient) the pill
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::overlay.isInitialized) overlay.onRotate()
    }

    override fun onDestroy() {
        scope.cancel()
        live = null
        overlay.hide()
        wl?.release(); wl = null
        super.onDestroy()
    }

    companion object {
        @Volatile private var live: KeepAliveService? = null
        /** The accessibility layer came or went: rebuild the pill and the buddy on the right window layer. */
        fun relayout(c: Context) { live?.let { s -> android.os.Handler(android.os.Looper.getMainLooper()).post { s.overlay.relayout() } } }
        const val ACTION_STOP = "com.claudechat.STOP"
        fun start(c: Context) {
            try { ContextCompat.startForegroundService(c, Intent(c, KeepAliveService::class.java)) } catch (e: Exception) { }
        }
    }
}

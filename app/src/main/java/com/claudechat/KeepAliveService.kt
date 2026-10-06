package com.claudechat

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
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

    fun open(c: Context): PendingIntent =
        PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun done(c: Context, error: Boolean, text: String) {
        if (Prefs.overlay.value != "off" && !error) return // the island already shows the result
        val l = c.localized()
        val n = NotificationCompat.Builder(l, CH_DONE)
            .setSmallIcon(R.drawable.ic_stat_mascot)
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
    private lateinit var overlay: Overlay
    private var stopJob: Job? = null

    override fun attachBaseContext(b: Context) = super.attachBaseContext(b.localized())
    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifier.channels(this)
        startFg(getString(R.string.status_idle))
        overlay = Overlay(applicationContext)
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
                    else if (++fails >= 2) { fails = 0; Termux.startBridge(applicationContext) }
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
            kotlinx.coroutines.flow.merge(Prefs.pillColor.flow.map { }, Prefs.pillGap.flow.map { }, Prefs.pillOutfit.flow.map { }, Prefs.danceMode.flow.map { })
                .drop(4).collect { overlay.onRotate() }
        }
    }

    private data class Snap(val st: Status, val det: String, val last: String, val style: String, val keep: Boolean)

    private fun label(st: Status, det: String) = when (st) {
        Status.Working, Status.Background -> det.ifEmpty { getString(R.string.status_working) }
        Status.Done -> getString(R.string.status_done)
        Status.Error -> getString(R.string.status_error)
        Status.Offline -> getString(R.string.status_offline)
        Status.Idle -> getString(R.string.status_idle)
    }

    private fun apply(s: Snap) {
        startFg(label(s.st, s.det), s.st.running)
        overlay.render(s.style, s.st, s.det, s.last)
        val hold = s.keep || s.st.running
        if (hold && wl == null) {
            wl = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "claudechat:keep").apply { acquire() }
        } else if (!hold) {
            wl?.release(); wl = null
        }
        stopJob?.cancel()
        if (!s.keep && !s.st.running && s.style == "off") {
            stopJob = scope.launch { delay(12_000); stopSelf() }
        }
    }

    private var fgText = ""
    private var fgWorking: Boolean? = null

    // The flipper animates inside the notification by itself, so the notification is not re-posted to "dance".
    private fun view(text: String, working: Boolean): android.widget.RemoteViews =
        android.widget.RemoteViews(packageName, if (working) R.layout.notif_dance else R.layout.notif_still).apply {
            setTextViewText(R.id.ntitle, getString(R.string.notif_keep_title))
            setTextViewText(R.id.ntext, text)
        }

    private fun startFg(text: String, working: Boolean = false) {
        if (text == fgText && fgWorking == working) return // identical: do not re-post the notification
        fgText = text; fgWorking = working
        val n = NotificationCompat.Builder(this, Notifier.CH_KEEP)
            .setSmallIcon(R.drawable.ic_stat_mascot)
            .setContentTitle(getString(R.string.notif_keep_title))
            .setContentText(text)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(view(text, working))
            .setPriority(NotificationCompat.PRIORITY_MIN).setVisibility(NotificationCompat.VISIBILITY_SECRET).setOngoing(true).setOnlyAlertOnce(true).setContentIntent(Notifier.open(this))
            .addAction(0, getString(R.string.notif_stop),
                PendingIntent.getService(this, 1, Intent(this, KeepAliveService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
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
        overlay.hide()
        wl?.release(); wl = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.claudechat.STOP"
        fun start(c: Context) {
            try { ContextCompat.startForegroundService(c, Intent(c, KeepAliveService::class.java)) } catch (e: Exception) { }
        }
    }
}

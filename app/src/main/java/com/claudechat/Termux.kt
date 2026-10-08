package com.claudechat

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Base64

object Termux {
    const val PERM = "com.termux.permission.RUN_COMMAND"
    const val BIN = "/data/data/com.termux/files/usr/bin"
    const val SETUP_CMD =
        "mkdir -p ~/.termux; grep -qs '^allow-external-apps *= *true' ~/.termux/termux.properties || echo 'allow-external-apps=true' >> ~/.termux/termux.properties; termux-reload-settings"

    fun installed(c: Context) = try { c.packageManager.getPackageInfo("com.termux", 0); true } catch (e: Exception) { false }

    fun granted(c: Context) = c.checkSelfPermission(PERM) == PackageManager.PERMISSION_GRANTED

    /** Runs a shell script inside Termux (background). Returns null on success, else a string resource id. */
    fun run(c: Context, script: String): Int? {
        if (!installed(c)) return R.string.termux_not_installed
        if (!granted(c)) return R.string.err_termux_perm
        return try {
            val i = Intent("com.termux.RUN_COMMAND").setClassName("com.termux", "com.termux.app.RunCommandService")
            i.putExtra("com.termux.RUN_COMMAND_PATH", "$BIN/bash")
            i.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", script))
            i.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            c.startService(i)
            null
        } catch (e: Exception) {
            R.string.err_termux_perm
        }
    }

    private fun clean(s: String, extra: String = "") = s.filter { it.isLetterOrDigit() || it in "_-$extra" }

    fun cleanToken() = clean(Prefs.token.value)

    private const val USR = "/data/data/com.termux/files/usr"
    private const val HOME = "/data/data/com.termux/files/home"

    /** What the connection attempt is doing right now ("" when idle), shown under the spinner. */
    val note = kotlinx.coroutines.flow.MutableStateFlow("")

    /** The bridge script for the setup / repair script served to Termux. */
    fun bridgeB64(c: Context): String = Base64.encodeToString(c.assets.open("bridge.js").readBytes(), Base64.NO_WRAP)
    fun portStr(): String = Prefs.port.value.filter(Char::isDigit).ifEmpty { "8787" }
    fun distroName(): String = distro()

    /** Last output / error of the direct (root) launcher, shown when the bridge cannot be reached. */
    @Volatile var log = ""
    @Volatile private var proc: Process? = null

    private fun inner(c: Context, toFile: Boolean = false, kill: Boolean = true): String {
        val b64 = Base64.encodeToString(c.assets.open("bridge.js").readBytes(), Base64.NO_WRAP)
        val port = Prefs.port.value.filter(Char::isDigit).ifEmpty { "8787" }
        val token = cleanToken()
        return "mkdir -p /root/claudechat; : > /root/claudechat/bridge.log; echo $b64 | base64 -d > /root/claudechat/bridge.js; " +
            (if (kill) "pkill -f '^node /root/claudechat/bridge.js'; sleep 0.3;" else "") +
            "CC_TOKEN=$token CC_PORT=$port exec node /root/claudechat/bridge.js" + if (toFile) " >>/root/claudechat/bridge.log 2>&1" else ""
    }

    /** One line to paste into Termux by hand: needs no permission, root or allow-external-apps. */
    fun manualCmd(c: Context) =
        "termux-wake-lock 2>/dev/null; proot-distro login ${distro()} -- bash -lc " + shQuote(inner(c))

    fun openTermux(c: Context) = try {
        c.startActivity(c.packageManager.getLaunchIntentForPackage("com.termux")!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (e: Exception) { false }

    private fun distro() = clean(Prefs.distro.value, ".").ifEmpty { "ubuntu" }

    private fun termuxUid(c: Context) = try { c.packageManager.getApplicationInfo("com.termux", 0).uid } catch (e: Exception) { -1 }

    private fun pingBlocking(): Boolean = try {
        val port = Prefs.port.value.filter(Char::isDigit).ifEmpty { "8787" }
        val u = java.net.URL("http://127.0.0.1:$port/ping").openConnection() as java.net.HttpURLConnection
        u.connectTimeout = 1000; u.readTimeout = 1000
        u.setRequestProperty("X-Token", cleanToken())
        u.responseCode == 200
    } catch (e: Exception) { false }

    /** Starts the bridge through `su` without Termux's RUN_COMMAND (needs no allow-external-apps). */
    private fun suAttempt(c: Context, args: List<String>, script: String): Boolean {
        val p = try { ProcessBuilder(args).redirectErrorStream(true).start() } catch (e: Exception) { log += "\n${args.take(2)}: ${e.message}"; return false }
        proc?.let { try { it.destroy() } catch (e: Exception) { } }
        proc = p
        Thread {
            try {
                val buf = StringBuilder()
                p.inputStream.bufferedReader().forEachLine { buf.append(it).append('\n'); if (buf.length > 3000) buf.delete(0, buf.length - 3000); log = buf.toString() }
            } catch (e: Exception) { }
        }.start()
        repeat(40) { // cold start takes ~10s; a failing su launcher must not block the Termux fallback for minutes
            Thread.sleep(500)
            if (pingBlocking()) { guard(); return true }
        }
        return false
    }

    /** Root watchdog (detached): keeps proot/node/claude at a low oom_score_adj so Android's killer picks them last. */
    private fun guard() {
        val loop = "while :; do for p in \\\$(pgrep -f 'proot|claudechat/bridge|claude'); do echo -900 > /proc/\\\$p/oom_score_adj; done; sleep 15; done"
        try {
            ProcessBuilder("su", "-c", "pgrep -f 'oom_score_a[d]j' >/dev/null || setsid nohup sh -c \"$loop\" >/dev/null 2>&1 </dev/null &")
                .redirectErrorStream(true).start().waitFor()
        } catch (e: Exception) { }
    }

    private fun suChain(c: Context, kill: Boolean): Boolean {
        val uid = termuxUid(c)
        val env = "export PREFIX=$USR HOME=$HOME TMPDIR=$USR/tmp PATH=$USR/bin:$USR/bin/applets:/system/bin LANG=en_US.UTF-8; " +
            "for f in $USR/lib/libtermux-exec*.so; do [ -e \"\$f\" ] && export LD_PRELOAD=\$f && break; done; "
        // detached (own session, reparented to init) so it is not a child of the app/Termux and survives their death
        val script = env + "setsid nohup $USR/bin/proot-distro login ${distro()} -- bash -lc " + shQuote(inner(c, true, kill)) + " >/dev/null 2>&1 </dev/null &"
        // Phantom-process / doze tweaks first (not persistent), then the launcher as Termux's own uid when possible, else as root.
        try { ProcessBuilder("su", "-c", tweakCmds(c).joinToString("; ")).redirectErrorStream(true).start().waitFor() } catch (e: Exception) { log = "su: ${e.message}"; return false }
        val tries = buildList {
            if (uid > 0) { add(listOf("su", uid.toString(), "-c", script)); add(listOf("su", "-c", script, uid.toString())) }
            add(listOf("su", "-c", script))
        }
        for (t in tries) if (suAttempt(c, t, script)) return true
        return false
    }

    private fun shQuote(s: String) = "'" + s.replace("'", "'\\''") + "'"

    /** Starts the bridge (background thread): root launcher first, Termux RUN_COMMAND as the fallback. */
    private val starting = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Bridge's own log file (written by the su launcher), read back through su for diagnostics. */
    private fun bridgeLogTail(): String = try {
        val f = "$USR/var/lib/proot-distro/installed-rootfs/${distro()}/root/claudechat/bridge.log"
        val pr = ProcessBuilder("su", "-c", "tail -c 700 $f 2>&1; ls $USR/bin/proot-distro 2>&1").redirectErrorStream(true).start()
        pr.inputStream.bufferedReader().readText().trim()
    } catch (e: Exception) { "" }

    /** [kill] = false (watchdog / auto start): never kill a bridge that may just be busy; a stale one makes the new one exit (EADDRINUSE). */
    fun startBridge(c: Context, kill: Boolean = true): Int? {
        // one launch at a time: concurrent launches (watchdog + chat) would pkill each other's fresh bridge
        if (!starting.compareAndSet(false, true)) return null
        log = ""
        val app = c.applicationContext
        Thread {
            try {
                if (pingBlocking()) return@Thread
                // Termux's own context first: it is the same environment as the hand-typed command, which is known to work
                // (proot under the su domain often fails); root launcher only as the fallback.
                val viaTermux = installed(app) && granted(app)
                if (viaTermux) {
                    note.value = tr("Starting through Termux…", "Termux üzerinden başlatılıyor…")
                    run(app, "termux-wake-lock 2>/dev/null; exec proot-distro login ${distro()} -- bash -lc " + shQuote(inner(app, false, kill)))
                    repeat(40) { Thread.sleep(500); if (pingBlocking()) { guard(); return@Thread } }
                    log += "Termux RUN_COMMAND: no answer (is allow-external-apps=true set and Termux restarted?)\n"
                }
                note.value = tr("Trying root…", "Root deneniyor…")
                if (suChain(app, kill)) return@Thread
                val tail = bridgeLogTail()
                if (tail.isNotEmpty()) log += "\n--- bridge.log ---\n$tail"
                if (!viaTermux && log.isEmpty()) log = "root: denied or unavailable; Termux RUN_COMMAND: not set up"
            } finally { starting.set(false); note.value = "" }
        }.start()
        return null
    }

    /** Enables allow-external-apps by editing Termux's properties through su, then restarts Termux. Returns true on success. */
    fun rootSetup(): Boolean = try {
        val f = "/data/data/com.termux/files/home/.termux/termux.properties"
        val sh = "mkdir -p ${f.substringBeforeLast('/')}; grep -qs '^allow-external-apps *= *true' $f || echo 'allow-external-apps=true' >> $f; " +
            "pm grant com.claudechat.app com.termux.permission.RUN_COMMAND; " +
            "appops set --uid com.claudechat.app MANAGE_EXTERNAL_STORAGE allow; " +
            "grep -qs '^allow-external-apps *= *true' $f && am force-stop com.termux"
        val pr = ProcessBuilder("su", "-c", sh).redirectErrorStream(true).start()
        pr.inputStream.readBytes(); pr.waitFor() == 0
    } catch (e: Exception) { false }

    fun wakeLock(c: Context): Int? = run(c, "termux-wake-lock")

    private fun tweakCmds(c: Context): List<String> {
        val pkgs = listOf("com.termux", c.packageName)
        return pkgs.flatMap { listOf("cmd appops set $it RUN_ANY_IN_BACKGROUND allow", "dumpsys deviceidle whitelist +$it") } +
            listOf("settings put global settings_enable_monitor_phantom_procs false",
                "device_config set_sync_disabled_for_tests persistent",
                "device_config put activity_manager max_phantom_processes 2147483647")
    }

    /** Non-persistent keep-alive tweaks through su (gone after reboot, so no bootloop risk). */
    fun rootTweaks(c: Context): Int? = run(c, "su -c \"${tweakCmds(c).joinToString("; ")}\"")

    fun rootDozeOff(c: Context): Int? = run(c, "su -c \"dumpsys deviceidle disable\"")
}

package com.claudechat

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * The app's own Ubuntu, run through proot: no Termux, no RUN_COMMAND, no su. proot and its loader ship in the APK as
 * lib*.so files because Android 10+ only lets an app execute files from its nativeLibraryDir. The Ubuntu base itself is
 * downloaded on first use (checked against Ubuntu's SHA256SUMS) and unpacked into the app's private storage.
 */
object Linux {
    private const val BASE = "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/"
    const val PACKAGES = "curl ca-certificates nodejs npm git gh util-linux procps zstd less"

    private val ctx: Context get() = App.ctx
    fun root() = File(ctx.filesDir, "linux/ubuntu")
    private fun lib(n: String) = File(ctx.applicationInfo.nativeLibraryDir, n)
    fun supported() = lib("libproot.so").exists()
    fun installed() = File(root(), ".cc-installed").exists()
    /** Packages and Claude Code are in place (the bridge can start). */
    fun ready() = File(root(), ".cc-setup").exists()
    /** Claude Code runs here instead of in Termux. */
    fun use() = Prefs.builtinLinux.value && ready()

    /** What the current job is doing, and the last lines it printed. */
    val status = MutableStateFlow("")
    val busy = MutableStateFlow(false)
    val log = MutableStateFlow("")
    private fun out(line: String) { log.value = (log.value + line + "\n").takeLast(4000) }

    /** Download, check and unpack Ubuntu, then install the packages and Claude Code. Returns an error or null. */
    fun install(): String? {
        if (!supported()) return "proot is missing from this build (arm64 only)."
        val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
        if (!installed()) {
            status.value = tr("Looking for Ubuntu…", "Ubuntu aranıyor…")
            val index = get(http, BASE) ?: return "Cannot reach cdimage.ubuntu.com"
            val name = Regex("ubuntu-base-24\\.04(\\.\\d+)?-base-arm64\\.tar\\.gz").findAll(index).map { it.value }.distinct()
                .maxByOrNull { n -> n.removePrefix("ubuntu-base-").substringBefore("-base").split('.').map { it.toInt() }.let { it[0] * 1_000_000 + it[1] * 1000 + it.getOrElse(2) { 0 } } }
                ?: return "No Ubuntu 24.04 arm64 base found"
            val sums = get(http, BASE + "SHA256SUMS") ?: return "Cannot get SHA256SUMS"
            val want = sums.lineSequence().firstOrNull { it.trim().endsWith("*$name") || it.trim().endsWith(" $name") }?.substringBefore(' ')?.lowercase()
                ?: return "$name is not in SHA256SUMS"
            val tgz = File(ctx.cacheDir, name)
            val md = MessageDigest.getInstance("SHA-256")
            try {
                http.newCall(Request.Builder().url(BASE + name).build()).execute().use { r ->
                    if (!r.isSuccessful) return "Download failed: HTTP ${r.code}"
                    val total = r.body!!.contentLength()
                    var done = 0L
                    val buf = ByteArray(1 shl 16)
                    r.body!!.byteStream().use { inp -> tgz.outputStream().use { o ->
                        while (true) {
                            val n = inp.read(buf); if (n < 0) break
                            o.write(buf, 0, n); md.update(buf, 0, n); done += n
                            status.value = tr("Downloading Ubuntu", "Ubuntu indiriliyor") + if (total > 0) " ${done * 100 / total}%" else " ${done shr 20} MB"
                        }
                    } }
                }
                val got = md.digest().joinToString("") { "%02x".format(it) }
                if (got != want) return "Checksum mismatch: the download is damaged, try again"
                status.value = tr("Unpacking…", "Açılıyor…")
                val dir = root()
                File(ctx.filesDir, "linux").mkdirs()
                var n = 0
                tgz.inputStream().use { Tar.untgz(it, dir) { n++; if (n % 200 == 0) status.value = tr("Unpacking", "Açılıyor") + " $n"; true } }
            } finally { tgz.delete() }
            fixRootfs()
            File(root(), ".cc-installed").writeText(System.currentTimeMillis().toString())
        }
        status.value = tr("Installing packages and Claude Code…", "Paketler ve Claude Code kuruluyor…")
        // apt as fake root cannot switch to its _apt sandbox user under proot
        // the DeepSeek build only needs a shell for the Bash tool: no Claude Code
        val code = if (App.dsOnly) exec("export DEBIAN_FRONTEND=noninteractive; apt-get update && apt-get install -y --no-install-recommends curl ca-certificates git util-linux procps zstd less python3 nodejs")
        else exec("export DEBIAN_FRONTEND=noninteractive; apt-get update && apt-get install -y --no-install-recommends $PACKAGES && " +
            "(command -v claude >/dev/null || npm install -g @anthropic-ai/claude-code) && command -v claude")
        if (code != 0) return tr("Setup stopped (exit $code). See the log.", "Kurulum durdu (çıkış $code). Günlüğe bak.")
        File(root(), ".cc-setup").writeText(System.currentTimeMillis().toString())
        return null
    }

    private fun get(http: OkHttpClient, url: String): String? = try {
        http.newCall(Request.Builder().url(url).build()).execute().use { if (it.isSuccessful) it.body!!.string() else null }
    } catch (e: Exception) { null }

    /** What proot-distro does after unpacking: DNS, hosts, apt without its sandbox user, and stand-ins for /proc files Android hides. */
    private fun fixRootfs() {
        val r = root()
        File(r, "etc/resolv.conf").apply { delete(); writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n") }
        File(r, "etc/hosts").writeText("127.0.0.1 localhost\n::1 localhost ip6-localhost\n")
        File(r, "etc/apt/apt.conf.d/01-claudechat").writeText("APT::Sandbox::User \"root\";\n")
        File(r, "tmp").apply { mkdirs(); setWritable(true, false) }
        val fake = File(r, ".cc-proc").apply { mkdirs() }
        val cpus = Runtime.getRuntime().availableProcessors()
        File(fake, "stat").writeText("cpu  1957 0 2877 93280 262 342 254 87 0 0\n" +
            (0 until cpus).joinToString("") { "cpu$it 240 0 360 11660 32 42 31 10 0 0\n" } +
            "intr 0\nctxt 140223\nbtime 1680020856\nprocesses 772\nprocs_running 2\nprocs_blocked 0\nsoftirq 0 0 0 0 0 0 0 0 0 0 0\n")
        File(fake, "loadavg").writeText("0.12 0.07 0.02 2/165 765\n")
        File(fake, "uptime").writeText("124.08 932.80\n")
        File(fake, "version").writeText("Linux version 6.1.0 (proot) #1 SMP PREEMPT\n")
    }

    /** proot around `bash -lc cmd` inside the Ubuntu. Output and errors are one stream. */
    fun process(cmd: String): ProcessBuilder {
        val r = root().path
        val tmp = File(ctx.cacheDir, "proot").apply { mkdirs() }
        val args = mutableListOf(lib("libproot.so").path, "--kill-on-exit", "--link2symlink", "-0", "-r", r,
            "-b", "/dev", "-b", "/proc", "-b", "/sys", "-b", "$r/tmp:/dev/shm", "-w", "/root")
        for (f in listOf("stat", "loadavg", "uptime", "version")) if (!File("/proc/$f").canRead()) args += listOf("-b", "$r/.cc-proc/$f:/proc/$f")
        for (d in listOf("/sdcard", "/storage")) if (File(d).exists()) args += listOf("-b", d)
        args += listOf("/usr/bin/env", "-i", "HOME=/root", "USER=root", "LANG=C.UTF-8", "TERM=xterm-256color", "TMPDIR=/tmp",
            "PATH=/root/.local/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin", "/bin/bash", "-lc", cmd)
        return ProcessBuilder(args).redirectErrorStream(true).apply {
            environment().apply {
                put("PROOT_LOADER", lib("libproot-loader.so").path); put("PROOT_TMP_DIR", tmp.path)
                remove("LD_PRELOAD")
            }
        }
    }

    /** Runs a command to the end, its output going to [log]. Returns the exit code (-1 when it cannot start). */
    @Volatile private var curProc: Process? = null
    /** The service ("atomic" / "ollama") being installed or removed right now, "" when none; its card shows progress and a cancel button. */
    val installing = MutableStateFlow("")
    fun cancel() { curProc?.destroyForcibly() }

    fun exec(cmd: String): Int = try {
        val p = process(cmd).start()
        curProc = p
        p.outputStream.close()
        p.inputStream.bufferedReader().forEachLine { out(it) }
        p.waitFor()
    } catch (e: Exception) { out("proot: ${e.message}"); -1 } finally { curProc = null }

    const val ATOMIC_REMOVE = "rm -f \$(command -v atomic-agent) 2>/dev/null; true"
    const val OLLAMA_REMOVE = "rm -rf \$(command -v ollama) /usr/local/lib/ollama /usr/lib/ollama 2>/dev/null; true"

    // ---- long-running things: the bridge, Atomic Agent, Ollama ------------------------------------------

    private val procs = HashMap<String, Process>()
    fun running(name: String) = synchronized(procs) { procs[name]?.isAlive == true }

    /** Starts [cmd] as a background service of the app (it lives as long as the app does). False when it cannot start. */
    fun service(name: String, cmd: String): Boolean {
        synchronized(procs) {
            if (procs[name]?.isAlive == true) return true
            val p = try { process(cmd).start() } catch (e: Exception) { out("$name: ${e.message}"); return false }
            p.outputStream.close()
            procs[name] = p
            Thread { try { p.inputStream.bufferedReader().forEachLine { out("[$name] $it") } } catch (e: Exception) { }; out("[$name] stopped") }.start()
            return true
        }
    }

    fun stop(name: String) { synchronized(procs) { procs.remove(name) }?.destroy() }

    /** The same bridge script Termux runs (restart loop, Atomic Agent beside it), here as the app's own process. */
    fun startBridge(c: Context): Boolean {
        if (!ready()) return false
        return service("bridge", Termux.bridgeScript(c))
    }

    fun stopBridge() {
        stop("bridge")
        exec("[ -f /root/claudechat/loop.pid ] && kill \$(cat /root/claudechat/loop.pid) 2>/dev/null; pkill -f '[c]laudechat/bridge.js'; true")
    }

    const val OLLAMA_INSTALL = "command -v ollama >/dev/null || curl -fsSL https://ollama.com/install.sh | sh"
    const val OLLAMA_SERVE = "OLLAMA_HOST=127.0.0.1:11434 OLLAMA_CONTEXT_LENGTH=8192 OLLAMA_KEEP_ALIVE=30m exec ollama serve"
    const val ATOMIC_INSTALL = "command -v atomic-agent >/dev/null || curl -fsSL https://atomicagent.io/install | sh"
    fun atomicServe(key: String) = "exec atomic-agent serve --host 127.0.0.1 --port ${Termux.ATOMIC_PORT} --cwd /root --api-key $key --no-parent-exit"
}

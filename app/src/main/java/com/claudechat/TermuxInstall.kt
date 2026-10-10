package com.claudechat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Downloads the open-source Termux app (github.com/termux/termux-app, GPLv3) straight from its GitHub release
 * and hands it to Android's installer, so a new user never has to leave Claude Chat to get it.
 */
object TermuxInstall {
    sealed class State {
        object Idle : State()
        object Looking : State()
        data class Downloading(val percent: Int) : State()
        data class Ready(val file: File) : State()
        data class Failed(val why: String) : State()
    }

    val state = MutableStateFlow<State>(State.Idle)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).followRedirects(true).build()

    /** Picks the debug build matching this phone's CPU (the GitHub build is the one that allows RUN_COMMAND). */
    private fun pickAsset(assets: org.json.JSONArray): Pair<String, String>? {
        val names = (0 until assets.length()).map { assets.getJSONObject(it) }.filter { it.getString("name").endsWith(".apk") }
        for (abi in Build.SUPPORTED_ABIS) {
            names.firstOrNull { it.getString("name").contains("github-debug_$abi") }?.let { return it.getString("name") to it.getString("browser_download_url") }
        }
        return names.firstOrNull { it.getString("name").contains("universal") }?.let { it.getString("name") to it.getString("browser_download_url") }
    }

    fun start(c: Context) {
        if (state.value is State.Looking || state.value is State.Downloading) return
        state.value = State.Looking
        scope.launch {
            try {
                val rel = http.newCall(Request.Builder().url("https://api.github.com/repos/termux/termux-app/releases/latest").header("Accept", "application/vnd.github+json").build())
                    .execute().use { check(it.isSuccessful) { "GitHub ${it.code}" }; JSONObject(it.body!!.string()) }
                val (name, url) = pickAsset(rel.getJSONArray("assets")) ?: error("No APK for this phone")
                val dir = File(c.cacheDir, "apk").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
                val out = File(dir, name)
                http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                    check(r.isSuccessful) { "Download ${r.code}" }
                    val total = r.body!!.contentLength().coerceAtLeast(1)
                    var done = 0L
                    r.body!!.byteStream().use { inp -> out.outputStream().use { o ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = inp.read(buf); if (n < 0) break
                            o.write(buf, 0, n); done += n
                            state.value = State.Downloading((done * 100 / total).toInt().coerceIn(0, 100))
                        }
                    } }
                }
                state.value = State.Ready(out)
            } catch (e: Exception) {
                state.value = State.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** Opens Android's installer for the downloaded file (asks for "install unknown apps" first when needed). */
    fun install(c: Context, f: File) {
        if (Build.VERSION.SDK_INT >= 26 && !c.packageManager.canRequestPackageInstalls()) {
            c.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${c.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        // the installer runs as another user and cannot read files that only the owner group can (EACCES): copy it into our own cache first
        try {
            val dir = java.io.File(c.cacheDir, "apk").apply { mkdirs() }
            val copy = java.io.File(dir, f.name)
            f.inputStream().use { i -> copy.outputStream().use { o -> i.copyTo(o) } }
            val uri = FileProvider.getUriForFile(c, "${c.packageName}.files", copy)
            c.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            // the file could not be read or shared: say so instead of closing the app
            android.widget.Toast.makeText(c, "Could not open the APK: ${e.message ?: e.javaClass.simpleName}", android.widget.Toast.LENGTH_LONG).show()
        }
    }
}

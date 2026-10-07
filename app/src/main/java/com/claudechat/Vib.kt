package com.claudechat

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.mutableStateMapOf

/**
 * Vibration alerts the user shapes: one pattern per event (answer finished, error, build finished, build failed).
 * A pattern is a comma list of milliseconds: pause, buzz, pause, buzz ... (like Android's own waveform timings).
 */
object Vib {
    class Ev(val id: String, val en: String, val tr: String, val def: String)

    val EVENTS = listOf(
        Ev("done", "Answer finished", "Cevap bitti", "0,120,90,120"),
        Ev("error", "Error", "Hata", "0,500,150,500"),
        Ev("build_ok", "Build finished", "Derleme bitti", "0,100,70,100,70,320"),
        Ev("build_fail", "Build failed", "Derleme başarısız", "0,700,120,300"),
    )

    /** Ready-made shapes to start from. */
    val PRESETS = listOf(
        Triple("short", "Short", "Kısa") to "0,150",
        Triple("double", "Double", "Çift") to "0,120,100,120",
        Triple("triple", "Triple", "Üçlü") to "0,100,80,100,80,100",
        Triple("long", "Long", "Uzun") to "0,650",
        Triple("heart", "Heartbeat", "Kalp") to "0,90,90,90,420,90,90,90",
        Triple("sos", "SOS", "SOS") to "0,100,80,100,80,100,200,300,80,300,80,300,200,100,80,100,80,100",
        Triple("rise", "Rising", "Yükselen") to "0,60,60,120,60,200,60,320",
    )

    /** Live values so the settings screen updates: "<id>.on" = "1"/"0", "<id>.pat" = pattern, "amp" = 1..255. */
    val state = mutableStateMapOf<String, String>()
    private var loaded = false

    private fun sp(c: Context = App.ctx) = c.getSharedPreferences("vib", Context.MODE_PRIVATE)

    private fun load() {
        if (loaded) return
        loaded = true
        val p = sp()
        EVENTS.forEach { e ->
            state["${e.id}.on"] = p.getString("${e.id}.on", "0") ?: "0"
            state["${e.id}.pat"] = p.getString("${e.id}.pat", e.def) ?: e.def
        }
        state["amp"] = p.getString("amp", "255") ?: "255"
    }

    fun on(id: String): Boolean { load(); return state["$id.on"] == "1" }
    fun pattern(id: String): String { load(); return state["$id.pat"] ?: EVENTS.first { it.id == id }.def }
    fun amp(): Int { load(); return state["amp"]?.toIntOrNull()?.coerceIn(1, 255) ?: 255 }
    fun set(key: String, v: String) { load(); state[key] = v; sp().edit().putString(key, v).apply() }
    fun anyBuild() = on("build_ok") || on("build_fail")

    fun parse(p: String): LongArray {
        val l = p.split(',', ' ', ';').mapNotNull { it.trim().toLongOrNull() }.map { it.coerceIn(0L, 3000L) }.take(40)
        return if (l.sum() == 0L) LongArray(0) else l.toLongArray()
    }

    /** Plays [pat]; [amplitude] 1..255 where the motor can do it. */
    fun play(ctx: Context, pat: String, amplitude: Int = amp()) {
        val t = parse(pat)
        if (t.isEmpty()) return
        try {
            val v: Vibrator = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(VibratorManager::class.java).defaultVibrator else @Suppress("DEPRECATION") ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            val eff = if (v.hasAmplitudeControl()) VibrationEffect.createWaveform(t, IntArray(t.size) { if (it % 2 == 1) amplitude else 0 }, -1) else VibrationEffect.createWaveform(t, -1)
            v.vibrate(eff)
        } catch (_: Exception) { }
    }

    /** Called when something happens; vibrates if that event is switched on. */
    fun event(id: String) { if (on(id)) play(App.ctx, pattern(id)) }
}

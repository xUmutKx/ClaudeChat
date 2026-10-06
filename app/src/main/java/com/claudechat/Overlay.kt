package com.claudechat

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.ContextThemeWrapper
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/**
 * Always-on status overlay (pill / line / curtain). It never hides on its own:
 * a visible overlay is what keeps Termux alive on the user's device.
 */
class Overlay(base: Context) {
    private val ctx = ContextThemeWrapper(base, android.R.style.Theme_Material)
    private val wm = base.getSystemService(WindowManager::class.java)
    private var root: View? = null
    private var style = ""

    private var mascot: ImageView? = null
    private var spinner: ProgressBar? = null
    private var mark: TextView? = null
    private var label: TextView? = null
    private var sub: TextView? = null
    private var bar: View? = null
    private var pulse: ObjectAnimator? = null
    private var bob: ObjectAnimator? = null
    private var eyes: ObjectAnimator? = null
    private var ring: RingView? = null
    private var lastStatus: Status? = null
    private var blackLayer: View? = null
    private var bStatus: TextView? = null
    private var bDet: TextView? = null
    private var bLast: TextView? = null
    private var bBatt: TextView? = null
    private var cache = Triple(Status.Idle, "", "")
    private var lastStyleIn = ""

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    private fun color(st: Status) = when (st) {
        Status.Working -> 0xFFFF9800.toInt()
        Status.Done -> 0xFF4CAF50.toInt()
        Status.Error -> 0xFFF44336.toInt()
        Status.Offline -> 0xFF9E9E9E.toInt()
        Status.Idle -> 0xFF757575.toInt()
    }

    private fun text(st: Status, det: String) = when (st) {
        Status.Working -> if (det.isEmpty()) ctx.localized().getString(R.string.ov_working) else "Claude · $det"
        Status.Done -> ctx.localized().getString(R.string.ov_done)
        Status.Error -> ctx.localized().getString(R.string.ov_error)
        Status.Offline -> ctx.localized().getString(R.string.ov_offline)
        Status.Idle -> ctx.localized().getString(R.string.ov_idle)
    }

    private fun markOf(st: Status) = when (st) {
        Status.Done -> "✓"
        Status.Error -> "✕"
        Status.Offline -> "!"
        else -> "•"
    }

    fun render(styleIn: String, st: Status, det: String, last: String) {
        val black = styleIn.endsWith("+black")
        val style = styleIn.removeSuffix("+black")
        lastStyleIn = styleIn; cache = Triple(st, det, last)
        if (style == "off" || !Settings.canDrawOverlays(ctx)) { hide(); return }
        if (style != this.style || root == null || black != (blackLayer != null)) { hide(); if (black) showBlack(); show(style) }
        val c = color(st)
        val working = st == Status.Working
        spinner?.visibility = if (working && style != "pill") View.VISIBLE else View.GONE
        spinner?.indeterminateTintList = ColorStateList.valueOf(c)
        ring?.set(c, working, st != Status.Idle)
        mark?.visibility = if (working) View.GONE else View.VISIBLE
        mark?.text = markOf(st); mark?.setTextColor(c)
        label?.text = text(st, det)
        if (style == "pill") {
            // idle: nothing visible (the window stays so the process keeps its priority); done fades away after a moment
            root?.visibility = if (st == Status.Idle) View.INVISIBLE else View.VISIBLE
            if (st == Status.Done) root?.let { r -> r.postDelayed({ if (lastStatus == Status.Done) r.visibility = View.INVISIBLE }, 3500) }
            val show = st != Status.Idle
            val sides = listOfNotNull(mascot, spinner?.parent as? View)
            sides.forEach { v ->
                if (show && v.visibility != View.VISIBLE) { v.visibility = View.VISIBLE; v.scaleX = 0f; v.scaleY = 0f; v.animate().scaleX(1f).scaleY(1f).setDuration(700).setInterpolator(android.view.animation.DecelerateInterpolator()).start() }
                else if (!show) { v.animate().cancel(); v.visibility = View.GONE }
            }
        }
        sub?.text = last.trim().lines().lastOrNull { it.isNotBlank() }?.take(140).orEmpty()
        bar?.setBackgroundColor(c)
        (root?.background as? GradientDrawable)?.setStroke(dp(2), c)
        pulse?.let { if (working) { if (!it.isRunning) it.start() } else { it.cancel(); bar?.alpha = 1f } }
        (root as? LinearLayout)?.let { r ->
            if (working) r.animate().alpha(0.9f).setDuration(1800).withEndAction { r.animate().alpha(1f).setDuration(1800).start() }.start() else { r.animate().cancel(); r.alpha = 1f }
        }
        bob?.let { if (working) { if (!it.isRunning) it.start() } else { it.cancel(); mascot?.translationY = 0f } }
        if (blackLayer != null) updateBlack(st, det, last)
        // Eyes animation on status change (start/done)
        if (st != lastStatus) {
            lastStatus = st
            if (st == Status.Working || st == Status.Done) mascot?.let { playEyesAnimation(it) }
        }
    }

    /** Mascot pops bigger (eyes widen) on start/finish, then settles back. */
    private fun playEyesAnimation(v: View) {
        v.animate().cancel()
        v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(800).setInterpolator(android.view.animation.DecelerateInterpolator())
            .withEndAction { v.animate().scaleX(1f).scaleY(1f).setDuration(900).start() }.start()
    }

    /** Thin ring drawn around the camera hole: a spinning arc while working, a full ring otherwise. */
    private class RingView(c: Context) : View(c) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        private var spin = 0f
        private var working = false
        private val anim = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 2200; repeatCount = ValueAnimator.INFINITE; interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { spin = it.animatedValue as Float; invalidate() }
        }

        private var shown = true
        fun set(color: Int, working: Boolean, shown: Boolean = true) {
            p.color = color; this.working = working; this.shown = shown
            if (working) { if (!anim.isRunning) anim.start() } else { anim.cancel(); spin = 0f }
            invalidate()
        }

        override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }

        override fun onDraw(c: Canvas) {
            val d = resources.displayMetrics.density
            if (!shown) return
            p.strokeWidth = 1.2f * d
            val r = minOf(width, height) / 2f - p.strokeWidth
            val box = RectF(width / 2f - r, height / 2f - r, width / 2f + r, height / 2f + r)
            if (working) c.drawArc(box, spin, 90f, false, p) else c.drawArc(box, 0f, 360f, false, p)
        }
    }

    private fun params(w: Int, h: Int, flags: Int, gravity: Int, y: Int = 0) =
        WindowManager.LayoutParams(w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.TRANSLUCENT).also {
            it.gravity = gravity; it.y = y
        }

    private fun statusBarHeight(): Int {
        val id = ctx.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) ctx.resources.getDimensionPixelSize(id) else dp(24)
    }

    private fun screenWidth(): Int =
        if (Build.VERSION.SDK_INT >= 30) wm.currentWindowMetrics.bounds.width() else ctx.resources.displayMetrics.widthPixels

    /** The camera hole at the top edge; a centred stand-in when the device has no cutout. */
    private fun cameraRect(): android.graphics.Rect {
        if (Build.VERSION.SDK_INT >= 30) {
            val r = wm.currentWindowMetrics.windowInsets.displayCutout?.boundingRects?.filter { it.top <= statusBarHeight() && it.width() < screenWidth() / 2 }?.minByOrNull { it.top }
            if (r != null) return android.graphics.Rect(r)
        }
        val w = screenWidth()
        return android.graphics.Rect(w / 2 - dp(14), dp(8), w / 2 + dp(14), dp(8) + dp(28))
    }

    /** Full-screen AMOLED black under the island; keeps the screen on, double-tap leaves. */
    private fun showBlack() {
        val dim = 0xFF9A9A9A.toInt()
        val v = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK); setPadding(dp(32), dp(48), dp(32), dp(48))
        }
        fun tv(size: Float, color: Int = dim, mono: Boolean = false, lines: Int = 1) = TextView(ctx).apply {
            textSize = size; setTextColor(color); gravity = Gravity.CENTER; maxLines = lines
            ellipsize = android.text.TextUtils.TruncateAt.END
            if (mono) typeface = android.graphics.Typeface.MONOSPACE
        }
        fun add(w: View, top: Int = 0) = v.addView(w, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(top) })
        if (Prefs.blackMascot.value) v.addView(ImageView(ctx).apply { setImageResource(R.drawable.ic_mascot) }, LinearLayout.LayoutParams(dp(56), dp(40)).apply { bottomMargin = dp(12) })
        if (Prefs.blackClock.value) {
            val face = clockFont(ctx, Prefs.blackFont.value)
            val size = (Prefs.blackSize.value.toFloatOrNull() ?: 72f).coerceIn(32f, 120f)
            val accent = 0xFFD97757.toInt()
            fun clock(fmt12: String, fmt24: String, color: Int, sp: Float) = android.widget.TextClock(ctx).apply {
                format12Hour = fmt12; format24Hour = fmt24; textSize = sp; setTextColor(color); gravity = Gravity.CENTER; typeface = face
                includeFontPadding = false
            }
            when (Prefs.blackStyle.value) {
                "stacked" -> { // Samsung-style: hours over minutes
                    add(clock("h", "HH", 0xFFE8E8E8.toInt(), size * 1.25f))
                    add(clock("mm", "mm", accent, size * 1.25f), 2)
                }
                "analog", "ticks" -> v.addView(ClockView(ctx, Prefs.blackStyle.value == "analog", face, accent),
                    LinearLayout.LayoutParams(dp((size * 2.6f).toInt()), dp((size * 2.6f).toInt())))
                else -> add(clock("h:mm", "HH:mm", 0xFFD0D0D0.toInt(), size))
            }
        }
        if (Prefs.blackDate.value) add(android.widget.TextClock(ctx).apply {
            format12Hour = "EEEE, d MMMM"; format24Hour = "EEEE, d MMMM"; textSize = 16f; setTextColor(dim); gravity = Gravity.CENTER
        })
        bStatus = if (Prefs.blackStatus.value) tv(18f).also { add(it, 28) } else null
        bDet = if (Prefs.blackStatus.value) tv(14f).also { add(it, 4) } else null
        bLast = if (Prefs.blackLast.value) tv(13f, 0xFF7A7A7A.toInt(), mono = true, lines = 5).also { add(it, 20) } else null
        bBatt = if (Prefs.blackBattery.value) tv(14f).also { add(it, 24) } else null
        Prefs.blackText.value.takeIf { it.isNotBlank() }?.let { add(tv(16f, 0xFFB0B0B0.toInt(), lines = 3).apply { text = it }, 20) }
        val gd = android.view.GestureDetector(ctx, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: android.view.MotionEvent) = true
            override fun onDoubleTap(e: android.view.MotionEvent): Boolean { Prefs.black.value = false; return true }
        })
        v.setOnTouchListener { _, e -> gd.onTouchEvent(e) }
        blackLayer = v
        v.postDelayed(object : Runnable {
            override fun run() { if (blackLayer === v) { bBatt?.text = battText(); v.postDelayed(this, 60_000) } }
        }, 60_000)
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        wm.addView(v, params(-1, -1, flags, Gravity.TOP).also {
            it.screenBrightness = (Prefs.blackDim.value.toIntOrNull() ?: 15).coerceIn(1, 100) / 100f // dim to save power
            if (Build.VERSION.SDK_INT >= 28) it.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        })
        updateBlack(cache.first, cache.second, cache.third)
    }

    private fun battText(): String {
        val i = ctx.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val lvl = i?.getIntExtra("level", -1) ?: -1
        val scale = (i?.getIntExtra("scale", 100) ?: 100).coerceAtLeast(1)
        val plugged = (i?.getIntExtra("plugged", 0) ?: 0) != 0
        return if (lvl < 0) "" else "${lvl * 100 / scale}%" + if (plugged) " ⚡" else ""
    }

    /** Fills the black-mode widgets with the current status, what Claude is doing and its last lines. */
    private fun updateBlack(st: Status, det: String, last: String) {
        cache = Triple(st, det, last)
        bStatus?.apply { text = text(st, ""); setTextColor(color(st)) }
        bDet?.text = det
        bLast?.text = last.trim().takeLast(240)
        bBatt?.text = battText()
    }

    /** Black-mode settings changed: rebuild the layers (black first, island on top). */
    fun refreshBlack() {
        if (blackLayer == null) return
        val s = lastStyleIn
        hide()
        render(s, cache.first, cache.second, cache.third)
    }

    private fun show(style: String) {
        this.style = style
        val base = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        when (style) {
            "line" -> {
                val v = View(ctx)
                bar = v
                pulse = ObjectAnimator.ofFloat(v, "alpha", 1f, 0.35f).apply { duration = 700; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE }
                root = v
                wm.addView(v, params(WindowManager.LayoutParams.MATCH_PARENT, dp(4),
                    base or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, Gravity.TOP))
            }
            "pill" -> { // long thin pill around the camera: dancing mascot left, loading / tick / cross right
                val cut = cameraRect()
                val hole = cut.height().coerceAtLeast(dp(18))
                val rim = dp(1) // equal black rim above and below the camera
                val h = hole + 2 * rim
                val side = dp(40)
                val box = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                    background = GradientDrawable().apply { cornerRadius = h / 2f; setColor(Color.BLACK) }
                }
                val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                mascot = ImageView(ctx).apply { setImageResource(R.drawable.ic_mascot); setPadding(dp(8), dp(3), dp(4), dp(3)) }
                row.addView(mascot, LinearLayout.LayoutParams(side, h))
                ring = RingView(ctx) // thin ring around the camera hole (spinner while working)
                row.addView(ring, LinearLayout.LayoutParams(cut.width() + dp(8), h))
                val icon = FrameLayout(ctx)
                spinner = ProgressBar(ctx).apply { isIndeterminate = true }
                mark = TextView(ctx).apply { textSize = 15f; gravity = Gravity.CENTER; setTypeface(typeface, android.graphics.Typeface.BOLD) }
                icon.addView(spinner, FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER))
                icon.addView(mark, FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
                row.addView(icon, LinearLayout.LayoutParams(side, h))
                box.addView(row, LinearLayout.LayoutParams(-2, -2).apply { topMargin = (cut.top - rim).coerceAtLeast(0) })
                label = TextView(ctx) // off-screen: the pill is a single row
                bob = ObjectAnimator.ofFloat(mascot, "translationY", dp(1).toFloat() / 3, -dp(1).toFloat() / 3).apply { duration = 2600; interpolator = android.view.animation.AccelerateDecelerateInterpolator(); repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE }
                root = box
                var taps = 0; var last = 0L
                box.setOnClickListener {
                    val now = System.currentTimeMillis()
                    taps = if (now - last < 700) taps + 1 else 1
                    last = now
                    if (taps >= 3) { taps = 0; closeNow() }
                }
                val flags = base or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                wm.addView(box, params(-2, -2, flags, Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0).also {
                    it.x = (cut.centerX() - screenWidth() / 2)
                    if (Build.VERSION.SDK_INT >= 30) it.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    else if (Build.VERSION.SDK_INT >= 28) it.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                })
            }
            else -> { // curtain
                val col = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    setBackgroundColor(Color.BLACK); setPadding(dp(16), statusBarHeight(), dp(16), dp(6))
                }
                mascot = ImageView(ctx).apply { setImageResource(R.drawable.ic_mascot) }
                col.addView(mascot, LinearLayout.LayoutParams(dp(40), dp(28)))
                label = TextView(ctx).apply { setTextColor(Color.WHITE); textSize = 14f; maxLines = 1; setPadding(dp(12), 0, dp(12), 0) }
                col.addView(label, LinearLayout.LayoutParams(0, -2, 1f))
                val icon = FrameLayout(ctx)
                spinner = ProgressBar(ctx).apply { isIndeterminate = true }
                mark = TextView(ctx).apply { textSize = 18f; gravity = Gravity.CENTER; setTypeface(typeface, android.graphics.Typeface.BOLD) }
                icon.addView(spinner, FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
                icon.addView(mark, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
                col.addView(icon, LinearLayout.LayoutParams(dp(28), dp(28)))
                bob = ObjectAnimator.ofFloat(mascot, "translationY", dp(1).toFloat(), -dp(1).toFloat()).apply { duration = 1000; interpolator = android.view.animation.AccelerateDecelerateInterpolator(); repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE }
                col.setOnLongClickListener { Prefs.overlay.value = "pill"; true }
                var flags = base or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                if (Prefs.screenOn.value) flags = flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                root = col
                wm.addView(col, params(-1, -2, flags, Gravity.TOP).also {
                    if (Build.VERSION.SDK_INT >= 28) it.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                })
            }
        }
    }

    /** Triple tap on the island: switch everything off (service, overlay, notification). */
    private fun closeNow() {
        android.widget.Toast.makeText(ctx, ctx.localized().getString(R.string.ov_closed), android.widget.Toast.LENGTH_SHORT).show()
        ctx.startService(android.content.Intent(ctx, KeepAliveService::class.java).setAction(KeepAliveService.ACTION_STOP))
    }

    fun hide() {
        pulse?.cancel(); bob?.cancel(); eyes?.cancel()
        root?.let { try { wm.removeView(it) } catch (e: Exception) { } }
        blackLayer?.let { try { wm.removeView(it) } catch (e: Exception) { } }; blackLayer = null
        bStatus = null; bDet = null; bLast = null; bBatt = null
        root = null; style = ""
        mascot = null; spinner = null; mark = null; label = null; sub = null; bar = null; pulse = null; bob = null; eyes = null; ring = null
        lastStatus = null
    }
}

/** Bundled fonts (SIL OFL) for the always-on clock; variable ones get a weight. */
private fun clockFont(ctx: Context, key: String): android.graphics.Typeface {
    fun asset(file: String, wght: Int? = null) = runCatching {
        android.graphics.Typeface.Builder(ctx.assets, "fonts/$file.ttf").apply { if (wght != null) setFontVariationSettings("'wght' $wght") }.build()
    }.getOrNull()
    return when (key) {
        "orbitron" -> asset("orbitron", 500)
        "grotesk" -> asset("grotesk", 300)
        "audiowide" -> asset("audiowide")
        "rajdhani" -> asset("rajdhani")
        "chakra" -> asset("chakra")
        "exo2i" -> asset("exo2i", 300)
        "bungee" -> asset("bungee")
        "sharetech" -> asset("sharetech")
        "majormono" -> asset("majormono")
        "michroma" -> asset("michroma")
        "regular" -> android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        "bold" -> android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
        "mono" -> android.graphics.Typeface.MONOSPACE
        else -> asset("outfit", 200) // default: "outfit" (older saved value "thin" lands here too)
    } ?: android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
}

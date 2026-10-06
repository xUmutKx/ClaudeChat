package com.claudechat

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
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
    private var term: TextView? = null            // Termux-style ">_" shown on the right while a shell command runs
    private var blink: ValueAnimator? = null
    private var label: TextView? = null
    private var sub: TextView? = null
    private var bar: View? = null
    private var pulse: ObjectAnimator? = null
    private var bob: ObjectAnimator? = null
    private var eyes: ObjectAnimator? = null
    private var bMascot: ImageView? = null
    private var bBob: ObjectAnimator? = null
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

    /** The mascot's dance while Claude works: steps through Dance.ROT / Dance.UP at Dance.FRAME_MS per frame, like the notification. */
    private fun dancer(v: View, hop: Float): ObjectAnimator {
        val n = Dance.ROT.size
        fun kf(f: (Int) -> Float) = Array(n + 1) { i -> android.animation.Keyframe.ofFloat(i / n.toFloat(), f(i % n)) }
        return ObjectAnimator.ofPropertyValuesHolder(v,
            android.animation.PropertyValuesHolder.ofKeyframe(View.ROTATION, *kf { Dance.ROT[it] }),
            android.animation.PropertyValuesHolder.ofKeyframe(View.TRANSLATION_Y, *kf { -Dance.UP[it] * hop }),
        ).apply {
            duration = Dance.FRAME_MS * n; repeatCount = ValueAnimator.INFINITE
            // frame by frame: snap time to whole frames; smooth: let it glide between them
            interpolator = if (Prefs.danceMode.value == "smooth") android.view.animation.LinearInterpolator()
                           else android.animation.TimeInterpolator { t -> kotlin.math.floor(t * n) / n }
        }
    }

    /** The mascot picture: awake or asleep (closed eyes), with the chosen outfit on its head. */
    private fun mascotDrawable(sleep: Boolean, computer: Boolean = false): android.graphics.drawable.Drawable {
        val hat = when (Prefs.pillOutfit.value) {
            "wizard" -> R.drawable.ic_hat_wizard
            "crown" -> R.drawable.ic_hat_crown
            "party" -> R.drawable.ic_hat_party
            "bow" -> R.drawable.ic_hat_bow
            else -> 0
        }
        fun d(id: Int) = androidx.core.content.ContextCompat.getDrawable(ctx, id)!!.mutate()
        if (computer) return if (hat == 0) d(R.drawable.ic_mascot_pc) else android.graphics.drawable.LayerDrawable(arrayOf(d(R.drawable.ic_mascot_pc), d(hat)))
        if (hat == 0) return d(if (sleep) R.drawable.ic_mascot_sleep else R.drawable.ic_mascot)
        // outfit drawables share a taller 20x18 canvas so the hat fits above the head
        return android.graphics.drawable.LayerDrawable(arrayOf(d(if (sleep) R.drawable.ic_mascot_sleep18 else R.drawable.ic_mascot18), d(hat)))
    }

    private fun color(st: Status) = when (st) {
        Status.Working -> 0xFFFF9800.toInt()
        Status.Background -> 0xFF2196F3.toInt()
        Status.Done -> 0xFF4CAF50.toInt()
        Status.Error -> 0xFFF44336.toInt()
        Status.Offline -> 0xFF9E9E9E.toInt()
        Status.Idle -> 0xFF757575.toInt()
    }

    private fun text(st: Status, det: String) = when (st) {
        Status.Working, Status.Background -> if (det.isEmpty()) ctx.localized().getString(R.string.ov_working) else "Claude · $det"
        Status.Done -> ctx.localized().getString(R.string.ov_done)
        Status.Error -> ctx.localized().getString(R.string.ov_error)
        Status.Offline -> ctx.localized().getString(R.string.ov_offline)
        Status.Idle -> ctx.localized().getString(R.string.ov_idle)
    }

    private fun markOf(st: Status) = when (st) {
        Status.Done -> "✓"
        Status.Error -> "✕"
        Status.Offline -> "!"
        Status.Idle -> "z" // asleep
        else -> "•"
    }

    fun render(styleIn: String, st: Status, det: String, last: String) {
        val black = styleIn.endsWith("+black")
        val style = styleIn.removeSuffix("+black")
        lastStyleIn = styleIn; cache = Triple(st, det, last)
        if (style == "off" || !Settings.canDrawOverlays(ctx)) { hide(); return }
        if (style != this.style || root == null || black != (blackLayer != null)) { hide(); if (black) showBlack(); show(style) }
        val shell = st == Status.Working && Engine.shell.value
        val c = if (shell) 0xFF2196F3.toInt() else color(st)
        val working = st.running
        spinner?.visibility = if (working && style != "pill") View.VISIBLE else View.GONE
        spinner?.indeterminateTintList = ColorStateList.valueOf(c)
        ring?.set(c, working, st != Status.Idle)
        mark?.visibility = if (working) View.GONE else View.VISIBLE
        term?.let { t ->
            // like the colon of a digital clock: the underscore comes and goes
            if (shell && style == "pill") {
                t.visibility = View.VISIBLE
                if (blink == null) blink = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 1000; repeatCount = ValueAnimator.INFINITE
                    var on = true
                    addUpdateListener { val n = (it.animatedValue as Float) < 0.5f; if (n != on) { on = n; t.text = if (n) ">_" else "> " } }
                    start()
                }
            } else { blink?.cancel(); blink = null; t.visibility = View.GONE; t.text = ">_" }
        }
        mark?.text = markOf(st); mark?.setTextColor(c)
        mascot?.setImageDrawable(mascotDrawable(st == Status.Idle, st == Status.Background || shell)) // background shell: laptop + blue face; idle: eyes closed + "z"
        label?.text = text(st, det)
        if (style == "pill") {
            // idle: the mascot sleeps in the pill (eyes closed, "z"); done turns into sleep after a moment
            root?.visibility = View.VISIBLE
            if (st == Status.Done) root?.let { r -> r.postDelayed({
                if (lastStatus == Status.Done) { mascot?.setImageDrawable(mascotDrawable(true)); mark?.text = markOf(Status.Idle); mark?.setTextColor(color(Status.Idle)) }
            }, 3500) }
            val show = true
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
        bob?.let { if (working && st != Status.Background && !shell) { if (!it.isRunning) it.start() } else { it.cancel(); mascot?.translationY = 0f; mascot?.rotation = 0f } }
        if (blackLayer != null) updateBlack(st, det, last)
        // Eyes animation on status change (start/done)
        if (st != lastStatus) {
            lastStatus = st
            if (st.running || st == Status.Done) mascot?.let { playEyesAnimation(it) }
        }
        applyQuiet()
    }

    /** While the chat is open the pill/curtain are hidden but stay attached (the window itself keeps Termux alive). */
    private var quiet = false
    fun setQuiet(q: Boolean) { quiet = q; applyQuiet() }
    private fun applyQuiet() {
        val v = if (quiet) View.INVISIBLE else View.VISIBLE
        root?.visibility = v; blackLayer?.visibility = v
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
            val r = minOf(width, height) / 2f - p.strokeWidth - 1f // 1px off the radius = 2px smaller diameter
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

    private fun screenHeight(): Int =
        if (Build.VERSION.SDK_INT >= 30) wm.currentWindowMetrics.bounds.height() else ctx.resources.displayMetrics.heightPixels

    private enum class Edge { TOP, LEFT, RIGHT, BOTTOM }

    /**
     * The camera hole in the current rotation and the screen edge it sits on (top in portrait, a side edge in
     * landscape); a centred stand-in at the top when the device has no cutout.
     */
    private fun cameraSpot(): Pair<Edge, android.graphics.Rect> {
        val w = screenWidth(); val h = screenHeight()
        if (Build.VERSION.SDK_INT >= 30) {
            val r = wm.currentWindowMetrics.windowInsets.displayCutout?.boundingRects
                ?.filter { it.width() < w / 2 && it.height() < h / 2 }
                ?.minByOrNull { it.width() * it.height() }
            if (r != null) {
                // nearest edge wins; on a tie top, then left, then right, then bottom
                val d = listOf(Edge.TOP to r.top, Edge.LEFT to r.left, Edge.RIGHT to w - r.right, Edge.BOTTOM to h - r.bottom)
                return d.minByOrNull { it.second }!!.first to android.graphics.Rect(r)
            }
        }
        return Edge.TOP to android.graphics.Rect(w / 2 - dp(14), dp(8), w / 2 + dp(14), dp(8) + dp(28))
    }

    /** The screen was rotated (or folded): put the pill back around the camera. */
    fun onRotate() {
        if (root == null) return
        val s = lastStyleIn
        hide()
        render(s, cache.first, cache.second, cache.third)
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
        if (Prefs.blackMascot.value) {
            val m = ImageView(ctx).apply { setImageResource(R.drawable.ic_mascot) }
            bMascot = m; bBob = dancer(m, dp(6).toFloat())
            v.addView(m, LinearLayout.LayoutParams(dp(56), dp(40)).apply { bottomMargin = dp(12) })
        }
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
        bBob?.let { if (st.running) { if (!it.isRunning) it.start() } else { it.cancel(); bMascot?.translationY = 0f; bMascot?.rotation = 0f } }
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
                val (edge, cut) = cameraSpot()
                val vertical = edge == Edge.LEFT || edge == Edge.RIGHT // camera on a side edge (landscape): stand the pill up
                val rim = dp(Prefs.pillGap.value.toIntOrNull()?.coerceIn(0, 8) ?: 1) // equal rim on both sides of the camera: more room = the ring floats further out
                val hole = (if (vertical) cut.width() else cut.height()).coerceAtLeast(dp(18))
                val trim = 1 // px taken off each long side of the pill (the pill is 2 px thinner than the camera rim)
                val thick = hole + 2 * rim - 2 * trim // pill thickness across the edge
                val ringLen = (if (vertical) cut.height() else cut.width()) + 2 * rim + dp(6)
                val side = dp(40)
                val box = LinearLayout(ctx).apply {
                    orientation = if (vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    background = GradientDrawable().apply { cornerRadius = thick / 2f; setColor(if (Prefs.pillColor.value == "white") Color.WHITE else Color.BLACK) }
                }
                fun lp(len: Int) = if (vertical) LinearLayout.LayoutParams(thick, len) else LinearLayout.LayoutParams(len, thick)
                mascot = ImageView(ctx).apply { setImageResource(R.drawable.ic_mascot); setPadding(dp(8), dp(3), dp(4), dp(3)) }
                box.addView(mascot, lp(side))
                ring = RingView(ctx) // thin ring around the camera hole (spinner while working)
                box.addView(ring, lp(ringLen))
                val icon = FrameLayout(ctx)
                spinner = ProgressBar(ctx).apply { isIndeterminate = true }
                mark = TextView(ctx).apply { textSize = 15f; gravity = Gravity.CENTER; setTypeface(typeface, android.graphics.Typeface.BOLD) }
                icon.addView(spinner, FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER))
                icon.addView(mark, FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
                term = TextView(ctx).apply {
                    text = ">_"; textSize = 13f; gravity = Gravity.CENTER; typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                    setTextColor(0xFF2196F3.toInt()); visibility = View.GONE
                }
                icon.addView(term, FrameLayout.LayoutParams(dp(30), dp(20), Gravity.CENTER))
                box.addView(icon, lp(side))
                label = TextView(ctx) // off-screen: the pill is a single strip
                bob = dancer(mascot!!, dp(2).toFloat())
                root = box
                val total = 2 * side + ringLen // camera sits in the middle segment
                var taps = 0; var last = 0L
                box.setOnClickListener {
                    val now = System.currentTimeMillis()
                    taps = if (now - last < 700) taps + 1 else 1
                    last = now
                    if (taps >= 3) { taps = 0; closeNow() }
                }
                val flags = base or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                wm.addView(box, params(-2, -2, flags, Gravity.TOP or Gravity.START, 0).also {
                    // absolute position so the middle segment lands exactly on the camera, whatever the edge
                    if (vertical) { it.x = (cut.left - rim + trim).coerceAtLeast(0); it.y = (cut.centerY() - total / 2).coerceAtLeast(0) }
                    else { it.x = (cut.centerX() - total / 2).coerceAtLeast(0); it.y = (cut.top - rim + trim).coerceAtLeast(0) }
                    if (Build.VERSION.SDK_INT >= 30) it.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    else if (Build.VERSION.SDK_INT >= 28) it.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                })
            }
            "bubble" -> { // a draggable round head: tap opens the chat, drag moves it, it snaps to the nearest side
                val size = dp(58)
                val frame = FrameLayout(ctx).apply {
                    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (Prefs.pillColor.value == "white") Color.WHITE else Color.BLACK) }
                    elevation = dp(6).toFloat()
                }
                ring = RingView(ctx); frame.addView(ring, FrameLayout.LayoutParams(size, size))
                mascot = ImageView(ctx).apply { setImageResource(R.drawable.ic_mascot) }
                frame.addView(mascot, FrameLayout.LayoutParams(dp(36), dp(28), Gravity.CENTER))
                spinner = null; mark = null; label = TextView(ctx); bob = dancer(mascot!!, dp(2).toFloat())
                root = frame
                val lp = params(size, size, base or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, Gravity.TOP or Gravity.START).also {
                    it.x = Prefs.bubbleX.value.toIntOrNull() ?: (screenWidth() - size - dp(8)); it.y = Prefs.bubbleY.value.toIntOrNull() ?: (screenHeight() / 3)
                }
                var dx = 0f; var dy = 0f; var sx = 0; var sy = 0; var moved = false
                frame.setOnTouchListener { v, e ->
                    when (e.actionMasked) {
                        android.view.MotionEvent.ACTION_DOWN -> { dx = e.rawX; dy = e.rawY; sx = lp.x; sy = lp.y; moved = false; true }
                        android.view.MotionEvent.ACTION_MOVE -> {
                            if (Math.abs(e.rawX - dx) > dp(6) || Math.abs(e.rawY - dy) > dp(6)) moved = true
                            if (moved) { lp.x = (sx + (e.rawX - dx)).toInt(); lp.y = (sy + (e.rawY - dy)).toInt(); try { wm.updateViewLayout(v, lp) } catch (_: Exception) {} }
                            true
                        }
                        android.view.MotionEvent.ACTION_UP -> {
                            if (!moved) {
                                ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                            } else {
                                lp.x = if (lp.x + size / 2 < screenWidth() / 2) dp(4) else screenWidth() - size - dp(4)
                                lp.y = lp.y.coerceIn(statusBarHeight(), screenHeight() - size - dp(24))
                                try { wm.updateViewLayout(v, lp) } catch (_: Exception) {}
                                Prefs.bubbleX.value = lp.x.toString(); Prefs.bubbleY.value = lp.y.toString()
                            }
                            true
                        }
                        else -> false
                    }
                }
                wm.addView(frame, lp)
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
                bob = dancer(mascot!!, dp(3).toFloat())
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
        pulse?.cancel(); bob?.cancel(); eyes?.cancel(); bBob?.cancel(); blink?.cancel(); blink = null; term = null
        root?.let { try { wm.removeView(it) } catch (e: Exception) { } }
        blackLayer?.let { try { wm.removeView(it) } catch (e: Exception) { } }; blackLayer = null
        bStatus = null; bDet = null; bLast = null; bBatt = null; bMascot = null; bBob = null
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

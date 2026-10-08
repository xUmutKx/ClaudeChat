package com.claudechat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.ColorMatrixColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.core.content.ContextCompat
import org.json.JSONObject

/**
 * Pixel mascots for the other AIs (a whale for DeepSeek, a rabbit, a llama...). Each has the same poses as Claude's creature, drawn in the same
 * orange so the body colour setting still recolours it. The character follows the provider unless one is picked by hand ("auto").
 */
object Mascots {
    val IDS = listOf("claude", "whale", "rabbit", "star", "bolt", "cat", "owl", "llama", "ghost")

    // ---- body colour: every character has its own, with its own default -----------------------------------------------

    /** What each character looks like before anyone changes it. */
    val DEFAULT_SKIN = mapOf("claude" to "orange", "whale" to "blue", "rabbit" to "white", "star" to "yellow", "bolt" to "teal",
        "cat" to "gray", "owl" to "brown", "llama" to "cream", "ghost" to "purple")

    private fun skins() = try { JSONObject(Prefs.mascotSkins.value) } catch (e: Exception) { JSONObject() }

    fun skinOf(c: String): String = skins().optString(c).takeIf { it in Outfit.SKINS } ?: DEFAULT_SKIN[c] ?: "orange"

    /** [Prefs.mascotSkin] is the colour of whoever is on screen now; everything that draws the mascot reads that one. */
    fun syncSkin() { val s = skinOf(current()); if (Prefs.mascotSkin.value != s) Prefs.mascotSkin.value = s }

    /** Recolours one character only; the others keep theirs. */
    fun setSkin(c: String, skin: String) {
        val m = skins(); m.put(c, skin); Prefs.mascotSkins.value = m.toString()
        if (c == current()) syncSkin()
    }

    /** Picks the character (or "auto") and shows it in its own colour. */
    fun pick(c: String) { Prefs.mascotChar.value = c; syncSkin() }

    /** The colour matrix of one character's body colour (null = the drawable's own orange). */
    fun tint(c: String): FloatArray? = Outfit.skinMatrix(skinOf(c))

    // ---- hats: drawn for Claude's flat head, moved and scaled to sit on the others ---------------------------------

    /** Where the hat goes on a character, in the 20x18 canvas: shift right/down, and a scale about the point where a hat meets Claude's head (x 10, y 6). */
    class Fit(val dx: Float, val dy: Float, val s: Float) { val none get() = dx == 0f && dy == 0f && s == 1f }

    private val FIT = mapOf(
        "whale" to Fit(-1f, 1f, .85f),     // the back (x 4-14) a row lower than Claude's head
        "rabbit" to Fit(0f, 3f, .6f),      // between the ears, on the head that starts at y 9
        "cat" to Fit(0f, 2f, .75f),        // between the ears, head at y 8
        "star" to Fit(0f, 0f, .45f),       // only the top point is 4 wide
        "bolt" to Fit(2.5f, 0f, .6f),      // the head of the bolt is on the right
        "owl" to Fit(0f, 1f, .8f),         // between the ear tufts, head at y 7
        "llama" to Fit(4f, 1f, .55f),      // the head is the right-hand part
        "ghost" to Fit(0f, 0f, 1f),
    )

    fun fit(c: String = current()): Fit = FIT[c] ?: Fit(0f, 0f, 1f)

    /** A hat drawable that lands correctly on the current character; give it the same bounds as the face. */
    class HatFit(private val inner: Drawable, private val f: Fit) : Drawable() {
        override fun draw(c: Canvas) {
            val b = bounds
            c.save()
            c.translate(b.left.toFloat(), b.top.toFloat()); c.scale(b.width() / 20f, b.height() / 18f)
            c.translate(f.dx, f.dy); c.scale(f.s, f.s, 10f, 6f)
            inner.setBounds(0, 0, 20, 18); inner.draw(c)
            c.restore()
        }
        override fun setAlpha(alpha: Int) { inner.alpha = alpha }
        override fun setColorFilter(cf: ColorFilter?) { inner.colorFilter = cf }
        @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    fun hat(ctx: Context, res: Int, c: String = current()): Drawable {
        val d = ContextCompat.getDrawable(ctx, res)!!.mutate()
        val f = fit(c)
        return if (f.none) d else HatFit(d, f)
    }

    /** Face in its colour, the blanket when asleep, the hat fitted: one picture, for places that only take a picture (the notification). */
    fun bitmap(ctx: Context, faces: List<Int>, hatRes: Int, blanket: Boolean, w: Int = 100, h: Int = 90): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val cv = Canvas(bmp)
        for (res in faces) {
            val face = ContextCompat.getDrawable(ctx, res)!!.mutate()
            Outfit.skinMatrix()?.let { face.colorFilter = ColorMatrixColorFilter(it) }
            face.setBounds(0, 0, w, h); face.draw(cv)
        }
        if (blanket) { val b = ContextCompat.getDrawable(ctx, R.drawable.ic_blanket)!!.mutate(); b.setBounds(0, 0, w, h); b.draw(cv) }
        if (hatRes != 0) { val d = hat(ctx, hatRes); d.setBounds(0, 0, w, h); d.draw(cv) }
        return bmp
    }

    fun current(): String { val c = Prefs.mascotChar.value; return if (c == "auto") Providers.current().ch else c }

    private fun id(name: String) = App.ctx.resources.getIdentifier(name, "drawable", App.ctx.packageName)

    /** The drawable for one of Claude's own mascot poses, swapped for the current character's (laptop and error poses stay Claude's). */
    fun r(base: Int): Int {
        val c = current()
        if (c == "claude") return base
        val suffix = when (base) {
            R.drawable.ic_mascot -> ""
            R.drawable.ic_mascot_sleep -> "_s"
            R.drawable.ic_mascot18, R.drawable.ic_mascot_happy18, R.drawable.ic_mascot_wow18 -> "_18"
            R.drawable.ic_mascot_sleep18 -> "_s18"
            else -> return base
        }
        return id("ic_ch_$c$suffix").takeIf { it != 0 } ?: base
    }

    /** The laptop pose: Claude has its own drawables; every other character keeps its face and gets the laptop laid over its body. Drawn in this order. */
    fun pc(alt: Boolean): List<Int> =
        if (current() == "claude") listOf(if (alt) R.drawable.ic_mascot_pc2 else R.drawable.ic_mascot_pc)
        else listOf(r(R.drawable.ic_mascot18), if (alt) R.drawable.ic_laptop2 else R.drawable.ic_laptop)

    /** A preview drawable of [c] for the picker. */
    fun preview(c: String): Int = if (c == "claude") R.drawable.ic_mascot18 else id("ic_ch_${c}_18").takeIf { it != 0 } ?: R.drawable.ic_mascot18
}

/** Compose side of the hat fit: put this on the hat image (which fills the same box as the face). */
fun Modifier.hatFit(c: String = Mascots.current()): Modifier {
    val f = Mascots.fit(c)
    return if (f.none) this else graphicsLayer {
        transformOrigin = TransformOrigin(0.5f, 6f / 18f)
        scaleX = f.s; scaleY = f.s
        translationX = f.dx / 20f * size.width; translationY = f.dy / 18f * size.height
    }
}

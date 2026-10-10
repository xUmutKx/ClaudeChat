package com.claudechat

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import org.json.JSONObject

/**
 * Shoes, trousers and body extras for the mascot, drawn in the same 20x18 grid as the bodies (ic_wear_<style>_<character>),
 * and the look of every chat: the outfit in use is the working copy in Prefs, and it is kept per chat when you switch.
 */
object Wear {
    val FEET = listOf("none", "sneakers", "boots", "socks")
    val LEGS = listOf("none", "jeans", "shorts")
    val BODY = listOf("none", "cape", "scarf", "belt", "bowtie")
    val FACE = listOf("none", "glasses", "sunglasses", "monocle", "party", "mustache", "handlebar", "curly")
    val EYES = listOf("dark", "blue", "green", "purple", "red", "gold")

    /** One mascot's clothes and face: each slot is independent, so a hat, glasses and shoes can be worn together. */
    class Look(val hat: String, val feet: String, val legs: String, val body: String, val face: String = "none", val eyes: String = "dark")

    fun res(style: String, ch: String = Mascots.current()): Int =
        if (style == "none") 0 else App.ctx.resources.getIdentifier("ic_wear_${style}_$ch", "drawable", App.ctx.packageName)

    fun faceRes(face: String, ch: String): Int =
        if (face == "none") 0 else App.ctx.resources.getIdentifier("ic_face_${face}_$ch", "drawable", App.ctx.packageName)

    fun eyeRes(eyes: String, ch: String): Int =
        if (eyes == "dark") 0 else App.ctx.resources.getIdentifier("ic_eye_${eyes}_$ch", "drawable", App.ctx.packageName)

    /** Bottom to top: the eye colour, trousers, shoes, the body extra, then the face. */
    fun layers(feet: String, legs: String, body: String, face: String = "none", eyes: String = "dark", ch: String = Mascots.current()): List<Int> =
        listOf(eyeRes(eyes, ch), res(legs, ch), res(feet, ch), res(body, ch), faceRes(face, ch)).filter { it != 0 }

    /** What the pill, the AOD and the notification wear now; asleep the face and the eye colour are left off, as the closed eyes need. */
    fun worn(sleep: Boolean): List<Int> = layers(Prefs.wearFeet.value, Prefs.wearLegs.value, Prefs.wearBody.value,
        if (sleep) "none" else Prefs.wearFace.value, if (sleep) "dark" else Prefs.eyeColor.value)

    private fun sp() = App.ctx.getSharedPreferences("wear", Context.MODE_PRIVATE)

    fun working() = Look(Prefs.pillOutfit.value, Prefs.wearFeet.value, Prefs.wearLegs.value, Prefs.wearBody.value, Prefs.wearFace.value, Prefs.eyeColor.value)

    fun saved(id: String): Look? {
        val j = sp().getString(id, null) ?: return null
        return try { JSONObject(j).let { Look(it.optString("hat", "none"), it.optString("feet", "none"), it.optString("legs", "none"), it.optString("body", "none"), it.optString("face", "none"), it.optString("eyes", "dark")) } } catch (_: Exception) { null }
    }

    fun save(id: String) {
        if (id.isEmpty()) return
        val w = working()
        sp().edit().putString(id, JSONObject().put("hat", w.hat).put("feet", w.feet).put("legs", w.legs).put("body", w.body).put("face", w.face).put("eyes", w.eyes).toString()).apply()
    }

    fun saveCurrent() = save(Engine.currentId.value)

    /** A chat that was never dressed keeps the outfit you had; one that was dressed gets its own back. */
    fun switch(old: String, new: String) {
        save(old)
        val s = saved(new)
        if (s == null) { save(new); return }
        Prefs.pillOutfit.value = s.hat; Prefs.wearFeet.value = s.feet; Prefs.wearLegs.value = s.legs; Prefs.wearBody.value = s.body
        Prefs.wearFace.value = s.face; Prefs.eyeColor.value = s.eyes
    }

    fun lookOf(id: String, current: String): Look = if (id == current) working() else saved(id) ?: working()
}

/** A still mascot in the given outfit, for lists and previews. */
@Composable
fun MascotLook(look: Wear.Look, modifier: Modifier) {
    Prefs.mascotChar.flow.collectAsState().value; Prefs.provider.flow.collectAsState().value
    val skin by Prefs.mascotSkin.flow.collectAsState()
    val ch = Mascots.current()
    val tint = remember(skin) { Outfit.skinMatrix(skin)?.let { androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(it)) } }
    val layers = Wear.layers(look.feet, look.legs, look.body, look.face, look.eyes, ch)
    val hat = Outfit.hat(look.hat)
    Box(modifier) {
        Image(painterResource(Mascots.preview(ch)), null, Modifier.fillMaxSize(), colorFilter = tint)
        layers.forEach { Image(painterResource(it), null, Modifier.fillMaxSize()) }
        if (hat != 0) Image(painterResource(hat), null, Modifier.fillMaxSize().hatFit())
    }
}

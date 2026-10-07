package com.claudechat

import kotlinx.coroutines.flow.MutableStateFlow

/** The mascot's looks (Settings / tap the header mascot): hat or accessory, body colour. Shared by the pill, bubble, notification, status-bar icon and chat header. */
object Outfit {
    val ALL = listOf("none", "wizard", "crown", "party", "bow", "cap", "phones", "halo", "ears", "shades", "santa")

    fun hat(name: String = Prefs.pillOutfit.value): Int = when (name) {
        "wizard" -> R.drawable.ic_hat_wizard
        "crown" -> R.drawable.ic_hat_crown
        "party" -> R.drawable.ic_hat_party
        "bow" -> R.drawable.ic_hat_bow
        "cap" -> R.drawable.ic_hat_cap
        "phones" -> R.drawable.ic_hat_phones
        "halo" -> R.drawable.ic_hat_halo
        "ears" -> R.drawable.ic_hat_ears
        "shades" -> R.drawable.ic_hat_shades
        "santa" -> R.drawable.ic_hat_santa
        else -> 0
    }

    /** One-colour status-bar icon with the hat drawn in. */
    fun statIcon(name: String = Prefs.pillOutfit.value): Int = when (name) {
        "wizard" -> R.drawable.ic_stat_mascot_wizard
        "crown" -> R.drawable.ic_stat_mascot_crown
        "party" -> R.drawable.ic_stat_mascot_party
        "bow" -> R.drawable.ic_stat_mascot_bow
        "cap" -> R.drawable.ic_stat_mascot_cap
        "phones" -> R.drawable.ic_stat_mascot_phones
        "halo" -> R.drawable.ic_stat_mascot_halo
        "ears" -> R.drawable.ic_stat_mascot_ears
        "shades" -> R.drawable.ic_stat_mascot_shades
        "santa" -> R.drawable.ic_stat_mascot_santa
        else -> R.drawable.ic_stat_mascot
    }

    /** Backgrounds behind the mascot (header circle + picker previews). */
    val SCENES = listOf("none", "beach", "forest", "home", "library", "cave", "sea", "plane")
    fun scene(name: String = Prefs.mascotScene.value): Int = when (name) {
        "beach" -> R.drawable.bg_scene_beach; "forest" -> R.drawable.bg_scene_forest; "home" -> R.drawable.bg_scene_home
        "library" -> R.drawable.bg_scene_library; "cave" -> R.drawable.bg_scene_cave; "sea" -> R.drawable.bg_scene_sea
        "plane" -> R.drawable.bg_scene_plane; else -> 0
    }

    /** Body colours: the target hue (degrees) the orange body is rotated to; "gray" is a special case. */
    val SKINS = listOf("orange", "red", "pink", "purple", "blue", "teal", "green", "yellow", "gray")
    private val HUE = mapOf("red" to 355f, "pink" to 330f, "purple" to 270f, "blue" to 215f, "teal" to 175f, "green" to 125f, "yellow" to 48f)
    val SKIN_SWATCH = mapOf("orange" to 0xFFD97757, "red" to 0xFFE0524D, "pink" to 0xFFE06AA8, "purple" to 0xFF9A6AE0, "blue" to 0xFF4C8FE8, "teal" to 0xFF3FB7A8, "green" to 0xFF5DB85D, "yellow" to 0xFFE0B83C, "gray" to 0xFF9A9A9A)

    /** 4x5 colour matrix (row-major, the layout both Android and Compose use) for the chosen body colour, or null for the original orange. */
    fun skinMatrix(name: String = Prefs.mascotSkin.value): FloatArray? {
        if (name == "gray") return floatArrayOf(.3f, .59f, .11f, 0f, 0f,  .3f, .59f, .11f, 0f, 0f,  .3f, .59f, .11f, 0f, 0f,  0f, 0f, 0f, 1f, 0f)
        val target = HUE[name] ?: return null
        val a = Math.toRadians((target - 15f).toDouble()); val c = Math.cos(a).toFloat(); val s = Math.sin(a).toFloat()
        return floatArrayOf(
            .213f + c * .787f - s * .213f, .715f - c * .715f - s * .715f, .072f - c * .072f + s * .928f, 0f, 0f,
            .213f - c * .213f + s * .143f, .715f + c * .285f + s * .140f, .072f - c * .072f - s * .283f, 0f, 0f,
            .213f - c * .213f - s * .787f, .715f - c * .715f + s * .715f, .072f + c * .928f + s * .072f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }
}

/** True while the chat screen is visible: the pill hides then, because the mascot in the chat header shows the same status. */
object AppState {
    val foreground = MutableStateFlow(false)
}

package com.claudechat

import kotlinx.coroutines.flow.MutableStateFlow

/** The mascot's looks (Settings / tap the header mascot): hat or accessory, body colour. Shared by the pill, bubble, notification, status-bar icon and chat header. */
object Outfit {
    val ALL = listOf("none", "wizard", "crown", "party", "bow", "cap", "phones", "halo", "ears", "santa",
        "tophat", "pirate", "chef", "cowboy", "beanie", "viking", "devil", "antlers", "bunny", "ninja", "sprout", "flower", "unicorn", "propeller", "knight", "graduate", "detective", "witch", "hardhat", "mushroom", "astronaut", "pumpkin", "fez", "tiara")

    fun hat(name: String = Prefs.pillOutfit.value): Int = when (name) {
        "wizard" -> R.drawable.ic_hat_wizard
        "crown" -> R.drawable.ic_hat_crown
        "party" -> R.drawable.ic_hat_party
        "bow" -> R.drawable.ic_hat_bow
        "cap" -> R.drawable.ic_hat_cap
        "phones" -> R.drawable.ic_hat_phones
        "halo" -> R.drawable.ic_hat_halo
        "ears" -> R.drawable.ic_hat_ears
        "santa" -> R.drawable.ic_hat_santa
        "tophat" -> R.drawable.ic_hat_tophat
        "pirate" -> R.drawable.ic_hat_pirate
        "chef" -> R.drawable.ic_hat_chef
        "cowboy" -> R.drawable.ic_hat_cowboy
        "beanie" -> R.drawable.ic_hat_beanie
        "viking" -> R.drawable.ic_hat_viking
        "devil" -> R.drawable.ic_hat_devil
        "antlers" -> R.drawable.ic_hat_antlers
        "bunny" -> R.drawable.ic_hat_bunny
        "ninja" -> R.drawable.ic_hat_ninja
        "sprout" -> R.drawable.ic_hat_sprout
        "flower" -> R.drawable.ic_hat_flower
        "unicorn" -> R.drawable.ic_hat_unicorn
        "propeller" -> R.drawable.ic_hat_propeller
        "knight" -> R.drawable.ic_hat_knight
        "graduate" -> R.drawable.ic_hat_graduate
        "detective" -> R.drawable.ic_hat_detective
        "witch" -> R.drawable.ic_hat_witch
        "hardhat" -> R.drawable.ic_hat_hardhat
        "astronaut" -> R.drawable.ic_cos_astronaut
        "pumpkin" -> R.drawable.ic_cos_pumpkin
        "fez" -> R.drawable.ic_cos_fez
        "tiara" -> R.drawable.ic_cos_tiara
        "mushroom" -> R.drawable.ic_hat_mushroom
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
        "santa" -> R.drawable.ic_stat_mascot_santa
        "tophat" -> R.drawable.ic_stat_mascot_tophat
        "pirate" -> R.drawable.ic_stat_mascot_pirate
        "chef" -> R.drawable.ic_stat_mascot_chef
        "cowboy" -> R.drawable.ic_stat_mascot_cowboy
        "beanie" -> R.drawable.ic_stat_mascot_beanie
        "viking" -> R.drawable.ic_stat_mascot_viking
        "devil" -> R.drawable.ic_stat_mascot_devil
        "antlers" -> R.drawable.ic_stat_mascot_antlers
        "bunny" -> R.drawable.ic_stat_mascot_bunny
        "ninja" -> R.drawable.ic_stat_mascot_ninja
        "sprout" -> R.drawable.ic_stat_mascot_sprout
        "flower" -> R.drawable.ic_stat_mascot_flower
        "unicorn" -> R.drawable.ic_stat_mascot_unicorn
        "propeller" -> R.drawable.ic_stat_mascot_propeller
        "knight" -> R.drawable.ic_stat_mascot_knight
        "graduate" -> R.drawable.ic_stat_mascot_graduate
        "detective" -> R.drawable.ic_stat_mascot_detective
        "witch" -> R.drawable.ic_stat_mascot_witch
        "hardhat" -> R.drawable.ic_stat_mascot_hardhat
        "mushroom" -> R.drawable.ic_stat_mascot_mushroom
        else -> R.drawable.ic_stat_mascot
    }

    /** Backgrounds behind the mascot (header circle + picker previews). */
    val SCENES = listOf("none", "beach", "forest", "home", "library", "cave", "sea", "plane",
        "space", "snow", "desert", "sunset", "mountains", "city", "castle", "farm", "aurora", "volcano", "coral", "moon", "rain", "garden", "candy", "neon", "autumn", "rainbow", "meadow", "clouds")
    fun scene(name: String = Prefs.mascotScene.value): Int = when (name) {
        "beach" -> R.drawable.bg_scene_beach; "forest" -> R.drawable.bg_scene_forest; "home" -> R.drawable.bg_scene_home
        "library" -> R.drawable.bg_scene_library; "cave" -> R.drawable.bg_scene_cave; "sea" -> R.drawable.bg_scene_sea
        "plane" -> R.drawable.bg_scene_plane
        "space" -> R.drawable.bg_scene_space; "snow" -> R.drawable.bg_scene_snow; "desert" -> R.drawable.bg_scene_desert; "sunset" -> R.drawable.bg_scene_sunset; "mountains" -> R.drawable.bg_scene_mountains; "city" -> R.drawable.bg_scene_city; "castle" -> R.drawable.bg_scene_castle; "farm" -> R.drawable.bg_scene_farm; "aurora" -> R.drawable.bg_scene_aurora; "volcano" -> R.drawable.bg_scene_volcano; "coral" -> R.drawable.bg_scene_coral; "moon" -> R.drawable.bg_scene_moon; "rain" -> R.drawable.bg_scene_rain; "garden" -> R.drawable.bg_scene_garden; "candy" -> R.drawable.bg_scene_candy; "neon" -> R.drawable.bg_scene_neon; "autumn" -> R.drawable.bg_scene_autumn; "rainbow" -> R.drawable.bg_scene_rainbow; "meadow" -> R.drawable.bg_scene_meadow; "clouds" -> R.drawable.bg_scene_clouds
        else -> 0
    }

    /** Body colours: the target hue (degrees) the orange body is rotated to; "gray" is a special case. */
    val SKINS = listOf("orange", "red", "pink", "purple", "blue", "teal", "green", "yellow", "gray", "white", "cream", "brown")
    private val HUE = mapOf("red" to 355f, "pink" to 330f, "purple" to 270f, "blue" to 215f, "teal" to 175f, "green" to 125f, "yellow" to 48f)
    val SKIN_SWATCH = mapOf("orange" to 0xFFD97757, "red" to 0xFFE0524D, "pink" to 0xFFE06AA8, "purple" to 0xFF9A6AE0, "blue" to 0xFF4C8FE8, "teal" to 0xFF3FB7A8, "green" to 0xFF5DB85D, "yellow" to 0xFFE0B83C, "gray" to 0xFF9A9A9A, "white" to 0xFFF1F1F1, "cream" to 0xFFEBD9B8, "brown" to 0xFF8A4B36)

    /** 4x5 colour matrix (row-major, the layout both Android and Compose use) for the chosen body colour, or null for the original orange. */
    fun skinMatrix(name: String = Prefs.mascotSkin.value): FloatArray? {
        if (name == "gray") return floatArrayOf(.3f, .59f, .11f, 0f, 0f,  .3f, .59f, .11f, 0f, 0f,  .3f, .59f, .11f, 0f, 0f,  0f, 0f, 0f, 1f, 0f)
        // white: grey scale lifted so the orange body lands near white while the dark eyes stay dark
        if (name == "white") return floatArrayOf(.465f, .915f, .17f, 0f, 10f,  .465f, .915f, .17f, 0f, 10f,  .465f, .915f, .17f, 0f, 10f,  0f, 0f, 0f, 1f, 0f)
        // cream: a little of the colour left, brightened
        if (name == "cream") {
            val s = .35f; val g = 1.3f
            val l = floatArrayOf(.3f * (1 - s), .59f * (1 - s), .11f * (1 - s))
            val m = FloatArray(20)
            for (row in 0..2) for (k in 0..2) m[row * 5 + k] = g * (l[k] + (if (k == row) s else 0f))
            m[18] = 1f
            return m
        }
        // brown: the orange, darkened
        if (name == "brown") return floatArrayOf(.62f, 0f, 0f, 0f, 0f,  0f, .62f, 0f, 0f, 0f,  0f, 0f, .62f, 0f, 0f,  0f, 0f, 0f, 1f, 0f)
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

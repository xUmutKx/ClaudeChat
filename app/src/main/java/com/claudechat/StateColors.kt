package com.claudechat

/**
 * Colours of the pill and the chat header for the build and done states, chosen in Customize.
 * "rainbow" cycles through the hues; every other key is one fixed colour.
 */
object StateColors {
    /** key, English name, Turkish name */
    val CHOICES = listOf(
        Triple("pink", "Pink", "Pembe"), Triple("green", "Green", "Yeşil"), Triple("cyan", "Cyan", "Turkuaz"),
        Triple("blue", "Blue", "Mavi"), Triple("gold", "Gold", "Altın"), Triple("orange", "Orange", "Turuncu"),
        Triple("red", "Red", "Kırmızı"), Triple("white", "White", "Beyaz"), Triple("rainbow", "Rainbow", "Gökkuşağı"),
    )

    private val FIXED = mapOf(
        "pink" to 0xFFFF4D9D.toInt(), "green" to 0xFF4CAF50.toInt(), "cyan" to 0xFF3CF0FF.toInt(),
        "blue" to 0xFF2196F3.toInt(), "gold" to 0xFFFFC107.toInt(), "orange" to 0xFFFF9800.toInt(),
        "red" to 0xFFF44336.toInt(), "white" to 0xFFF2F2F5.toInt(),
    )

    /** The colour for [key]; [hue] (0..360) is used only by "rainbow". */
    fun argb(key: String, hue: Float = 0f): Int =
        if (key == "rainbow") android.graphics.Color.HSVToColor(floatArrayOf(hue % 360f, .85f, 1f))
        else FIXED[key] ?: FIXED.getValue("pink")

    /** A choice in Customize with the colour it shows. */
    fun tileColor(key: String): Int = argb(key, 0f)
}

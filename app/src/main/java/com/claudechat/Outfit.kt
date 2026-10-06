package com.claudechat

import kotlinx.coroutines.flow.MutableStateFlow

/** The mascot's chosen outfit (Settings > pill outfit), shared by the pill, the notification, the status-bar icon and the chat header. */
object Outfit {
    fun hat(name: String = Prefs.pillOutfit.value): Int = when (name) {
        "wizard" -> R.drawable.ic_hat_wizard
        "crown" -> R.drawable.ic_hat_crown
        "party" -> R.drawable.ic_hat_party
        "bow" -> R.drawable.ic_hat_bow
        else -> 0
    }

    /** One-colour status-bar icon with the hat drawn in. */
    fun statIcon(name: String = Prefs.pillOutfit.value): Int = when (name) {
        "wizard" -> R.drawable.ic_stat_mascot_wizard
        "crown" -> R.drawable.ic_stat_mascot_crown
        "party" -> R.drawable.ic_stat_mascot_party
        "bow" -> R.drawable.ic_stat_mascot_bow
        else -> R.drawable.ic_stat_mascot
    }
}

/** True while the chat screen is visible: the pill hides then, because the mascot in the chat header shows the same status. */
object AppState {
    val foreground = MutableStateFlow(false)
}

package com.claudechat

import android.content.Context
import android.content.Intent
import android.graphics.Color as AColor
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** What tapping the mascot (buddy, pill, strip, bubble head) opens. */
object ChatLauncher {
    /** A small chat bubble over whatever app is open (default), or the whole app when the setting says so. */
    fun open(c: Context) { if (Prefs.buddyTap.value == "chat") app(c) else bubble(c) }

    fun app(c: Context) {
        c.startActivity(Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
    }

    fun bubble(c: Context) {
        try { c.startActivity(Intent(c, BubbleChatActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)) }
        catch (e: Exception) { app(c) }
    }
}

/**
 * Android's bubble idea without the system bubble: the chat opens as a panel over whatever you were doing, the app underneath stays
 * visible, and Back (or a tap outside the panel) closes it so you carry on with the phone.
 */
class BubbleChatActivity : ComponentActivity() {
    override fun attachBaseContext(b: Context) = super.attachBaseContext(b.localized())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val clear = SystemBarStyle.auto(AColor.TRANSPARENT, AColor.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = clear, navigationBarStyle = clear)
        window.setBackgroundDrawable(ColorDrawable(AColor.TRANSPARENT))
        setContent { ClaudeTheme { BubblePanel(onClose = { finish() }, onApp = { ChatLauncher.app(this); finish() }) } }
    }

    override fun onStart() { super.onStart(); Engine.appVisible = true; AppState.foreground.value = true }
    override fun onStop() { Engine.appVisible = false; AppState.foreground.value = false; super.onStop() }
}

@Composable
fun BubblePanel(onClose: () -> Unit, onApp: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.7f)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(cs.background)
                // taps inside the panel must not fall through to the dimmed area and close it
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .consumeWindowInsets(WindowInsets.statusBars)
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.size(44.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(36.dp).height(4.dp).clip(CircleShape).background(cs.onSurfaceVariant.copy(alpha = 0.4f)))
                }
                IconButton(onApp, Modifier.size(44.dp)) { Icon(Icons.Filled.OpenInFull, tr("Open the app", "Uygulamayı aç")) }
            }
            Box(Modifier.weight(1f)) { ChatScreen(onSettings = onApp, onChats = onApp, onSetup = onApp, onTasks = onApp) }
        }
    }
}

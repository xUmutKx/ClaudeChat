package com.claudechat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    // a screen asked for by the bubble or the pill ("settings"): the app opens straight on it
    private val jump = mutableStateOf<String?>(null)

    override fun attachBaseContext(b: Context) = super.attachBaseContext(b.localized())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) jump.value = intent.getStringExtra("screen")
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Prefs.keepAlive.value || Prefs.overlay.value != "off" || Prefs.buddyOn.value || Vib.anyBuild()) KeepAliveService.start(this)
        setContent {
            ClaudeTheme {
                val dark = isDarkTheme()
                LaunchedEffect(dark) {
                    val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                var screen by rememberSaveable { mutableStateOf("chat") }
                LaunchedEffect(jump.value) { jump.value?.let { screen = it; jump.value = null } }
                BackHandler(screen != "chat") { screen = if (screen == "guide" || screen == "setup" || screen == "tasks") "settings" else if (screen == "sessions") "chats" else "chat" }
                // going deeper slides in from the right, going back from the left (like the system settings), not a blind cross-fade
                fun depth(s: String) = when (s) { "chat" -> 0; "settings", "chats" -> 1; else -> 2 }
                // the startup mascot fades out over the chat, which is already drawn underneath (no blank screen at launch)
                var splash by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) { delay(600); splash = false }
                Box(Modifier.fillMaxSize()) {
                SetupGate {
                    AnimatedContent(screen, transitionSpec = { pageSlide(depth(targetState) - depth(initialState)) }, label = "screen") {
                        when (it) {
                            "settings" -> SettingsScreen({ screen = "chat" }, { screen = "guide" }, { recreate() }, { screen = "setup" }, { screen = "tasks" })
                            "guide" -> GuideScreen { screen = "settings" }
                            "setup" -> SetupScreen { screen = "chat" }
                            "tasks" -> TaskManagerScreen { screen = "settings" }
                            "chats" -> ChatsScreen({ screen = "chat" }, { screen = "chat" }, { screen = "sessions" })
                            "sessions" -> SessionsScreen { screen = "chats" }
                            else -> ChatScreen({ screen = "settings" }, { screen = "chats" }, { screen = "setup" }, { screen = "tasks" })
                        }
                    }
                }
                AnimatedVisibility(splash, Modifier.fillMaxSize(), exit = fadeOut(tween(150))) {
                    StartSplash()
                }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) { super.onNewIntent(intent); jump.value = intent.getStringExtra("screen") }

    override fun onStart() { super.onStart(); Engine.appVisible = true; AppState.foreground.value = true }
    override fun onStop() { Engine.appVisible = false; AppState.foreground.value = false; super.onStop() }
}

/** 0.6 s opening: the mascot shrinks inward, then a burst of dots flies out while the mascot dances. */
@Composable
private fun StartSplash() {
    val t = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, androidx.compose.animation.core.tween(600, easing = androidx.compose.animation.core.LinearEasing)) }
    val ring = MaterialTheme.colorScheme.primary
    val bg = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxSize().background(bg), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val k = t.value
            if (k < 0.45f) return@Canvas
            // the burst: dots fly outward from the mascot and fade
            val c = center; val maxR = size.minDimension * 0.45f
            val b = (k - 0.45f) / 0.55f
            for (i in 0 until 14) {
                val a = Math.PI * 2 * i / 14
                val d = maxR * (0.2f + 0.8f * b)
                drawCircle(ring.copy(alpha = 1f - b), radius = 6f * (1f - b * 0.5f),
                    center = androidx.compose.ui.geometry.Offset(c.x + (d * Math.cos(a)).toFloat(), c.y + (d * Math.sin(a)).toFloat()))
            }
        }
        // shrinks to 15% at the middle, springs back as the burst starts; it sways and bounces the whole time
        Mascot(working = true, modifier = Modifier.size(132.dp).graphicsLayer {
            val k = t.value
            val s = if (k < 0.45f) 1f - 0.85f * (k / 0.45f) else 0.15f + 0.85f * ((k - 0.45f) / 0.55f)
            scaleX = s; scaleY = s
            rotationZ = kotlin.math.sin(k * 6.2832f * 3f) * 14f
            translationY = -kotlin.math.abs(kotlin.math.sin(k * 3.1416f * 3f)) * 18f * s
        })
    }
}

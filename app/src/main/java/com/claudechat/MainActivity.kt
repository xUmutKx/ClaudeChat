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
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable

class MainActivity : ComponentActivity() {
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun attachBaseContext(b: Context) = super.attachBaseContext(b.localized())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Prefs.keepAlive.value || Prefs.overlay.value != "off") KeepAliveService.start(this)
        setContent {
            ClaudeTheme {
                val dark = isDarkTheme()
                LaunchedEffect(dark) {
                    val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                var screen by rememberSaveable { mutableStateOf("chat") }
                BackHandler(screen != "chat") { screen = if (screen == "guide" || screen == "setup") "settings" else "chat" }
                Crossfade(screen, label = "screen") {
                    when (it) {
                        "settings" -> SettingsScreen({ screen = "chat" }, { screen = "guide" }, { recreate() }, { screen = "setup" })
                        "guide" -> GuideScreen { screen = "settings" }
                        "setup" -> SetupScreen { screen = "chat" }
                        "chats" -> ChatsScreen({ screen = "chat" }, { screen = "chat" })
                        else -> ChatScreen({ screen = "settings" }, { screen = "chats" }, { screen = "setup" })
                    }
                }
            }
        }
    }

    override fun onStart() { super.onStart(); Engine.appVisible = true; AppState.foreground.value = true }
    override fun onStop() { Engine.appVisible = false; AppState.foreground.value = false; super.onStop() }
}

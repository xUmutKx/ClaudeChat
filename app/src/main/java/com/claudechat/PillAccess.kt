package com.claudechat

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.provider.Settings
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * An accessibility service that does nothing but own the window layer for the pill and the buddy: windows of type
 * TYPE_ACCESSIBILITY_OVERLAY sit above the status bar, the notification shade and Settings' permission screens,
 * where plain app overlays are drawn under them or hidden. It reads no screen content and listens to no events.
 */
class PillAccess : AccessibilityService() {
    override fun onServiceConnected() { instance = this; KeepAliveService.relayout(this) }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onUnbind(i: android.content.Intent?): Boolean { instance = null; KeepAliveService.relayout(this); return super.onUnbind(i) }

    companion object {
        const val ID = "com.umutk.claudechat/com.claudechat.PillAccess"
        @Volatile var instance: PillAccess? = null

        fun wm(c: Context): WindowManager = instance?.getSystemService(WindowManager::class.java) ?: c.getSystemService(WindowManager::class.java)
        fun type() = if (instance != null) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        fun enabled(c: Context) = (Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "").contains(ID)

        /** Switches it on through root (the same way BlackOut does), so no trip through the accessibility settings is needed. */
        fun enableWithRoot(): Boolean = try {
            val cmd = "cur=\$(settings get secure enabled_accessibility_services); case \"\$cur\" in *$ID*) ;; null|\"\") settings put secure enabled_accessibility_services $ID ;; *) settings put secure enabled_accessibility_services \"\$cur:$ID\" ;; esac; settings put secure accessibility_enabled 1"
            Runtime.getRuntime().exec(arrayOf("su", "-c", cmd)).waitFor() == 0
        } catch (e: Exception) { false }
    }
}

/*
 * Adapted from buddy-android (https://github.com/ghorbelhamdi/buddy-android), Copyright ghorbelhamdi,
 * Apache License 2.0. Modified for Claude Chat: no bubble, lock-screen or chat hub; the approval dialog is
 * ConfirmActivity; the status chip is a plain ongoing notification.
 */
package com.claudechat.phone;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.claudechat.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

/**
 * Accessibility service that gives Claude the phone tools (see {@link Tools}). The local MCP server runs only
 * while "Let Claude use the phone" is on in Claude Chat's settings; approvals go through {@link ConfirmActivity}.
 */
public class PhoneService extends AccessibilityService implements McpServer.Handler {
    static final String ACTION_STOP = "com.claudechat.phone.STOP_CONTROL";
    private static final String CHANNEL = "phone_control";
    private static final int NOTICE_ID = 7;
    private static final long IDLE_MS = 15_000, TICK_MS = 1_000, REVOKE_MS = 60_000;

    private static volatile PhoneService instance;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Object confirmLock = new Object();
    private Tools tools;
    private McpServer server;
    private BroadcastReceiver stopReceiver;
    /** After the user taps Stop, phone actions are refused until this (elapsedRealtime). */
    private volatile long revokedUntil;
    // the fields below are used on the main thread only
    private boolean controlling;
    private long lastActivity;

    /** The running service, or null while the accessibility service is off. */
    static PhoneService instance() {
        return instance;
    }

    /** Starts or stops the MCP server to match the switch. Call after the switch changes. */
    public static void sync() {
        PhoneService s = instance;
        if (s != null) s.main.post(s::syncServer);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        tools = new Tools(this);
        stopReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                stopControl();
            }
        };
        ContextCompat.registerReceiver(this, stopReceiver, new IntentFilter(ACTION_STOP), ContextCompat.RECEIVER_NOT_EXPORTED);
        syncServer();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(Intent intent) {
        teardown();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        teardown();
        super.onDestroy();
    }

    private void teardown() {
        if (instance == this) instance = null;
        stopServer();
        endControl();
        if (stopReceiver != null) {
            try {
                unregisterReceiver(stopReceiver);
            } catch (IllegalArgumentException ignored) {
            }
            stopReceiver = null;
        }
    }

    private void syncServer() {
        if (PhoneControl.enabled(this)) {
            if (server == null) {
                server = new McpServer(PhoneControl.token(this), this);
                server.start();
            }
        } else {
            stopServer();
        }
    }

    private void stopServer() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    // ------------------------------------------------------- MCP handler

    @Override
    public JSONArray listTools() throws JSONException {
        return tools.list();
    }

    @Override
    public JSONObject callTool(String name, JSONObject args) throws Exception {
        return tools.call(name, args);
    }

    // ------------------------------------------- called from tool threads

    Handler mainHandler() {
        return main;
    }

    /** A phone tool is being used: show the "Claude is using your phone" notice. */
    void onToolActivity(final String tool) {
        // asking for approval or posting a note isn't operating the phone
        if ("confirm".equals(tool) || "notify".equals(tool)) return;
        main.post(this::onControlActivity);
    }

    private void onControlActivity() {
        lastActivity = SystemClock.elapsedRealtime();
        if (controlling) return;
        controlling = true;
        showNotice();
        main.postDelayed(idleCheck, TICK_MS);
    }

    private final Runnable idleCheck = new Runnable() {
        @Override
        public void run() {
            if (!controlling) return;
            if (SystemClock.elapsedRealtime() - lastActivity > IDLE_MS) endControl();
            else main.postDelayed(this, TICK_MS);
        }
    };

    private void endControl() {
        if (!controlling) return;
        controlling = false;
        main.removeCallbacks(idleCheck);
        getSystemService(NotificationManager.class).cancel(NOTICE_ID);
    }

    /** Null if phone actions are allowed, else why not (the user took back control). */
    String controlRevoked() {
        if (SystemClock.elapsedRealtime() >= revokedUntil) return null;
        return "The user tapped Stop to take back control of the phone. Don't use the phone tools for now; "
                + "stop and ask the user what they want.";
    }

    /** Stop on the notice: refuse phone actions for a minute. */
    private void stopControl() {
        main.post(() -> {
            revokedUntil = SystemClock.elapsedRealtime() + REVOKE_MS;
            endControl();
            Toast.makeText(this, R.string.phone_stopped, Toast.LENGTH_SHORT).show();
        });
    }

    private void showNotice() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL, getString(R.string.phone_channel),
                NotificationManager.IMPORTANCE_DEFAULT);
        ch.setSound(null, null);
        ch.enableVibration(false);
        nm.createNotificationChannel(ch);
        PendingIntent stop = PendingIntent.getBroadcast(this, 2, new Intent(ACTION_STOP).setPackage(getPackageName()),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_mascot)
                .setContentTitle(getString(R.string.phone_notice_title))
                .setContentText(getString(R.string.phone_notice_text))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .addAction(new Notification.Action.Builder(null, getString(R.string.phone_stop), stop).build())
                .build();
        nm.notify(NOTICE_ID, n);
    }

    void showStatus(final String text) {
        main.post(() -> Toast.makeText(this, text, Toast.LENGTH_LONG).show());
    }

    /** Short vibration to get the user's attention (e.g. "tap Update"). */
    void nudge() {
        try {
            Vibrator v = getSystemService(Vibrator.class);
            if (v != null) v.vibrate(VibrationEffect.createWaveform(new long[]{0, 60, 80, 60}, -1));
        } catch (Exception ignored) {
        }
    }

    /**
     * Blocks the calling (tool) thread until the user answers the dialog. Anything but Approve is a denial,
     * and so is no answer within 5 minutes.
     */
    String askConfirm(final String summary) throws InterruptedException {
        synchronized (confirmLock) {
            ConfirmActivity.Ask ask = new ConfirmActivity.Ask(summary);
            ConfirmActivity.pending = ask;
            try {
                startActivity(new Intent(this, ConfirmActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (RuntimeException e) {
                ConfirmActivity.pending = null;
                return "DENIED: could not show the approval dialog on the phone. Do not do it.";
            }
            boolean answered = ask.await(5, TimeUnit.MINUTES);
            ask.answer(false); // no-op when the user already answered
            ask.close();
            ConfirmActivity.pending = null;
            if (ask.approved()) return "APPROVED by the user. Go ahead.";
            return answered
                    ? "DENIED by the user. Do not do it; ask what they want instead."
                    : "DENIED: the user did not answer within 5 minutes. Do not do it.";
        }
    }
}

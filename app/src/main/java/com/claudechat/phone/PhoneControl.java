package com.claudechat.phone;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.security.SecureRandom;

/** The "Let Claude use the phone" switch and the server token, shared by settings, the bridge request and the service. */
public final class PhoneControl {
    /** Claude Chat's own preferences file (see Prefs in App.kt), so the switch has one source. */
    private static final String PREFS = "cc";
    /** Same key as Prefs.phoneTools. */
    private static final String KEY_ENABLED = "phoneTools";
    private static final String KEY_TOKEN = "phoneToken";

    private PhoneControl() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, 0);
    }

    public static boolean enabled(Context c) {
        return prefs(c).getBoolean(KEY_ENABLED, false);
    }

    /** Bearer token for the local server; created once and kept in the app's preferences. */
    public static synchronized String token(Context c) {
        SharedPreferences sp = prefs(c);
        String t = sp.getString(KEY_TOKEN, "");
        if (t.isEmpty()) {
            byte[] b = new byte[32];
            new SecureRandom().nextBytes(b);
            t = Base64.encodeToString(b, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
            sp.edit().putString(KEY_TOKEN, t).apply();
        }
        return t;
    }

    /** Time of the last phone-tool call (set by the server). The green dot shows only for a short while after one. */
    public static volatile long lastUse = 0L;

    /** True while the accessibility service is connected (the server can run). */
    public static boolean connected() {
        return PhoneService.instance() != null;
    }

    /** True only when the phone tools were really used a moment ago: connected is not enough for the dot. */
    public static boolean inUse() {
        return connected() && System.currentTimeMillis() - lastUse < 20_000L;
    }
}

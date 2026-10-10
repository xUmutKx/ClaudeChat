package com.claudechat.phone;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;

import com.claudechat.R;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Shows one approval question (the {@code confirm} tool) and reports the answer. Only Approve counts as yes;
 * Deny, Back, tapping outside or leaving the dialog are all no.
 */
public class ConfirmActivity extends Activity {
    /** The question being asked now; set by PhoneService.askConfirm, which asks one at a time. */
    static volatile Ask pending;

    /** One question, answered once: from the dialog, or by PhoneService when it gives up waiting. */
    static final class Ask {
        final String summary;
        private final CountDownLatch done = new CountDownLatch(1);
        private volatile boolean approved;
        private volatile ConfirmActivity host;

        Ask(String summary) {
            this.summary = summary;
        }

        synchronized void answer(boolean ok) {
            if (done.getCount() == 0) return;
            approved = ok;
            done.countDown();
        }

        boolean await(long amount, TimeUnit unit) throws InterruptedException {
            return done.await(amount, unit);
        }

        boolean approved() {
            return approved;
        }

        /** Closes the dialog if it is still on screen. */
        void close() {
            ConfirmActivity h = host;
            if (h != null) h.runOnUiThread(h::finish);
        }
    }

    private AlertDialog dialog;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        final Ask ask = pending;
        if (ask == null) {
            finish();
            return;
        }
        ask.host = this;
        dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_title)
                .setMessage(ask.summary)
                .setPositiveButton(R.string.confirm_approve, (d, w) -> ask.answer(true))
                .setNegativeButton(R.string.confirm_deny, (d, w) -> ask.answer(false))
                .setOnCancelListener(d -> ask.answer(false))
                .setOnDismissListener(d -> {
                    ask.answer(false);
                    finish();
                })
                .create();
        dialog.show();
        // a tap on Approve must not go through an overlay another app draws on top of the dialog
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setFilterTouchesWhenObscured(true);
    }

    @Override
    protected void onDestroy() {
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
        super.onDestroy();
    }
}

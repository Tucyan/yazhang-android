package com.yazhang.demoime;
import android.inputmethodservice.InputMethodService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Base64;
import android.view.inputmethod.InputConnection;
import java.nio.charset.StandardCharsets;

public final class DemoIme extends InputMethodService {
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            InputConnection connection = getCurrentInputConnection();
            String value = intent.getStringExtra("msg");
            if (connection != null && value != null) {
                connection.commitText(new String(Base64.decode(value, Base64.DEFAULT), StandardCharsets.UTF_8), 1);
            }
        }
    };
    @Override public void onCreate() {
        super.onCreate();
        registerReceiver(receiver, new IntentFilter("com.yazhang.demoime.INPUT"));
    }
    @Override public void onDestroy() {
        unregisterReceiver(receiver);
        super.onDestroy();
    }
}

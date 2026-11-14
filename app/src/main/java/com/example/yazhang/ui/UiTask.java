package com.example.yazhang.ui;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class UiTask {

    interface Work<T> {
        T run() throws Exception;
    }

    interface Callback<T> {
        void accept(T value);
    }

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private UiTask() {
    }

    static <T> void run(Work<T> work, Callback<T> onSuccess, Callback<Exception> onError) {
        IO.execute(() -> {
            try {
                T value = work.run();
                MAIN.post(() -> onSuccess.accept(value));
            } catch (Exception error) {
                MAIN.post(() -> onError.accept(error));
            }
        });
    }
}

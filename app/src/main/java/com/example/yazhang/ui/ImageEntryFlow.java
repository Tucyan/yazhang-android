package com.example.yazhang.ui;

import android.net.Uri;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.yazhang.ai.AiClient;
import com.example.yazhang.ai.ImageAiService;
import com.example.yazhang.ai.ImageRecognizer;
import com.example.yazhang.ai.config.SecureSettings;
import com.example.yazhang.data.LedgerRepository;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.CancellationException;

/** User-initiated image recognition flow; no screenshot listener or gallery scanning. */
public final class ImageEntryFlow {

    private final AppCompatActivity activity;
    private final LedgerRepository repository;
    private final SecureSettings settings;
    private final Runnable onSaved;
    private final ActivityResultLauncher<String> picker;
    private AlertDialog progress;
    private ImageAiService activeService;
    private int generation;
    private boolean cancelled;

    public ImageEntryFlow(AppCompatActivity activity, LedgerRepository repository,
                          Runnable onSaved) {
        this.activity = activity;
        this.repository = repository;
        this.settings = new SecureSettings(activity);
        this.onSaved = onSaved;
        this.picker = activity.registerForActivityResult(
                new ActivityResultContracts.GetContent(), this::onPicked);
    }

    public void start() {
        picker.launch("image/*");
    }

    private void onPicked(Uri uri) {
        if (uri == null) {
            return;
        }
        final int request = ++generation;
        cancelled = false;
        progress = new AlertDialog.Builder(activity)
                .setTitle("正在识别图片")
                .setMessage("只把这次选择的图片发送到你配置的 AI 服务，不会保存到云端。")
                .setNegativeButton("取消", (dialog, which) -> cancel(request))
                .setCancelable(false)
                .create();
        progress.show();
        UiTask.run(() -> recognize(request, uri),
                result -> finish(request, () -> handle(result)),
                error -> finish(request,
                        () -> Ui.showError(activity, "无法识别图片", error)));
    }

    private ImageAiService.Result recognize(int request, Uri uri) throws Exception {
        ImageRecognizer.Captured captured = ImageRecognizer.capture(activity, uri);
        if (request != generation || cancelled) {
            throw new CancellationException("请求已取消");
        }
        JSONObject provider = settings.getProvider();
        String key = settings.getApiKey(provider.optString("id"));
        if (key.isEmpty()) {
            throw new IllegalStateException("请先在设置中填写 API Key，再使用图片记账。");
        }
        final AiClient client = new AiClient(provider, key);
        ImageAiService service = new ImageAiService(repository, new ImageAiService.Completer() {
            @Override
            public String complete(String system, JSONArray window, String imageDataUrl)
                    throws Exception {
                return client.complete(system, window, imageDataUrl);
            }

            @Override
            public void cancel() {
                client.cancel();
            }
        });
        activeService = service;
        if (request != generation || cancelled) {
            service.cancel();
            throw new CancellationException("请求已取消");
        }
        return service.analyze(captured.sha256, captured.dataUrl);
    }

    private void finish(int request, Runnable action) {
        if (progress != null) {
            progress.dismiss();
            progress = null;
        }
        activeService = null;
        if (request != generation || cancelled) {
            return;
        }
        action.run();
    }

    private void cancel(int request) {
        if (request != generation) {
            return;
        }
        cancelled = true;
        ImageAiService service = activeService;
        if (service != null) {
            service.cancel();
        }
        if (progress != null) {
            progress.dismiss();
            progress = null;
        }
    }

    private void handle(ImageAiService.Result result) {
        switch (result.kind) {
            case SAVED:
                new AlertDialog.Builder(activity)
                        .setTitle("识别完成")
                        .setMessage(result.message + "\n\n可以在首页查看这笔支出。")
                        .setPositiveButton("知道了", (dialog, which) -> {
                            if (onSaved != null) {
                                onSaved.run();
                            }
                        })
                        .show();
                break;
            case NEEDS_CONFIRMATION:
                if (result.image != null) {
                    EntryDialog.showPrefilled(
                            activity,
                            repository,
                            result.image.amountFen,
                            result.image.category,
                            result.image.merchant,
                            result.image.occurredOn,
                            result.message,
                            onSaved);
                } else {
                    notice(result.message);
                }
                break;
            case NOTICE:
            case DUPLICATE:
            case ERROR:
            default:
                notice(result.message);
                break;
        }
    }

    private void notice(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle("没有记账")
                .setMessage(message)
                .setPositiveButton("知道了", null);
        builder.show();
    }
}

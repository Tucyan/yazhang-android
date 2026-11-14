package com.example.yazhang.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.yazhang.R;
import com.example.yazhang.ai.config.ModelsApi;
import com.example.yazhang.ai.config.ProviderProfiles;
import com.example.yazhang.ai.config.SecureSettings;
import com.example.yazhang.backup.BackupManager;
import com.example.yazhang.data.LedgerRepository;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

@SuppressLint("ViewConstructor")
public final class SettingsView extends ScrollView implements RefreshablePage {

    private static final int MAX_BACKUP_BYTES = 5 * 1024 * 1024;
    private static final String[] EFFORT_CODES = {"off", "low", "medium", "high", "max"};
    private static final String[] EFFORT_LABELS = {"关闭思考", "低", "中", "高", "最高"};

    private final AppCompatActivity activity;
    private final LedgerRepository repository;
    private final Runnable onDataChanged;
    private final SecureSettings settings;
    private final TextView countText;
    private final TextView aiStatusText;
    private final ActivityResultLauncher<String> createJsonBackup;
    private final ActivityResultLauncher<String> createCsvFile;
    private final ActivityResultLauncher<String[]> openDocument;

    public SettingsView(AppCompatActivity activity, LedgerRepository repository,
                        Runnable onDataChanged) {
        super(activity);
        this.activity = activity;
        this.repository = repository;
        this.onDataChanged = onDataChanged;
        this.settings = new SecureSettings(activity);
        setVerticalScrollBarEnabled(false);
        setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        createJsonBackup = activity.registerForActivityResult(
                new ActivityResultContracts.CreateDocument("*/*"),
                uri -> writeExport(uri, () ->
                        BackupManager.snapshot(repository.allEntries(), null).toString(2)));
        createCsvFile = activity.registerForActivityResult(
                new ActivityResultContracts.CreateDocument("*/*"),
                uri -> writeExport(uri, () ->
                        BackupManager.toCsv(repository.allEntries())));
        openDocument = activity.registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                this::onRestoreSource);

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(activity, 16), Ui.dp(activity, 10), Ui.dp(activity, 16), Ui.dp(activity, 24));
        content.addView(Ui.title(activity, activity.getString(R.string.tab_settings)));
        content.addView(Ui.subtitle(activity, "本地优先 · 数据由你掌控"));

        LinearLayout aiCard = card(content);
        aiCard.addView(createSectionHeader(activity, "AI 服务商", R.drawable.ic_key));
        aiStatusText = Ui.body(activity, "正在读取模型设置…");
        aiStatusText.setPadding(0, Ui.dp(activity, 4), 0, 0);
        aiCard.addView(aiStatusText);
        aiCard.addView(Ui.empty(activity,
                "图片只在你点击后发送给所选服务商。API Key 使用本机密钥加密保存，"
                        + "不会写入备份、日志或导出文件。"));

        Button configure = Ui.primaryButton(activity, "配置 AI 服务", R.drawable.ic_key);
        configure.setOnClickListener(view -> openAiConfig());
        Ui.addWithMargin(aiCard, configure, 10, activity);

        Button testText = Ui.secondaryButton(activity, "测试文本连接", R.drawable.ic_send);
        testText.setOnClickListener(view -> testAi(false));
        Ui.addWithMargin(aiCard, testText, 8, activity);

        Button testVision = Ui.secondaryButton(activity, "测试视觉能力（识别图片）", R.drawable.ic_camera);
        testVision.setOnClickListener(view -> testAi(true));
        Ui.addWithMargin(aiCard, testVision, 8, activity);

        LinearLayout dataCard = card(content);
        dataCard.addView(createSectionHeader(activity, "本地数据", R.drawable.ic_shield_check));
        countText = Ui.body(activity, "正在读取账目数量…");
        countText.setPadding(0, Ui.dp(activity, 4), 0, 0);
        dataCard.addView(countText);

        Button exportJson = Ui.primaryButton(activity, "导出 JSON 备份", R.drawable.ic_export);
        exportJson.setOnClickListener(view -> exportJson());
        Ui.addWithMargin(dataCard, exportJson, 10, activity);

        Button exportCsv = Ui.secondaryButton(activity, "导出 CSV 表格", R.drawable.ic_export);
        exportCsv.setOnClickListener(view -> exportCsv());
        Ui.addWithMargin(dataCard, exportCsv, 8, activity);

        Button restore = Ui.secondaryButton(activity, "从备份恢复", R.drawable.ic_import);
        restore.setOnClickListener(view -> openDocument.launch(new String[]{
                "application/json", "text/plain", "application/octet-stream"
        }));
        Ui.addWithMargin(dataCard, restore, 8, activity);

        LinearLayout privacyCard = card(content);
        privacyCard.addView(createSectionHeader(activity, "隐私与备份", R.drawable.ic_info));
        privacyCard.addView(Ui.empty(activity,
                "备份只包含本地账目，不会导出 API Key或登录凭据。"
                        + "恢复前会预览新增与跳过的数量，确认后才写入数据库。"));

        addView(content, new ScrollView.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private View createSectionHeader(Context context, String title, int iconRes) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, Ui.dp(context, 4));

        if (iconRes != 0) {
            ImageView icon = new ImageView(context);
            icon.setImageResource(iconRes);
            icon.setImageTintList(ColorStateList.valueOf(Ui.ACCENT));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(Ui.dp(context, 18), Ui.dp(context, 18));
            p.rightMargin = Ui.dp(context, 6);
            row.addView(icon, p);
        }

        TextView heading = Ui.heading(context, title);
        row.addView(heading);
        return row;
    }

    private LinearLayout card(LinearLayout parent) {
        LinearLayout card = Ui.card(activity);
        LinearLayout.LayoutParams params = Ui.matchWrap();
        params.topMargin = Ui.dp(activity, 14);
        parent.addView(card, params);
        return card;
    }

    @Override
    public View getView() {
        return this;
    }

    @Override
    public void refresh() {
        UiTask.run(() -> {
            long count = repository.countAll();
            JSONObject provider = settings.getProvider();
            String key = "";
            try {
                key = settings.getApiKey();
            } catch (Exception ignored) {
            }
            return new State(count, provider, !key.isEmpty());
        }, state -> {
            countText.setText(String.format(Locale.CHINA, "本地共 %d 笔账目", state.entryCount));
            String model = state.provider.optString("model_id");
            String name = state.provider.optString("name");
            aiStatusText.setText("当前服务商：" + name + "\n模型：" + model
                    + (state.hasKey ? "（已配置本机密钥）" : "（未配置 API Key）"));
        }, error -> {
            countText.setText("读取本地数据失败");
            aiStatusText.setText("读取模型设置失败");
        });
    }

    private void exportJson() {
        String filename = "yazhang-backup-" + LocalDate.now() + ".json";
        createJsonBackup.launch(filename);
    }

    private void exportCsv() {
        String filename = "yazhang-" + LocalDate.now() + ".csv";
        createCsvFile.launch(filename);
    }

    private void writeExport(Uri uri, StringSupplier payload) {
        if (uri == null) {
            return;
        }
        UiTask.run(() -> {
            String text = payload.get();
            try (OutputStream stream = activity.getContentResolver().openOutputStream(uri)) {
                if (stream == null) {
                    throw new IOException("无法打开输出流");
                }
                stream.write(text.getBytes(StandardCharsets.UTF_8));
            }
            return null;
        }, unused -> Toast.makeText(activity, "导出成功", Toast.LENGTH_SHORT).show(),
                error -> Ui.showError(activity, "导出失败", error));
    }

    private void onRestoreSource(Uri uri) {
        if (uri == null) {
            return;
        }
        UiTask.run(() -> {
            byte[] bytes = readAllBytes(uri);
            if (bytes.length > MAX_BACKUP_BYTES) {
                throw new IllegalArgumentException("备份文件超过 5MB 限制");
            }
            String content = new String(bytes, StandardCharsets.UTF_8);
            return BackupManager.inspect(content, new HashSet<>(repository.allRequestKeys()));
        }, this::showRestorePreview,
                error -> Ui.showError(activity, "无法解析备份文件", error));
    }

    private byte[] readAllBytes(Uri uri) throws IOException {
        try (InputStream stream = activity.getContentResolver().openInputStream(uri)) {
            if (stream == null) {
                throw new IOException("无法打开输入流");
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = stream.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return buffer.toByteArray();
        }
    }

    private void showRestorePreview(BackupManager.RestorePlan plan) {
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("恢复预览")
                .setMessage(String.format(Locale.CHINA,
                        "将导入 %d 笔新账目，跳过 %d 笔已存在的重复账目。\n是否继续？",
                        plan.newEntries.size(), plan.skippedCount))
                .setNegativeButton("取消", null)
                .setPositiveButton("确认导入", (d, which) ->
                        UiTask.run(() -> {
                            return repository.importEntries(plan.newEntries);
                        }, count -> {
                            Toast.makeText(activity, "已恢复 " + count + " 笔账目",
                                    Toast.LENGTH_SHORT).show();
                            refresh();
                            onDataChanged.run();
                        }, error -> Ui.showError(activity, "恢复数据失败", error)))
                .create();
        Ui.styleDialog(dialog);
        dialog.show();
    }

    private void testAi(boolean isVision) {
        AlertDialog progress = new AlertDialog.Builder(activity)
                .setTitle("正在测试连接")
                .setMessage(isVision
                        ? "发送 1x1 像素纯色图片测试 Vision 接口…"
                        : "发送问候文本测试 Chat 接口…")
                .setCancelable(false)
                .create();
        progress.show();

        UiTask.run(() -> {
            JSONObject provider = settings.getProvider();
            String key = settings.getApiKey();
            if (key.isEmpty()) {
                throw new IllegalStateException("尚未配置 API Key，请先进入“配置 AI 服务”保存。");
            }
            ModelsApi api = new ModelsApi(provider, key);
            JSONObject res = api.testConnection(isVision);
            if (!res.optBoolean("success", false)) {
                throw new IOException(res.optString("message", "测试未通过"));
            }
            return res.optString("message", "测试成功");
        }, reply -> {
            progress.dismiss();
            AlertDialog okDialog = new AlertDialog.Builder(activity)
                    .setTitle("测试成功")
                    .setMessage("服务商已正常响应：\n\n" + reply)
                    .setPositiveButton("好的", null)
                    .create();
            Ui.styleDialog(okDialog);
            okDialog.show();
        }, error -> {
            progress.dismiss();
            Ui.showError(activity, "连接测试失败", error);
        });
    }

    void openAiConfig() {
        JSONObject current = settings.getProvider();
        JSONArray allProviders = settings.getProviders();
        String currentId = current.optString("id", "tokendance");

        List<String> names = new ArrayList<>();
        int selectedIndex = 0;
        for (int i = 0; i < allProviders.length(); i++) {
            JSONObject p = allProviders.optJSONObject(i);
            if (p != null) {
                names.add(p.optString("name", "未命名"));
                if (currentId.equals(p.optString("id"))) {
                    selectedIndex = i;
                }
            }
        }

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(Ui.dp(activity, 20), Ui.dp(activity, 8),
                Ui.dp(activity, 20), Ui.dp(activity, 8));

        Spinner providerSpinner = Ui.spinner(activity, names.toArray(new String[0]));
        providerSpinner.setSelection(selectedIndex);

        EditText baseInput = input(activity, "API Base URL", InputType.TYPE_CLASS_TEXT);
        baseInput.setText(current.optString("base_url"));
        EditText modelInput = input(activity, "Model ID", InputType.TYPE_CLASS_TEXT);
        modelInput.setText(current.optString("model_id"));
        EditText keyInput = input(activity, "API Key（留空则保持不变）",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        String currentKey = "";
        try {
            currentKey = settings.getApiKey();
        } catch (Exception ignored) {
        }
        if (currentKey.isEmpty()) {
            keyInput.setHint("尚未设置 API Key");
        } else {
            keyInput.setHint("已加密保存（输入新 Key 可覆盖）");
        }

        Spinner effortSpinner = Ui.spinner(activity, EFFORT_LABELS);
        String currentEffort = current.optString("reasoning_effort", "off");
        for (int i = 0; i < EFFORT_CODES.length; i++) {
            if (EFFORT_CODES[i].equalsIgnoreCase(currentEffort)) {
                effortSpinner.setSelection(i);
                break;
            }
        }

        Button probeButton = Ui.secondaryButton(activity, "自动探测可用模型列表", R.drawable.ic_ai_sparkle);

        Ui.addWithMargin(layout, Ui.labeledField(activity, "服务商预设", providerSpinner), 8, activity);
        Ui.addWithMargin(layout, Ui.labeledField(activity, "接口地址", baseInput), 8, activity);
        Ui.addWithMargin(layout, Ui.labeledField(activity, "模型标识", modelInput), 8, activity);
        Ui.addWithMargin(layout, probeButton, 8, activity);
        Ui.addWithMargin(layout, Ui.labeledField(activity, "API Key", keyInput), 8, activity);
        Ui.addWithMargin(layout, Ui.labeledField(activity, "推理思考程度", effortSpinner), 12, activity);

        providerSpinner.setOnItemSelectedListener(new SimpleItemSelectedListener(position -> {
            JSONObject preset = allProviders.optJSONObject(position);
            if (preset != null) {
                baseInput.setText(preset.optString("base_url"));
                modelInput.setText(preset.optString("model_id"));
                String id = preset.optString("id");
                String savedKey = "";
                try {
                    savedKey = settings.getApiKey(id);
                } catch (Exception ignored) {
                }
                keyInput.setText("");
                keyInput.setHint(savedKey.isEmpty()
                        ? "尚未设置 API Key" : "已加密保存（输入新 Key 可覆盖）");
            }
        }));

        probeButton.setOnClickListener(v -> {
            String base = baseInput.getText().toString().trim();
            String key = keyInput.getText().toString().trim();
            if (key.isEmpty()) {
                try {
                    key = settings.getApiKey();
                } catch (Exception ignored) {
                }
            }
            if (key.isEmpty()) {
                Toast.makeText(activity, "探测模型需要填写或已保存 API Key", Toast.LENGTH_SHORT).show();
                return;
            }
            probeButton.setEnabled(false);
            final String finalKey = key;
            UiTask.run(() -> {
                JSONObject tempProvider = new JSONObject().put("base_url", base);
                ModelsApi probeApi = new ModelsApi(tempProvider, finalKey);
                JSONArray data = probeApi.fetchModels();
                List<String> list = new ArrayList<>();
                for (int i = 0; i < data.length(); i++) {
                    JSONObject m = data.optJSONObject(i);
                    if (m != null) {
                        list.add(m.optString("id"));
                    }
                }
                return list;
            }, models -> {
                probeButton.setEnabled(true);
                showModelPicker(models, modelInput);
            }, error -> {
                probeButton.setEnabled(true);
                Ui.showError(activity, "探测模型失败", error);
            });
        });

        ScrollView scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        scroll.addView(layout, new ScrollView.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("配置 AI 服务")
                .setView(scroll)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        Ui.styleDialog(dialog);

        dialog.setOnShowListener(ignored -> {
            Button saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (saveButton != null) {
                saveButton.setTextColor(Ui.ACCENT);
                saveButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                saveButton.setOnClickListener(view -> {
                    String base = baseInput.getText().toString().trim();
                    String model = modelInput.getText().toString().trim();
                    if (base.isEmpty() || model.isEmpty()) {
                        Toast.makeText(activity, "Base URL 和 Model 不能为空",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        int pos = providerSpinner.getSelectedItemPosition();
                        JSONObject selectedPreset = allProviders.optJSONObject(pos);
                        String id = selectedPreset != null ? selectedPreset.optString("id") : "tokendance";
                        String name = selectedPreset != null ? selectedPreset.optString("name") : "TokenDance";

                        JSONObject updated = new JSONObject();
                        updated.put("id", id);
                        updated.put("name", name);
                        updated.put("base_url", base);
                        updated.put("model_id", model);
                        updated.put("reasoning_effort",
                                EFFORT_CODES[effortSpinner.getSelectedItemPosition()]);

                        String newKey = keyInput.getText().toString().trim();
                        settings.selectProvider(id);
                        settings.saveProvider(updated, newKey.isEmpty() ? null : newKey);

                        dialog.dismiss();
                        Toast.makeText(activity, "设置已保存到本机", Toast.LENGTH_SHORT).show();
                        refresh();
                    } catch (Exception error) {
                        Ui.showError(activity, "保存失败", error);
                    }
                });
            }
            Button cancelButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (cancelButton != null) {
                cancelButton.setTextColor(Ui.MUTED);
            }
        });
        dialog.show();
    }

    private void showModelPicker(List<String> models, EditText target) {
        if (models == null || models.isEmpty()) {
            Toast.makeText(activity, "未探测到任何可用模型", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] array = models.toArray(new String[0]);
        new AlertDialog.Builder(activity)
                .setTitle("选择可用模型")
                .setItems(array, (dialog, which) -> target.setText(array[which]))
                .show();
    }

    private static EditText input(Context context, String hint, int inputType) {
        return Ui.input(context, hint, inputType);
    }

    private interface StringSupplier {
        String get() throws Exception;
    }

    private static final class State {
        final long entryCount;
        final JSONObject provider;
        final boolean hasKey;

        State(long entryCount, JSONObject provider, boolean hasKey) {
            this.entryCount = entryCount;
            this.provider = provider;
            this.hasKey = hasKey;
        }
    }

    private static final class SimpleItemSelectedListener
            implements android.widget.AdapterView.OnItemSelectedListener {

        interface Callback {
            void onSelected(int position);
        }

        private final Callback callback;

        SimpleItemSelectedListener(Callback callback) {
            this.callback = callback;
        }

        @Override
        public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                   int position, long id) {
            callback.onSelected(position);
        }

        @Override
        public void onNothingSelected(android.widget.AdapterView<?> parent) {
        }
    }
}

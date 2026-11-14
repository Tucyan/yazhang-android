package com.example.yazhang.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.yazhang.R;
import com.example.yazhang.ai.AgentReply;
import com.example.yazhang.ai.AgentService;
import com.example.yazhang.ai.AiClient;
import com.example.yazhang.ai.config.SecureSettings;
import com.example.yazhang.data.AgentMessageDao;
import com.example.yazhang.data.AgentMessageEntity;
import com.example.yazhang.data.LedgerRepository;
import com.example.yazhang.domain.CategoryTotal;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;

@SuppressLint("ViewConstructor")
public final class AgentView extends LinearLayout implements RefreshablePage {

    private final AppCompatActivity activity;
    private final LedgerRepository repository;
    private final AgentMessageDao messages;
    private final SecureSettings settings;
    private final TextView statusText;
    private final LinearLayout messageBox;
    private final ScrollView scroll;
    private final EditText input;
    private final Button send;

    private AlertDialog progress;
    private AgentService active;
    private int generation;
    private volatile boolean cancelled;

    public AgentView(AppCompatActivity activity, LedgerRepository repository,
                     AgentMessageDao messages) {
        super(activity);
        this.activity = activity;
        this.repository = repository;
        this.messages = messages;
        this.settings = new SecureSettings(activity);
        setOrientation(VERTICAL);

        scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(VERTICAL);
        content.setPadding(Ui.dp(activity, 16), Ui.dp(activity, 10), Ui.dp(activity, 16), Ui.dp(activity, 12));
        content.addView(Ui.title(activity, activity.getString(R.string.tab_agent)));
        content.addView(Ui.subtitle(activity, "本地工具 · 最近 12 条上下文"));

        LinearLayout statusCard = card(content);
        statusCard.addView(createSectionHeader(activity, "AI 记账助手", R.drawable.ic_ai_sparkle));
        statusText = Ui.body(activity, "正在读取模型设置…");
        statusText.setPadding(0, Ui.dp(activity, 4), 0, 0);
        statusCard.addView(statusText);
        statusCard.addView(Ui.empty(activity,
                "可以记账、按日期/类型/分类统计和查看攒钱进度。"
                        + "缺少金额、分类或日期时会先追问，不会直接写入。"
                        + "没有付款、删除、导出或文件工具。"));

        LinearLayout chatCard = card(content);
        chatCard.addView(createSectionHeader(activity, "对话记录", 0));
        messageBox = new LinearLayout(activity);
        messageBox.setOrientation(VERTICAL);
        chatCard.addView(messageBox, Ui.matchWrap());

        scroll.addView(content, new ScrollView.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(scroll, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, 0, 1f));

        // Bottom Input Row
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(activity, 16), Ui.dp(activity, 8), Ui.dp(activity, 16), Ui.dp(activity, 8));

        input = new EditText(activity);
        input.setHint("例如：记一笔打车 20 元");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setSingleLine(true);
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(Color.WHITE);
        inputBg.setCornerRadius(Ui.dp(activity, 20));
        inputBg.setStroke(Ui.dp(activity, 1), Color.rgb(220, 228, 222));
        input.setBackground(inputBg);
        int padH = Ui.dp(activity, 14);
        int padV = Ui.dp(activity, 10);
        input.setPadding(padH, padV, padH, padV);
        row.addView(input, Ui.matchWeight());

        send = Ui.primaryButton(activity, "发送", R.drawable.ic_send);
        send.setOnClickListener(view -> send());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        buttonParams.leftMargin = Ui.dp(activity, 8);
        row.addView(send, buttonParams);
        addView(row, Ui.matchWrap());
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
        loadStatus();
        loadHistory();
    }

    private void loadStatus() {
        UiTask.run(() -> {
            JSONObject provider = settings.getProvider();
            String key = "";
            try {
                key = settings.getApiKey();
            } catch (Exception ignored) {
            }
            return "当前：" + provider.optString("name") + " · "
                    + provider.optString("model_id")
                    + (key.isEmpty()
                    ? "\n尚未填写 API Key，请先到设置完成配置。"
                    : "\n完整历史保存在本机，只发送最近 12 条消息。");
        }, statusText::setText,
                error -> statusText.setText("模型设置无法读取，请到设置重新配置。"));
    }

    private void loadHistory() {
        UiTask.run(() -> messages.recent(AgentService.SESSION_ID, 50),
                this::renderHistory,
                error -> Ui.showError(activity, "读取 Agent 历史失败", error));
    }

    private void renderHistory(List<AgentMessageEntity> newestFirst) {
        messageBox.removeAllViews();
        if (newestFirst == null || newestFirst.isEmpty()) {
            messageBox.addView(Ui.empty(activity,
                    "还没有对话。可以试试：记一笔打车 20 元，"
                            + "或这个月餐饮花了多少？"));
            return;
        }
        List<AgentMessageEntity> chronological = new ArrayList<>(newestFirst);
        java.util.Collections.reverse(chronological);
        for (AgentMessageEntity message : chronological) {
            addBubble(message);
        }
        scrollDown();
    }

    private void addBubble(AgentMessageEntity message) {
        boolean user = "user".equals(message.role);
        LinearLayout bubble = new LinearLayout(activity);
        bubble.setOrientation(VERTICAL);
        bubble.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 10),
                Ui.dp(activity, 14), Ui.dp(activity, 10));

        if (user) {
            GradientDrawable userBg = new GradientDrawable();
            userBg.setColor(Color.rgb(234, 246, 238));
            userBg.setCornerRadius(Ui.dp(activity, 14));
            userBg.setStroke(Ui.dp(activity, 1), Color.rgb(208, 235, 218));
            bubble.setBackground(userBg);
        } else {
            GradientDrawable aiBg = new GradientDrawable();
            aiBg.setColor(Color.rgb(248, 250, 248));
            aiBg.setCornerRadius(Ui.dp(activity, 14));
            aiBg.setStroke(Ui.dp(activity, 1), Color.rgb(230, 235, 231));
            bubble.setBackground(aiBg);
        }

        LinearLayout headerRow = new LinearLayout(activity);
        headerRow.setOrientation(HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        ImageView roleIcon = new ImageView(activity);
        roleIcon.setImageResource(user ? R.drawable.ic_tab_agent : R.drawable.ic_ai_sparkle);
        roleIcon.setImageTintList(ColorStateList.valueOf(user ? Ui.ACCENT : Ui.MUTED));
        LinearLayout.LayoutParams iconP = new LinearLayout.LayoutParams(Ui.dp(activity, 14), Ui.dp(activity, 14));
        iconP.rightMargin = Ui.dp(activity, 4);
        headerRow.addView(roleIcon, iconP);

        TextView who = Ui.body(activity, user ? "你" : "助手");
        who.setTextSize(12);
        who.setTextColor(user ? Ui.ACCENT : Ui.MUTED);
        headerRow.addView(who);
        bubble.addView(headerRow);

        TextView text = Ui.body(activity, message.content);
        text.setTextSize(14);
        text.setTextColor(user ? Ui.GREEN : Color.rgb(45, 58, 50));
        text.setPadding(0, Ui.dp(activity, 4), 0, 0);
        bubble.addView(text);

        if (!user && message.chartJson != null) {
            List<CategoryTotal> totals = chartData(message.chartJson);
            if (!totals.isEmpty()) {
                SimpleChartView chart = new SimpleChartView(activity);
                chart.setData(totals);
                LinearLayout.LayoutParams chartParams = Ui.matchWrap();
                chartParams.topMargin = Ui.dp(activity, 8);
                bubble.addView(chart, chartParams);
            }
        }

        LinearLayout.LayoutParams params = Ui.matchWrap();
        params.topMargin = Ui.dp(activity, 8);
        messageBox.addView(bubble, params);
    }

    private static List<CategoryTotal> chartData(String raw) {
        List<CategoryTotal> totals = new ArrayList<>();
        try {
            JSONArray chart = new JSONArray(raw);
            for (int i = 0; i < chart.length() && i < 12; i++) {
                JSONObject item = chart.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                totals.add(new CategoryTotal(
                        item.optString("category"), item.optLong("totalFen")));
            }
        } catch (Exception malformed) {
            return new ArrayList<>();
        }
        return totals;
    }

    void setInputText(String text) {
        input.setText(text);
    }

    void send() {
        final String text = input.getText().toString();
        if (text.trim().isEmpty()) {
            Toast.makeText(activity, "请先输入内容", Toast.LENGTH_SHORT).show();
            return;
        }
        final int request = ++generation;
        cancelled = false;
        send.setEnabled(false);
        progress = new AlertDialog.Builder(activity)
                .setTitle("AI 正在处理")
                .setMessage("只发送最近 12 条消息和你这次输入的内容。")
                .setNegativeButton("取消", null)
                .create();
        progress.setCancelable(false);
        progress.setOnShowListener(dialog ->
                progress.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(view -> {
                    cancelled = true;
                    AgentService current = active;
                    if (current != null) {
                        current.cancel();
                    }
                    dismissProgress();
                    send.setEnabled(true);
                }));
        progress.show();

        UiTask.run(() -> {
            JSONObject provider = settings.getProvider();
            String key = settings.getApiKey(provider.optString("id"));
            if (key.isEmpty()) {
                throw new IllegalStateException("请先在设置中填写 API Key，再使用 AI 对话。");
            }
            final AiClient client = new AiClient(provider, key);
            AgentService service = new AgentService(
                    repository, messages, new AgentService.Completer() {
                @Override
                public String complete(String system, JSONArray window, String message) throws Exception {
                    return client.complete(system, window, null, message);
                }

                @Override
                public void cancel() {
                    client.cancel();
                }
            });
            active = service;
            try {
                return service.handle(AgentService.SESSION_ID, text);
            } finally {
                active = null;
            }
        }, reply -> {
            if (request != generation || cancelled) {
                return;
            }
            dismissProgress();
            send.setEnabled(true);
            input.setText("");
            loadHistory();
            if (AgentReply.KIND_CREATED.equals(reply.kind)) {
                Toast.makeText(activity, "已记录到本地账本", Toast.LENGTH_SHORT).show();
            }
        }, error -> {
            if (request != generation || cancelled) {
                return;
            }
            dismissProgress();
            send.setEnabled(true);
            if (error instanceof CancellationException) {
                return;
            }
            Ui.showError(activity, "Agent 处理失败", error);
        });
    }

    private void dismissProgress() {
        if (progress != null && progress.isShowing()) {
            try {
                progress.dismiss();
            } catch (Exception ignored) {
            }
        }
        progress = null;
    }

    private void scrollDown() {
        scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
    }
}

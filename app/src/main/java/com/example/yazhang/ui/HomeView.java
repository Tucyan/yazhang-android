package com.example.yazhang.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.example.yazhang.R;
import com.example.yazhang.data.EntryEntity;
import com.example.yazhang.data.GoalEntity;
import com.example.yazhang.data.LedgerRepository;
import com.example.yazhang.domain.CategoryTotal;
import com.example.yazhang.domain.HomeSummary;
import com.example.yazhang.domain.Money;
import com.example.yazhang.domain.StatsFilter;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

@SuppressLint("ViewConstructor")
public final class HomeView extends FrameLayout implements RefreshablePage {

    public interface ManualEntryLauncher {
        void open(String initialType);
    }

    private final LedgerRepository repository;
    private final ManualEntryLauncher launcher;
    private final Runnable imageLauncher;
    private final LinearLayout summaryCard;
    private final LinearLayout goalCard;
    private final LinearLayout chartCard;
    private final LinearLayout recentCard;

    public HomeView(Context context, LedgerRepository repository, ManualEntryLauncher launcher,
                    Runnable imageLauncher) {
        super(context);
        this.repository = repository;
        this.launcher = launcher;
        this.imageLauncher = imageLauncher;

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(context, 16), Ui.dp(context, 10), Ui.dp(context, 16), Ui.dp(context, 96));

        // Header with Sprout icon and offline badge
        content.addView(createHeader(context));

        // AI Smart Receipt Card
        content.addView(createAiCard(context));

        summaryCard = addCard(content, 14);
        goalCard = addCard(content, 14);
        chartCard = addCard(content, 14);
        recentCard = addCard(content, 14);

        scroll.addView(content, new ScrollView.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
        ));
        addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        FloatingActionButton fab = new FloatingActionButton(context);
        fab.setImageResource(R.drawable.ic_add);
        fab.setContentDescription("记一笔");
        fab.setBackgroundTintList(ColorStateList.valueOf(Ui.ACCENT));
        fab.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        fab.setOnClickListener(view -> launcher.open("EXPENSE"));
        LayoutParams fabParams = new LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        fabParams.setMargins(0, 0, Ui.dp(context, 16), Ui.dp(context, 20));
        addView(fab, fabParams);
    }

    private View createHeader(Context context) {
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(0, Ui.dp(context, 4), 0, Ui.dp(context, 8));

        LinearLayout topRow = new LinearLayout(context);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        ImageView sprout = new ImageView(context);
        sprout.setImageResource(R.drawable.ic_sprout);
        sprout.setImageTintList(ColorStateList.valueOf(Ui.ACCENT));
        LinearLayout.LayoutParams sproutParams = new LinearLayout.LayoutParams(
                Ui.dp(context, 26), Ui.dp(context, 26));
        sproutParams.rightMargin = Ui.dp(context, 8);
        topRow.addView(sprout, sproutParams);

        TextView title = new TextView(context);
        title.setText(context.getString(R.string.app_name));
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(Ui.GREEN);
        topRow.addView(title, Ui.matchWeight());

        // Privacy badge
        LinearLayout badge = new LinearLayout(context);
        badge.setOrientation(LinearLayout.HORIZONTAL);
        badge.setGravity(Gravity.CENTER_VERTICAL);
        badge.setBackground(Ui.rounded(Ui.SOFT, 12, context));
        badge.setPadding(Ui.dp(context, 8), Ui.dp(context, 3), Ui.dp(context, 8), Ui.dp(context, 3));

        ImageView shield = new ImageView(context);
        shield.setImageResource(R.drawable.ic_shield_check);
        shield.setImageTintList(ColorStateList.valueOf(Ui.ACCENT));
        badge.addView(shield, new LinearLayout.LayoutParams(Ui.dp(context, 14), Ui.dp(context, 14)));

        TextView badgeText = new TextView(context);
        badgeText.setText("本地优先");
        badgeText.setTextSize(11);
        badgeText.setTextColor(Ui.ACCENT);
        badgeText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        badgeText.setPadding(Ui.dp(context, 4), 0, 0, 0);
        badge.addView(badgeText);

        topRow.addView(badge);
        header.addView(topRow);

        TextView subtitle = Ui.subtitle(context, context.getString(R.string.home_subtitle));
        header.addView(subtitle);
        return header;
    }

    private View createAiCard(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(context, 16), Ui.dp(context, 14), Ui.dp(context, 16), Ui.dp(context, 14));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(238, 247, 241));
        bg.setCornerRadius(Ui.dp(context, 16));
        bg.setStroke(Ui.dp(context, 1), Color.rgb(212, 235, 221));
        card.setBackground(bg);

        LinearLayout titleRow = new LinearLayout(context);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        ImageView aiIcon = new ImageView(context);
        aiIcon.setImageResource(R.drawable.ic_ai_sparkle);
        aiIcon.setImageTintList(ColorStateList.valueOf(Ui.ACCENT));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                Ui.dp(context, 20), Ui.dp(context, 20));
        iconParams.rightMargin = Ui.dp(context, 8);
        titleRow.addView(aiIcon, iconParams);

        TextView aiTitle = new TextView(context);
        aiTitle.setText("AI 智能单据识别");
        aiTitle.setTextSize(16);
        aiTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        aiTitle.setTextColor(Ui.GREEN);
        titleRow.addView(aiTitle);
        card.addView(titleRow);

        TextView desc = Ui.body(context, "只识别你主动选择的图片，不会监听截图或自动扫描相册。");
        desc.setTextSize(13);
        desc.setPadding(0, Ui.dp(context, 4), 0, Ui.dp(context, 10));
        card.addView(desc);

        Button imageButton = Ui.primaryButton(context, "从账单图片记账（AI）", R.drawable.ic_camera);
        imageButton.setOnClickListener(view -> this.imageLauncher.run());
        card.addView(imageButton, Ui.matchWrap());

        TextView tip = Ui.body(context, "也可以点右下角加号手动记一笔，AI 不可用时账本照常使用。");
        tip.setTextSize(12);
        tip.setTextColor(Ui.MUTED_LIGHT);
        tip.setPadding(0, Ui.dp(context, 6), 0, 0);
        card.addView(tip);

        LinearLayout.LayoutParams params = Ui.matchWrap();
        params.topMargin = Ui.dp(context, 10);
        card.setLayoutParams(params);
        return card;
    }

    private LinearLayout addCard(LinearLayout parent, int topMarginDp) {
        LinearLayout card = Ui.card(getContext());
        LinearLayout.LayoutParams params = Ui.matchWrap();
        params.topMargin = Ui.dp(getContext(), topMarginDp);
        parent.addView(card, params);
        return card;
    }

    @Override
    public View getView() {
        return this;
    }

    @Override
    public void refresh() {
        final LocalDate today = LocalDate.now();
        UiTask.run(() -> load(today), this::render,
                error -> Ui.showError(getContext(), "读取首页失败", error));
    }

    private Data load(LocalDate today) {
        HomeSummary summary = repository.home(today);
        GoalEntity goal = repository.activeGoal();
        StatsFilter month = StatsFilter.of(
                today.withDayOfMonth(1).toString(),
                today.toString(),
                "EXPENSE",
                StatsFilter.ALL
        );
        List<CategoryTotal> totals = repository.stats(month);
        StatsFilter recent = StatsFilter.of(
                today.minusDays(29).toString(),
                today.toString(),
                StatsFilter.ALL,
                StatsFilter.ALL
        );
        return new Data(summary, goal, totals, repository.entries(recent));
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

    private void render(Data data) {
        Context context = getContext();

        summaryCard.removeAllViews();
        summaryCard.addView(createSectionHeader(context, "首页概览", R.drawable.ic_calendar));
        if (data.summary.kind == HomeSummary.Kind.TODAY) {
            summaryCard.addView(paddedBody(context, "今日支出"));
            summaryCard.addView(Ui.amount(context, Formats.yuan(data.summary.totalFen), Ui.GREEN));
        } else if (data.summary.kind == HomeSummary.Kind.YESTERDAY) {
            summaryCard.addView(paddedBody(context, "昨日支出"));
            summaryCard.addView(Ui.amount(context, Formats.yuan(data.summary.totalFen), Ui.GREEN));
            summaryCard.addView(paddedBody(context, "今天还没有支出，先显示昨日数据"));
        } else {
            TextView empty = Ui.body(context, context.getString(R.string.home_empty));
            empty.setTextSize(14);
            empty.setTextColor(Ui.MUTED);
            empty.setPadding(0, Ui.dp(context, 8), 0, Ui.dp(context, 2));
            summaryCard.addView(empty);
        }

        goalCard.removeAllViews();
        goalCard.addView(createSectionHeader(context, "攒钱目标", R.drawable.ic_target_flag));
        if (data.goal == null) {
            TextView empty = Ui.body(context, "还没有进行中的攒钱目标，设定目标开启储蓄计划");
            empty.setTextSize(13);
            empty.setPadding(0, Ui.dp(context, 6), 0, Ui.dp(context, 10));
            goalCard.addView(empty);

            Button newGoal = Ui.secondaryButton(context, "新建目标", R.drawable.ic_add);
            newGoal.setOnClickListener(view -> showGoalDialog());
            goalCard.addView(newGoal, Ui.matchWrap());
        } else {
            LinearLayout goalTitleRow = new LinearLayout(context);
            goalTitleRow.setOrientation(LinearLayout.HORIZONTAL);
            goalTitleRow.setGravity(Gravity.CENTER_VERTICAL);
            goalTitleRow.setPadding(0, Ui.dp(context, 6), 0, 0);

            TextView goalName = Ui.body(context, data.goal.name);
            goalName.setTextSize(16);
            goalName.setTextColor(Ui.GREEN);
            goalName.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            goalTitleRow.addView(goalName, Ui.matchWeight());

            int pct = progressPercent(data.goal);
            TextView pctView = new TextView(context);
            pctView.setText(pct + "%");
            pctView.setTextSize(12);
            pctView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            pctView.setTextColor(Ui.ACCENT);
            pctView.setBackground(Ui.rounded(Ui.SOFT, 8, context));
            pctView.setPadding(Ui.dp(context, 6), Ui.dp(context, 2), Ui.dp(context, 6), Ui.dp(context, 2));
            goalTitleRow.addView(pctView);
            goalCard.addView(goalTitleRow);

            goalCard.addView(paddedBody(context, "已存 " + Formats.yuan(data.goal.currentFen)
                    + " / " + Formats.yuan(data.goal.targetFen)));
            ProgressBar progress = new ProgressBar(
                    context, null, android.R.attr.progressBarStyleHorizontal);
            progress.setMax(1000);
            progress.setProgress(progressOf(data.goal));
            progress.setProgressTintList(ColorStateList.valueOf(Ui.ACCENT));
            Ui.addWithMargin(goalCard, progress, 10, context);

            Button save = Ui.secondaryButton(context, "存入一笔", R.drawable.ic_cat_saving);
            save.setOnClickListener(view -> launcher.open("SAVING"));
            Ui.addWithMargin(goalCard, save, 4, context);
        }

        chartCard.removeAllViews();
        chartCard.addView(createSectionHeader(context, "本月分类支出", R.drawable.ic_tab_stats));
        if (data.totals.isEmpty()) {
            chartCard.addView(Ui.empty(context, "本月还没有支出数据"));
        } else {
            SimpleChartView chart = new SimpleChartView(context);
            chart.setData(data.totals);
            chartCard.addView(chart, Ui.matchWrap());
        }

        recentCard.removeAllViews();
        recentCard.addView(createSectionHeader(context, "最近账目", R.drawable.ic_tab_ledger));
        if (data.recent.isEmpty()) {
            recentCard.addView(Ui.empty(context, "最近 30 天还没有账目"));
        } else {
            int limit = Math.min(data.recent.size(), 10);
            for (int i = 0; i < limit; i++) {
                recentCard.addView(Ui.entryRow(context, data.recent.get(i)));
                if (i < limit - 1) {
                    recentCard.addView(Ui.divider(context));
                }
            }
        }
    }

    void showGoalDialog() {
        Context context = getContext();
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(context, 20), Ui.dp(context, 12),
                Ui.dp(context, 20), Ui.dp(context, 12));

        EditText nameInput = Ui.input(context, "例如：旅行基金", InputType.TYPE_CLASS_TEXT);
        EditText targetInput = Ui.input(context, "目标金额（元）",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText dateInput = Ui.input(context, "YYYY-MM-DD", InputType.TYPE_CLASS_DATETIME);
        dateInput.setText(LocalDate.now().plusMonths(3).toString());

        Ui.addWithMargin(content, Ui.labeledField(context, "目标名称", nameInput), 10, context);
        Ui.addWithMargin(content, Ui.labeledField(context, "目标金额", targetInput), 10, context);
        Ui.addWithMargin(content, Ui.labeledField(context, "目标日期", dateInput), 10, context);

        ScrollView scroll = new ScrollView(context);
        scroll.addView(content, new ScrollView.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("新建攒钱目标")
                .setView(scroll)
                .setNegativeButton("取消", null)
                .setPositiveButton("创建", null)
                .create();
        Ui.styleDialog(dialog);
        dialog.setOnShowListener(d -> {
            Button pos = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (pos != null) {
                pos.setTextColor(Ui.ACCENT);
                pos.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                pos.setOnClickListener(view -> {
                    long targetFen;
                    try {
                        targetFen = Money.parseFen(targetInput.getText().toString().trim());
                    } catch (IllegalArgumentException error) {
                        targetInput.setError(error.getMessage());
                        return;
                    }
                    String rawDate = dateInput.getText().toString().trim();
                    try {
                        LocalDate.parse(rawDate);
                    } catch (DateTimeParseException error) {
                        dateInput.setError("日期格式无效，请使用 YYYY-MM-DD");
                        return;
                    }
                    pos.setEnabled(false);
                    UiTask.run(
                            () -> repository.createGoal(
                                    nameInput.getText().toString(), targetFen, rawDate),
                            goalId -> {
                                dialog.dismiss();
                                Toast.makeText(context, "目标已创建", Toast.LENGTH_SHORT).show();
                                refresh();
                            },
                            error -> {
                                pos.setEnabled(true);
                                Ui.showError(context, "无法创建目标", error);
                            });
                });
            }
            Button neg = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (neg != null) {
                neg.setTextColor(Ui.MUTED);
            }
        });
        dialog.show();
    }

    private static int progressPercent(GoalEntity goal) {
        if (goal.targetFen <= 0) {
            return 0;
        }
        long scaled = goal.currentFen * 100L / goal.targetFen;
        return (int) Math.max(0L, Math.min(100L, scaled));
    }

    private static int progressOf(GoalEntity goal) {
        if (goal.targetFen <= 0) {
            return 0;
        }
        long scaled = goal.currentFen * 1000L / goal.targetFen;
        return (int) Math.max(0L, Math.min(1000L, scaled));
    }

    private static TextView paddedBody(Context context, String text) {
        TextView view = Ui.body(context, text);
        view.setPadding(0, Ui.dp(context, 4), 0, 0);
        return view;
    }

    private static final class Data {
        final HomeSummary summary;
        final GoalEntity goal;
        final List<CategoryTotal> totals;
        final List<EntryEntity> recent;

        Data(HomeSummary summary, GoalEntity goal,
             List<CategoryTotal> totals, List<EntryEntity> recent) {
            this.summary = summary;
            this.goal = goal;
            this.totals = totals;
            this.recent = recent;
        }
    }
}

package com.example.yazhang.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import com.example.yazhang.R;
import com.example.yazhang.data.EntryEntity;
import com.example.yazhang.data.LedgerRepository;
import com.example.yazhang.domain.CategoryTotal;
import com.example.yazhang.domain.StatsCategoryOptions;
import com.example.yazhang.domain.StatsFilter;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@SuppressLint("ViewConstructor")
public final class StatsView extends ScrollView implements RefreshablePage {

    private static final String[] PERIOD_LABELS = {"本周", "本月", "今年"};
    private static final String[] TYPE_LABELS = {"全部", "支出", "收入", "攒钱"};
    private static final String[] TYPE_CODES = {
            StatsFilter.ALL, "EXPENSE", "INCOME", "SAVING"
    };

    private final LedgerRepository repository;
    private final Spinner periodSpinner;
    private final Spinner typeSpinner;
    private final Spinner categorySpinner;
    private final LinearLayout chartCard;
    private final LinearLayout detailCard;
    private boolean updatingCategory;
    private String selectedType = StatsFilter.ALL;
    private String selectedCategory = StatsFilter.ALL;

    public StatsView(Context context, LedgerRepository repository) {
        super(context);
        this.repository = repository;
        setVerticalScrollBarEnabled(false);
        setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(context, 16), Ui.dp(context, 10), Ui.dp(context, 16), Ui.dp(context, 24));
        content.addView(Ui.title(context, context.getString(R.string.tab_statistics)));
        content.addView(Ui.subtitle(context, "按周期、类型与分类查看本地账目"));

        LinearLayout filterCard = Ui.card(context);
        filterCard.addView(createSectionHeader(context, "筛选条件", R.drawable.ic_filter));

        periodSpinner = Ui.spinner(context, PERIOD_LABELS);
        periodSpinner.setSelection(1);
        typeSpinner = Ui.spinner(context, TYPE_LABELS);
        categorySpinner = Ui.spinner(context, StatsCategoryOptions.labelsFor(StatsFilter.ALL));

        LinearLayout row1 = new LinearLayout(context);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout fieldPeriod = Ui.labeledField(context, "周期", periodSpinner);
        LinearLayout fieldType = Ui.labeledField(context, "类型", typeSpinner);
        LinearLayout.LayoutParams p1 = Ui.matchWeight();
        p1.rightMargin = Ui.dp(context, 8);
        LinearLayout.LayoutParams p2 = Ui.matchWeight();
        row1.addView(fieldPeriod, p1);
        row1.addView(fieldType, p2);
        Ui.addWithMargin(filterCard, row1, 8, context);
        Ui.addWithMargin(filterCard, Ui.labeledField(context, "分类", categorySpinner), 4, context);
        LinearLayout.LayoutParams filterParams = Ui.matchWrap();
        filterParams.topMargin = Ui.dp(context, 14);
        content.addView(filterCard, filterParams);

        chartCard = addCard(content, context);
        detailCard = addCard(content, context);

        addView(content, new ScrollView.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        AdapterView.OnItemSelectedListener refreshListener =
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view,
                                               int position, long id) {
                        refresh();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                };
        periodSpinner.setOnItemSelectedListener(refreshListener);
        categorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (updatingCategory) {
                    return;
                }
                String[] codes = StatsCategoryOptions.codesFor(selectedType);
                if (position >= 0 && position < codes.length) {
                    selectedCategory = codes[position];
                }
                refresh();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        typeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedType = TYPE_CODES[position];
                updateCategoryOptions();
                refresh();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
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

    private LinearLayout addCard(LinearLayout parent, Context context) {
        LinearLayout card = Ui.card(context);
        LinearLayout.LayoutParams params = Ui.matchWrap();
        params.topMargin = Ui.dp(context, 14);
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
        final int periodIndex = periodSpinner.getSelectedItemPosition();
        final String type = selectedType;
        final String category = selectedCategory;
        final String from = periodStart(today, periodIndex);

        UiTask.run(() -> {
            StatsFilter filter = StatsFilter.of(from, today.toString(), type, category);
            return new Data(repository.stats(filter), repository.entries(filter));
        }, this::render, error -> Ui.showError(getContext(), "读取统计失败", error));
    }

    private void render(Data data) {
        Context context = getContext();

        chartCard.removeAllViews();
        chartCard.addView(createSectionHeader(context, "分类汇总", R.drawable.ic_tab_stats));
        if (data.totals.isEmpty()) {
            chartCard.addView(Ui.empty(context, "当前筛选下没有账目"));
        } else {
            long total = 0L;
            for (CategoryTotal categoryTotal : data.totals) {
                total += categoryTotal.totalFen;
            }

            LinearLayout totalRow = new LinearLayout(context);
            totalRow.setOrientation(LinearLayout.HORIZONTAL);
            totalRow.setGravity(Gravity.CENTER_VERTICAL);
            totalRow.setPadding(0, Ui.dp(context, 6), 0, Ui.dp(context, 8));

            TextView totalLabel = Ui.body(context, "合计：");
            totalLabel.setTextSize(14);
            totalRow.addView(totalLabel);

            TextView totalVal = new TextView(context);
            totalVal.setText(Formats.yuan(total));
            totalVal.setTextSize(22);
            totalVal.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            totalVal.setTextColor(Ui.GREEN);
            totalRow.addView(totalVal);

            chartCard.addView(totalRow);

            SimpleChartView chart = new SimpleChartView(context);
            chart.setData(data.totals);
            chartCard.addView(chart, Ui.matchWrap());
        }

        detailCard.removeAllViews();
        detailCard.addView(createSectionHeader(context, "账目明细", R.drawable.ic_tab_ledger));
        detailCard.addView(paddedBody(context, "最多显示最近 50 笔"));
        if (data.entries.isEmpty()) {
            detailCard.addView(Ui.empty(context, "当前筛选下没有账目"));
        } else {
            for (int i = 0; i < data.entries.size(); i++) {
                detailCard.addView(Ui.entryRow(context, data.entries.get(i)));
                if (i < data.entries.size() - 1) {
                    detailCard.addView(Ui.divider(context));
                }
            }
        }
    }

    private void updateCategoryOptions() {
        updatingCategory = true;
        selectedCategory = StatsFilter.ALL;
        String[] labels = StatsCategoryOptions.labelsFor(selectedType);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                getContext(), android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(adapter);
        categorySpinner.setSelection(0);
        updatingCategory = false;
    }

    private static String periodStart(LocalDate today, int periodIndex) {
        if (periodIndex == 0) {
            return today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();
        }
        if (periodIndex == 2) {
            return today.withDayOfYear(1).toString();
        }
        return today.withDayOfMonth(1).toString();
    }

    private static TextView paddedBody(Context context, String text) {
        TextView view = Ui.body(context, text);
        view.setPadding(0, Ui.dp(context, 4), 0, 0);
        return view;
    }

    private static final class Data {
        final List<CategoryTotal> totals;
        final List<EntryEntity> entries;

        Data(List<CategoryTotal> totals, List<EntryEntity> entries) {
            this.totals = totals;
            this.entries = entries;
        }
    }
}

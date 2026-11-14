package com.example.yazhang.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.example.yazhang.data.GoalEntity;
import com.example.yazhang.data.LedgerRepository;
import com.example.yazhang.domain.EntryDraft;
import com.example.yazhang.domain.EntryValidator;
import com.example.yazhang.domain.Money;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public final class EntryDialog {

    private static final String[] TYPES = {"EXPENSE", "INCOME", "SAVING"};
    private static final String[] TYPE_LABELS = {"支出", "收入", "攒钱"};

    private static final String[] EXPENSE_CATEGORIES = {
            "FOOD", "TRANSPORT", "SHOPPING", "HOUSING", "ENTERTAINMENT", "OTHER"
    };
    private static final String[] EXPENSE_LABELS = {
            "餐饮", "交通", "购物", "居住", "娱乐", "其他"
    };
    private static final String[] INCOME_CATEGORIES = {"SALARY"};
    private static final String[] INCOME_LABELS = {"工资"};
    private static final String[] SAVING_CATEGORIES = {"SAVING"};
    private static final String[] SAVING_LABELS = {"攒钱"};

    private EntryDialog() {
    }

    private static final class Prefill {
        final long amountFen;
        final String category;
        final String merchant;
        final String occurredOn;
        final String note;

        Prefill(long amountFen, String category, String merchant, String occurredOn,
                String note) {
            this.amountFen = amountFen;
            this.category = category;
            this.merchant = merchant;
            this.occurredOn = occurredOn;
            this.note = note;
        }
    }

    public static void show(Activity activity, LedgerRepository repository,
                            String initialType, Runnable onSaved) {
        loadGoals(activity, repository, initialType, null, onSaved);
    }

    /** Opens the manual form with fields already taken from a confirmed image result. */
    public static void showPrefilled(Activity activity, LedgerRepository repository,
                                     long amountFen, String category, String merchant,
                                     String occurredOn, String note, Runnable onSaved) {
        loadGoals(activity, repository, "EXPENSE",
                new Prefill(amountFen, category, merchant, occurredOn, note), onSaved);
    }

    private static void loadGoals(Activity activity, LedgerRepository repository,
                                  String initialType, Prefill prefill, Runnable onSaved) {
        UiTask.run(() -> {
            List<GoalEntity> active = new ArrayList<>();
            for (GoalEntity goal : repository.goals()) {
                if (GoalEntity.STATUS_ACTIVE.equals(goal.status)) {
                    active.add(goal);
                }
            }
            return active;
        }, goals -> showLoaded(activity, repository, initialType, goals, prefill, onSaved),
                error -> Ui.showError(activity, "无法打开记账表单", error));
    }

    private static void showLoaded(Activity activity, LedgerRepository repository,
                                   String initialType, List<GoalEntity> goals, Prefill prefill,
                                   Runnable onSaved) {
        final Context context = activity;
        final List<GoalEntity> activeGoals = goals;

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(context, 20), Ui.dp(context, 12),
                Ui.dp(context, 20), Ui.dp(context, 12));

        Spinner typeSpinner = Ui.spinner(context, TYPE_LABELS);
        final EditText amountInput = input(context, "0.00",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amountInput.setTextSize(22);
        amountInput.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        amountInput.setTextColor(Ui.GREEN);

        Spinner categorySpinner = Ui.spinner(context, EXPENSE_LABELS);
        Spinner goalSpinner = Ui.spinner(context, goalLabels(activeGoals));
        LinearLayout goalField = Ui.labeledField(context, "攒钱目标", goalSpinner);
        final EditText merchantInput = input(context, "例如：美团外卖、星巴克",
                InputType.TYPE_CLASS_TEXT);
        final EditText titleInput = input(context, "简短备注（可选）",
                InputType.TYPE_CLASS_TEXT);
        final EditText detailInput = input(context, "详细说明（可选）",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        detailInput.setSingleLine(false);
        detailInput.setMinLines(2);
        detailInput.setGravity(Gravity.TOP | Gravity.START);
        final EditText dateInput = input(context, "YYYY-MM-DD",
                InputType.TYPE_CLASS_DATETIME);
        dateInput.setText(prefill == null
                ? LocalDate.now().toString()
                : prefill.occurredOn == null ? "" : prefill.occurredOn);
        if (prefill != null && prefill.occurredOn == null) {
            dateInput.setHint("YYYY-MM-DD（请填写）");
        }

        if (prefill != null && prefill.note != null && !prefill.note.trim().isEmpty()) {
            TextView note = Ui.body(context, prefill.note);
            note.setTextColor(Ui.ACCENT);
            note.setBackground(Ui.rounded(Ui.SOFT, 8, context));
            note.setPadding(Ui.dp(context, 10), Ui.dp(context, 6), Ui.dp(context, 10), Ui.dp(context, 6));
            Ui.addWithMargin(content, note, 10, context);
        }

        Ui.addWithMargin(content, Ui.labeledField(context, "类型", typeSpinner), 10, context);
        Ui.addWithMargin(content, Ui.labeledField(context, "金额（元）", amountInput), 10, context);
        Ui.addWithMargin(content, Ui.labeledField(context, "分类", categorySpinner), 10, context);
        Ui.addWithMargin(content, goalField, 10, context);
        Ui.addWithMargin(content, Ui.labeledField(context, "商家", merchantInput), 10, context);
        Ui.addWithMargin(content, Ui.labeledField(context, "标题", titleInput), 10, context);
        Ui.addWithMargin(content, Ui.labeledField(context, "详情", detailInput), 10, context);
        Ui.addWithMargin(content, Ui.labeledField(context, "日期", dateInput), 10, context);

        int initialIndex = indexOfType(initialType);
        typeSpinner.setSelection(initialIndex);
        applyCategoryOptions(typeSpinner, categorySpinner, goalField);
        if (prefill != null) {
            if (prefill.amountFen > 0) {
                amountInput.setText(Money.format(prefill.amountFen));
            }
            if (prefill.merchant != null) {
                merchantInput.setText(prefill.merchant);
            }
            if (prefill.category != null) {
                int categoryIndex = indexOf(categoriesFor(TYPES[initialIndex]),
                        prefill.category);
                if (categoryIndex >= 0) {
                    categorySpinner.setSelection(categoryIndex);
                }
            }
        }

        typeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                applyCategoryOptions(typeSpinner, categorySpinner, goalField);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(prefill == null ? "记一笔" : "确认识别结果")
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
            }
            Button cancelButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (cancelButton != null) {
                cancelButton.setTextColor(Ui.MUTED);
            }
            saveButton.setOnClickListener(view -> {
                String type = TYPES[typeSpinner.getSelectedItemPosition()];
                long amountFen;
                try {
                    amountFen = Money.parseFen(amountInput.getText().toString().trim());
                } catch (IllegalArgumentException error) {
                    amountInput.setError(error.getMessage());
                    return;
                }

                String rawDate = dateInput.getText().toString().trim();
                LocalDate date;
                try {
                    date = LocalDate.parse(rawDate);
                } catch (DateTimeParseException error) {
                    dateInput.setError("日期格式无效，请使用 YYYY-MM-DD");
                    return;
                }
                if (!date.toString().equals(rawDate)) {
                    dateInput.setError("日期格式无效，请使用 YYYY-MM-DD");
                    return;
                }

                String[] categories = categoriesFor(type);
                String category = categories[categorySpinner.getSelectedItemPosition()];
                EntryDraft draft = new EntryDraft(
                        type,
                        amountFen,
                        category,
                        optional(merchantInput),
                        optional(titleInput),
                        optional(detailInput),
                        date
                );

                String validationError = EntryValidator.validate(draft);
                if (validationError != null) {
                    Ui.showError(context, "无法保存账目", new IllegalArgumentException(validationError));
                    return;
                }

                GoalEntity goal = null;
                if ("SAVING".equals(type)) {
                    if (activeGoals.isEmpty()) {
                        Ui.showError(context, "无法保存账目",
                                new IllegalArgumentException("还没有进行中的攒钱目标，请先在首页新建目标"));
                        return;
                    }
                    goal = activeGoals.get(goalSpinner.getSelectedItemPosition());
                }

                saveButton.setEnabled(false);
                final GoalEntity selectedGoal = goal;
                UiTask.run(() -> {
                    if (selectedGoal != null) {
                        repository.addSaving(selectedGoal.id, draft);
                    } else {
                        repository.create(draft);
                    }
                    return null;
                }, done -> {
                    dialog.dismiss();
                    Toast.makeText(context, "已记账", Toast.LENGTH_SHORT).show();
                    if (onSaved != null) {
                        onSaved.run();
                    }
                }, error -> {
                    saveButton.setEnabled(true);
                    Ui.showError(context, "无法保存账目", error);
                });
            });
        });

        dialog.show();
    }

    private static void applyCategoryOptions(Spinner typeSpinner, Spinner categorySpinner,
                                             LinearLayout goalField) {
        String type = TYPES[typeSpinner.getSelectedItemPosition()];
        String[] labels = labelsFor(type);
        Context context = categorySpinner.getContext();
        android.widget.ArrayAdapter<String> adapter =
                new android.widget.ArrayAdapter<>(
                        context, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(adapter);
        categorySpinner.setSelection(0);
        goalField.setVisibility("SAVING".equals(type) ? View.VISIBLE : View.GONE);
    }

    private static String[] categoriesFor(String type) {
        if ("INCOME".equals(type)) {
            return INCOME_CATEGORIES;
        }
        if ("SAVING".equals(type)) {
            return SAVING_CATEGORIES;
        }
        return EXPENSE_CATEGORIES;
    }

    private static String[] labelsFor(String type) {
        if ("INCOME".equals(type)) {
            return INCOME_LABELS;
        }
        if ("SAVING".equals(type)) {
            return SAVING_LABELS;
        }
        return EXPENSE_LABELS;
    }

    private static int indexOfType(String type) {
        for (int i = 0; i < TYPES.length; i++) {
            if (TYPES[i].equals(type)) {
                return i;
            }
        }
        return 0;
    }

    private static int indexOf(String[] values, String target) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(target)) {
                return i;
            }
        }
        return -1;
    }

    private static String[] goalLabels(List<GoalEntity> goals) {
        if (goals.isEmpty()) {
            return new String[]{"暂无进行中的目标"};
        }
        String[] labels = new String[goals.size()];
        for (int i = 0; i < goals.size(); i++) {
            GoalEntity goal = goals.get(i);
            labels[i] = goal.name + "（" + Formats.yuan(goal.currentFen)
                    + " / " + Formats.yuan(goal.targetFen) + "）";
        }
        return labels;
    }

    private static EditText input(Context context, String hint, int inputType) {
        return Ui.input(context, hint, inputType);
    }

    private static String optional(EditText input) {
        String value = input.getText().toString().trim();
        return value.isEmpty() ? null : value;
    }
}

package com.example.yazhang.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.example.yazhang.R;
import com.example.yazhang.data.EntryEntity;
import com.google.android.material.button.MaterialButton;

import java.util.Locale;

final class Ui {

    static final int GREEN = Color.rgb(24, 52, 38);
    static final int MUTED = Color.rgb(108, 122, 114);
    static final int MUTED_LIGHT = Color.rgb(155, 168, 160);
    static final int LINE = Color.rgb(236, 240, 237);
    static final int ACCENT = Color.rgb(42, 110, 74);
    static final int ACCENT_LIGHT = Color.rgb(230, 245, 236);
    static final int SOFT = Color.rgb(236, 244, 238);
    static final int BG_WARM = Color.rgb(250, 249, 246);
    static final int DANGER = Color.rgb(196, 48, 48);
    static final int CARD_STROKE = Color.rgb(232, 238, 233);

    private Ui() {
    }

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static LinearLayout card(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 16));
        
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(context, 16));
        bg.setStroke(dp(context, 1), CARD_STROKE);
        card.setBackground(bg);
        return card;
    }

    static GradientDrawable rounded(int color, int radiusDp, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    static GradientDrawable roundedStroked(int fillColor, int strokeColor, int strokeWidthDp, int radiusDp, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(context, radiusDp));
        drawable.setStroke(dp(context, strokeWidthDp), strokeColor);
        return drawable;
    }

    static TextView title(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(26);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setTextColor(GREEN);
        return view;
    }

    static TextView subtitle(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(13);
        view.setTextColor(MUTED);
        view.setPadding(0, dp(context, 4), 0, 0);
        return view;
    }

    static TextView heading(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(16);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setTextColor(GREEN);
        return view;
    }

    static TextView body(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(14);
        view.setTextColor(MUTED);
        view.setLineSpacing(0, 1.35f);
        return view;
    }

    static TextView amount(Context context, String text, int color) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(28);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setTextColor(color);
        return view;
    }

    static Button primaryButton(Context context, String text) {
        return primaryButton(context, text, 0);
    }

    static Button primaryButton(Context context, String text, int iconRes) {
        MaterialButton button = new MaterialButton(context);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(Color.WHITE);
        button.setBackgroundTintList(ColorStateList.valueOf(ACCENT));
        button.setCornerRadius(dp(context, 12));
        button.setElevation(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        int padH = dp(context, 16);
        int padV = dp(context, 12);
        button.setPadding(padH, padV, padH, padV);
        if (iconRes != 0) {
            button.setIconResource(iconRes);
            button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
            button.setIconPadding(dp(context, 8));
            button.setIconSize(dp(context, 18));
            button.setIconTint(ColorStateList.valueOf(Color.WHITE));
        }
        return button;
    }

    static Button secondaryButton(Context context, String text) {
        return secondaryButton(context, text, 0);
    }

    static Button secondaryButton(Context context, String text, int iconRes) {
        MaterialButton button = new MaterialButton(context);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(ACCENT);
        button.setBackgroundTintList(ColorStateList.valueOf(SOFT));
        button.setCornerRadius(dp(context, 12));
        button.setElevation(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        int padH = dp(context, 16);
        int padV = dp(context, 12);
        button.setPadding(padH, padV, padH, padV);
        if (iconRes != 0) {
            button.setIconResource(iconRes);
            button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
            button.setIconPadding(dp(context, 8));
            button.setIconSize(dp(context, 18));
            button.setIconTint(ColorStateList.valueOf(ACCENT));
        }
        return button;
    }

    static EditText input(Context context, String hint, int inputType) {
        EditText input = new EditText(context);
        input.setHint(hint);
        input.setInputType(inputType);
        input.setSingleLine(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(context, 8));
        bg.setStroke(dp(context, 1), Color.rgb(222, 228, 224));
        input.setBackground(bg);
        int padH = dp(context, 12);
        int padV = dp(context, 10);
        input.setPadding(padH, padV, padH, padV);
        return input;
    }

    static Spinner spinner(Context context, String[] labels) {
        Spinner spinner = new Spinner(context);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                context, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(context, 8));
        bg.setStroke(dp(context, 1), Color.rgb(228, 236, 231));
        spinner.setBackground(bg);
        int padH = dp(context, 12);
        int padV = dp(context, 8);
        spinner.setPadding(padH, padV, padH, padV);
        return spinner;
    }

    static LinearLayout labeledField(Context context, String label, View input) {
        LinearLayout field = new LinearLayout(context);
        field.setOrientation(LinearLayout.VERTICAL);
        TextView labelView = body(context, label);
        labelView.setTextColor(GREEN);
        labelView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        labelView.setTextSize(13);
        labelView.setPadding(0, 0, 0, dp(context, 4));
        field.addView(labelView);
        field.addView(input, matchWrap());
        return field;
    }

    static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    static LinearLayout.LayoutParams matchWeight() {
        return new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        );
    }

    static void addWithMargin(LinearLayout parent, View child, int marginDp, Context context) {
        LinearLayout.LayoutParams params = matchWrap();
        params.bottomMargin = dp(context, marginDp);
        parent.addView(child, params);
    }

    static View divider(Context context) {
        View divider = new View(context);
        divider.setBackgroundColor(LINE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 1));
        divider.setLayoutParams(params);
        return divider;
    }

    static TextView empty(Context context, String text) {
        TextView view = body(context, text);
        view.setPadding(0, dp(context, 8), 0, 0);
        return view;
    }

    static int categoryIconRes(String category) {
        if (category == null) {
            return R.drawable.ic_cat_other;
        }
        switch (category.toUpperCase(Locale.ROOT)) {
            case "FOOD":
                return R.drawable.ic_cat_food;
            case "TRANSPORT":
                return R.drawable.ic_cat_transport;
            case "SHOPPING":
                return R.drawable.ic_cat_shopping;
            case "HOUSING":
                return R.drawable.ic_cat_housing;
            case "ENTERTAINMENT":
                return R.drawable.ic_cat_entertainment;
            case "SAVING":
                return R.drawable.ic_cat_saving;
            case "SALARY":
            case "INCOME":
                return R.drawable.ic_cat_income;
            default:
                return R.drawable.ic_cat_other;
        }
    }

    static int categoryColor(String category) {
        if (category == null) {
            return Color.rgb(108, 122, 114);
        }
        switch (category.toUpperCase(Locale.ROOT)) {
            case "FOOD":
                return Color.rgb(230, 115, 34);
            case "TRANSPORT":
                return Color.rgb(33, 133, 208);
            case "SHOPPING":
                return Color.rgb(142, 68, 173);
            case "HOUSING":
                return Color.rgb(22, 160, 133);
            case "ENTERTAINMENT":
                return Color.rgb(224, 86, 36);
            case "SAVING":
                return Color.rgb(39, 174, 96);
            case "SALARY":
            case "INCOME":
                return Color.rgb(46, 170, 95);
            default:
                return Color.rgb(120, 135, 125);
        }
    }

    static int categoryBgColor(String category) {
        if (category == null) {
            return Color.rgb(240, 243, 241);
        }
        switch (category.toUpperCase(Locale.ROOT)) {
            case "FOOD":
                return Color.rgb(255, 243, 232);
            case "TRANSPORT":
                return Color.rgb(235, 245, 255);
            case "SHOPPING":
                return Color.rgb(247, 238, 255);
            case "HOUSING":
                return Color.rgb(232, 248, 245);
            case "ENTERTAINMENT":
                return Color.rgb(255, 241, 236);
            case "SAVING":
                return Color.rgb(235, 250, 240);
            case "SALARY":
            case "INCOME":
                return Color.rgb(235, 252, 240);
            default:
                return Color.rgb(240, 243, 241);
        }
    }

    static View categoryBadge(Context context, String category, int sizeDp) {
        int iconRes = categoryIconRes(category);
        int tintColor = categoryColor(category);
        int bgColor = categoryBgColor(category);

        ImageView view = new ImageView(context);
        view.setImageResource(iconRes);
        view.setImageTintList(ColorStateList.valueOf(tintColor));
        view.setBackground(rounded(bgColor, sizeDp / 2, context));
        int pad = dp(context, Math.max(4, Math.round(sizeDp * 0.2f)));
        view.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(context, sizeDp), dp(context, sizeDp));
        view.setLayoutParams(params);
        return view;
    }

    static View entryRow(Context context, EntryEntity entry) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(context, 10), 0, dp(context, 10));

        View badge = categoryBadge(context, entry.category, 40);
        LinearLayout.LayoutParams badgeParams = (LinearLayout.LayoutParams) badge.getLayoutParams();
        badgeParams.rightMargin = dp(context, 12);
        row.addView(badge);

        LinearLayout text = new LinearLayout(context);
        text.setOrientation(LinearLayout.VERTICAL);

        LinearLayout titleRow = new LinearLayout(context);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(context);
        title.setText(Formats.entryTitle(entry));
        title.setTextSize(15);
        title.setTextColor(GREEN);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleRow.addView(title);

        if (entry.source != null && entry.source.startsWith("AI")) {
            TextView aiBadge = new TextView(context);
            aiBadge.setText("AI");
            aiBadge.setTextSize(10);
            aiBadge.setTextColor(ACCENT);
            aiBadge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            aiBadge.setBackground(rounded(SOFT, 4, context));
            aiBadge.setPadding(dp(context, 4), dp(context, 1), dp(context, 4), dp(context, 1));
            LinearLayout.LayoutParams aiParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            aiParams.leftMargin = dp(context, 6);
            titleRow.addView(aiBadge, aiParams);
        }
        text.addView(titleRow);

        TextView meta = body(context, String.format(Locale.CHINA, "%s · %s · %s",
                entry.occurredOn,
                Formats.typeLabel(entry.type),
                Formats.categoryLabel(entry.category)));
        meta.setTextSize(12);
        meta.setTextColor(MUTED);
        meta.setPadding(0, dp(context, 2), 0, 0);
        text.addView(meta);
        row.addView(text, matchWeight());

        int color = "INCOME".equals(entry.type) ? ACCENT : DANGER;
        if ("SAVING".equals(entry.type)) {
            color = GREEN;
        }
        TextView amount = new TextView(context);
        amount.setText(Formats.signedYuan(entry));
        amount.setTextSize(16);
        amount.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        amount.setTextColor(color);
        row.addView(amount);
        return row;
    }

    static AlertDialog styleDialog(AlertDialog dialog) {
        if (dialog == null) {
            return null;
        }
        dialog.setOnShowListener(d -> {
            Button pos = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (pos != null) {
                pos.setTextColor(ACCENT);
                pos.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            }
            Button neg = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (neg != null) {
                neg.setTextColor(MUTED);
            }
            Button neu = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            if (neu != null) {
                neu.setTextColor(MUTED);
            }
        });
        return dialog;
    }

    static void showError(Context context, String title, Throwable error) {
        String message = error == null ? null : error.getMessage();
        if (message == null || message.trim().isEmpty()
                || message.contains("Exception") || message.contains("\tat ")) {
            message = "本地数据操作失败，请稍后重试";
        }
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("知道了", null)
                .create();
        styleDialog(dialog);
        dialog.show();
    }
}

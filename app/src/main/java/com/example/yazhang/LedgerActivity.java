package com.example.yazhang;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.Intent;

import com.example.yazhang.ui.StatsActivity;
import com.example.yazhang.ui.SettingsActivity;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;


import java.util.ArrayList;
import java.util.List;

public abstract class LedgerActivity extends AppCompatActivity {

    private static final int ACCENT = Color.rgb(42, 110, 74);
    private static final int MUTED = Color.rgb(108, 122, 114);
    private static final int SOFT = Color.rgb(234, 244, 237);
    private static final int BG_WARM = Color.rgb(250, 249, 246);
    private static final int DIVIDER = Color.rgb(235, 239, 236);

    private FrameLayout pageContainer;
    private final List<TabView> tabViews = new ArrayList<>();
    protected int currentPage;

    private static final class TabView {
        final View container;
        final View iconBg;
        final ImageView icon;
        final TextView label;

        TabView(View container, View iconBg, ImageView icon, TextView label) {
            this.container = container;
            this.iconBg = iconBg;
            this.icon = icon;
            this.label = label;
        }
    }

    protected void showLayout(int index, View page) {
        currentPage = index;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG_WARM);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(
                    insets.left,
                    insets.top,
                    insets.right,
                    insets.bottom
            );
            return windowInsets;
        });

        pageContainer = new FrameLayout(this);
        pageContainer.setPadding(0, 0, 0, 0);
        root.addView(pageContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        // Bottom separator
        View navDivider = new View(this);
        navDivider.setBackgroundColor(DIVIDER);
        root.addView(navDivider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));

        root.addView(createBottomTabs(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        setContentView(root);

        displayPage(index, page);
    }

    protected void displayPage(int index, View page) {
        currentPage = index;
        pageContainer.removeAllViews();
        pageContainer.addView(page, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        updateTabs();
    }

    public void showPage(int index) {
        if (index == currentPage) {
            return;
        }
        if (index == 1 || index == 3) {
            startActivity(new Intent(this, index == 1 ? StatsActivity.class : SettingsActivity.class));
        } else {
            Intent intent = new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(MainActivity.STATE_PAGE, index);
            startActivity(intent);
        }
        if (!(this instanceof MainActivity)) {
            finish();
        }
    }

    private void updateTabs() {
        for (int i = 0; i < tabViews.size(); i++) {
            TabView item = tabViews.get(i);
            boolean selected = i == currentPage;
            item.icon.setImageTintList(ColorStateList.valueOf(selected ? ACCENT : MUTED));
            item.label.setTextColor(selected ? ACCENT : MUTED);
            item.label.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);

            if (selected) {
                GradientDrawable pill = new GradientDrawable();
                pill.setColor(SOFT);
                pill.setCornerRadius(dp(16));
                item.iconBg.setBackground(pill);
            } else {
                item.iconBg.setBackground(null);
            }
        }
    }

    private View createBottomTabs() {
        LinearLayout tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setGravity(Gravity.CENTER);
        tabBar.setPadding(dp(8), dp(4), dp(8), dp(4));

        int[] labels = {
                R.string.tab_ledger,
                R.string.tab_statistics,
                R.string.tab_agent,
                R.string.tab_settings
        };
        int[] icons = {
                R.drawable.ic_tab_ledger,
                R.drawable.ic_tab_stats,
                R.drawable.ic_tab_agent,
                R.drawable.ic_tab_settings
        };

        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            LinearLayout itemLayout = new LinearLayout(this);
            itemLayout.setOrientation(LinearLayout.VERTICAL);
            itemLayout.setGravity(Gravity.CENTER);
            itemLayout.setPadding(0, dp(4), 0, dp(4));

            // Icon wrapper for capsule highlight
            FrameLayout iconWrapper = new FrameLayout(this);
            iconWrapper.setPadding(dp(16), dp(4), dp(16), dp(4));

            ImageView iconView = new ImageView(this);
            iconView.setImageResource(icons[i]);
            iconView.setImageTintList(ColorStateList.valueOf(MUTED));
            iconWrapper.addView(iconView, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));

            itemLayout.addView(iconWrapper, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            TextView labelView = new TextView(this);
            labelView.setText(labels[i]);
            labelView.setTextSize(11);
            labelView.setGravity(Gravity.CENTER);
            labelView.setTextColor(MUTED);
            labelView.setPadding(0, dp(1), 0, 0);
            itemLayout.addView(labelView);

            itemLayout.setOnClickListener(view -> showPage(index));

            tabBar.addView(itemLayout, new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
            ));
            tabViews.add(new TabView(itemLayout, iconWrapper, iconView, labelView));
        }
        return tabBar;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

package com.example.yazhang.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

import com.example.yazhang.domain.CategoryTotal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SimpleChartView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<CategoryTotal> totals = Collections.emptyList();

    public SimpleChartView(Context context) {
        super(context);
    }

    public void setData(List<CategoryTotal> data) {
        totals = data == null ? Collections.emptyList() : new ArrayList<>(data);
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec);
        int itemHeight = Ui.dp(getContext(), 42);
        int desired = Ui.dp(getContext(), 12) + totals.size() * itemHeight;
        int height = Math.max(Ui.dp(getContext(), 70), Math.min(desired, Ui.dp(getContext(), 320)));
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().scaledDensity;

        if (totals.isEmpty()) {
            paint.setColor(Ui.MUTED);
            paint.setTextSize(13 * density);
            paint.setTypeface(Typeface.DEFAULT);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("暂无数据，先记一笔吧", getWidth() / 2f, getHeight() / 2f, paint);
            return;
        }

        float max = 1f;
        for (CategoryTotal total : totals) {
            max = Math.max(max, total.totalFen);
        }

        float top = Ui.dp(getContext(), 6);
        float bottom = getHeight() - Ui.dp(getContext(), 6);
        float rowHeight = (bottom - top) / totals.size();
        float labelWidth = Ui.dp(getContext(), 52);
        float valueWidth = Ui.dp(getContext(), 74);
        float barLeft = labelWidth + Ui.dp(getContext(), 8);
        float barRight = Math.max(barLeft + Ui.dp(getContext(), 16),
                getWidth() - valueWidth - Ui.dp(getContext(), 8));
        float barHeight = Math.min(rowHeight * 0.40f, Ui.dp(getContext(), 12));

        for (int i = 0; i < totals.size(); i++) {
            CategoryTotal total = totals.get(i);
            float centerY = top + rowHeight * i + rowHeight / 2f;
            float barTop = centerY - barHeight / 2f;
            float barBottom = centerY + barHeight / 2f;

            // Draw Category Label
            paint.setColor(Ui.GREEN);
            paint.setTextSize(13 * density);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextAlign(Paint.Align.LEFT);
            float labelY = centerY - (paint.ascent() + paint.descent()) / 2f;
            canvas.drawText(Formats.categoryLabel(total.category), 0, labelY, paint);

            // Draw Background Track
            paint.setColor(Color.rgb(238, 243, 239));
            canvas.drawRoundRect(barLeft, barTop, barRight, barBottom,
                    barHeight / 2f, barHeight / 2f, paint);

            // Draw Filled Progress Bar
            float ratio = total.totalFen / max;
            float filledRight = barLeft + (barRight - barLeft) * ratio;
            int categoryColor = Ui.categoryColor(total.category);
            paint.setColor(categoryColor);
            canvas.drawRoundRect(barLeft, barTop, Math.max(barLeft + barHeight, filledRight),
                    barBottom, barHeight / 2f, barHeight / 2f, paint);

            // Draw Amount Value
            paint.setColor(Ui.GREEN);
            paint.setTextSize(13 * density);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(Formats.yuan(total.totalFen), getWidth(), labelY, paint);
        }
    }
}

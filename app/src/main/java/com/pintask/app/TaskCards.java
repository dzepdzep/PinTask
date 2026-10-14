package com.pintask.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.DateFormat;
import java.util.Date;

final class TaskCards {
    static TextView overflow(Activity activity, TaskStore.Task task, Runnable refresh) {
        TextView more = new TextView(activity);
        more.setText("⋮");
        more.setTextSize(26);
        more.setTextColor(Color.rgb(175, 241, 138));
        more.setGravity(Gravity.CENTER);
        more.setContentDescription("More options for " + task.title);
        more.setFocusable(true);
        more.setLayoutParams(new LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 48)));
        more.setOnClickListener(v -> TaskActions.show(activity, v, task, refresh));
        return more;
    }

    static LinearLayout done(Activity activity, TaskStore.Task task, Runnable refresh) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(activity, 12), dp(activity, 8), 0, dp(activity, 8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(24, 32, 43));
        bg.setCornerRadius(dp(activity, 12));
        row.setBackground(bg);
        LinearLayout details = new LinearLayout(activity);
        details.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(activity);
        title.setText("✓  " + task.title);
        title.setTextSize(14);
        title.setTextColor(Color.rgb(240, 244, 249));
        details.addView(title);
        TextView date = new TextView(activity);
        date.setText((task.shiftRunId > 0 ? task.shiftLabel + " · " : "") + "Done " +
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(task.completedAt)));
        date.setTextColor(Color.rgb(150, 163, 179));
        date.setTextSize(11);
        details.addView(date);
        row.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(overflow(activity, task, refresh));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(activity, 8);
        row.setLayoutParams(params);
        return row;
    }

    private static int dp(Activity activity, int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + .5f);
    }
}

package com.pintask.app;

import android.app.Activity;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

final class UndoBar extends LinearLayout {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hide = () -> setVisibility(GONE);
    UndoBar(Activity activity) { super(activity); setVisibility(GONE); }
    void offer(long id, Runnable refresh) {
        handler.removeCallbacks(hide);
        removeAllViews();
        setBackgroundColor(Color.rgb(35,50,40));
        TextView message = new TextView(getContext()); message.setText("Task completed"); message.setTextColor(Color.WHITE);
        addView(message,new LayoutParams(0,-2,1));
        Button undo = new Button(getContext()); undo.setText("Undo"); addView(undo);
        undo.setOnClickListener(v -> {
            try (TaskStore store = new TaskStore(getContext())) {
                long next = store.undoCompletion(id);
                if (next > 0) { ReminderScheduler.cancel(getContext(),next); TaskNotifications.cancel(getContext(),next); }
                TaskStore.Task task = store.find(id);
                TaskNotifications.show(getContext(),task); ReminderScheduler.schedule(getContext(),task);
                refresh.run();
            } catch (IllegalArgumentException e) { Toast.makeText(getContext(),e.getMessage(),Toast.LENGTH_LONG).show(); }
            setVisibility(GONE);
        });
        setVisibility(VISIBLE); handler.postDelayed(hide,10_000);
    }
    @Override protected void onDetachedFromWindow() { handler.removeCallbacks(hide); super.onDetachedFromWindow(); }
}

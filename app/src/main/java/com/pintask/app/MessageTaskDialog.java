package com.pintask.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputFilter;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.time.ZonedDateTime;

/** User-initiated text import: inspect source, then inspect schedule, then save. */
final class MessageTaskDialog {
    private MessageTaskDialog(){}

    static void editMessage(Activity activity,String shared,Runnable saved) {
        int dp=Math.round(16*activity.getResources().getDisplayMetrics().density);
        LinearLayout content=new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp,dp,dp,dp);
        TextView hint=new TextView(activity);
        hint.setText("Paste a message or share text from another app. PinTask finds its date and time offline. You can correct the task name, date, clock time, advance alert and repeat before saving. Nothing is created automatically.");
        hint.setTextSize(14);
        content.addView(hint);
        EditText text=new EditText(activity);
        text.setContentDescription("Text to extract a task from");
        text.setHint("Friday, 23 October 2026\n03:30 AM\nYour local time in Germany (CEST)");
        text.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        text.setMinLines(4);
        text.setMaxLines(12);
        text.setFilters(new InputFilter[]{new InputFilter.LengthFilter(5000)});
        text.setText(shared==null?"":shared);
        content.addView(text);
        ScrollView scroll=new ScrollView(activity);
        scroll.addView(content);

        AlertDialog dialog=new AlertDialog.Builder(activity)
                .setTitle("Add task from message")
                .setView(scroll)
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Review task",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{
                SpokenTask parsed=MessageTaskParser.parse(text.getText().toString(),ZonedDateTime.now());
                dialog.dismiss();
                VoiceTaskDialog.reviewImported(activity,parsed,saved);
            }catch(IllegalArgumentException|java.time.DateTimeException error){
                text.setError(error.getMessage());
                Toast.makeText(activity,error.getMessage(),Toast.LENGTH_LONG).show();
            }
        }));
        dialog.show();
    }
}

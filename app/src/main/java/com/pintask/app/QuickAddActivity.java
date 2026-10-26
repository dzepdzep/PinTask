package com.pintask.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.InputFilter;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;
import java.util.ArrayList;

/**
 * Lightweight, transparent home-widget entry screen; does not navigate into
 * PinTask. Text is an immediate pinned task; voice supports full spoken
 * schedules, repeats and advance alarms through the existing confirmation UI.
 */
public final class QuickAddActivity extends Activity {
    static final String ACTION_TYPE="com.pintask.app.WIDGET_TYPE_TASK";
    static final String ACTION_VOICE="com.pintask.app.WIDGET_SPEAK_TASK";
    private static final int REQUEST_VOICE=124;
    private boolean awaitingVoice;
    private String voiceLanguage;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        voiceLanguage=state!=null?state.getString("language",VoiceLanguages.language(this)):
                VoiceLanguages.language(this);
        awaitingVoice=state!=null&&state.getBoolean("awaiting_voice",false);
        if(awaitingVoice)return;
        if(ACTION_VOICE.equals(getIntent().getAction()))launchVoice();
        else showTextEntry();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean("awaiting_voice",awaitingVoice);
        out.putString("language",voiceLanguage);
    }

    private void showTextEntry() {
        EditText entry=new EditText(this);
        entry.setHint("What do you need to do?");
        entry.setSingleLine(true);
        entry.setContentDescription("New task title");
        entry.setFilters(new InputFilter[]{new InputFilter.LengthFilter(160)});
        entry.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle("Quick Add · Pinned task")
                .setMessage("Type a task, or use Voice for a spoken time and early warning.")
                .setView(entry)
                .setPositiveButton("Add",null)
                .setNeutralButton("🎤 Voice",(d,w)->launchVoice())
                .setNegativeButton("Cancel",(d,w)->finish())
                .setOnCancelListener(d->finish()).create();
        dialog.setOnShowListener(d->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                String title=entry.getText().toString().trim();
                if(title.isEmpty()){entry.setError("Enter a task title");return;}
                try(TaskStore store=new TaskStore(this)){
                    TaskStore.Task task=TaskSections.addPersonal(store,title);
                    TaskNotifications.show(this,task);
                    TaskWidget.refreshAll(this);
                    QuickCaptureWidget.refreshAll(this);
                    Toast.makeText(this,"Task added",Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    finish();
                }catch(IllegalArgumentException invalid){
                    entry.setError(invalid.getMessage());
                }
            });
            entry.requestFocus();
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        });
        dialog.show();
    }

    private void launchVoice() {
        voiceLanguage=VoiceLanguages.language(this);
        Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,voiceLanguage);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,voiceLanguage);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,VoiceLanguages.prompt(voiceLanguage));
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3);
        try {
            awaitingVoice=true;
            startActivityForResult(intent,REQUEST_VOICE);
        } catch(ActivityNotFoundException|SecurityException unavailable) {
            awaitingVoice=false;
            // The user can still type a natural spoken command and review it.
            Toast.makeText(this,"Speech service unavailable · type the command instead",
                    Toast.LENGTH_LONG).show();
            showTranscript("");
        }
    }

    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=REQUEST_VOICE)return;
        awaitingVoice=false;
        if(result!=RESULT_OK){finish();return;}
        ArrayList<String> results=data==null?null:
                data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        showTranscript(results==null||results.isEmpty()?"":results.get(0));
    }

    private void showTranscript(String speech) {
        VoiceTaskDialog.editCommand(this,speech,voiceLanguage,
                this::finish,this::finish);
    }
}

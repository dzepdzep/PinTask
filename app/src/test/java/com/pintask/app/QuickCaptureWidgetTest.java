package com.pintask.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import java.util.ArrayList;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class QuickCaptureWidgetTest {
    private Context context;
    @Before public void setup(){
        context=RuntimeEnvironment.getApplication();
        context.deleteDatabase("pintask.db");
        context.getSharedPreferences("settings",0).edit().clear()
                .putBoolean("startup_access_reviewed_v31",true)
                .putBoolean("interactive_shift_guide_v29",true).commit();
        context.getSystemService(android.app.NotificationManager.class).cancelAll();
    }

    @Test public void typeWidgetOpensOnlySmallDialogAndSavesPinnedTask(){
        try(var activity=Robolectric.buildActivity(QuickAddActivity.class,
                new Intent(context,QuickAddActivity.class)
                .setAction(QuickAddActivity.ACTION_TYPE)).setup();
            TaskStore store=new TaskStore(context)){
            AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();
            assertNotNull(dialog);
            assertEquals(0,store.activeAll().size());
            EditText entry=(EditText)findDescription(dialog.getWindow().getDecorView(),"New task title");
            assertNotNull(entry);
            entry.setText("Buy batteries");
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertTrue(activity.get().isFinishing());
            assertEquals(1,store.activePersonal().size());
            assertEquals("Buy batteries",store.activePersonal().get(0).title);
            assertEquals(0,store.activePersonal().get(0).dueAt);
            assertNull("Full PinTask activity must stay closed",
                    Shadows.shadowOf(activity.get()).getNextStartedActivity());
        }
    }

    @Test public void blankWidgetEntryNeverCreatesTask(){
        try(var activity=Robolectric.buildActivity(QuickAddActivity.class,
                new Intent(context,QuickAddActivity.class)).setup();
            TaskStore store=new TaskStore(context)){
            AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertEquals(0,store.activeAll().size());
            assertFalse(activity.get().isFinishing());
            assertNotNull(findDescription(dialog.getWindow().getDecorView(),"New task title"));
        }
    }

    @Test public void voiceWidgetUsesCurrentLanguageAndOnlyCreatesAfterReview(){
        VoiceLanguages.select(context,2);
        try(var activity=Robolectric.buildActivity(QuickAddActivity.class,
                new Intent(context,QuickAddActivity.class)
                .setAction(QuickAddActivity.ACTION_VOICE)).setup();
            TaskStore store=new TaskStore(context)){
            Intent recognition=Shadows.shadowOf(activity.get()).getNextStartedActivity();
            assertNotNull(recognition);
            assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH,recognition.getAction());
            assertEquals("hr-HR",recognition.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE));
            assertEquals(0,store.activeAll().size());

            Intent result=new Intent();
            ArrayList<String> candidates=new ArrayList<>();
            candidates.add("podsjeti me za sat vremena da ugasim kompjuter podsjeti me bar pet minuta ranije");
            result.putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS,candidates);
            long start=System.currentTimeMillis();
            activity.get().onActivityResult(124,Activity.RESULT_OK,result);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            AlertDialog transcript=ShadowAlertDialog.getLatestAlertDialog();
            assertNotNull(transcript);
            assertEquals(0,store.activeAll().size());
            transcript.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            AlertDialog review=ShadowAlertDialog.getLatestAlertDialog();
            assertNotSame(transcript,review);
            assertEquals(0,store.activeAll().size());
            review.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertTrue(activity.get().isFinishing());
            assertEquals(1,store.activeAll().size());
            TaskStore.Task saved=store.activePersonal().get(0);
            assertEquals("ugasim kompjuter",saved.title);
            assertEquals(5,saved.notifyBeforeMinutes);
            assertTrue(saved.dueAt>=start+59*60_000L);
            assertTrue(saved.dueAt<=System.currentTimeMillis()+61*60_000L);
            assertEquals(saved.dueAt-5*60_000L,saved.notificationAt());
            assertEquals(1,Shadows.shadowOf(context.getSystemService(
                    android.app.AlarmManager.class)).getScheduledAlarms().size());
        }
    }

    @Test public void cancellingVoiceOrTranscriptClosesOnlyTransientWindow(){
        Intent speech=new Intent(context,QuickAddActivity.class).setAction(QuickAddActivity.ACTION_VOICE);
        try(var activity=Robolectric.buildActivity(QuickAddActivity.class,speech).setup();
            TaskStore store=new TaskStore(context)){
            activity.get().onActivityResult(124,Activity.RESULT_CANCELED,null);
            assertTrue(activity.get().isFinishing());
            assertEquals(0,store.activeAll().size());
        }
        try(var activity=Robolectric.buildActivity(QuickAddActivity.class,speech).setup()){
            Intent result=new Intent();
            result.putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS,
                    new ArrayList<>(java.util.List.of("Remind me to buy milk")));
            activity.get().onActivityResult(124,Activity.RESULT_OK,result);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            ShadowAlertDialog.getLatestAlertDialog()
                    .getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
            assertTrue(activity.get().isFinishing());
        }
    }

    @Test public void voiceLanguageCyclesEnDeHrWithoutLaunchingFullApp(){
        assertEquals(0,VoiceLanguages.selection(context));
        QuickCaptureWidget provider=new QuickCaptureWidget();
        for(int i=1;i<=3;i++){
            provider.onReceive(context,new Intent(context,QuickCaptureWidget.class)
                    .setAction(QuickCaptureWidget.ACTION_CHANGE_LANGUAGE));
            assertEquals(i%3,VoiceLanguages.selection(context));
        }
        PendingIntent voice=QuickCaptureWidget.captureIntent(context,21,true);
        PendingIntent type=QuickCaptureWidget.captureIntent(context,21,false);
        assertNotEquals(voice,type);
        assertEquals(QuickAddActivity.class.getName(),
                Shadows.shadowOf(voice).getSavedIntent().getComponent().getClassName());
        assertEquals(QuickAddActivity.ACTION_VOICE,
                Shadows.shadowOf(voice).getSavedIntent().getAction());
        assertEquals(QuickAddActivity.ACTION_TYPE,
                Shadows.shadowOf(type).getSavedIntent().getAction());
    }

    @Test public void cancellingReviewDoesNotAutoSave(){
        VoiceLanguages.select(context,0);
        try(var activity=Robolectric.buildActivity(QuickAddActivity.class,
                new Intent(context,QuickAddActivity.class)
                .setAction(QuickAddActivity.ACTION_VOICE)).setup();
            TaskStore store=new TaskStore(context)){
            Intent result=new Intent();
            result.putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS,
                    new ArrayList<>(java.util.List.of("Remind me in an hour to turn off the computer. Remind me five minutes earlier.")));
            activity.get().onActivityResult(124,Activity.RESULT_OK,result);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            ShadowAlertDialog.getLatestAlertDialog()
                    .getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            ShadowAlertDialog.getLatestAlertDialog()
                    .getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
            assertEquals(0,store.activeAll().size());
            assertTrue(activity.get().isFinishing());
        }
    }

    private static View findDescription(View root,String desc){
        if(desc.equals(String.valueOf(root.getContentDescription())))return root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){
            View v=findDescription(((ViewGroup)root).getChildAt(i),desc);
            if(v!=null)return v;
        }
        return null;
    }
}

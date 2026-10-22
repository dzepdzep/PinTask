package com.pintask.app;

import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import java.time.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class NaturalEnglishGermanVoiceTest {
    private static final ZoneId ZONE=ZoneId.of("Europe/Berlin");
    private static final ZonedDateTime AT=ZonedDateTime.of(2026,10,23,9,0,0,0,ZONE);
    private Context context;

    @Before public void setup(){
        context=RuntimeEnvironment.getApplication();
        context.deleteDatabase("pintask.db");
        context.getSharedPreferences("settings",0).edit().clear()
                .putBoolean("startup_access_reviewed_v31",true)
                .putBoolean("interactive_shift_guide_v29",true).commit();
    }

    @Test public void exactEnglishExampleCreatesOneTaskWithFiveMinuteEarlyWarning(){
        String utterance="Remind me in an hour to turn off the computer. Remind me five minutes earlier.";
        assertScheduled(utterance,"en-US","turn off the computer",60,5);
    }

    @Test public void exactGermanExampleCreatesOneTaskWithFiveMinuteEarlyWarning(){
        String utterance="Erinnere mich in einer Stunde daran, den Computer auszuschalten. Erinnere mich fünf Minuten vorher.";
        assertScheduled(utterance,"de-DE","den Computer auszuschalten",60,5);
    }

    @Test public void englishVariationsInNaturalOrder(){
        assertScheduled("Please remind me to turn off the PC in one hour, at least 5 minutes early.",
                "en-US","turn off the PC",60,5);
        assertScheduled("Remind me in 2 hours to drink water. Notify me ten minutes beforehand.",
                "en-US","drink water",120,10);
        assertScheduled("Remind me to do laundry in half an hour five minutes before",
                "en-US","do laundry",30,5);
        assertScheduled("Remind me to buy groceries in thirty minutes 5 minutes early",
                "en-US","buy groceries",30,5);
        assertScheduled("Remind me in 90 minutes to stretch 15 minutes earlier.",
                "en-US","stretch",90,15);
        assertScheduled("Remind me to water the plants in five minutes one minute before",
                "en-US","water the plants",5,1);
    }

    @Test public void germanVariationsInNaturalOrder(){
        assertScheduled("Erinnere mich in zwei Stunden daran, die Pflanzen zu gießen. Erinnere mich mindestens zehn Minuten vorher.",
                "de-DE","die Pflanzen zu gießen",120,10);
        assertScheduled("Erinnere mich Müll rausbringen in dreißig Minuten fünf Minuten vorher",
                "de-DE","Müll rausbringen",30,5);
        assertScheduled("Erinnere mich in einer halben Stunde daran, den Ofen auszuschalten. Erinnere mich fünf Minuten früher.",
                "de-DE","den Ofen auszuschalten",30,5);
        assertScheduled("Bitte erinnere mich in 90 Minuten daran, die Wäsche aufzuhängen. Erinnere mich 15 Minuten vorher.",
                "de-DE","die Wäsche aufzuhängen",90,15);
        assertScheduled("Erinnere mich in fünf Minuten daran, den Computer herunterzufahren. Eine Minute vorher.",
                "de-DE","den Computer herunterzufahren",5,1);
    }

    @Test public void englishGermanPreserveOlderFormsWithoutEarlyWarning(){
        assertScheduled("Remind me to buy milk in 30 minutes","en-US","buy milk",30,0);
        assertScheduled("Erinnere mich Müll rausbringen in 30 Minuten","de-DE","Müll rausbringen",30,0);
        assertScheduled("Wash dishes in two hours","en-US","Wash dishes",120,0);
        assertScheduled("Erinnere mich den Müll rausbringen in zwei Stunden",
                "de-DE","den Müll rausbringen",120,0);
    }

    @Test public void conflictsOrAlreadyPassedEarlyWarningsAreRejected(){
        for(String[] bad:new String[][]{
                {"en-US","Remind me to go out in 5 minutes five minutes earlier"},
                {"en-US","Remind me to go out five minutes early"},
                {"en-US","Remind me in an hour to go out five minutes before ten minutes earlier"},
                {"de-DE","Erinnere mich in fünf Minuten daran, den Computer auszuschalten. Fünf Minuten vorher."},
                {"de-DE","Erinnere mich in einer Stunde daran, den Computer auszuschalten. Fünf Minuten vorher. Zehn Minuten vorher."},
                {"de-DE","Erinnere mich fünf Minuten vorher daran, die Küche zu putzen."}
        }){
            assertThrows(bad[1],IllegalArgumentException.class,
                    ()->VoiceLanguages.parse(bad[1],AT,bad[0]));
        }
    }

    @Test public void selectingLanguageChangesParsingButDoesNotChangeTaskStorage(){
        String[] cases={
                "Remind me in an hour to turn off the computer. Remind me five minutes earlier.",
                "Erinnere mich in einer Stunde daran, den Computer auszuschalten. Erinnere mich fünf Minuten vorher."
        };
        String[] tags={"en-US","de-DE"};
        String[] titles={"turn off the computer","den Computer auszuschalten"};
        for(int i=0;i<2;i++){
            final int index=i;
            try(var activity=Robolectric.buildActivity(ShiftActivity.class).setup();
                TaskStore store=new TaskStore(context)){
                VoiceTaskDialog.editCommand(activity.get(),cases[index],tags[index],()->{});
                Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
                AlertDialog transcript=ShadowAlertDialog.getLatestAlertDialog();
                assertEquals(0,store.activeAll().size());
                transcript.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
                AlertDialog review=ShadowAlertDialog.getLatestAlertDialog();
                assertNotSame(transcript,review);
                assertEquals(0,store.activeAll().size());
                EditText title=(EditText)findDescription(review.getWindow().getDecorView(),"Recognized task name");
                assertNotNull(title);
                assertEquals(titles[index],title.getText().toString());
                Button earlier=(Button)findDescription(review.getWindow().getDecorView(),"Change advance reminder");
                assertNotNull(earlier);
                assertTrue(earlier.getText().toString().contains("5 minutes before"));
                review.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                TaskStore.Task saved=store.activeAll().get(0);
                assertEquals(titles[index],saved.title);
                assertEquals(5,saved.notifyBeforeMinutes);
                assertEquals(5*60_000L,saved.dueAt-saved.notificationAt());
            }
            context.deleteDatabase("pintask.db");
        }
    }

    private static void assertScheduled(String utterance,String language,String title,
                                         int delayMinutes,int earlyMinutes){
        SpokenTask parsed=VoiceLanguages.parse(utterance,AT,language);
        assertEquals(utterance,title,parsed.title);
        assertEquals(utterance,AT.plusMinutes(delayMinutes).toInstant().toEpochMilli(),parsed.dueAt);
        assertEquals(utterance,earlyMinutes,parsed.remindBeforeMinutes);
        assertEquals(utterance,0,parsed.repeatCode);
        assertEquals(utterance,AT.plusMinutes(delayMinutes-earlyMinutes).toInstant().toEpochMilli(),
                parsed.dueAt-parsed.remindBeforeMinutes*60_000L);
        assertEquals(utterance,parsed.original);
    }

    private static View findDescription(View root,String description){
        if(description.equals(String.valueOf(root.getContentDescription())))return root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){
            View found=findDescription(((ViewGroup)root).getChildAt(i),description);
            if(found!=null)return found;
        }
        return null;
    }
}

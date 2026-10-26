package com.pintask.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

/**
 * Standalone small home-screen capture widget. Android RemoteViews do not
 * support EditText or direct microphone capture: both shortcuts launch only
 * a transparent, transient quick-capture window, never the full main screen.
 */
public final class QuickCaptureWidget extends AppWidgetProvider {
    static final String ACTION_CHANGE_LANGUAGE="com.pintask.app.widget.CHANGE_VOICE_LANGUAGE";

    @Override public void onReceive(Context context,Intent intent) {
        if(intent!=null && ACTION_CHANGE_LANGUAGE.equals(intent.getAction())){
            VoiceLanguages.select(context,(VoiceLanguages.selection(context)+1)%VoiceLanguages.TAGS.length);
            refreshAll(context);
            TaskWidget.refreshAll(context);
            return;
        }
        super.onReceive(context,intent);
    }

    @Override public void onUpdate(Context context,AppWidgetManager manager,int[] ids) {
        update(context,manager,ids);
    }

    @Override public void onAppWidgetOptionsChanged(Context context,
            AppWidgetManager manager,int id,android.os.Bundle options) {
        update(context,manager,new int[]{id});
    }

    static void refreshAll(Context context) {
        AppWidgetManager manager=AppWidgetManager.getInstance(context);
        int[] ids=manager.getAppWidgetIds(new ComponentName(context,QuickCaptureWidget.class));
        if(ids.length>0)update(context,manager,ids);
    }

    private static void update(Context context,AppWidgetManager manager,int[] ids) {
        for(int id:ids) {
            RemoteViews view=new RemoteViews(context.getPackageName(),R.layout.quick_capture_widget);
            view.setTextViewText(R.id.capture_language,VoiceLanguages.SHORT[VoiceLanguages.selection(context)]);
            view.setOnClickPendingIntent(R.id.capture_type, captureIntent(context,id,false));
            view.setOnClickPendingIntent(R.id.capture_voice, captureIntent(context,id,true));
            Intent change=new Intent(context,QuickCaptureWidget.class)
                    .setAction(ACTION_CHANGE_LANGUAGE)
                    .setData(Uri.parse("pintask://widget/voice-language/"+id));
            PendingIntent cycle=PendingIntent.getBroadcast(context,41000+id,change,
                    PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            view.setOnClickPendingIntent(R.id.capture_language,cycle);
            manager.updateAppWidget(id,view);
        }
    }

    static PendingIntent captureIntent(Context context,int widgetId,boolean voice) {
        Intent intent=new Intent(context,QuickAddActivity.class)
                .setAction(voice?QuickAddActivity.ACTION_VOICE:QuickAddActivity.ACTION_TYPE)
                .setData(Uri.parse("pintask://widget/capture/"+widgetId+(voice?"/voice":"/type")))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(context,(voice?51000:61000)+widgetId,intent,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
}

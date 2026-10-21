package com.pintask.app;

import android.app.*;
import android.content.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Explicit opt-in alarm sound, independently of user-muted notification channels.
 * Starts ONLY for a newly claimed, on-time scheduled reminder.
 * A 30-second bounded foreground service keeps audio alive when the screen locks.
 */
public final class AlarmSoundService extends Service {
    private static final String PREF = "direct_alarm_sound_optin_v311";
    private static final String CHANNEL = "pintask_alarm_playback_v311";
    private static final String RING = "com.pintask.app.PLAY_ALARM_TONE";
    private static final String STOP = "com.pintask.app.STOP_ALARM_TONE";
    private static final String ID = "alarm_task_id";
    private static final int NOTIFICATION_ID = 1999999997;
    private static final long MAX_RING_MS = 30_000L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Ringtone ringtone;
    private final Runnable timeout = this::stopRinging;
    private long activeTask;

    static boolean enabled(Context context) {
        return context.getSharedPreferences("settings",Context.MODE_PRIVATE)
                .getBoolean(PREF,false);
    }
    static void setEnabled(Context context,boolean enabled) {
        context.getSharedPreferences("settings",Context.MODE_PRIVATE)
                .edit().putBoolean(PREF,enabled).apply();
        if(!enabled)stop(context);
    }
    static boolean start(Context context,long taskId) {
        if(!enabled(context) || !TaskNotifications.canNotify(context))return false;
        try {
            Intent i=new Intent(context,AlarmSoundService.class).setAction(RING).putExtra(ID,taskId);
            context.startForegroundService(i);
            ReminderAudit.note(context,"ringtone_service_requested",taskId,0);
            return true;
        } catch(RuntimeException denied) {
            ReminderAudit.note(context,"ringtone_service_blocked",taskId,0);
            android.util.Log.w("PinTask","Android prevented background alarm playback",denied);
            return false;
        }
    }
    static void stop(Context context) {
        try {
            context.startService(new Intent(context,AlarmSoundService.class).setAction(STOP));
        } catch(RuntimeException ignored) {
            // Android may refuse background service starts; existing ring stops at 30s.
        }
    }
    private void channel() {
        NotificationManager nm=getSystemService(NotificationManager.class);
        if(nm==null)return;
        NotificationChannel c=new NotificationChannel(CHANNEL,"Active PinTask ringtone",
                NotificationManager.IMPORTANCE_LOW);
        c.setDescription("Temporary notification while your explicitly enabled alarm ringtone plays");
        c.enableVibration(false);
        c.setSound(null,null);
        nm.createNotificationChannel(c);
    }
    private Notification notification() {
        Intent stop=new Intent(this,AlarmSoundService.class).setAction(STOP);
        PendingIntent action=PendingIntent.getService(this,NOTIFICATION_ID,stop,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent open=PendingIntent.getActivity(this,NOTIFICATION_ID,
                new Intent(this,ShiftActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL)
                .setSmallIcon(R.drawable.ic_pin)
                .setContentTitle("PinTask alarm ringing")
                .setContentText("Stops automatically after 30 seconds")
                .setCategory(Notification.CATEGORY_ALARM)
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(new Notification.Action.Builder(
                        android.graphics.drawable.Icon.createWithResource(this,R.drawable.ic_check),
                        "Stop ringing",action).build())
                .build();
    }
    @Override public void onCreate() {
        super.onCreate();
        channel();
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent==null || STOP.equals(intent.getAction()) || !enabled(this)) {
            stopRinging();
            return START_NOT_STICKY;
        }
        if(!RING.equals(intent.getAction()) || !TaskNotifications.canNotify(this)) {
            stopRinging();
            return START_NOT_STICKY;
        }
        long taskId=intent.getLongExtra(ID,0);
        if(taskId<=0){stopRinging();return START_NOT_STICKY;}
        try {
            // Must enter foreground promptly before playback on Android 8+.
            startForeground(NOTIFICATION_ID,notification());
            if(ringtone!=null) {
                // Already playing for a simultaneously due task; never layer two tones.
                handler.removeCallbacks(timeout);
                activeTask=taskId;
                handler.postDelayed(timeout,MAX_RING_MS);
                return START_NOT_STICKY;
            }
            Uri uri=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if(uri==null)throw new IllegalStateException("No alarm sound configured");
            Ringtone candidate=RingtoneManager.getRingtone(this,uri);
            if(candidate==null)throw new IllegalStateException("Default alarm sound could not be loaded");
            candidate.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            AudioManager audio=getSystemService(AudioManager.class);
            if(audio!=null && audio.getStreamVolume(AudioManager.STREAM_ALARM)==0)
                throw new IllegalStateException("Alarm sound volume is zero");
            ringtone=candidate;
            activeTask=taskId;
            ringtone.play();
            if(!ringtone.isPlaying())
                throw new IllegalStateException("Alarm ringtone did not start");
            ReminderAudit.note(this,"ringtone_playing",taskId,0);
            handler.postDelayed(timeout,MAX_RING_MS);
        } catch(Exception failure) {
            ReminderAudit.note(this,"ringtone_playback_failed",taskId,0);
            android.util.Log.w("PinTask","Alarm ringtone could not play; check DND and alarm audio",failure);
            stopRinging();
        }
        return START_NOT_STICKY;
    }
    private void stopRinging() {
        handler.removeCallbacks(timeout);
        if(ringtone!=null) {
            try {ringtone.stop();}catch(RuntimeException ignored) {}
            ringtone=null;
            ReminderAudit.note(this,"ringtone_stopped",activeTask,0);
        }
        activeTask=0;
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }
    @Override public void onDestroy() {
        handler.removeCallbacks(timeout);
        if(ringtone!=null) {
            try {ringtone.stop();} catch(RuntimeException ignored) {}
            ringtone=null;
        }
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}

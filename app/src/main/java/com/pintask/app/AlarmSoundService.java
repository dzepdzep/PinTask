package com.pintask.app;

import android.app.*;
import android.content.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import java.util.HashSet;
import java.util.Set;

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
    // The receiver and this service run in the same app process. Keep one ringtone
    // for simultaneous alarms, but don't silence a different task when Done is
    // pressed on an unrelated reminder.
    private static final Set<Long> activeRequests = new HashSet<>();
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
        synchronized(activeRequests){activeRequests.add(taskId);}
        try {
            Intent i=new Intent(context,AlarmSoundService.class).setAction(RING).putExtra(ID,taskId);
            context.startForegroundService(i);
            ReminderAudit.note(context,"ringtone_service_requested",taskId,0);
            return true;
        } catch(RuntimeException denied) {
            synchronized(activeRequests){activeRequests.remove(taskId);}
            ReminderAudit.note(context,"ringtone_service_blocked",taskId,0);
            android.util.Log.w("PinTask","Android prevented background alarm playback",denied);
            return false;
        }
    }
    /** Done or Snooze stops ringing if this task was playing. When multiple
     * reminders ring together, the others keep ringing until addressed. */
    static void stopForTask(Context context,long taskId) {
        boolean last;
        synchronized(activeRequests) {
            if(!activeRequests.remove(taskId))return;
            last=activeRequests.isEmpty();
        }
        if(last)stop(context);
    }
    static void stop(Context context) {
        synchronized(activeRequests){activeRequests.clear();}
        try {
            // Do not start a NEW background service just to stop audio:
            // Android 8+ may reject startService() from notification actions.
            // stopService() destroys an existing service and releases the ringtone
            // in onDestroy(), even if the screen is locked.
            context.stopService(new Intent(context,AlarmSoundService.class));
        } catch(RuntimeException error) {
            ReminderAudit.note(context,"ringtone_stop_failed",0,0);
            android.util.Log.w("PinTask","Android could not stop the active ringtone service",error);
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
        synchronized(activeRequests) {
            // A user could press Done before Android finishes launching the
            // foreground service. A cancelled task must never start ringing.
            if(!activeRequests.contains(taskId)) {
                // Other concurrently due tasks may still have pending start
                // commands. Ignore only the already-dismissed alarm.
                if(activeRequests.isEmpty())stopRinging();
                return START_NOT_STICKY;
            }
        }
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
        synchronized(activeRequests){activeRequests.clear();}
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }
    @Override public void onDestroy() {
        handler.removeCallbacks(timeout);
        synchronized(activeRequests){activeRequests.clear();}
        if(ringtone!=null) {
            try {ringtone.stop();} catch(RuntimeException ignored) {}
            ringtone=null;
        }
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}

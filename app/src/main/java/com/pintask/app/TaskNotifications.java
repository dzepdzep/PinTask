package com.pintask.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.provider.Settings;
import android.os.Build;
import android.widget.RemoteViews;

import java.text.DateFormat;
import java.util.Date;

/** Watch-compatible task notifications, optional phone-only pinning, and inline Quick Add. */
public final class TaskNotifications {
    public static final String CHANNEL = "unfinished_tasks";
    /** Separate channel for the user-toggleable persistent status-bar shortcut. */
    static final String STATUS_CHANNEL = "pintask_status_icon_v39";
    private static final String STATUS_PREF = "show_status_icon_v39";
    private static final int STATUS_ID = 1999999998;
    // New ID: Android does not allow apps to change the sound of an existing channel.
    public static final String REMINDER_CHANNEL = "due_reminders_alarm_v3";
    // Android channels have immutable importance. Separate default-importance
    // channel enables disabling heads-up while keeping alarm sound and vibration.
    static final String NO_POPUP_CHANNEL="due_reminders_no_popup_v36";
    private static final String POPUP_PREF="reminder_popup_banners_v36";
    public static final String ACTION_DONE = "com.pintask.app.ACTION_DONE";
    public static final String ACTION_UNDO = "com.pintask.app.ACTION_UNDO";
    public static final String ACTION_DISMISSED = "com.pintask.app.ACTION_DISMISSED";
    public static final String ACTION_REMINDER = "com.pintask.app.ACTION_REMINDER";
    public static final String ACTION_QUICK_ADD = "com.pintask.app.ACTION_QUICK_ADD";
    public static final String EXTRA_ID = "task_id";
    static final String EXTRA_DISMISSED_DUE = "dismissed_due";
    public static final String REMOTE_TEXT = "quick_task_text";
    private static final int QUICK_ID = 1999999999;
    private static final int REMINDER_OFFSET = 1000000000;

    private TaskNotifications() {}

    public static void ensureChannel(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        NotificationChannel persistent = new NotificationChannel(
                CHANNEL, "Unfinished tasks", NotificationManager.IMPORTANCE_LOW);
        persistent.setDescription("Unfinished tasks and Quick Add shortcut");
        persistent.enableVibration(false);
        persistent.setSound(null, null);
        manager.createNotificationChannel(persistent);
        NotificationChannel status = new NotificationChannel(
                STATUS_CHANNEL,"PinTask status-bar shortcut",NotificationManager.IMPORTANCE_DEFAULT);
        status.setDescription("Optional PinTask shortcut icon in the status bar; silent and separate from reminder alarms");
        status.enableVibration(false);
        status.setSound(null,null);
        manager.createNotificationChannel(status);
        NotificationChannel due = new NotificationChannel(
                REMINDER_CHANNEL, "Timed task alarms", NotificationManager.IMPORTANCE_HIGH);
        due.setDescription("Audible alarm ringtone and vibration for scheduled tasks and snoozed reminders");
        due.enableVibration(true);
        due.setVibrationPattern(new long[]{0, 300, 180, 300, 180, 500});
        AudioAttributes audio=new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM).build();
        due.setSound(Settings.System.DEFAULT_ALARM_ALERT_URI,audio);
        manager.createNotificationChannel(due);
        NotificationChannel calm=new NotificationChannel(
                NO_POPUP_CHANNEL,"Timed alarms without pop-up",NotificationManager.IMPORTANCE_DEFAULT);
        calm.setDescription("Audible timed reminders that stay in the shade without heads-up banners");
        calm.enableVibration(true);
        calm.setVibrationPattern(new long[]{0,300,180,300,180,500});
        calm.setSound(Settings.System.DEFAULT_ALARM_ALERT_URI,audio);
        manager.createNotificationChannel(calm);
    }

    static boolean popupEnabled(Context context) {
        return context.getSharedPreferences("settings",Context.MODE_PRIVATE)
                .getBoolean(POPUP_PREF,true);
    }
    static void setPopupEnabled(Context context,boolean enabled){
        context.getSharedPreferences("settings",Context.MODE_PRIVATE)
                .edit().putBoolean(POPUP_PREF,enabled).apply();
        ensureChannel(context);
    }
    static String activeReminderChannel(Context context){
        return popupEnabled(context)?REMINDER_CHANNEL:NO_POPUP_CHANNEL;
    }

    public static boolean canNotify(Context context) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        return manager != null && manager.areNotificationsEnabled();
    }

    static boolean canNotifyChannel(Context context, String channelId) {
        if (!canNotify(context)) return false;
        ensureChannel(context);
        NotificationChannel channel = context.getSystemService(NotificationManager.class).getNotificationChannel(channelId);
        return channel != null && channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }

    /**
     * Posting is not the same as alerting. IMPORTANCE_LOW, a null sound or a
     * user-silenced channel can produce a perfectly valid but silent notification.
     * Do not report those channels as ready for audible timed reminders.
     */
    static boolean canAlertAudibly(Context context, String channelId) {
        if (!canNotifyChannel(context, channelId)) return false;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel channel = manager == null ? null : manager.getNotificationChannel(channelId);
        return channel != null &&
                channel.getImportance() >= NotificationManager.IMPORTANCE_DEFAULT &&
                channel.getSound() != null;
    }

    private static PendingIntent open(Context context) {return open(context,0);}
    private static PendingIntent open(Context context,long id) {
        return PendingIntent.getActivity(context,2,TaskSections.openIntent(context,id),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent doneIntent(Context context, long id) {
        Intent intent = new Intent(context, TaskActionReceiver.class);
        intent.setAction(ACTION_DONE);
        intent.putExtra(EXTRA_ID, id);
        return PendingIntent.getBroadcast(context, (int) id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static Notification.Action doneAction(Context context, long id) {
        return new Notification.Action.Builder(
                Icon.createWithResource(context, R.drawable.ic_check), "Done", doneIntent(context, id))
                .extend(new Notification.Action.WearableExtender().setHintLaunchesActivity(false).setAvailableOffline(false)).build();
    }

    /** A tappable Done button in the collapsed notification, not just its expanded actions.
     * DecoratedCustomViewStyle keeps Android's normal notification header and Wear OS metadata. */
    private static RemoteViews compactTask(Context context, TaskStore.Task task, boolean dueNow) {
        RemoteViews compact = new RemoteViews(context.getPackageName(), R.layout.notification_task_collapsed);
        compact.setTextViewText(R.id.notification_task_title,
                dueNow ? "Due now: " + task.title : task.title);
        compact.setOnClickPendingIntent(R.id.notification_task_title, open(context, task.id));
        compact.setOnClickPendingIntent(R.id.notification_snooze, snoozeIntent(context, task.id));
        compact.setOnClickPendingIntent(R.id.notification_done, doneIntent(context, task.id));
        return compact;
    }

    public static boolean watchEnabled(Context context) {
        return context.getSharedPreferences("settings",Context.MODE_PRIVATE).getBoolean("watch_notifications",true);
    }
    public static void setWatchEnabled(Context context,boolean enabled) {
        context.getSharedPreferences("settings",Context.MODE_PRIVATE).edit().putBoolean("watch_notifications",enabled).apply();
        // Update existing cards in place, including already-delivered legacy due alerts.
        NotificationManager manager=context.getSystemService(NotificationManager.class);
        java.util.Set<Integer> dueIds=new java.util.HashSet<>();
        if(manager!=null)for(android.service.notification.StatusBarNotification active:manager.getActiveNotifications())
            if(active.getTag()==null && active.getId()>=REMINDER_OFFSET && active.getId()<QUICK_ID)dueIds.add(active.getId());
        syncAll(context);
        try(TaskStore store=new TaskStore(context)) {
            for(TaskStore.Task task:store.activeAll())if(dueIds.contains(REMINDER_OFFSET+(int)task.id) && task.reminderEnabled())showDueInternal(context,task,true);
        }
    }

    /** Distinct delete callbacks for the main card and the legacy due card. */
    static PendingIntent dismissIntent(Context context, long id, boolean dueCard) {
        Intent intent=new Intent(context,TaskActionReceiver.class)
                .setAction(ACTION_DISMISSED)
                .putExtra(EXTRA_ID,id)
                .putExtra(EXTRA_DISMISSED_DUE,dueCard);
        return PendingIntent.getBroadcast(context,
                dueCard?REMINDER_OFFSET+(int)id:(int)id,intent,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }

    private static Notification.WearableExtender watchActions(Notification.Action action) {
        // Replace phone actions on the watch: Snooze opens a phone activity, Done does not.
        return new Notification.WearableExtender().addAction(action);
    }

    private static String priorityText(int priority) {
        return priority == TaskStore.PRIORITY_HIGH ? "High priority" :
                priority == TaskStore.PRIORITY_LOW ? "Low priority" : "Normal priority";
    }

    private static PendingIntent snoozeIntent(Context context,long id) {
        Intent intent=new Intent(context,SnoozeActivity.class).putExtra(EXTRA_ID,id);
        return PendingIntent.getActivity(context,(int)id,intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    private static Notification.Action snoozeAction(Context context, long id) {
        return new Notification.Action.Builder(Icon.createWithResource(context,R.drawable.ic_pin),
                "Snooze",snoozeIntent(context,id)).build();
    }

    public static void show(Context context, TaskStore.Task task) {
        showInternal(context,task,false);
    }

    /** Reposting a swiped card must never replay the alarm ringtone. */
    private static void showInternal(Context context,TaskStore.Task task,boolean silent) {
        TaskWidget.refreshAll(context);
        if (task == null || task.done || !task.reminderEnabled() || !canNotify(context)) return;
        if (task.snoozedUntil > System.currentTimeMillis()) return;
        if (task.dueAt == 0 && task.shiftRunId > 0 && System.currentTimeMillis() < task.notificationAt()) return;
        // Scheduled tasks appear only when their selected advance interval begins.
        if (task.dueAt > 0 && task.notifyBeforeMinutes >= 0 &&
                System.currentTimeMillis() < task.notificationAt()) return;
        ensureChannel(context);
        String detail = (task.shiftRunId > 0 ? "Shift · " + task.shiftLabel + "  ·  " : "") + priorityText(task.priority);
        if (task.dueAt > 0) detail += "  ·  Due " + DateFormat.getDateTimeInstance(
                DateFormat.SHORT, DateFormat.SHORT).format(new Date(task.dueAt));
        // Use the reminder channel for scheduled arrivals, so the first notification alerts.
        String channel = silent ? CHANNEL :
                (task.snoozedUntil > 0 || (task.notifyBeforeMinutes >= 0 && task.dueAt > 0)
                ? activeReminderChannel(context) : CHANNEL);
        boolean watch=watchEnabled(context);
        Notification.Action done=doneAction(context,task.id);
        Notification notification = new Notification.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_pin)
                .setContentTitle(task.title)
                .setContentText(detail)
                .setCustomContentView(compactTask(context, task, false))
                .setStyle(new Notification.DecoratedCustomViewStyle())
                .setCategory(channel.equals(CHANNEL) ? Notification.CATEGORY_REMINDER : Notification.CATEGORY_ALARM)
                .setContentIntent(open(context,task.id))
                .setDeleteIntent(dismissIntent(context,task.id,false))
                .setAutoCancel(false)
                .setOngoing(true)
                .setLocalOnly(!watch)
                .setOnlyAlertOnce(true)
                .setShowWhen(true)
                .setWhen(task.createdAt)
                .extend(watchActions(done)).build();
        // Android 14+ permits individual dismissal despite these flags.
        notification.flags |= Notification.FLAG_NO_CLEAR;
        context.getSystemService(NotificationManager.class).notify((int) task.id, notification);
    }

    public static void showDue(Context context, TaskStore.Task task) {
        showDueInternal(context,task,false);
    }

    private static void showDueInternal(Context context,TaskStore.Task task,boolean silent) {
        if(task==null||task.done||!task.reminderEnabled()||!canNotify(context))return;
        if(task.snoozedUntil>System.currentTimeMillis())return;
        ensureChannel(context);
        Notification.Action done=doneAction(context,task.id);
        Notification notification = new Notification.Builder(context, silent?CHANNEL:activeReminderChannel(context))
                .setSmallIcon(R.drawable.ic_pin)
                .setContentTitle("Due now: " + task.title)
                .setContentText("Your scheduled PinTask reminder")
                .setCustomContentView(compactTask(context, task, true))
                .setStyle(new Notification.DecoratedCustomViewStyle())
                .setOnlyAlertOnce(true)
                .setContentIntent(open(context,task.id))
                .setDeleteIntent(dismissIntent(context,task.id,true))
                .setCategory(Notification.CATEGORY_ALARM)
                .setAutoCancel(false)
                .setOngoing(true)
                .setLocalOnly(!watchEnabled(context))
                .extend(watchActions(done)).build();
        notification.flags |= Notification.FLAG_NO_CLEAR;
        context.getSystemService(NotificationManager.class)
                .notify(REMINDER_OFFSET + (int) task.id, notification);
    }

    /**
     * Best effort recovery when Android 14+ or the phone manufacturer allows swiping
     * an ongoing card away. Never revive completed, paused, silent or snoozed tasks.
     * No new reminder is claimed; an already-delivered alarm stays delivered.
     */
    static void restoreDismissed(Context context,long id,boolean dueCard) {
        if(!canNotify(context))return;
        try(TaskStore store=new TaskStore(context)) {
            TaskStore.Task task=store.find(id);
            if(task==null||task.done||!task.reminderEnabled()||
                    task.snoozedUntil>System.currentTimeMillis())return;
            if(task.dueAt>0 && task.notifyBeforeMinutes>=0 &&
                    task.notificationAt()>System.currentTimeMillis())return;
            if(dueCard)showDueInternal(context,task,true);
            else showInternal(context,task,true);
        }
    }

    public static void showUndo(Context context,long id) {
        if(!canNotify(context)) return;
        ensureChannel(context);
        Intent intent=new Intent(context,TaskActionReceiver.class).setAction(ACTION_UNDO).putExtra(EXTRA_ID,id);
        PendingIntent undo=PendingIntent.getBroadcast(context,(int)id,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Action undoAction=new Notification.Action.Builder(Icon.createWithResource(context,R.drawable.ic_pin),"Undo",undo)
                .extend(new Notification.Action.WearableExtender().setHintLaunchesActivity(false).setAvailableOffline(false)).build();
        Notification notification=new Notification.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_check)
                .setContentTitle("Task completed").setContentText("Undo is available for 10 seconds")
                .setTimeoutAfter(10_000).setContentIntent(open(context,id))
                .setLocalOnly(!watchEnabled(context)).addAction(undoAction).extend(watchActions(undoAction)).build();
        context.getSystemService(NotificationManager.class).notify("undo",(int)id,notification);
    }

    /** User-toggleable notification shortcut; Android ultimately controls the top bar. */
    static boolean statusIconEnabled(Context context){
        return context.getSharedPreferences("settings",Context.MODE_PRIVATE)
                .getBoolean(STATUS_PREF,false);
    }
    static void setStatusIconEnabled(Context context,boolean enabled){
        context.getSharedPreferences("settings",Context.MODE_PRIVATE)
                .edit().putBoolean(STATUS_PREF,enabled).apply();
        showStatusIcon(context);
    }
    static void showStatusIcon(Context context){
        NotificationManager manager=context.getSystemService(NotificationManager.class);
        if(manager==null)return;
        if(!statusIconEnabled(context) || !canNotify(context)){
            manager.cancel(STATUS_ID);return;
        }
        ensureChannel(context);
        NotificationChannel channel=manager.getNotificationChannel(STATUS_CHANNEL);
        if(channel==null||channel.getImportance()==NotificationManager.IMPORTANCE_NONE){
            manager.cancel(STATUS_ID);return;
        }
        Notification notification=new Notification.Builder(context,STATUS_CHANNEL)
                .setSmallIcon(R.drawable.ic_pin)
                .setContentTitle("PinTask")
                .setContentText("Tap to open your tasks")
                .setContentIntent(open(context))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_STATUS)
                .build();
        notification.flags|=Notification.FLAG_NO_CLEAR;
        manager.notify(STATUS_ID,notification);
    }

    /** Whether Android accepted our status-bar shortcut notification. Visibility
     * in the clock area is still controlled by OxygenOS system status-bar settings. */
    static String statusIconState(Context context) {
        if(!statusIconEnabled(context))return "Off · enable the switch below";
        if(!canNotify(context))return "Blocked · enable PinTask app notifications";
        if(!canNotifyChannel(context,STATUS_CHANNEL))
            return "Blocked · enable PinTask status-bar shortcut notification category";
        NotificationManager manager=context.getSystemService(NotificationManager.class);
        if(manager==null)return "Android notification manager not available";
        for(android.service.notification.StatusBarNotification notification:manager.getActiveNotifications()) {
            if(notification.getId()==STATUS_ID && notification.getTag()==null)
                return "Posted · if no icon appears, check OxygenOS notification icons";
        }
        return "Not posted · use Refresh icon, then check notification settings";
    }

    /** v2 deliberately requires fresh opt-in after older always-on builds. */
    public static boolean quickAddEnabled(Context context) {
        return context.getSharedPreferences("settings",Context.MODE_PRIVATE).getBoolean("quick_add_opt_in_v2",false);
    }
    public static void setQuickAddEnabled(Context context,boolean enabled) {
        clearQuickAdd(context);
        context.getSharedPreferences("settings",Context.MODE_PRIVATE).edit().remove("quick_add").putBoolean("quick_add_opt_in_v2",enabled).apply();
        showQuickAdd(context);
    }
    private static void clearQuickAdd(Context context) {
        NotificationManager manager=context.getSystemService(NotificationManager.class);if(manager==null)return;
        manager.cancel(QUICK_ID);
        // Older builds may have used a tag or a different ID. Only remove the shortcut.
        for(android.service.notification.StatusBarNotification active:manager.getActiveNotifications()) {
            boolean shortcut=active.getId()==QUICK_ID;
            Notification notification=active.getNotification();
            if(notification.actions!=null)for(Notification.Action action:notification.actions) {
                RemoteInput[] inputs=action.getRemoteInputs();if(inputs!=null)for(RemoteInput input:inputs)if(REMOTE_TEXT.equals(input.getResultKey()))shortcut=true;
            }
            if(shortcut)manager.cancel(active.getTag(),active.getId());
        }
        Intent intent=new Intent(context,TaskActionReceiver.class).setAction(ACTION_QUICK_ADD);
        PendingIntent old=PendingIntent.getBroadcast(context,QUICK_ID,intent,PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_MUTABLE);
        if(old!=null)old.cancel();
    }

    public static void showQuickAdd(Context context) {
        if(!quickAddEnabled(context)) { clearQuickAdd(context);return; }
        if (!canNotify(context)) return;
        ensureChannel(context);
        Intent intent = new Intent(context, TaskActionReceiver.class);
        intent.setAction(ACTION_QUICK_ADD);
        // RemoteInput requires a mutable PendingIntent on Android 12+.
        PendingIntent submit = PendingIntent.getBroadcast(context, QUICK_ID, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        RemoteInput text = new RemoteInput.Builder(REMOTE_TEXT)
                .setLabel("New task name")
                .build();
        Notification.Action add = new Notification.Action.Builder(
                Icon.createWithResource(context, R.drawable.ic_check), "Add task", submit)
                .addRemoteInput(text)
                .setAllowGeneratedReplies(true)
                .build();
        Notification notification = new Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_pin)
                .setContentTitle("PinTask · Quick Add")
                .setContentText("Add tasks without opening the app")
                .setContentIntent(open(context))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .addAction(add).build();
        notification.flags |= Notification.FLAG_NO_CLEAR;
        context.getSystemService(NotificationManager.class).notify(QUICK_ID, notification);
    }

    public static void syncAll(Context context) {
        showQuickAdd(context);
        showStatusIcon(context);
        if (!canNotify(context)) return;
        try (TaskStore store = new TaskStore(context)) {
            for (TaskStore.Task task : store.activeAll()) {
                // Do not pre-post an unclaimed due alarm. Its first notification
                // must come from ReminderScheduler.deliver(), which records the
                // delivery and starts the sound. Pre-posting here together with
                // setOnlyAlertOnce(true) can make the actual alarm update silent.
                if (task.remindedAt == 0 && task.dueAt > 0 &&
                        task.notifyBeforeMinutes >= 0 &&
                        task.notificationAt() <= System.currentTimeMillis()) continue;
                show(context, task);
            }
        }
    }

    public static void cancel(Context context, long id) {
        TaskWidget.refreshAll(context);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        manager.cancel((int) id);
        manager.cancel("undo",(int)id);
        manager.cancel(REMINDER_OFFSET + (int) id);
    }
}

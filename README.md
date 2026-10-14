## v1.5 changes

- Separate To do and Done sections, with completion dates and three-dot task menus. Load older completed tasks with Show more.
- Repeat a completed task immediately or at a chosen time, preserving the Done record. Make a new daily, weekly or monthly repeating task; the next occurrence is created on completion.
- Rename tasks, change active priorities, set a 15-minute reminder, stop repeating, or delete a single task with confirmation.
- Undo completion for one-off personal tasks returns the same task to To do without a time. Recurring items use Repeat instead, so their existing next occurrence is not accidentally duplicated by undo.
- Shift completions remain visible in Done after ending a shift. Repeating a finished shift item creates a personal task, as labeled in the menu; it does not restart an ended shift.
## v1.4 changes

- Add personal or shift tasks without a specific time. Add, change or remove timing later from the task.
- Untimed shift tasks become available at shift start and are included when starting midway through a shift.
- Define your own shift hours; edit existing template hours. Template edits apply to future runs only, and timed rules retain their offsets from shift start.
- Repeating shift rules still require a first time. Personal task timing can be removed, which also turns off recurrence.

# PinTask v1.4 — Optional timing + custom shifts

Native Android Java project (minimum Android 8.0 / API 26, targets API 35, builds against SDK 36). No login, network service, or third-party runtime dependencies. All tasks and shifts are stored locally in SQLite.

## What's new: separate Shift Scheduler

Tap **Shift scheduler** on the personal task screen. This is a separate screen: personal tasks remain in their own list. Shift tasks appear in the shift screen and in the Android notification shade when their time arrives.

- Two empty, editable-task presets: **TD / Day shift 08:00–20:00** and **ND / Night shift 20:00–08:00**. No medical or workflow tasks are hard-coded; add your own.
- **Create custom shift templates** with a name, start and end time (including across midnight).
- Add template task rules with: task name, first time (clock time within the shift), **Once / every 30 minutes / every 1 / 2 / 3 / 4 hours**, priority, and **0 / 5 / 10 / 15 / 30 / 60 minutes early** notification appearance.
- Choose a date and press **Start / schedule selected shift**. This creates all future occurrences that fit inside that shift, up to 120, as separate tasks. Each occurrence has its own notification with **Done**; finishing an hourly occurrence will not mark the next hourly occurrence Done.
- Repeat intervals stop at the shift's end and **do not run outside the shift**. A shift template is reusable but starting a new shift is **manual**, so your routine won't schedule tasks on days you aren't working.
- Start a shift mid-way through its hours: earlier occurrences are skipped, avoiding a burst of past reminders. Starting a shift whose end is already in the past is rejected.
- One active/scheduled shift run at a time. Use **End shift** to cancel any outstanding occurrences (not mark them completed). Existing completed occurrences stay saved as completed history in the database.
- Remove template rules without altering an already-started shift. Templates and rules persist after restart and across v1.2→v1.3 upgrade.
- Daylight-saving-time-aware local shift calculations, so a night shift can still end at the correct local clock time over DST changes.

### Example

1. Open **Shift scheduler → ND (20:00–08:00)**.
2. Add **Check supplies**, first time **22:00**, repeat **Once**, notification **15 minutes early**.
3. Add **General hourly checklist**, first time **20:00**, repeat **Every hour**, notification **5 minutes early**.
4. Choose your shift date and press **Start / schedule selected shift**.
5. The hourly rule creates 12 independently completable occurrences (20:00 through 07:00), plus one 22:00 item. Each notification appears at the configured lead time, subject to Android scheduling delay, and remains until **Done** (except where the OS allows the notification to be dismissed).

## Personal tasks (unchanged)

- Add tasks, priorities Low / Normal / High, optional date/time, notification early appearance (Immediately / At time / 5 / 10 / 15 / 30 / 60 minutes before).
- Daily, weekly, monthly recurrence for personal tasks.
- Inline **Done** notification actions and persistent Quick Add notification.
- Tasks persist locally and alarms are restored after reboot, update, time/date changes.

## Build an APK without Android Studio

1. Extract this ZIP. Create a **new GitHub repository** and upload the **contents** of `PinTask-Android`, including the hidden `.github` directory, at the repository root.
2. Open **Actions → Build installable Android APK → Run workflow**.
3. After a successful run, download the `PinTask-debug-apk` artifact, unzip it and install `app-debug.apk` on your Android phone.
4. Allow notification permission and check that reminder notifications are enabled.

You can alternatively open the project in Android Studio and choose **Build → Build APK(s)**.

**This ZIP contains source code, not a compiled APK.** The project files were updated and structurally inspected; the Android SDK is not available in the authoring environment, so a full build and phone test could not be performed here. The `ShiftTimesTest` standalone Java test was compiled and passed using the locally installed JDK; it verifies normal day/night offsets and overnight DST behavior. To rerun it:

```sh
javac -d /tmp/pintask-test app/src/main/java/com/pintask/app/ShiftTimes.java tests/ShiftTimesTest.java
java -cp /tmp/pintask-test com.pintask.app.ShiftTimesTest
```

The workflow builds a **debug-signed** APK for personal testing. Debug signing keys can change between GitHub build machines, so later APKs may fail to install over the previous version. For reliable updates without clearing data, use a consistently signed release APK or backup the app database first.

## Important Android limitations

- On Android 14+, an ongoing notification may still be individually swiped away. A dismissed task notification is best-effort re-posted, **not guaranteed to be impossible to remove**. **Clear all** generally preserves ongoing notifications.
- Timed notifications use Android's `AlarmManager.setAndAllowWhileIdle()`, **not** exact alarm permission. Android Doze, battery optimization, OEM policies, and notification settings may delay, block, or remove a reminder. Hourly repeats are especially susceptible to delays during deep idle. The scheduled task itself stays in the app even if its notification is delayed.
- Do not use this as the sole reminder or alarm for time-critical care, clinical interventions, medications, or patient safety. Follow your institution's approved procedures and equipment.
- Shift schedules are tied to the phone's local time zone. Manual starting is intentional (no automatic next-day shifts).

### Key code

- `ShiftActivity.java` — shift UI and creation workflows.
- `ShiftRepository.java` — SQLite shift templates, rules, scheduled instances and end-shift cancellation.
- `ShiftTimes.java` — DST-aware local time offsets.
- `TaskStore.java` — SQLite schema v4; adds shift tagging to task records; upgrades v1–v3 data.
- `TaskNotifications.java`, `ReminderScheduler.java` — existing notification and scheduling logic.
- `.github/workflows/build-apk.yml` — GitHub Actions build workflow.

# PinTask 2.8 — Custom shifts and first-time guide

- **No hardcoded TD1/TD2/ND choices for new shifts:** Work → Set a shift offers your saved custom shift types, Create custom shift, and Off. Existing TD/ND shift data and previously saved tasks stay untouched and readable.
- **Create custom shift:** Choose any name and 24-hour start/end times (e.g. 08:00–20:00 or an overnight 20:00–08:00). Scheduling a shift does not force you to add tasks first. Reuse saved custom shift types for other dates or plan several dates.
- **First-time walkthrough:** A welcome guide offers seven simple steps, covering custom shifts, dates, shift tasks, clock times, reminders, repeats, reusable task templates and notifications. Skip it or replay anytime under More → Shift setup guide. Work also links to the guide.
- **Snooze + Done side by side:** Compact task notifications now show both buttons, while expanded actions and watch behavior remain intact. Snooze opens 5/15/30-minute, tomorrow, or custom-time choices; Done finishes only its own task.
- **Quality of life:** Work links directly to Create custom shift, newly scheduled shifts open directly to their task list, and overnight shifts describe next-morning task timing. Backup and signed automatic GitHub Releases remain supported.
- **Device caveat:** Android/ColorOS may replace custom collapsed notifications. Expanded native Snooze/Done actions remain available. For time-sensitive patient care, use approved clinical reminders.

## PinTask 2.7 — One-tap Done in collapsed notifications

- Unfinished task cards now provide a **Done** button directly in the collapsed notification (including shift tasks and due-now alerts), using Android's decorated custom notification layout. No expand tap is required on Android builds that render custom compact notifications.
- The existing expanded **Done** and **Snooze** actions, Wear OS Done action, task IDs, background completion and undo are preserved. Tapping the title still opens the task.
- The compact Done action removes only its own task notification; other tasks remain. A long title is truncated to leave room for Done.
- Android / OEM notification layouts can vary, so test on the target phone. The OS may override or hide custom views; the expanded Done action remains available as a fallback.
- Successful signed builds on the main branch now publish a matching versioned GitHub Release containing the installable APK and SHA256SUMS. Each version must increment versionCode and versionName.

## PinTask 2.6 — Quick Add notifications by default

- On **Tasks**, typing a name and tapping **Add** immediately posts an independent notification for that task. No date or time is required. Adding several tasks gives each its own Done action and notification ID.
- When notification permission is missing, Quick Add requests it on Android 13+; granting permission displays still-unfinished tasks. Enable the **Unfinished tasks** notification category if individual task cards remain hidden.
- The advanced editor still offers an explicit **No reminder** option. Existing saved tasks keep their reminder choices, including previously created silent tasks. Scheduled shift reminders are unchanged.
- **Watch notifications** controls whether notification cards also bridge to Wear OS; **Phone only, pinned** favors persistent phone cards. Android can still allow dismissal or delay delivery.

## PinTask 2.4 — Done from your Wear OS watch

- **Wear OS notifications:** task/reminder cards now allow phone-to-watch bridging and expose a watch-specific **Done** action. Open a PinTask notification on the watch and tap Done; the connected phone completes the task, clears its notifications and creates the next repeat once. A brief Undo notification is also available. No separate watch app is required for this notification action.
- Enable PinTask notifications in Galaxy Wearable, Google Pixel Watch or your watch companion app. Watch delivery still depends on pairing/connectivity, Android notification permission, companion notification filters and watch notification settings.
- **More → Watch notifications** defaults to Phone + watch. Watch-compatible cards must be clearable: Wear OS excludes ongoing/non-clearable cards. Swiping dismisses a card without completing the task; it remains in PinTask and may reappear when notifications refresh. Choose **Phone only, pinned** for the previous pinned-phone behavior. This setting does not turn silent tasks into reminders or change alert times. Quick Add is still opt-in/off by default.

- **Tasks:** simple personal tasks. Date and time are immediately visible in Add with time or repeat; advanced options stay optional.
- **Work:** choose a job/patient and date. TD1 is 08:00–20:00, TD2 is 09:00–21:00, ND is 20:00–08:00 the next morning. Older TD entries retain 08:00–20:00. Multiple shifts can share one date. Overlapping shifts ask before keeping both.
- **Only TD1, TD2, ND and Off are offered.** U8 and EZ have been removed from new scheduling. Old records remain readable so existing tasks are preserved; old no-shift markers display as Off.
- **Next shift:** one tap from Work opens the earliest future working shift for the selected job/patient.
- **Copy day / plan several days:** copy the selected day's shifts and tasks into other empty dates, or assign TD1/TD2/ND/Off to several dates at once. Add/remove dates in a review screen (up to 31); selected dates survive rotation. Copying keeps job/patient groups, wall-clock times and reminders, resets completion/checklist ticks, includes completed source tasks, and skips already-past timed occurrences. Existing destination work is never overwritten.
- Batch operations are all-or-nothing. Conflicts leave all selected days unchanged. Overlaps require an explicit Keep both choice; existing overlaps within a copied day are retained.
- Open a shift card for its own task list. Add tasks there, then use **Save as template at the bottom**. This snapshots each occurrence, including completed tasks, with its relative time, reminder, priority, notes and checklist. Reuse resets completion and checklist ticks. The original shift stays unchanged.
- **Use saved template** adds tasks directly to an existing shift with matching hours. Repeated application adds another copy and asks first. Times already past are skipped; existing tasks are retained. Templates are always optional.
- **More:** advanced library/search, history, bundles, templates, backup and Quick Add settings. Quick Add remains off unless enabled.

Database v8 adds an explicit day-label flag and optional template notes/checklists. Upgrades and older backups retain existing task IDs, reminder choices, shifts and template data. Signed releases install over prior signed releases without uninstalling. Back up before changing installations; uninstalling deletes local data.

## Earlier v1.9 features retained

- **Notes and checklists:** open a task's three-dot menu → Notes / checklist. Edit notes and enter one checklist step per line. Ticks save immediately; repeated copies reset ticks and keep the instructions. Main-list task titles also open details. Completion does not require every step to be ticked.
- **Task bundles:** open Task bundles on the main screen or Today. Create a bundle from task names or select existing tasks to capture their names, priority, notes and checklists. Tap a saved bundle to add independent, untimed personal tasks with reminders off. With a group selected, copies go to that group; All tasks uses the bundle's saved group. Bundles are optional and reusable across groups.
- **Pause / resume:** an active recurring personal task's overflow menu pauses its notifications and recurrence progression. It remains visible with a Paused label. Resume retains a future occurrence or advances a missed one, without creating a backlog.
- **Repeat after completion:** overflow → Repeat timing → Count interval after completion. Fixed scheduled patterns remain the default. After-completion mode counts the interval from the time you finish or skip. For selected weekdays, it chooses the next matching day at that completion time.
- **Selected weekdays:** both entry screens support weekdays, weekends, Mon/Wed/Fri, and a custom weekday picker. Existing tasks can change repeat to Selected weekdays. The first occurrence advances to a matching day if necessary.
- **Move to tomorrow:** an active personal task's overflow menu moves it to tomorrow at its existing time, or 09:00 if untimed. A 10-second Undo button restores the previous timing. Fixed repeat patterns keep their original anchor; shift-bound tasks use their existing time editor instead.
- **Search across all groups:** Today has Search all groups; the main screen has a search-scope switch. Global search covers personal and shift tasks plus history, including names, groups, notes and checklist text, while preserving the selected group for new tasks.
- Database v7 adds optional metadata and a bundles table without deleting existing data. Backups include these additions and still accept older backups.

## PinTask v1.8 features retained

- Personal task repeats: daily, weekly, monthly, every 2 weeks, every other day, every 3 days, every 3/4 weeks, and yearly.
- Use **Custom repeat…** for any interval from 1–365 days, 1–52 weeks, or 1–24 months. Choose the first date/time to anchor the schedule. Every two weeks from a Friday stays on alternate Fridays.
- **Skip this occurrence** is in an active recurring personal task's three-dot menu. It moves the same task to its next future scheduled date, clears snooze and reminder-delivery state, and preserves the group, priority and notification preference. It does not mark the task Done or add a duplicate.
- Repeat changes keep the task's existing reminder setting. Today and the main task list show the repeat label. Custom repeat selections and task drafts survive rotation.
- Repeats continue to generate their next occurrence when marked Done or skipped. A task left unfinished remains one outstanding item; there is no automatic backlog of missed copies.
- Existing stored repeat codes and schema v6 are retained. New presets/custom intervals are included in JSON backups and validated on restore.

## PinTask v1.7 features retained

- Open **Shifts** for **Today**, **Schedule**, and **Add Task**.
- Originally supported TD/ND and U8 working shifts. Version 2.2 corrects new U8/EZ entries to no-shift labels and adds TD1/TD2 as described above. No template or task is required to schedule a day. Existing templates remain under Optional templates.
- Choose **Job / patient** to create and select a named group. All tasks shows every group; Ungrouped contains old tasks and items created while All tasks is selected. Personal tasks can move individually; moving a shift task moves its whole shift and all its tasks, as the menu states. Group filters affect lists and the widget, never reminder delivery.
- The **Persistent Quick Add notification** switch on the main screen starts off. Enabling it restores the notification shortcut; disabling it immediately removes that shortcut. Task reminders and the widget's Add button continue to work.
- Add Task needs only a name. Time, reminder, and repeat are optional. New tasks default to No reminder; old tasks keep their notification behavior.
- Personal repeats support daily, weekly, monthly, and every two weeks. For every other Friday, choose the first Friday and Every 2 weeks. The next task is generated when the current one is marked Done; overdue missed dates are skipped while preserving the original two-week pattern.
- Shift repeats run every 30 minutes or 1/2/3/4 hours and stop before shift end. An ND task at 01:00 belongs to the following morning. Wall-clock timing handles daylight-saving changes.
- Database v6 adds group labels to existing rows without deleting tasks, history, templates, or favorites. Old JSON backups remain importable; new backups retain group labels and two-week repeats.
- Add Task drafts survive rotation, completion offers Undo, and Today shows unfinished tasks from earlier shifts.

# Earlier features retained

- **Reliable updates:** the workflow now builds a release APK with a permanent private signing key stored in GitHub Actions secrets. Subsequent signed releases can update in place. Earlier debug previews used different keys; Android may require a one-time reinstall when moving to this release. Reinstalling deletes local data.
- **Backup & restore:** use the button on the main screen to export a JSON file containing tasks, Done history, favorites, shift templates/rules and runs. Restore asks before replacing data, validates the file, and rolls back a failed import. Keep backups private: they contain readable task names.
- **Search and filters:** search tasks and Done history; filter overdue, today, no time, and high/normal/low priority.
- **Snooze:** notification Snooze opens choices for 5/15/30 minutes, tomorrow at this time, or a custom date/time. Due times and recurrence remain unchanged. Shift snooze stays within the shift.
- **Duplicate shifts:** select a template, tap Duplicate selected shift, give it a name, then adjust its hours and rules independently.
- **Favorites:** save any task from its three-dot menu. Open Favorites to add a fresh untimed copy with one tap. Manage favorites removes the shortcut without deleting existing tasks.
- **Quick undo:** a 10-second Undo button follows completion. Recurring-task undo removes the automatically created next occurrence only if it has not been changed. Notification/widget completions offer Undo through a temporary notification when notifications are permitted.
- **Widget:** long-press the Android home screen, choose Widgets, then PinTask tasks. It displays up to five active personal tasks depending on size. Add opens a quick-entry dialog; Done completes tasks without opening the main app. Tap the heading for the full list.

## Build and install 2.8

Each successful main-branch build automatically publishes a versioned GitHub Release containing **PinTask-VERSION.apk** and SHA256SUMS. You can also download the **PinTask-installable-apk** Actions artifact and install **app-release.apk**. The workflow requires PINTASK_KEYSTORE_BASE64 and PINTASK_KEYSTORE_PASSWORD secrets, already configured for this repository. Never commit signing keys or passwords. Local debug builds remain available with `gradle :app:assembleDebug`.

## Important Android limitations

- On Android 14+, an ongoing notification may still be individually swiped away. A dismissed task notification is best-effort re-posted, **not guaranteed to be impossible to remove**. **Clear all** generally preserves ongoing notifications.
- Timed notifications use `setExactAndAllowWhileIdle()` when permitted. On Android 12+, open **More → Reminder timing → Open settings** and allow **Alarms & reminders**. Without access, reminders use the previous inexact fallback and may arrive late. Returning to PinTask or granting access reschedules reminders; revoking access cancels Android's exact alarms, so reopen PinTask to restore the fallback. Doze quotas, OEM policies, and notification settings can still delay or suppress alerts. One alarm delivery now checks all due tasks, so simultaneous occurrences do not depend on separate wake-ups. Each task retains its own notification and Done/Snooze actions.
- Blocked notification channels do not consume reminder state. Re-enable the channel and reopen PinTask to retry due reminders. Posting exceptions also leave reminders eligible for retry on the next alarm or app resume. Notification grouping and sounds remain controlled by Android; three task cards do not guarantee three separate banners or sounds.
- Do not use this as the sole reminder or alarm for time-critical care, clinical interventions, medications, or patient safety. Follow your institution's approved procedures and equipment.
- Shift schedules use the phone's local time zone. Calendar plans become current during their scheduled hours; templates remain optional.


## Development and validation

Requires Java 17, Gradle 8.13, Android SDK 36 and Build Tools 35.0.0. Run `gradle :app:testDebugUnitTest :app:assembleDebug` for a local debug build. Release builds use PINTASK_KEYSTORE_PATH and PINTASK_KEYSTORE_PASSWORD; the alias is pintask. Keep the permanent signing key backed up privately.

Tests cover task entry, optional timing, Done/repeat, favorites, snooze, undo, filters, database migration, backup rollback, independent shift copies and widget completion. The workflow also runs the standalone ShiftTimesTest for overnight and daylight-saving behavior and verifies the release APK signature.
Wear OS bridging rules: https://developer.android.com/training/wearables/notifications/bridger

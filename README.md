# PinTask 2.0 — simpler home and Quick Add reset

- The app now opens directly to **Today**. The task library is under **More → All tasks and search**.
- **Today:** a shift summary, compact task rows with completion checkboxes, and Add task. Tap a task name for notes/checklists, or its overflow for task actions.
- **Schedule:** tap a calendar date, then **Choose shift**. TD, ND, U8, EZ and Off are in one picker. After selecting a working shift, tap Add task. Existing-shift management is behind its Change control. Templates are under More → Saved templates.
- **Add Task:** name, optional time and Save. Date/shift selection, reminders, repeat and custom weekdays are collapsed under **More options**. Draft text, timing and option state survive rotation.
- **More** holds job/patient selection, task bundles, search, history, saved templates, Quick Add settings and backup. Existing data and these features remain available.
- **Quick Add notification now requires fresh opt-in.** Older quick_add settings no longer enable it. Upgrade/boot/app refresh clears the known shortcut ID, tagged copies and older shortcut notifications identified by their inline Quick Add input; ordinary task notifications are preserved. To explicitly enable it: More → Quick Add notification → On. Off removes it and cancels its old action intent.
- The application ID, database v7 and signing identity are unchanged. Install this APK as an update; no uninstall is needed.

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
- Schedule TD 08:00–20:00, ND 20:00–08:00 the following morning, U8 08:00–16:00, EZ without fixed hours, or Off. No template or task is required to schedule a day. Existing templates remain under Optional templates.
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

## Build and install 2.0

Actions → Build installable Android APK → Run workflow. Download the **PinTask-installable-apk** artifact and install **app-release.apk**. The workflow requires PINTASK_KEYSTORE_BASE64 and PINTASK_KEYSTORE_PASSWORD secrets, already configured for this repository. Never commit signing keys or passwords. Local debug builds remain available with `gradle :app:assembleDebug`.

## Important Android limitations

- On Android 14+, an ongoing notification may still be individually swiped away. A dismissed task notification is best-effort re-posted, **not guaranteed to be impossible to remove**. **Clear all** generally preserves ongoing notifications.
- Timed notifications use Android's `AlarmManager.setAndAllowWhileIdle()`, **not** exact alarm permission. Android Doze, battery optimization, OEM policies, and notification settings may delay, block, or remove a reminder. Hourly repeats are especially susceptible to delays during deep idle. The scheduled task itself stays in the app even if its notification is delayed.
- Do not use this as the sole reminder or alarm for time-critical care, clinical interventions, medications, or patient safety. Follow your institution's approved procedures and equipment.
- Shift schedules use the phone's local time zone. Calendar plans become current during their scheduled hours; templates remain optional.


## Development and validation

Requires Java 17, Gradle 8.13, Android SDK 36 and Build Tools 35.0.0. Run `gradle :app:testDebugUnitTest :app:assembleDebug` for a local debug build. Release builds use PINTASK_KEYSTORE_PATH and PINTASK_KEYSTORE_PASSWORD; the alias is pintask. Keep the permanent signing key backed up privately.

Tests cover task entry, optional timing, Done/repeat, favorites, snooze, undo, filters, database migration, backup rollback, independent shift copies and widget completion. The workflow also runs the standalone ShiftTimesTest for overnight and daylight-saving behavior and verifies the release APK signature.
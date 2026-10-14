# PinTask v1.6 — Everyday improvements

- **Reliable updates:** the workflow now builds a release APK with a permanent private signing key stored in GitHub Actions secrets. Subsequent signed releases can update in place. Earlier debug previews used different keys; Android may require a one-time reinstall when moving to this release. Reinstalling deletes local data.
- **Backup & restore:** use the button on the main screen to export a JSON file containing tasks, Done history, favorites, shift templates/rules and runs. Restore asks before replacing data, validates the file, and rolls back a failed import. Keep backups private: they contain readable task names.
- **Search and filters:** search tasks and Done history; filter overdue, today, no time, and high/normal/low priority.
- **Snooze:** notification Snooze opens choices for 5/15/30 minutes, tomorrow at this time, or a custom date/time. Due times and recurrence remain unchanged. Shift snooze stays within the shift.
- **Duplicate shifts:** select a template, tap Duplicate selected shift, give it a name, then adjust its hours and rules independently.
- **Favorites:** save any task from its three-dot menu. Open Favorites to add a fresh untimed copy with one tap. Manage favorites removes the shortcut without deleting existing tasks.
- **Quick undo:** a 10-second Undo button follows completion. Recurring-task undo removes the automatically created next occurrence only if it has not been changed. Notification/widget completions offer Undo through a temporary notification when notifications are permitted.
- **Widget:** long-press the Android home screen, choose Widgets, then PinTask tasks. It displays up to five active personal tasks depending on size. Add opens a quick-entry dialog; Done completes tasks without opening the main app. Tap the heading for the full list.

## Build and install v1.6+

Actions → Build installable Android APK → Run workflow. Download the **PinTask-installable-apk** artifact and install **app-release.apk**. The workflow requires PINTASK_KEYSTORE_BASE64 and PINTASK_KEYSTORE_PASSWORD secrets, already configured for this repository. Never commit signing keys or passwords. Local debug builds remain available with `gradle :app:assembleDebug`.

## Important Android limitations

- On Android 14+, an ongoing notification may still be individually swiped away. A dismissed task notification is best-effort re-posted, **not guaranteed to be impossible to remove**. **Clear all** generally preserves ongoing notifications.
- Timed notifications use Android's `AlarmManager.setAndAllowWhileIdle()`, **not** exact alarm permission. Android Doze, battery optimization, OEM policies, and notification settings may delay, block, or remove a reminder. Hourly repeats are especially susceptible to delays during deep idle. The scheduled task itself stays in the app even if its notification is delayed.
- Do not use this as the sole reminder or alarm for time-critical care, clinical interventions, medications, or patient safety. Follow your institution's approved procedures and equipment.
- Shift schedules are tied to the phone's local time zone. Manual starting is intentional (no automatic next-day shifts).


## Development and validation

Requires Java 17, Gradle 8.13, Android SDK 36 and Build Tools 35.0.0. Run `gradle :app:testDebugUnitTest :app:assembleDebug` for a local debug build. Release builds use PINTASK_KEYSTORE_PATH and PINTASK_KEYSTORE_PASSWORD; the alias is pintask. Keep the permanent signing key backed up privately.

Tests cover task entry, optional timing, Done/repeat, favorites, snooze, undo, filters, database migration, backup rollback, independent shift copies and widget completion. The workflow also runs the standalone ShiftTimesTest for overnight and daylight-saving behavior and verifies the release APK signature.
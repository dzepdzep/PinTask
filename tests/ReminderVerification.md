# Reminder verification

Automated: run `:app:testDebugUnitTest :app:assembleDebug` with Java 17 and Android SDK 36.
ReminderSchedulerTest covers three independent same-time alarms, cancellation isolation,
all three cards from one broadcast, duplicate suppression, blocked channel state,
1,001 active tasks, denied exact access, and rescheduling after access is granted.

On a device (Android 12+ and Android 13+ notification permission):

1. Enable notifications and the Due reminders channel. In More → Reminder timing,
   grant Alarms & reminders access. Schedule three distinct shift tasks for the
   same minute a few minutes ahead, all with At task time.
2. Leave the app and lock the phone. At the due time, expand the notification shade
   and verify three separate titles. Record expected time and actual arrival time.
   A single banner or sound is not a missing-card failure.
3. Complete one card and snooze another. Verify the third is unaffected, and that
   snooze returns only the selected task. Repeat with phone/watch mode if used.
4. Repeat with exact access denied, reopening the app after changing access.
   Verify the timing status says Approximate; delivery can be delayed.
5. Repeat with the Due reminders channel blocked. After the due time, unblock it
   and reopen PinTask; all eligible cards should appear. Repeat with app-wide
   notification permission denied, then granted.
6. Repeat across reboot and with idle mode on a test emulator/device. Verify all
   cards eventually appear and no completed, paused, reminder-off, or canceled
   tasks are resurrected. Exact alarms remain subject to Android idle quotas.

Existing request codes and notification IDs are preserved to avoid migration
regressions. Normal sequential SQLite task IDs do not collide at equal times;
64-bit ID overflow/namespace migration is outside this targeted fix.

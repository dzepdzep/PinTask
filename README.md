# PinTask 3.16-rc1 — Home-screen Quick Add + Voice widget

- **New compact home widget:** Long-press the Android home screen → Widgets → PinTask → **Quick Add and Voice**. The compact ~4×1 widget features a **Type a task** area, a microphone button, and an EN/DE/HR chip; tapping the chip cycles the voice language without visiting the main PinTask screen.
- **Type without opening the main app:** Tap Type to open only a translucent, lightweight entry dialog over the launcher; type and confirm Add to create an immediate pinned unfinished task. Cancel and blank input create nothing.
- **Speak without opening the main app:** Tap the widget microphone to launch Android's installed speech recognizer using the selected language, then edit the transcription and review the due date/time, advanced warning and repeat. No task is created until **Create task** is tapped. The existing alarm, early reminders and notification architecture is reused unchanged. Android's speech recognition overlay and the temporary confirmation window are technically activities, since Android does not allow an editable text field or active mic capture directly inside an AppWidget RemoteViews. The full PinTask app is never opened.
- **Existing task-list widget improved:** Its Add row now contains both Type and Voice shortcuts.
- **Consistency and safety:** Voice review cancellation closes the temporary widget window; speech cancellation creates nothing. No new microphone permission, no remote service, no migration/uninstall, and no changes to the working alarm sound, Done/Snooze, conditional status icon, timer or voice parser rules.
- **Validation:** New Robolectric tests cover typed and spoken widget flows, user confirmation, canceled voice input, current HR language use, locale cycling and task scheduling.
- **Install:** Update over the existing package, then add the **PinTask · Quick Add and Voice** widget from Android launcher Widgets. Some launchers may require widget refresh/re-add after upgrading.

# PinTask 3.15-rc1 — Natural English and German spoken task reminders

- English now understands normal spoken delay expressions including **"in an hour"**, "in half an hour", "in two hours", "in thirty minutes", and secondary clauses such as **"Remind me five minutes earlier"** and "notify me at least ten minutes beforehand".
- German now handles **"in einer Stunde"**, "in einer halben Stunde", number words in relative delays, and independent early warnings such as **"Erinnere mich fünf Minuten vorher"**, "mindestens zehn Minuten vorher". Handles the grammatical filler in **"daran, den Computer auszuschalten"**, preserving the meaningful task name.
- Both map the main time and separate early warning to **one task with one due time**. E.g. task due in 60 minutes, alarm in 55 minutes. Review still shows the exact originally recognized transcript, lets users correct title/time/early warnings, and requires explicit confirmation.
- Existing Croatian grammar, English/German past formats, saved SQLite tasks, working alarm sound, Done/Snooze/Stop sound, status-icon condition and time pickers are unchanged.
- Ambiguous schedules, duplicate advance phrases and warnings in the past are still rejected conservatively. Android speech recognizer is outside the app; correctly transcribed text is required, with editable fallback.

# PinTask 3.14-rc1 — Natural Croatian voice reminders with advance warnings

- Croatian (hr-HR) text can now understand **“podsjeti me za sat vremena da ugasim kompjuter podsjeti me bar pet minuta ranije”**. The task is due in one hour, the alarm is scheduled **five minutes before** that due time, the task title is **“ugasim kompjuter”**, and the entire originally recognized transcript is shown for correction before saving.
- Adds “za sat vremena”, “za jedan sat”, “za dva sata”, “za pola sata”, optional “vremena”, and normal minimum-lead phrases like “bar/barem/najmanje pet minuta ranije/prije”, including natural second “podsjeti me” clauses.
- Localized “da” before a task clause is stripped **only** from extracted Croatian task names; English/German titles and the full recognized text remain unchanged.
- Preserves the conservative parser: ambiguous or contradictory times fail validation, and early alerts that would already be due cannot be saved. No automatic task creation from speech without explicit confirmation.
- Android's external speech recognition must transcribe the actual words reasonably; if not, the existing editable transcript lets the user correct them. No cloud service added.
- No changes to working ringtone playback, Done/Snooze stop actions, notification channels, scheduled alarms, custom stopwatch/time pickers, other languages, saved tasks or SQLite schema.

# PinTask 3.13-rc1 — Done / Snooze stops the ringing alarm

- **Confirmed by user:** Opt-in independent alarm ringtone can be heard on their OnePlus/Android 16; however, the alarm continued after Done on the reminder notification.
- **Done:** Both the normal pinned task notification and the temporary ringing foreground notification's Done action complete the task and immediately stop its associated ringtone, without completing unrelated tasks. The same works from task completion inside the app through the existing shared notification-cancellation path.
- **Snooze:** Selecting Snooze on either notification silences immediately before showing the 5/15/30-minute, tomorrow, or custom time chooser; confirming a time schedules a new reminder without completing the task.
- **Stop sound:** The foreground ringing notification also provides an explicit Stop sound control. This silences playback **only**, leaving the task pending until Done or Snooze.
- **Android compatibility:** The stop request uses `Context.stopService()` instead of trying to start a service in the background, which could be rejected on Android 16. Only the currently ringing task can stop its playback when completed. Foreground service playback limits, notification channels, exact scheduling, voice recognition, custom time pickers and existing task data are unchanged.
- **Safety:** Includes automated tests for Done, Snooze, unrelated tasks, and stop request. Install as an update, never uninstall to migrate tasks. The behavior still requires actual OnePlus locked-screen verification before calling it fully confirmed.

# PinTask 3.12-rc1 — Task-only status icon, stopwatch immediately, and stable settings scroll

- **Only show the PinTask status icon for real task reminders:** More → Show icon only for active reminders enables an optional task-attention notification. It stays hidden when there are no active timed reminders. It appears when a scheduled task's configured reminder actually fires (including early reminders), remains while that timed task needs attention and clears on Done or Snooze. No unscheduled tasks, paused/silent tasks, distant upcoming tasks or already completed tasks generate a permanent PinTask shortcut.
- **Do not pre-post silent reminders:** The conditional status icon is added after the reminder callback marks the task as delivered. The original audible reminder notification channel remains unchanged. The user can turn the indicator off; OxygenOS still controls which silent notification icons it shows at the top of the screen.
- **Stopwatch is the primary control now:** Tasks → When and the editor's Or alert me open hour/minute/second wheels directly, with Type duration for examples like 1h 17m, 1:17:30, 90 seconds; a Quick presets option remains, and the quick task picker has a Now button. No intermediate list hides the stopwatch.
- **More page keeps its place:** App settings toggles rebuild the page while restoring its previous vertical scrolling position. Switching to a different page still starts at the top.
- **No changes to old user task data, language recognition, saved advance reminder minutes or alarm scheduler.** This is an opt-in test candidate, not a verified alarm-audio fix; the OnePlus missing-alarm-sound issue still requires device diagnostics.

# PinTask 3.11-rc1 — Independent alarm ringtone and verifiable icon switch

- **Status icon is a real toggle:** More → Show PinTask icon next to the clock (default off). The adjacent **Status icon diagnostics** distinguishes Off, permission/category blocked, not posted, and posted. PinTask cannot force OxygenOS to display silent notification icons if the device's status-bar settings hide them. Refresh and category-settings shortcuts are provided.
- **Optional alarm ringtone:** More → Also ring with alarm ringtone (default off). On a newly delivered, timely scheduled alarm, PinTask also requests a bounded 30-second foreground audio playback of the system alarm ringtone, independently of the sound chosen for Android notification channels. A Stop ringing action and 30-second auto-stop are provided. Because this is additional to the normal notification, users whose notification sounds already work may hear two sounds. Android DND/power restrictions can still prevent sound. Due alarms recovered very late by the watchdog do not unexpectedly ring.
- **Diagnostic detail:** More → Reminder health check shows direct-ringtone and icon status; More → Reminder diagnostics records ringtone service request, blocked start, playback started, playback failure or ringtone stopped (the logs cannot prove an audible sound actually reached the user).
- **Preserves existing behavior:** The stored tasks, voice parsing, import from messages, timer wheels, Quick Add, active task notifications and exact alarm scheduler remain. No data migration or uninstall. No promise of a confirmed hardware fix until a locked-screen alarm rings on the affected OnePlus.
- **Android FGS:** Only opt-in playback uses `foregroundServiceType=mediaPlayback`. This is an experimental candidate, not a stable release.

# PinTask 3.10-rc1 — Scroll-or-type time and earlier reminder pickers

- **Clock time:** For regular, work, recurring-routine, spoken, or message-imported tasks, users can scroll separate **HOURS** and **MINUTES** wheels or switch to **Type** and enter exact 24-hour `HH:mm` (for example `03:30` or `15:45`). 24:00 and invalid minutes are rejected; Cancel does not change the task.
- **Advance reminders:** The main task editor retains existing easy presets and guided-tour spinner, and adds **Remind me earlier · Scroll or type**. The two-wheel input allows 0 minutes (at time) up to 24 hours (max stored in database) or typed `75`, `1:15`, `1h 15m`. The chosen amount is persisted with the task, including non-preset minutes such as 77.
- **Voice / imported-message review:** Its date/time/advance fields now use the same flexible picker, preserving editability and confirmation. No changes to voice-language parsing or message import.
- **No database migration or deletion:** Existing tasks, saved early reminders and other 3.9 features remain intact. The earlier OnePlus alarm-sound failure is **not fixed** by these UI changes; the release is a candidate pending real-device checks.

# PinTask 3.9-rc1 — Message-to-task import and status bar icon

- **Share or paste text as a task:** In Android, select text or share a message to PinTask, or open Tasks → **Paste message → task**. PinTask only reads text that you explicitly share or paste, and parses dates/times offline; no SMS, messaging, notification-listener or microphone permissions are added.
- **Example:** `Friday, 23 October 2026` followed by `03:30 AM` and `Your local time in Germany (CESt)` resolves to 23 October 2026 at 03:30 Europe/Berlin. The message does not give a task name, so you must enter one manually before creating it.
- **Always review first:** The message and parsed schedule open in the existing editable voice-task review UI. You can adjust title, date, time, early alert and repeat. Nothing is scheduled until **Create task** is tapped. Conflicting multiple dates or times, wrong weekdays/time-zone labels and ambiguous daylight-saving times are rejected rather than guessed. This does not import chats automatically.
- **PinTask icon next to the clock:** A separate, user-toggleable status-bar shortcut notification appears when allowed by Android, controlled under More → **Status bar icon**. It is silent and separate from Quick Add and alarm notification channels. Android/ColorOS may hide silent status-bar icons, so on-device visibility cannot be guaranteed solely through the app.
- **Existing functions preserved:** Voice task input and its supported EN/DE/HR language parsing, work shifts, task data, reminders, widgets and notification settings are unchanged except for the new optional shortcut. This branch remains a test release candidate. **The OnePlus alarm sound issue is not fixed or claimed fixed.**

# PinTask 3.8-rc1 — Optional alarm-clock timing and audible troubleshooting

- **Reported failure not yet resolved on-device:** OnePlus/Android 16 notifications have arrived 7 minutes later than the selected early reminder and without audible alarms. Do not mark as fixed until the 15-minute early alarm and ringtone succeed on the affected device with the screen locked.
- **Opt-in Alarm-clock priority:** More → Alarm-clock priority → Enable switches timed tasks to Android `AlarmManager.setAlarmClock()` when exact-alarm permission is granted. This is a stronger wake-up promise than `setExactAndAllowWhileIdle()`, particularly in Doze. Android may display upcoming alarms, and it consumes more battery. It does not itself play a ringtone or override notification-channel settings; use only for alerts requiring alarm-clock-level importance. Unavailable permission is logged and falls back to inexact scheduling, which can be late.
- **Separate sound checks:** More → Reminder health check now distinguishes the existing timed notification test from a five-second direct system alarm ringtone playback. An audible ringtone plus silent notification suggests a category/Do Not Disturb issue; two silent tests suggest ringtone/volume/system audio settings. A direct test is not evidence the OS delivers scheduled alarms on time.
- **Preserves v3.7 voice input, transcripts, schedule review, German/Croatian/English language parsing, custom early-reminder values, shifts, and task data.** No task database migration or clearing; install over the existing signed app. A fourth speech language mentioned by the user is not present in the accessible GitHub `VoiceLanguages.java`; do not claim it is included until its newer source is provided.

# PinTask 3.7 — Voice-to-task with schedule confirmation

- **Microphone on Tasks:** Tap **🎤** beside Quick Add to use Android's installed speech recognizer in English (US) and say a natural instruction such as "I need to wash the dishes today at 13", "Take out the bins every other Tuesday at 8", "Remind me to buy milk tomorrow at 14:00", or "Refill water in 30 minutes". This starts a user-initiated recognition intent; the app itself does not request direct microphone access or record the audio. Depending on the phone, the recognition provider may process speech online and its own privacy policy applies.
- **Review first, never auto-save:** Speech results open an editable transcript, followed by a clearly separated **What I heard**, **Task name**, **Date**, **Time (24-hour clock)**, and **Repeat** review. Each field can be corrected independently: Date opens the Android date picker, Time opens the native clock picker, and Repeat opens a choice menu. A live summary updates to reflect changes, and **Create task** is the only action that saves. The model validates the edited future timestamp, repeating weekday consistency, and nonexistent daylight-saving clock times so a correction never silently reverts to the recognized time. Incorrect transcription can be edited; unsupported phrases show an error rather than silently scheduling for an arbitrary time.
- **Conservative parsing:** Supports English today/tomorrow, next weekday, explicit `HH:mm` or `at 13`, am/pm, delays in minutes or hours, daily routines, weekly named weekdays, every-other-week named weekdays and recurring hourly intervals. `tomorrow at 24` is explicitly rejected because end-of-day midnight is ambiguous: say `tomorrow at 00:00` or a precise following date/time instead. A recurring task requires a meaningful first due time.
- **Spoken early reminders:** Say **“Remind me to wash the dishes tomorrow at 13:00, 15 minutes before,” “Remind me 10 minutes before my appointment tomorrow at 14:00,” “Take medication today at 20:00 five minutes early,”** or **“Take out bins every other Tuesday at 08:00 half an hour before.”** The recognition review shows **Task due** and **Alarm rings** as separate local clock times. **Remind me before** can be corrected using presets **At task time / 5 / 10 / 15 / 30 / 60 minutes**, or a custom **1–1440 minutes**. The database, backup validator and routine scheduler now preserve custom advance values; any advance reminder falling in the past is rejected at confirmation.
- **Same alarm/repeat engine:** A timed voice task uses the existing exact/inexact reminder scheduler, default audible at its task time unless the spoken early reminder or review picker changes it. Repeating tasks use the same automatic routine engine as Regular tasks, and retain independent Done/Snooze. Speech input is scoped to personal Tasks; existing Work shifts and other settings are left unchanged.
- **Graceful fallback:** If the device has no compatible speech recognition activity, an editable command dialog opens so the task can still be entered without voice. No additional network or microphone permission is declared by PinTask.
- **Tests:** Validate the parser with fixed Europe/Berlin clock values, ambiguous and past-time rejections, biweekly Tuesday schedules, daily/weekday/hourly repeats, recognition launching without auto-saving, and two explicit confirmations before persistence. A real microphone and UI appearance test on ColorOS remains necessary; successful Android unit tests alone are not end-to-end validation.

- **Voice languages: English, Deutsch, Hrvatski.** On the Tasks screen, tap the **🎤 EN/DE/HR** microphone button; a compact picker immediately presents **EN English**, **DE Deutsch**, and **HR Hrvatski**. Tapping any choice launches speech recognition directly, with no separate Settings screen or second microphone tap. The microphone label shows the last-used language. The selection is saved for future recordings; speech recognition requests the corresponding Android locale (`en-US`, `de-DE`, `hr-HR`). Common localized commands for today/tomorrow, weekdays, every other Tuesday, daily and hourly repeats, clock time, relative minutes/hours and early reminders are translated into the existing conservative scheduler. German example: `Erinnere mich daran, das Geschirr morgen um 13 Uhr zu spülen`. Croatian example: `Podsjeti me da operem suđe sutra u 13 sati`. The original transcript stays visible in the review screen and no task is created without confirmation. Speech-recognition language availability depends on the installed Android provider; this is not a translation of every app screen and unsupported grammatical phrasing may need manual editing.

## PinTask 3.6 — Independent missed-reminder recovery and optional pop-up banners

- **Missed 07:15 reminders without opening the app:** PinTask continues using Android exact `setExactAndAllowWhileIdle` alarms when allowed and inexact wakeup alarms as a fallback. It now also registers a **persistent OS-managed JobScheduler recovery check approximately every 15 minutes**, which runs without opening the UI and independently checks for overdue notifications, schedules upcoming reminders, and maintains persistent task cards. Android Doze, ColorOS background limits and battery restrictions can delay this recovery; it is not an exact 07:15 guarantee.
- **Proof instead of guessing:** **More → Reminder diagnostics** shows when the last Android alarm callback arrived, when the background recovery job ran, when reminders were posted, and when overdue reminders were recovered. The Copy report button collects a short history of task IDs and timestamps (no task names) plus current exact-alarm, notification and battery settings. It helps distinguish missed AlarmManager dispatch from blocked notification posting and later app-open recovery.
- **Optional WhatsApp-like heads-up alerts:** **More → Reminder pop-up banners → On/Off**. On selects the existing urgent, high-importance timed-alarm channel to request a top-of-screen notification banner. Off selects a new default-importance channel that keeps the default alarm ringtone, vibration and ordinary status-bar/shade notification, but does not request heads-up. The choice affects future alerts; Android/ColorOS notification-channel and floating banner settings may override it. Android channels cannot be silently changed after creation.
- **Safer background delivery:** On each real alarm receiver callback, PinTask records a timestamp and delivers all due tasks independently; delayed recovery is recorded separately. Recurring occurrences still generate their next task exactly once and work without requiring Done; old routine history is preserved.
- **Permissions and limits:** Continue to allow **Alarms & reminders**, app notifications and the active Timed alarm channel. On ColorOS, allow background activity/auto-launch and review battery optimization, Do Not Disturb, alarm volume and floating notification settings. PinTask cannot programmatically force permission or override OEM app termination.
- **Verified in automation:** Android tests cover independent recovery after an overdue reminder without launching an Activity, routine regeneration, persistent scheduling of recovery work, exact/inexact callback logging, and enabled/disabled pop-up channels. Actual locked-screen 07:15 delivery still needs testing on the affected device.

## PinTask 3.5 — Persistent unfinished-task notifications

- **Pinned until Done (as far as Android permits):** Every incomplete task card, including timed alarm notifications, now uses `setOngoing(true)`, `FLAG_NO_CLEAR`, and `setAutoCancel(false)` whether **Phone + watch** or **Phone only** is selected. The old Phone + watch default accidentally disabled persistence. Each task still gets its own independent notification and actionable Snooze / ✓ Done buttons.
- **Accidental swipe recovery:** Android 14+ explicitly allows people to swipe away most ongoing notifications on unlocked phones. PinTask now attaches a unique dismissal receiver to both normal and legacy due cards and attempts a **silent** re-post after an individual dismissal, without marking a task Done or replaying the alarm ringtone. Recovery never resurrects a completed, paused, silent, not-yet-due or currently snoozed task. Android / ColorOS may still suppress or delay the callback, so absolute non-dismissability cannot be guaranteed.
- **Clear all protection:** The ongoing and no-clear flags provide system-supported protection against Clear all, where supported.
- **Preserve existing behavior:** No changes to task storage, shift plans, repeat intervals, notification text/buttons, startup permission checks, or backups. Done cancels the matching card; Snooze intentionally hides it until its next alert. Watch Done remains available; phone/wear mirroring still depends on Android.
- **Tests:** Check flag combinations for both phone modes, due-card persistence, idempotent per-task swipe recovery, two independent task cards, and no revival after Done or Snooze. Automated tests cannot simulate ColorOS notification-swipe restrictions.

## PinTask 3.4 — Readable notifications and regular tasks

- **Legible notification text on ColorOS:** The compact notification layout now uses a deliberately dark surface with white, bold task names. Each task continues to have its own notification. App subtext was removed to avoid the repeated "PinTask · PinTask" header.
- **One set of actions, not two:** Keep the compact **Snooze** and **✓ Done** buttons that are already directly visible on this phone, but remove their duplicate native phone notification action row. The Wear OS Done action is still provided via the WearableExtender. The notification title remains in standard fields for other Android surfaces and accessibility.
- **Regular task directly on the Tasks home screen:** Choose **Regular task · Daily / hourly** underneath Quick Add. Create a daily reminder at the same clock time, several independent daily reminders at different clock times, or a recurring hourly/2-hour/3-hour/4-hour/custom 1–24-hour schedule. Pick daily times using the Android clock dialog, not manual HH:mm typing.
- **Reliable opt-in routines:** New regular tasks have an alarm at their due time and create the next occurrence when an alert is successfully delivered, even if you have not pressed Done on the previous one. Completed occurrences remain in history, and repeating does not create duplicate occurrences if Done or alarm handling happens twice. **Old repeating tasks retain their previous Done-to-generate-next behavior.**
- **Stop a routine:** The task’s ⋮ menu provides **Stop this routine…**, which cancels its future unalerted items without removing other routines or completed history. Individual Done/Snooze controls still target only their own task.
- **Backups and updates:** Automatic routine metadata is stored in a backward-compatible SQLite v9 migration, and backups restore old task records without the new fields. Existing tasks and shifts are retained.
- **Safety:** These are ordinary Android notifications with alarm sound. Delivery still depends on exact-alarm access, enabled notification channel, alarm volume and OS/OEM restrictions. Use a separate approved alarm for critical care tasks.
- **Verification:** Automated tests cover independent notification cards, daily multi-time planning, exact hourly recurrence, no duplicated next on Done, stop-routine isolation and older backup restoration. Physical ColorOS screen/locked-phone testing remains needed.

## PinTask 3.3 — Stopwatch-style delay selector (HH:MM:SS)

- **Custom delay uses a visual stopwatch instead of typing text:** Open Tasks → When → Custom delay, or choose a relative delay in the Tasks/Work editor. The custom dialog now displays three independently scrollable numeric wheels labeled **HOURS · MINUTES · SECONDS** with a live **HH:MM:SS** readout. No keyboard or free-text entry is required.
- **Choose any duration from 1 second to 30 days**, with whole-second accuracy. Examples: 01:30:00 means 90 minutes, 02:00:00 means two hours, and 00:05:15 means 5 minutes and 15 seconds. An all-zero duration or a value over 30 days is rejected rather than silently scheduling an immediate task.
- **Short presets are preserved:** Now, 5 minutes, 10 minutes, 15 minutes, 30 minutes, and 1 hour remain one-tap options. You can cancel the stopwatch without changing the current delay.
- **Accurate alarm scheduling:** Relative times in Quick Add and shift tasks now retain full second precision from the actual Add/Save press to their persisted due timestamps and Android scheduled alarms. Existing reminder sounds, permissions, Snooze/Done, shift clock selectors, tasks and custom templates remain unchanged. Shift-task delays must still fall within their shift.
- **No migration or data loss:** Saved tasks and schedules are preserved; the change only affects how newly chosen relative delays are entered. Automated tests cover wheel selection, second precision, preset compatibility, range checks and shift bounds. Physical-phone locked-screen testing is still needed for important alerts.

## PinTask 3.2 — Relative-time quick add, shift delays and clock pickers

- **Tasks home Quick Add now has a compact time dropdown:** **Now** (the unchanged default), **In 5 minutes**, **In 10 minutes**, **In 15 minutes**, **In 30 minutes**, **In 1 hour**, or **Custom delay…**. Pick a delay before entering a task or pressing Add. A delayed task stays in your list but its **audible, pinned reminder is scheduled for the selected interval from when you tap Add**, not displayed early. Quick Add resets to Now after adding, preventing accidental future delays on the next task.
- **Flexible custom delay entry:** Type **90 minutes**, **60 minutes**, **2 hours**, **1h 30m**, **1.5 hours**, or a plain number of minutes (1 minute through 30 days). Delays are saved as exact millisecond-based due times, without rounding to HH:mm.
- **Work/shift task editor now offers relative alerts too:** **Or alert me → Choose a delay** has the same 5/10/15/30/60-minute options and custom entry. Relative alerts default to **At task time**, with advanced options still available; the task time is measured from Save. For tasks assigned to a shift, relative times must fall within the chosen shift. Out-of-shift delays are rejected without saving a task.
- **No more typing 20:00 in Create custom shift:** **Shift begins** and **Shift ends** are buttons opening Android's built-in 24-hour clock picker, supporting overnight shifts.
- **Preserves existing behavior and data:** Immediate Quick Add, absolute Time picker, advanced date/repeats, existing shift schedules and interactive guide remain as before. Android's exact-alarm permission and notification access are still required for prompt audible reminders. Relative-time scheduling does not guarantee alarm delivery when OEM battery restrictions apply.
- **Tests:** Cover presets, free-text duration parser, independent delayed task alarm, shifting times without minute truncation, rejection outside shift, and native clock selection for overnight shifts.

## PinTask 3.1 — Automatic reminder-access checks on startup

- **Permission check every launch:** When PinTask opens, it checks Android notification permission, app-wide notification setting, the **Timed task alarms** and **Unfinished tasks** channels, and exact-alarm permission. Previously reviewed launches with all essential access enabled proceed without interruption. If anything required is blocked, PinTask shows a clear status checklist rather than silently assuming reminders will work.
- **Guided permission approval:** Choose **Fix next** to request the Android 13+ notification runtime permission or open the specific Android notification channel / Alarms & reminders settings. On return to PinTask (or after responding to the runtime permission dialog), the app **checks the actual granted status again** and offers the next missing step. The user may press **Later** and keep using PinTask, but blocked/delayed alarm risks remain visible.
- **Battery and device checks:** The same checklist shows alarm volume, whether standard Android battery optimization is active, and a clear caveat that ColorOS/OEM background or auto-start restrictions must be checked manually. **Battery settings** opens Android's system allowlist screen as an optional user choice. The app cannot automatically disable optimization, DND or OEM restrictions.
- **Review on demand:** **More → Permission checklist** re-runs all checks and settings links any time, even if all permissions were previously approved.
- **Preserves existing features:** The interactive shift guide, saved shifts and tasks, alarm sounds, Snooze/Done, reminder rescheduling on permission changes, and your existing data remain untouched. No new special permissions are declared in the manifest.
- **Verification:** Automated tests cover all core permissions allowed, a blocked timed-alert channel and its Android settings link, missing exact alarm access on Android 12, and denied runtime notification permission on Android 13. A real on-phone locked-screen reminder test is still needed.

## PinTask 3.0 — Audible scheduled alarms and reminder reliability

- **Timed tasks are no longer silent by default:** When you select a time in the three-tab Tasks/Work editor, PinTask automatically selects **At task time**, with that alarm status shown on the task editor. You can deliberately choose **No reminder** under More options. Untimed quick-added items continue to appear immediately as persistent, quiet tasks.
- **Real alarm-like notification sound:** Timed reminders, scheduled work tasks, early alerts and snoozes now use a NEW high-importance Android channel **Timed task alarms** with the system default alarm ringtone, alarm audio usage and vibration. A new channel is essential because Android won't let an update rewrite an existing channel's sound. The sound plays once per alert, not continuously until Done. Exact timing still requires Android's Alarms & reminders permission, and sounds depend on phone volume, Do Not Disturb and system/OEM restrictions.
- **Reminder health check:** More → Reminder health check shows notification permission, timed-alert channel, exact-alarm permission, and alarm volume. Use **Play an alarm notification now** to check sound or **Schedule an alarm test in 60 seconds** and lock the phone to test background delivery.
- **Recover silent existing tasks:** An explicit, confirmed option enables At task time for future unfinished timed tasks currently configured with No reminder. This intentionally also changes tasks you deliberately kept silent; no database migration silently changes your existing settings.
- **Clear warnings:** When saving an audible timed task, the app asks you to fix blocked notifications or denied exact-alarm permission; Android can otherwise skip or delay alerts. Users must verify critical reminders on their physical devices. The app cannot guarantee timing under battery restrictions.
- **Regression tests:** Cover the alarm sound channel and audio usage, delivered notifications and due channel, simultaneous reminders, scheduled AlarmManager PendingIntents, and user-approved migration of old silent tasks, as well as the actual editor's new default.

## PinTask 2.9 — Interactive shift setup tour

- **Real, in-app walkthrough instead of message-only instructions.** Work → How to set up shifts or More → Interactive shift guide starts a spotlight tour that highlights the **actual clickable controls**. Follow Work → Create custom shift → Set a shift → Add task to this shift → Task name → Time → More options → Reminder → Save task → Save as template.
- **Use the real screens:** The highlighted control is tappable, or use the tour panel's matching action. Shift creation, date selection, the reminder dropdown and task saving all use the existing app features—no fake sample tasks or data migration.
- **First launch and replay:** The app offers the interactive tour once and keeps it available from More. It can be exited without deleting data. During name entry a visible **Next: Choose task time** action moves the guide forward; optional template saving may be skipped at the last step.
- **Safe on existing installations:** Previously saved tasks, shift templates, schedules, notification settings, Snooze/Done behavior and the database schema are unchanged. The guide only saves the shifts or tasks you actually create.
- **Known limitations:** Android may control custom notification appearance, and precise reminders still require system permissions. The spotlight is designed for on-screen controls; dialog fields are explained directly in the corresponding real dialog.

## PinTask 2.8 — Custom shifts and first-time guide

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

## Build and install 3.7

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

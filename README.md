# PinTask

A small Android task and shift planner that keeps unfinished tasks visible and reminds you when it's time. Add something quickly, speak it, or plan it around work.

[**Download the latest signed APK**](https://github.com/dzepdzep/PinTask/releases/latest) · [**Report a bug**](https://github.com/dzepdzep/PinTask/issues)

## What it does

- **Tasks:** quick add, priorities, checklists, notes, search, favorites, repeating tasks, Done, Undo and Snooze.
- **Reminders:** exact alarms where Android permits, optional alarm ringtone, early alerts, and a status icon that appears when a timed task needs attention.
- **Voice (English / Deutsch / Hrvatski):** say a task naturally, then **review and correct** the task, date, time and early warning before saving.
- **Home-screen widgets:** a compact Quick Add + Voice widget and a task list with Done. The mic and text shortcuts open a small popup, not the full app.
- **Work:** plan day/night/custom shifts, job or patient groups, reusable shift templates and repeating shift tasks.
- **Your data:** local task storage, JSON backup/restore, and optional import of text you explicitly share or paste.

## Say it your way

| Language | Example |
| --- | --- |
| English | “Remind me in two days to take out the trash, 15 minutes earlier.” |
| Deutsch | “Erinnere mich in zwei Tagen daran, den Müll rauszubringen. 15 Minuten vorher.” |
| Hrvatski | “Podsjeti me za dva dana da iznesem smeće, petnaest minuta ranije.” |

PinTask creates **one task with an optional early alert**. For “in two days, 15 minutes earlier,” the due time is two calendar days away; the reminder fires 15 minutes before that. Speech recognition comes from your phone, so **always check the transcript and schedule**. For unclear dates or times, PinTask asks rather than guessing.

## Install and set up

1. Download **PinTask-VERSION.apk** from [GitHub Releases](https://github.com/dzepdzep/PinTask/releases/latest). **Install over your current version** to keep your tasks; uninstalling deletes local app data. Make a backup first.
2. Allow notifications and, on Android 12+, **Alarms & reminders** / exact-alarm access. See **More → Permission checklist** and **Reminder health check**.
3. For audible alerts, check your phone's notification category sound and volume. You can optionally enable **Also ring with alarm ringtone** and **Alarm-clock priority** in More; test these on your phone.
4. Add a widget by long-pressing the home screen → **Widgets → PinTask**. Use its EN / DE / HR control for voice language.

**Android limitation:** Battery saving, Do Not Disturb, blocked notification channels and manufacturer settings can delay or silence alerts. For safety-critical tasks, use a separate trusted alarm and established procedures.

## Build and contribute

Java 17 · Gradle 8.13 · Android SDK 36. Run `gradle :app:testDebugUnitTest :app:assembleDebug` to build/test locally. The GitHub Actions workflow runs unit/UI tests, verifies APK signatures and publishes **normal, signed Releases** from `main`. Signing keys stay in GitHub Secrets, never in the repository.

See [Releases](https://github.com/dzepdzep/PinTask/releases) for changes by version and [Issues](https://github.com/dzepdzep/PinTask/issues) for reports and requests.

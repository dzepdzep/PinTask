package com.pintask.app;

import android.content.Context;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Robolectric;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.EditText;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class TaskTimingTest {
    private Context context;
    private TaskStore store;
    private ShiftRepository shifts;

    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase("pintask.db");
        context.getSharedPreferences("settings",0).edit().putBoolean("custom_shifts_guide_seen_v28",true).commit();
        store = new TaskStore(context);
        shifts = new ShiftRepository(context);
    }

    @After public void cleanup() { shifts.close(); store.close(); }

    @Test public void personalTaskCanGainAndLoseTimeWithoutDuplication() {
        TaskStore.Task task = store.add("Buy milk");
        assertEquals(0, task.dueAt);
        assertEquals(-1, task.notifyBeforeMinutes);
        long due = System.currentTimeMillis() + 3_600_000;
        TaskStore.Task timed = store.updateTiming(task.id, due, TaskStore.REPEAT_DAILY, 15);
        assertEquals(due, timed.dueAt);
        assertEquals(due - 900_000, timed.notificationAt());
        TaskStore.Task cleared = store.updateTiming(task.id, 0, TaskStore.REPEAT_DAILY, 15);
        assertEquals(0, cleared.dueAt);
        assertEquals(TaskStore.REPEAT_NEVER, cleared.recurrence);
        assertEquals(1, store.activePersonal().size());
        assertEquals("Buy milk", cleared.title);
        store.complete(task.id);
        assertThrows(IllegalArgumentException.class, () -> store.updateTiming(task.id, due, 0, 0));
    }

    @Test public void mainScreenAddsTaskWithOnlyATitle() {
        try (org.robolectric.android.controller.ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class).setup()) {
            View root = controller.get().getWindow().getDecorView();
            EditText entry = findEntry(root);
            assertNotNull(entry);
            entry.setText("Quick untimed task");
            findText(root, "+  Add task").performClick();
            assertEquals(1, store.activePersonal().size());
            assertEquals(0, store.activePersonal().get(0).dueAt);
            assertNotNull(findText(root, "No time · Add time"));
        }
    }

    private static TextView findText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findText(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static EditText findEntry(View view) {
        if (view instanceof EditText) return (EditText) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                EditText found = findEntry(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test public void shiftsHaveNoPresetsAndUseUserHours() {
        assertTrue(shifts.templates().isEmpty());
        ShiftRepository.Template t = shifts.addTemplate("My shift", 23 * 60, 7 * 60 + 30);
        assertEquals(510, t.durationMinutes);
        shifts.addStep(t.id, "Whenever", -1, 0, 1, 0);
        ShiftRepository.Step step = shifts.steps(t.id).get(0);
        shifts.updateStepTime(step, 90);
        assertEquals(90, shifts.steps(t.id).get(0).firstMinute);
        shifts.updateTemplateTimes(t.id, 22 * 60, 6 * 60);
        assertEquals(22 * 60, shifts.findTemplate(t.id).startMinute);
        shifts.updateStepTime(step, -1);
        assertEquals(-1, shifts.steps(t.id).get(0).firstMinute);
        assertThrows(IllegalArgumentException.class, () -> shifts.addStep(t.id, "Repeat", -1, 60, 1, 0));
    }

    @Test public void untimedShiftTaskWaitsForShiftStartAndCanBeScheduledLater() {
        ShiftRepository.Template t = shifts.addTemplate("Custom", 600, 1080);
        shifts.addStep(t.id, "Whenever", -1, 0, 1, 0);
        long starts = System.currentTimeMillis() + 3_600_000;
        ShiftRepository.Run run = shifts.startShift(t.id, starts);
        TaskStore.Task task = shifts.runTasks(run.id).get(0);
        assertEquals(0, task.dueAt);
        assertEquals(starts, task.notificationAt());
        assertNull(store.claimDueReminder(task.id));
        TaskStore.Task timed = store.updateTiming(task.id, starts + 60_000, 0, 0);
        assertEquals(starts + 60_000, timed.dueAt);
        assertThrows(IllegalArgumentException.class, () -> store.updateTiming(task.id, run.endsAt, 0, 0));
        assertEquals(starts, store.updateTiming(task.id, 0, 0, -1).notificationAt());
        shifts.endShift(run.id);
        assertTrue(store.active().isEmpty());
    }

    @Test public void untimedTaskIsIncludedWhenStartingMidShift() {
        ShiftRepository.Template t = shifts.addTemplate("Custom", 600, 1080);
        shifts.addStep(t.id, "Whenever", -1, 0, 1, 0);
        ShiftRepository.Run run = shifts.startShift(t.id, System.currentTimeMillis() - 60_000);
        TaskStore.Task task = shifts.runTasks(run.id).get(0);
        assertEquals(0, task.dueAt);
        assertNotNull(store.claimDueReminder(task.id));
        assertNull(store.claimDueReminder(task.id));
        store.complete(task.id);
        assertTrue(store.active().isEmpty());
    }

    @Test public void repeatKeepsHistoryAndRecurrenceCreatesOnlyOneNextTask() {
        TaskStore.Task original = store.add("Reusable task", 2, 0, 0, -1);
        store.complete(original.id);
        TaskStore.Task copy = store.repeat(original.id, 0, 0, -1);
        assertNotEquals(original.id, copy.id);
        assertEquals(2, copy.priority);
        assertEquals(1, store.completedPersonalCount());
        long due = System.currentTimeMillis() + 3_600_000;
        TaskStore.Task repeating = store.updateTiming(copy.id, due, TaskStore.REPEAT_DAILY, 0);
        TaskStore.Completion completion = store.complete(repeating.id);
        assertNotNull(completion.nextTask);
        assertTrue(completion.nextTask.dueAt > due);
        assertNull(store.complete(repeating.id));
        assertEquals(1, store.activePersonal().size());
        assertThrows(IllegalArgumentException.class, () -> store.reopen(repeating.id));
        store.stopRepeating(completion.nextTask.id);
        assertNull(store.complete(completion.nextTask.id).nextTask);
        assertEquals(3, store.completedPersonalCount());
    }

    @Test public void undoRenamePriorityAndDeleteAffectOnlySelectedTask() {
        TaskStore.Task task = store.add("Original");
        store.complete(task.id);
        TaskStore.Task reopened = store.reopen(task.id);
        assertEquals(task.id, reopened.id);
        assertFalse(reopened.done);
        assertEquals(0, reopened.completedAt);
        assertEquals(0, reopened.dueAt);
        assertEquals("Renamed", store.rename(task.id, " Renamed ").title);
        assertEquals(2, store.changePriority(task.id, 2).priority);
        assertThrows(IllegalArgumentException.class, () -> store.rename(task.id, " "));
        TaskStore.Task other = store.add("Keep me");
        store.delete(task.id);
        assertNull(store.find(task.id));
        assertNotNull(store.find(other.id));
    }

    @Test public void historyCanLoadBeyondOriginalEightTasksAndKeepEndedShifts() {
        for (int i = 0; i < 25; i++) store.complete(store.add("Task " + i).id);
        assertEquals(20, store.completedPersonal(20).size());
        assertEquals(25, store.completedPersonal(40).size());
        ShiftRepository.Template template = shifts.addTemplate("Shift", 600, 1080);
        shifts.addStep(template.id, "Shift task", -1, 0, 1, 0);
        ShiftRepository.Run run = shifts.startShift(template.id, System.currentTimeMillis() - 60_000);
        TaskStore.Task shiftTask = shifts.runTasks(run.id).get(0);
        store.complete(shiftTask.id);
        shifts.endShift(run.id);
        assertEquals(1, store.completedShifts(20).size());
        assertEquals(0, store.repeat(shiftTask.id, 0, 0, -1).shiftRunId);
    }

    @Test public void completingInUiShowsDoneCardWithOverflowMenu() {
        store.add("Finish this");
        try (org.robolectric.android.controller.ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class).setup()) {
            View root = controller.get().getWindow().getDecorView();
            findText(root, "Done").performClick();
            assertNotNull(findText(root, "DONE  ·  1"));
            assertNotNull(findText(root, "✓  Finish this"));
            findText(root, "⋮").performClick();
            android.widget.PopupMenu popup = org.robolectric.shadows.ShadowPopupMenu.getLatestPopupMenu();
            assertEquals("Repeat now", popup.getMenu().getItem(1).getTitle());
            assertEquals("Repeat at a chosen time…", popup.getMenu().getItem(2).getTitle());
        }
    }
}

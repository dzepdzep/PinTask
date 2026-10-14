package com.pintask.app;

import android.content.Context;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
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
}

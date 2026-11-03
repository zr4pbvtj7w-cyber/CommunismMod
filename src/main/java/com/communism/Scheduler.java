package com.communism;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Простейший планировщик отложенных задач (в тиках). */
public final class Scheduler {
	private static final List<Task> TASKS = new ArrayList<>();
	private static final List<Task> PENDING = new ArrayList<>();

	private static final class Task {
		int left;
		final Runnable run;

		Task(int left, Runnable run) {
			this.left = left;
			this.run = run;
		}
	}

	public static void later(int ticks, Runnable r) {
		PENDING.add(new Task(Math.max(1, ticks), r));
	}

	/** Повторить действие count раз с интервалом every тиков. */
	public static void repeat(int count, int every, Runnable r) {
		for (int i = 0; i < count; i++) later(1 + i * every, r);
	}

	static void tick() {
		TASKS.addAll(PENDING);
		PENDING.clear();
		Iterator<Task> it = TASKS.iterator();
		List<Task> due = new ArrayList<>();
		while (it.hasNext()) {
			Task t = it.next();
			if (--t.left <= 0) {
				due.add(t);
				it.remove();
			}
		}
		for (Task t : due) {
			try {
				t.run.run();
			} catch (Exception e) {
				CommunismMod.LOG.error("Ошибка в отложенной задаче", e);
			}
		}
	}

	static void clear() {
		TASKS.clear();
		PENDING.clear();
	}
}

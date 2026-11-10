package com.ssafy.yeogirok.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * 테스트용 실행기. 평소에는 호출한 스레드에서 바로 실행하고, {@link #hold()} 뒤에는 작업을 쌓아 두었다가
 * {@link #runHeld()} 로 한꺼번에 실행한다(대기열에서 기다린 작업 흉내).
 */
public class HoldableExecutor implements Executor {

	private final List<Runnable> held = new ArrayList<>();
	private boolean holding;

	@Override
	public synchronized void execute(Runnable task) {
		if (holding) {
			held.add(task);
			return;
		}
		task.run();
	}

	public synchronized void hold() {
		holding = true;
	}

	/** 쌓인 작업을 실행하고 다시 바로 실행으로 돌아간다. */
	public void runHeld() {
		List<Runnable> tasks;
		synchronized (this) {
			tasks = new ArrayList<>(held);
			held.clear();
			holding = false;
		}
		tasks.forEach(Runnable::run);
	}

	public synchronized void reset() {
		held.clear();
		holding = false;
	}
}

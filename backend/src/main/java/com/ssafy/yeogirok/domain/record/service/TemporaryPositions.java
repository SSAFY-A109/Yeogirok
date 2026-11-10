package com.ssafy.yeogirok.domain.record.service;

import java.util.Collection;
import java.util.function.ObjIntConsumer;
import java.util.function.ToIntFunction;

/**
 * 순서(position)를 겹치지 않게 다시 매기기. {@code UNIQUE (.., position) DEFERRABLE INITIALLY IMMEDIATE}(Flyway V1)는
 * 문장마다 검사하므로, 자리가 바뀌는 행을 먼저 실제 순서와 겹치지 않는 임시 값으로 옮긴 뒤 최종 값으로 바꾼다
 * (방문 순서 {@link VisitLayout}, 방문 안 사진 순서 {@link VisitPhotoService} 공통).
 */
final class TemporaryPositions {

	/** 임시 position. CHECK (position >= 1) 를 지키면서 실제 순서와 겹치지 않는 값. */
	static final int TEMP_BASE = 1_000_000;

	private TemporaryPositions() {
	}

	/**
	 * @param changed       자리가 바뀌는 행
	 * @param place         행에 position 을 넣는다(사진은 방문 연결도 함께)
	 * @param finalPosition 행의 최종 position
	 * @param flush         지금까지의 변경을 DB 에 보낸다
	 */
	static <T> void apply(Collection<T> changed, ObjIntConsumer<T> place, ToIntFunction<T> finalPosition,
		Runnable flush) {
		if (changed.isEmpty()) {
			return;
		}
		int temp = TEMP_BASE;
		for (T row : changed) {
			place.accept(row, temp++);
		}
		flush.run();
		for (T row : changed) {
			place.accept(row, finalPosition.applyAsInt(row));
		}
		flush.run();
	}
}

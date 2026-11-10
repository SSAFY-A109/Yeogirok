package com.ssafy.yeogirok.domain.record.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

import com.ssafy.yeogirok.domain.record.entity.ActualVisit;

/**
 * 한 기록의 날짜별 방문 순서(명세 {@code specs/record-api.md} 5절 7). 메모리에서 끼워 넣고 뺀 뒤 {@link #apply} 로 position 을
 * 1부터 다시 매긴다.
 *
 * <p>자리가 바뀌는 방문은 {@link TemporaryPositions} 로 옮긴다(순서 유니크 DEFERRABLE). 새 방문은 그다음에 넣는다
 * (IDENTITY 라 저장 즉시 INSERT 되고, Hibernate 는 한 번의 flush 에서 INSERT 를 UPDATE 보다 먼저 보낸다).
 */
final class VisitLayout {

	private final Map<LocalDate, List<ActualVisit>> days = new TreeMap<>();
	private final Map<ActualVisit, Slot> before = new IdentityHashMap<>();

	/** @param visits 기록의 방문(날짜·순서대로) */
	VisitLayout(List<ActualVisit> visits) {
		for (ActualVisit visit : visits) {
			days.computeIfAbsent(visit.getVisitDate(), date -> new ArrayList<>()).add(visit);
			before.put(visit, new Slot(visit.getVisitDate(), visit.getPosition()));
		}
	}

	void remove(ActualVisit visit) {
		days.values().forEach(list -> list.remove(visit));
	}

	/**
	 * 그 날짜의 order 번째에 끼워 넣는다. 그 자리부터의 방문은 하나씩 뒤로 밀리고, 날짜의 방문 수보다 큰 order 와
	 * null 은 맨 뒤다. 날짜는 방문의 visitDate 를 쓴다(바꿀 때는 먼저 {@link ActualVisit#change} 를 부른다).
	 */
	void insert(ActualVisit visit, Integer order) {
		remove(visit);
		List<ActualVisit> list = days.computeIfAbsent(visit.getVisitDate(), date -> new ArrayList<>());
		list.add(order == null ? list.size() : Math.min(order - 1, list.size()), visit);
	}

	/**
	 * position 을 날짜마다 1부터 다시 매겨 저장한다.
	 * @param flush   지금까지의 변경을 DB 에 보낸다
	 * @param saveNew 아직 저장하지 않은 방문을 저장한다
	 * @return 기존 방문의 날짜나 순서가 하나라도 바뀌었으면 true
	 */
	boolean apply(Instant now, Runnable flush, Consumer<ActualVisit> saveNew) {
		Map<ActualVisit, Integer> moved = new IdentityHashMap<>();
		List<ActualVisit> created = new ArrayList<>();
		for (List<ActualVisit> list : days.values()) {
			for (int i = 0; i < list.size(); i++) {
				ActualVisit visit = list.get(i);
				Slot slot = before.get(visit);
				if (slot == null) {
					created.add(visit);
					visit.moveTo(i + 1, now);
				} else if (!slot.equals(new Slot(visit.getVisitDate(), i + 1))) {
					moved.put(visit, i + 1);
				}
			}
		}
		TemporaryPositions.apply(moved.keySet(), (visit, position) -> visit.moveTo(position, now), moved::get, flush);
		created.forEach(saveNew);
		return !moved.isEmpty();
	}

	private record Slot(LocalDate date, Integer position) {
	}
}

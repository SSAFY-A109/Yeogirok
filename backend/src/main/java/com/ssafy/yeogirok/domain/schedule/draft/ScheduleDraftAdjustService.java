package com.ssafy.yeogirok.domain.schedule.draft;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ssafy.yeogirok.domain.job.Job;
import com.ssafy.yeogirok.domain.job.JobProperties;
import com.ssafy.yeogirok.domain.job.JobStatus;
import com.ssafy.yeogirok.domain.job.service.JobRunner;
import com.ssafy.yeogirok.domain.room.entity.RoomPlace;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.domain.room.service.RoomFinder;
import com.ssafy.yeogirok.domain.schedule.ScheduleOptions.DayOption;
import com.ssafy.yeogirok.domain.schedule.ScheduleOptions.NamedPoint;
import com.ssafy.yeogirok.domain.schedule.dto.DraftAdjustCommand;
import com.ssafy.yeogirok.domain.schedule.dto.DraftJobResponse;
import com.ssafy.yeogirok.domain.schedule.service.ScheduleReader;
import com.ssafy.yeogirok.domain.schedule.travel.GeoPoint;
import com.ssafy.yeogirok.domain.schedule.travel.TravelLeg;
import com.ssafy.yeogirok.domain.schedule.travel.TravelTimeProvider;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.response.ApiDateTime;

/**
 * API-084 일정 초안 조정·재계산(선택). 사용자가 서버 재계산 미리보기를 원할 때만 부른다. 확정 일정은 바꾸지 않는다.
 *
 * <ul>
 *   <li>요청 확인과 새 배치(날짜·순서·체류 시간)는 바로 한다. 같은 날짜의 순서는 1부터 다시 매긴다. 보낸 항목과 그대로 둔
 *   항목의 순서가 겹치면 SC005.</li>
 *   <li>이동 시간·시각 계산은 비동기 작업으로 한다(202). 모든 일차를 다시 계산한다(같은 구간은 길찾기 캐시를 쓴다).</li>
 *   <li>종료 시각을 넘기는 날은 고정하지 않은 마지막 항목부터 미배치로 옮긴다. 고정 항목만으로도 알려진 시간으로 넘치면
 *   SC002 로 실패한다.</li>
 *   <li>성공하면 draftVersion 이 1 오르고, 실패하면 마지막 성공 초안 그대로다. 처리하는 동안 같은 초안의 재계산·최종 저장은
 *   SC003 이다(초안 잠금).</li>
 * </ul>
 */
@Service
public class ScheduleDraftAdjustService {

	private final RoomFinder roomFinder;
	private final ScheduleReader scheduleReader;
	private final ScheduleDraftStore draftStore;
	private final ScheduleDraftLock draftLock;
	private final JobRunner jobRunner;
	private final JobProperties jobProperties;
	private final TravelTimeProvider travelTimeProvider;
	private final TransactionTemplate readOnly;
	private final Clock clock;

	public ScheduleDraftAdjustService(RoomFinder roomFinder, ScheduleReader scheduleReader,
		ScheduleDraftStore draftStore, ScheduleDraftLock draftLock, JobRunner jobRunner, JobProperties jobProperties,
		TravelTimeProvider travelTimeProvider, PlatformTransactionManager transactionManager, Clock clock) {
		this.roomFinder = roomFinder;
		this.scheduleReader = scheduleReader;
		this.draftStore = draftStore;
		this.draftLock = draftLock;
		this.jobRunner = jobRunner;
		this.jobProperties = jobProperties;
		this.travelTimeProvider = travelTimeProvider;
		this.readOnly = new TransactionTemplate(transactionManager);
		this.readOnly.setReadOnly(true);
		this.clock = clock;
	}

	/** 트랜잭션 밖에서 부른다. */
	public DraftJobResponse adjust(Long userId, Long roomId, String draftKey, DraftAdjustCommand command) {
		String token = draftLock.acquire(draftKey);
		boolean submitted = false;
		try {
			Adjustment adjustment = readOnly.execute(status -> prepare(userId, roomId, draftKey, command));
			String jobId = JobRunner.newId();
			draftStore.save(adjustment.draft().recalculating(jobId, clock.instant(), jobProperties.maxLifetime()));
			Job job = jobRunner.submit(jobId, ScheduleDraftJobPolicy.TYPE, userId, roomId.toString(),
				() -> recalculate(draftKey, token, adjustment),
				() -> expire(draftKey, token));
			submitted = job.status() != JobStatus.FAILED;
			if (!submitted) {
				draftStore.find(draftKey)
					.ifPresent(draft -> draftStore.save(draft.recalculationFailed(clock.instant())));
			}
			return DraftJobResponse.queued(jobId, draftKey);
		} finally {
			if (!submitted) {
				draftLock.release(draftKey, token);
			}
		}
	}

	/** 같은 멱등키로 다시 보낸 요청의 응답. */
	public DraftJobResponse adjusted(Long roomId, String draftKey) {
		ScheduleDraft draft = draftStore.get(roomId, draftKey);
		return DraftJobResponse.queued(draft.jobId(), draftKey);
	}

	/* ── 요청 확인·새 배치 ─────────────────────────────────── */

	private Adjustment prepare(Long userId, Long roomId, String draftKey, DraftAdjustCommand command) {
		TravelRoom room = roomFinder.getForMember(roomId, userId).room();
		ScheduleDraft draft = draftStore.get(roomId, draftKey);
		if (draft.status() == JobStatus.RUNNING) {
			throw new BusinessException(ExceptionType.SCHEDULE_DRAFT_IN_PROGRESS);
		}
		if (draft.status() == JobStatus.FAILED) {
			throw new BusinessException(ExceptionType.SCHEDULE_DRAFT_FAILED);
		}
		if (draft.draftVersion() != command.draftVersion() || draft.baseVersion() != command.baseVersion()) {
			throw new BusinessException(ExceptionType.VERSION_MISMATCH, "초안 버전이 다릅니다. 초안을 다시 조회해 주세요.");
		}
		DraftRules.checkVersion(room, command.baseVersion());
		List<DayOption> days = command.days() == null ? draft.days() : DraftRules.checkDays(room, command.days());
		NamedPoint startPoint = command.startPoint() == null ? draft.startPoint() : command.startPoint().point();
		NamedPoint endPoint = command.endPoint() == null ? draft.endPoint() : command.endPoint().point();
		List<DraftItem> items = rearrange(draft.items(), command, days);

		Map<Long, RoomPlace> places = scheduleReader.places(roomId);
		Map<Integer, GeoPoint> locations = new HashMap<>();
		for (DraftItem item : items) {
			RoomPlace place = places.get(item.roomPlaceId());
			if (place == null) {
				throw DraftRules.notAllowed("itemId " + item.itemId() + ": 이 여행방의 후보가 아닙니다.");
			}
			locations.put(item.itemId(), ScheduleReader.location(place));
		}
		return new Adjustment(draft, room.getTransportMode(), days, startPoint, endPoint, items, locations);
	}

	/** 요청을 반영한 새 배치. 날짜마다 순서를 1부터 다시 매긴다. itemId 순서는 그대로다. */
	private static List<DraftItem> rearrange(List<DraftItem> original, DraftAdjustCommand command,
		List<DayOption> days) {
		Map<Integer, DraftItem> byId = new HashMap<>();
		original.forEach(item -> byId.put(item.itemId(), item));
		Set<LocalDate> dates = new HashSet<>();
		days.forEach(day -> dates.add(day.date()));

		Set<Integer> removed = new HashSet<>();
		for (Integer itemId : command.removeItemIds()) {
			if (!byId.containsKey(itemId)) {
				throw DraftRules.notAllowed("removeItemIds: 이 초안의 항목이 아닙니다(" + itemId + ").");
			}
			removed.add(itemId);
		}
		Map<Integer, DraftAdjustCommand.Item> edits = new HashMap<>();
		for (DraftAdjustCommand.Item edit : command.items()) {
			String path = "items[itemId=" + edit.itemId() + "]";
			DraftItem item = byId.get(edit.itemId());
			if (item == null) {
				throw DraftRules.notAllowed(path + ": 이 초안의 항목이 아닙니다.");
			}
			if (!item.roomPlaceId().equals(edit.roomPlaceId())) {
				throw DraftRules.notAllowed(path + ": 항목의 후보를 바꿀 수 없습니다.");
			}
			if (removed.contains(edit.itemId()) || edits.put(edit.itemId(), edit) != null) {
				throw DraftRules.notAllowed(path + ": 같은 항목을 두 번 보냈습니다.");
			}
			if (!dates.contains(edit.visitDate())) {
				throw DraftRules.dateRange(path + ".visitDate: 여행 날짜 밖입니다.");
			}
			if (edit.order() < 1) {
				throw DraftRules.notAllowed(path + ".order: 1 이상이어야 합니다.");
			}
			DraftRules.checkStay(edit.stayMinutes(), path + ".stayMinutes");
		}

		// 날짜별로 (요청한 순서 또는 지금 순서) 를 모은다. 겹치면 SC005.
		Map<LocalDate, Map<Integer, DraftItem>> byDate = new HashMap<>();
		List<DraftItem> unassigned = new ArrayList<>();
		for (DraftItem item : original) {
			DraftAdjustCommand.Item edit = edits.get(item.itemId());
			DraftItem next;
			if (removed.contains(item.itemId())) {
				next = DraftItem.unassigned(item.itemId(), item.roomPlaceId(), item.stayMinutes(), item.fixed());
			} else if (edit != null) {
				next = new DraftItem(item.itemId(), item.roomPlaceId(), edit.visitDate(), edit.order(),
					edit.stayMinutes(), edit.fixed() == null ? item.fixed() : edit.fixed(), null, null, null);
			} else if (item.placed() && !dates.contains(item.visitDate())) {
				// 일차 조건에서 빠진 날짜의 항목은 미배치로 둔다.
				next = DraftItem.unassigned(item.itemId(), item.roomPlaceId(), item.stayMinutes(), item.fixed());
			} else {
				next = item;
			}
			if (!next.placed()) {
				unassigned.add(next);
				continue;
			}
			Map<Integer, DraftItem> orders = byDate.computeIfAbsent(next.visitDate(), date -> new HashMap<>());
			if (orders.put(next.order(), next) != null) {
				throw new BusinessException(ExceptionType.SCHEDULE_ORDER_DUPLICATED,
					"%s 의 방문 순서 %d 가 중복됩니다.".formatted(next.visitDate(), next.order()));
			}
		}

		Map<Integer, DraftItem> result = new HashMap<>();
		unassigned.forEach(item -> result.put(item.itemId(), item));
		byDate.forEach((date, orders) -> {
			List<DraftItem> sorted = orders.values().stream().sorted(Comparator.comparing(DraftItem::order)).toList();
			for (int i = 0; i < sorted.size(); i++) {
				DraftItem item = sorted.get(i);
				result.put(item.itemId(), new DraftItem(item.itemId(), item.roomPlaceId(), date, i + 1,
					item.stayMinutes(), item.fixed(), null, null, null));
			}
		});
		return original.stream().map(item -> result.get(item.itemId())).toList();
	}

	/* ── 작업 스레드 ─────────────────────────────────────────── */

	/** 대기 제한 시간이 지나 계산하지 않는다. 마지막 성공 초안으로 돌리고 잠금을 푼다. */
	private void expire(String draftKey, String token) {
		try {
			draftStore.find(draftKey).ifPresent(draft -> draftStore.save(draft.recalculationFailed(clock.instant())));
		} finally {
			draftLock.release(draftKey, token);
		}
	}

	private Map<String, Object> recalculate(String draftKey, String token, Adjustment adjustment) {
		try {
			draftStore.find(draftKey)
				.ifPresent(draft -> draftStore.save(draft.started(clock.instant(), jobProperties.runTimeout())));
			List<DraftItem> items = adjustment.items();
			DraftTimings.Result timings;
			while (true) {
				timings = DraftTimings.calculate(items, adjustment.days(), adjustment.startPoint(),
					adjustment.endPoint(), adjustment.mode(), adjustment.locations(), travelTimeProvider);
				List<DraftItem> trimmed = trimOverflow(timings, adjustment.days());
				if (trimmed == null) {
					break;
				}
				items = trimmed;
			}
			ScheduleDraft draft = draftStore.find(draftKey)
				.orElseThrow(() -> new IllegalStateException("계산하는 사이 초안이 사라졌습니다: " + draftKey));
			draftStore.save(draft.computed(adjustment.days(), adjustment.startPoint(), adjustment.endPoint(),
				timings.items(), timings.endLeg(), clock.instant()));
			return Map.of("type", ScheduleDraftJobPolicy.TYPE, "draftKey", draftKey);
		} catch (RuntimeException e) {
			draftStore.find(draftKey).ifPresent(draft -> draftStore.save(draft.recalculationFailed(clock.instant())));
			throw e;
		} finally {
			draftLock.release(draftKey, token);
		}
	}

	/**
	 * 알려진 시각으로 종료 시각을 넘기는 첫 날에서 고정하지 않은 마지막 항목을 미배치로 옮긴 새 배치. 넘기는 날이 없으면 null.
	 * 넘기는 날에 고정 항목만 남았으면 SC002.
	 */
	private static List<DraftItem> trimOverflow(DraftTimings.Result timings, List<DayOption> days) {
		LocalDate lastDay = days.getLast().date();
		for (DayOption day : days) {
			List<DraftItem> dayItems = timings.items().stream()
				.filter(item -> day.date().equals(item.visitDate()))
				.sorted(Comparator.comparing(DraftItem::order))
				.toList();
			if (dayItems.isEmpty() || dayItems.getLast().departureAt() == null) {
				continue;
			}
			Instant finish = dayItems.getLast().departureAt();
			TravelLeg endLeg = timings.endLeg();
			if (day.date().equals(lastDay) && endLeg != null && endLeg.isKnown()) {
				finish = finish.plusSeconds(endLeg.travelMinutes() * 60L);
			}
			if (!finish.isAfter(ZonedDateTime.of(day.date(), day.endTime(), ApiDateTime.ZONE).toInstant())) {
				continue;
			}
			DraftItem drop = null;
			for (DraftItem item : dayItems) {
				if (!item.fixed()) {
					drop = item;
				}
			}
			if (drop == null) {
				throw new BusinessException(ExceptionType.SCHEDULE_UNSATISFIABLE,
					"%s: 순서를 고정한 항목만으로도 종료 시각을 넘깁니다.".formatted(day.date()));
			}
			int dropOrder = drop.order();
			int dropId = drop.itemId();
			return timings.items().stream().map(item -> {
				if (item.itemId() == dropId) {
					return DraftItem.unassigned(item.itemId(), item.roomPlaceId(), item.stayMinutes(), item.fixed());
				}
				if (day.date().equals(item.visitDate()) && item.order() > dropOrder) {
					return new DraftItem(item.itemId(), item.roomPlaceId(), item.visitDate(), item.order() - 1,
						item.stayMinutes(), item.fixed(), null, null, null);
				}
				return item;
			}).toList();
		}
		return null;
	}

	/** 작업 스레드로 넘기는 계산 입력. 엔티티를 담지 않는다. */
	private record Adjustment(ScheduleDraft draft, TransportMode mode, List<DayOption> days, NamedPoint startPoint,
		NamedPoint endPoint, List<DraftItem> items, Map<Integer, GeoPoint> locations) {
	}
}

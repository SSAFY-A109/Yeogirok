package com.ssafy.yeogirok.domain.schedule.draft;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ssafy.yeogirok.domain.category.TravelCategory;
import com.ssafy.yeogirok.domain.job.JobError;
import com.ssafy.yeogirok.domain.job.JobProperties;
import com.ssafy.yeogirok.domain.job.service.JobRunner;
import com.ssafy.yeogirok.domain.room.entity.RoomPlace;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.domain.room.service.RoomFinder;
import com.ssafy.yeogirok.domain.schedule.ScheduleOptions.DayOption;
import com.ssafy.yeogirok.domain.schedule.ScheduleOptions.NamedPoint;
import com.ssafy.yeogirok.domain.schedule.dto.DraftCreateCommand;
import com.ssafy.yeogirok.domain.schedule.dto.DraftCreateCommand.FixedItem;
import com.ssafy.yeogirok.domain.schedule.dto.DraftJobResponse;
import com.ssafy.yeogirok.domain.schedule.service.ScheduleReader;
import com.ssafy.yeogirok.domain.schedule.travel.GeoPoint;
import com.ssafy.yeogirok.domain.schedule.travel.TravelTimeProvider;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.extern.slf4j.Slf4j;

/**
 * API-037 일정 초안 생성. 요청을 확인한 뒤 초안(계산 전)을 저장하고 비동기 작업으로 배치·시각을 계산한다. 초안은 확정
 * 일정을 바꾸지 않는다.
 *
 * <ul>
 *   <li>항목: fixedItems 를 요청 순서대로 먼저 만들고(itemId 1부터), 그 밖의 후보는 roomPlaceIds 순서로 하나씩 만든다.
 *   fixedItems 에 있는 후보는 따로 더 만들지 않는다.</li>
 *   <li>fixed=true 인 이어받은 항목은 그 날짜·순서 자리에 두고, fixed=false 는 그 날짜에 두되 순서는 다시 정한다.</li>
 *   <li>체류 시간: fixedItems 는 그 값, 나머지는 stayMinutes 맵 값, 없으면 60분.</li>
 * </ul>
 */
@Slf4j
@Service
public class ScheduleDraftService {

	/** 새로 배치하는 항목의 기본 체류 시간(분, 팀 결정). */
	public static final int DEFAULT_STAY_MINUTES = 60;
	/** 초안을 만들 수 있는 최소 후보 수(명세 W13). */
	public static final int MIN_PLACES = 3;

	private final RoomFinder roomFinder;
	private final ScheduleReader scheduleReader;
	private final ScheduleDraftStore draftStore;
	private final JobRunner jobRunner;
	private final JobProperties jobProperties;
	private final TravelTimeProvider travelTimeProvider;
	private final TransactionTemplate readOnly;
	private final Clock clock;

	public ScheduleDraftService(RoomFinder roomFinder, ScheduleReader scheduleReader, ScheduleDraftStore draftStore,
		JobRunner jobRunner, JobProperties jobProperties, TravelTimeProvider travelTimeProvider,
		PlatformTransactionManager transactionManager, Clock clock) {
		this.roomFinder = roomFinder;
		this.scheduleReader = scheduleReader;
		this.draftStore = draftStore;
		this.jobRunner = jobRunner;
		this.jobProperties = jobProperties;
		this.travelTimeProvider = travelTimeProvider;
		this.readOnly = new TransactionTemplate(transactionManager);
		this.readOnly.setReadOnly(true);
		this.clock = clock;
	}

	/** 트랜잭션 밖에서 부른다(작업 스레드가 초안을 바로 읽을 수 있어야 한다). */
	public DraftJobResponse create(Long userId, Long roomId, DraftCreateCommand command) {
		Generation generation = readOnly.execute(status -> prepare(userId, roomId, command));
		String draftKey = JobRunner.newId();
		String jobId = JobRunner.newId();
		draftStore.save(ScheduleDraft.start(draftKey, jobId, roomId, userId, generation.baseVersion(),
			generation.mode(), generation.days(), generation.startPoint(), generation.endPoint(), generation.items(),
			clock.instant(), jobProperties.runTimeout()));
		jobRunner.submit(jobId, ScheduleDraftJobPolicy.TYPE, userId, roomId.toString(),
			() -> generate(draftKey, generation));
		return DraftJobResponse.queued(jobId, draftKey);
	}

	/** 같은 멱등키로 다시 보낸 요청의 응답. */
	public DraftJobResponse created(String draftKey) {
		ScheduleDraft draft = draftStore.find(draftKey)
			.orElseThrow(() -> new BusinessException(ExceptionType.SCHEDULE_DRAFT_NOT_FOUND));
		return DraftJobResponse.queued(draft.jobId(), draft.draftKey());
	}

	/* ── 요청 확인 ─────────────────────────────────────────── */

	private Generation prepare(Long userId, Long roomId, DraftCreateCommand command) {
		TravelRoom room = roomFinder.getForMember(roomId, userId).room();
		DraftRules.checkVersion(room, command.baseVersion());
		DraftRules.requirePlannable(room);
		List<DayOption> days = DraftRules.checkDays(room, command.days());

		Map<Long, RoomPlace> current = new LinkedHashMap<>();
		scheduleReader.places(roomId).values().stream()
			.filter(place -> !place.isRemoved())
			.forEach(place -> current.put(place.getId(), place));
		Set<Long> requested = new LinkedHashSet<>();
		for (Long id : command.roomPlaceIds()) {
			if (!current.containsKey(id)) {
				throw DraftRules.notAllowed("roomPlaceIds: 이 여행방의 현재 후보가 아닙니다(" + id + ").");
			}
			if (!requested.add(id)) {
				throw DraftRules.notAllowed("roomPlaceIds: 같은 후보가 두 번 있습니다(" + id + ").");
			}
		}
		if (!requested.equals(current.keySet())) {
			throw DraftRules.notAllowed("roomPlaceIds: 현재 담은 후보 전부를 보내야 합니다.");
		}
		if (requested.size() < MIN_PLACES) {
			throw DraftRules.notAllowed("roomPlaceIds: 후보가 %d곳 이상이어야 합니다.".formatted(MIN_PLACES));
		}
		command.stayMinutes().forEach((id, minutes) -> {
			if (!requested.contains(id)) {
				throw DraftRules.notAllowed("stayMinutes: roomPlaceIds 에 없는 후보입니다(" + id + ").");
			}
			DraftRules.checkStay(minutes, "stayMinutes." + id);
		});
		checkFixedItems(command.fixedItems(), requested, days);

		List<DraftItem> items = new ArrayList<>();
		List<DraftPlanner.Candidate> candidates = new ArrayList<>();
		Map<Integer, GeoPoint> locations = new HashMap<>();
		Set<Long> inherited = new HashSet<>();
		for (FixedItem fixed : command.fixedItems()) {
			int itemId = items.size() + 1;
			RoomPlace place = current.get(fixed.roomPlaceId());
			items.add(DraftItem.unassigned(itemId, fixed.roomPlaceId(), fixed.stayMinutes(), fixed.fixed()));
			candidates.add(new DraftPlanner.Candidate(itemId, ScheduleReader.location(place), isFood(place),
				fixed.stayMinutes(), fixed.visitDate(), fixed.fixed() ? fixed.order() : null));
			locations.put(itemId, ScheduleReader.location(place));
			inherited.add(fixed.roomPlaceId());
		}
		for (Long id : requested) {
			if (inherited.contains(id)) {
				continue;
			}
			int itemId = items.size() + 1;
			RoomPlace place = current.get(id);
			int stay = command.stayMinutes().getOrDefault(id, DEFAULT_STAY_MINUTES);
			items.add(DraftItem.unassigned(itemId, id, stay, false));
			candidates.add(new DraftPlanner.Candidate(itemId, ScheduleReader.location(place), isFood(place), stay,
				null, null));
			locations.put(itemId, ScheduleReader.location(place));
		}
		return new Generation(room.getVersion(), room.getTransportMode(), days, command.startPoint(),
			command.endPoint(), items, candidates, locations);
	}

	private static void checkFixedItems(List<FixedItem> fixedItems, Set<Long> requested, List<DayOption> days) {
		Set<LocalDate> dates = new HashSet<>();
		days.forEach(day -> dates.add(day.date()));
		Set<String> slots = new HashSet<>();
		for (int i = 0; i < fixedItems.size(); i++) {
			FixedItem item = fixedItems.get(i);
			String path = "fixedItems[" + i + "]";
			if (!requested.contains(item.roomPlaceId())) {
				throw DraftRules.notAllowed(path + ".roomPlaceId: roomPlaceIds 에 없는 후보입니다.");
			}
			if (!dates.contains(item.visitDate())) {
				throw DraftRules.dateRange(path + ".visitDate: 여행 날짜 밖입니다.");
			}
			if (item.order() < 1) {
				throw DraftRules.notAllowed(path + ".order: 1 이상이어야 합니다.");
			}
			DraftRules.checkStay(item.stayMinutes(), path + ".stayMinutes");
			if (item.fixed() && !slots.add(item.visitDate() + "#" + item.order())) {
				throw DraftRules.notAllowed(path + ": 같은 날짜·순서에 고정한 항목이 둘 있습니다.");
			}
		}
	}

	private static boolean isFood(RoomPlace place) {
		return TravelCategory.FOOD.name().equals(place.getPlace().getCategoryCode());
	}

	/* ── 작업 스레드 ─────────────────────────────────────────── */

	private Map<String, Object> generate(String draftKey, Generation generation) {
		try {
			DraftPlanner.Output output = DraftPlanner.plan(new DraftPlanner.Input(generation.days(),
				point(generation.startPoint()), point(generation.endPoint()), generation.candidates(),
				generation.mode()), travelTimeProvider);
			List<DraftItem> placed = place(generation.items(), output);
			DraftTimings.Result timings = DraftTimings.calculate(placed, generation.days(), generation.startPoint(),
				generation.endPoint(), generation.mode(), generation.locations(), travelTimeProvider);
			ScheduleDraft draft = current(draftKey);
			draftStore.save(draft.computed(draft.days(), draft.startPoint(), draft.endPoint(), timings.items(),
				timings.endLeg(), clock.instant()));
			return Map.of("type", ScheduleDraftJobPolicy.TYPE, "draftKey", draftKey);
		} catch (RuntimeException e) {
			JobError error = e instanceof BusinessException business
				? new JobError(business.getExceptionType().getCode(), business.getMessage())
				: new JobError(ExceptionType.SERVER_ERROR.getCode(), ExceptionType.SERVER_ERROR.getMessage());
			draftStore.find(draftKey).ifPresent(draft -> draftStore.save(draft.failed(error, clock.instant())));
			throw e;
		}
	}

	/** 배치 결과를 항목에 넣는다. itemId 순서는 그대로다. */
	private static List<DraftItem> place(List<DraftItem> items, DraftPlanner.Output output) {
		Map<Integer, LocalDate> dates = new HashMap<>();
		Map<Integer, Integer> orders = new HashMap<>();
		output.orders().forEach((date, ids) -> {
			for (int i = 0; i < ids.size(); i++) {
				dates.put(ids.get(i), date);
				orders.put(ids.get(i), i + 1);
			}
		});
		return items.stream()
			.map(item -> dates.containsKey(item.itemId())
				? new DraftItem(item.itemId(), item.roomPlaceId(), dates.get(item.itemId()), orders.get(item.itemId()),
				item.stayMinutes(), item.fixed(), null, null, null)
				: DraftItem.unassigned(item.itemId(), item.roomPlaceId(), item.stayMinutes(), item.fixed()))
			.toList();
	}

	private ScheduleDraft current(String draftKey) {
		return draftStore.find(draftKey)
			.orElseThrow(() -> new IllegalStateException("계산하는 사이 초안이 사라졌습니다: " + draftKey));
	}

	private static GeoPoint point(NamedPoint point) {
		return point == null ? null : point.toGeoPoint();
	}

	/** 작업 스레드로 넘기는 계산 입력. 엔티티를 담지 않는다. */
	private record Generation(int baseVersion, TransportMode mode, List<DayOption> days, NamedPoint startPoint,
		NamedPoint endPoint, List<DraftItem> items, List<DraftPlanner.Candidate> candidates,
		Map<Integer, GeoPoint> locations) {
	}
}

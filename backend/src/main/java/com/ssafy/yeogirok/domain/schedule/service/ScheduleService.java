package com.ssafy.yeogirok.domain.schedule.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.ssafy.yeogirok.domain.room.entity.RoomPlace;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.domain.room.repository.RoomPlaceRepository;
import com.ssafy.yeogirok.domain.room.service.RoomFinder;
import com.ssafy.yeogirok.domain.room.service.RoomVersions;
import com.ssafy.yeogirok.domain.schedule.ScheduleOptions;
import com.ssafy.yeogirok.domain.schedule.dto.ScheduleResponse;
import com.ssafy.yeogirok.domain.schedule.dto.ScheduleUpdateCommand;
import com.ssafy.yeogirok.domain.schedule.dto.ScheduleUpdateCommand.ItemEdit;
import com.ssafy.yeogirok.domain.schedule.dto.ScheduleUpdateResponse;
import com.ssafy.yeogirok.domain.schedule.entity.ScheduleItem;
import com.ssafy.yeogirok.domain.schedule.repository.ScheduleItemRepository;
import com.ssafy.yeogirok.domain.schedule.service.ScheduleCalculator.PlannedItem;
import com.ssafy.yeogirok.domain.schedule.service.ScheduleWriter.Placement;
import com.ssafy.yeogirok.domain.schedule.travel.GeoPoint;
import com.ssafy.yeogirok.domain.schedule.travel.TravelLeg;
import com.ssafy.yeogirok.domain.schedule.travel.TravelTimeProvider;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.response.ApiDateTime;

import jakarta.persistence.EntityManager;

/**
 * 확정 일정 조회·수동 편집(API-036·040). 참여자 권한은 모두 같아 현재 참여자면 편집할 수 있다(비참여자 RO002).
 *
 * <p>편집 순서: 요청 검사와 편집 결과 만들기(읽기 트랜잭션) → 바뀐 날짜만 이동 시간·시각 계산(외부 호출이라
 * 트랜잭션·잠금 밖) → 방 행을 잠그고 version 을 다시 확인한 뒤 저장. 그사이 방이나 후보·일정이 바뀌었으면 version 이
 * 달라져 C004 로 끝나므로 계산 결과를 그대로 저장해도 된다.
 */
@Service
public class ScheduleService {

	private final ScheduleItemRepository scheduleItemRepository;
	private final RoomPlaceRepository roomPlaceRepository;
	private final RoomFinder roomFinder;
	private final RoomVersions roomVersions;
	private final ScheduleWriter scheduleWriter;
	private final TravelTimeProvider travelTimeProvider;
	private final TransactionTemplate transactionTemplate;
	private final TransactionTemplate readOnlyTransactionTemplate;
	private final EntityManager entityManager;
	private final Clock clock;

	public ScheduleService(ScheduleItemRepository scheduleItemRepository, RoomPlaceRepository roomPlaceRepository,
		RoomFinder roomFinder, RoomVersions roomVersions, ScheduleWriter scheduleWriter,
		TravelTimeProvider travelTimeProvider, PlatformTransactionManager transactionManager,
		EntityManager entityManager, Clock clock) {
		this.entityManager = entityManager;
		this.scheduleItemRepository = scheduleItemRepository;
		this.roomPlaceRepository = roomPlaceRepository;
		this.roomFinder = roomFinder;
		this.roomVersions = roomVersions;
		this.scheduleWriter = scheduleWriter;
		this.travelTimeProvider = travelTimeProvider;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.readOnlyTransactionTemplate = new TransactionTemplate(transactionManager);
		this.readOnlyTransactionTemplate.setReadOnly(true);
		this.clock = clock;
	}

	/** API-036. 일정이 없으면 schedule 은 null. */
	@Transactional(readOnly = true)
	public ScheduleResponse getSchedule(Long userId, Long roomId) {
		TravelRoom room = roomFinder.getForMember(roomId, userId).room();
		return new ScheduleResponse(ScheduleViews.of(room, items(roomId)), room.getVersion());
	}

	/**
	 * API-040. 보내지 않은 기존 항목은 그대로 둔다. 바뀐 날짜(옮기기 전·후 날짜, 새 항목·지운 항목의 날짜)는 방문 순서를
	 * 1부터 다시 매기고 이동 시간·시각을 다시 계산한다. 바꿀 것이 없는 요청(items·removeItemIds 모두 빈 배열)은 version 을
	 * 올리지 않고 지금 일정을 돌려준다.
	 */
	public ScheduleUpdateResponse update(Long userId, Long roomId, ScheduleUpdateCommand command) {
		EditPlan plan = readOnlyTransactionTemplate.execute(status -> plan(userId, roomId, command));
		ScheduleCalculator.Result result = ScheduleCalculator.calculate(plan.plannedItems(), plan.options(),
			plan.fallbackStarts(), plan.transportMode(), travelTimeProvider);
		return transactionTemplate.execute(status -> apply(userId, roomId, command, plan, result));
	}

	private EditPlan plan(Long userId, Long roomId, ScheduleUpdateCommand command) {
		TravelRoom room = roomFinder.getForMember(roomId, userId).room();
		checkVersion(room, command.version());
		Map<Long, ScheduleItem> existing = items(roomId).stream()
			.collect(Collectors.toMap(ScheduleItem::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
		Map<Long, RoomPlace> places = roomPlaceRepository.findByRoomIdOrderByIdAsc(roomId).stream()
			.collect(Collectors.toMap(RoomPlace::getId, Function.identity()));

		Set<Long> removeIds = new HashSet<>();
		for (Long id : command.removeItemIds()) {
			if (!existing.containsKey(id)) {
				throw notAllowed("removeItemIds: 이 여행방의 일정 항목이 아닙니다(" + id + ").");
			}
			removeIds.add(id);
		}

		Map<Long, ItemEdit> editsById = new HashMap<>();
		List<ItemEdit> newItems = new ArrayList<>();
		Set<LocalDate> changedDates = new TreeSet<>();
		removeIds.forEach(id -> changedDates.add(existing.get(id).getVisitDate()));
		for (ItemEdit edit : command.items()) {
			validateEdit(room, edit);
			if (edit.id() == null) {
				checkNewPlace(places.get(edit.roomPlaceId()));
				newItems.add(edit);
			} else {
				ScheduleItem item = existing.get(edit.id());
				if (item == null || removeIds.contains(edit.id())) {
					throw notAllowed("items[].id: 이 여행방의 일정 항목이 아니거나 지울 항목입니다(" + edit.id() + ").");
				}
				if (editsById.put(edit.id(), edit) != null) {
					throw notAllowed("items[].id: 같은 항목을 두 번 보냈습니다(" + edit.id() + ").");
				}
				// 뺀 후보를 가리키는 기존 항목은 그대로 둘 수 있다. 다른 후보로 바꿀 때만 지금 후보인지 본다.
				if (!edit.roomPlaceId().equals(item.getRoomPlaceId())) {
					checkNewPlace(places.get(edit.roomPlaceId()));
				}
				changedDates.add(item.getVisitDate());
			}
			changedDates.add(edit.visitDate());
		}

		// 바뀐 날짜에 남을 항목 전체(그대로 둘 기존 항목 + 바꾼 항목 + 새 항목)를 순서대로 놓는다.
		List<Entry> entries = new ArrayList<>();
		for (ScheduleItem item : existing.values()) {
			if (removeIds.contains(item.getId())) {
				continue;
			}
			ItemEdit edit = editsById.get(item.getId());
			if (edit != null) {
				entries.add(new Entry(item, edit.roomPlaceId(), edit.visitDate(), edit.order(), edit.stayMinutes(),
					edit.fixed() == null ? item.isFixed() : edit.fixed(), edit));
			} else if (changedDates.contains(item.getVisitDate())) {
				entries.add(new Entry(item, item.getRoomPlaceId(), item.getVisitDate(), item.getPosition(),
					item.getStayMinutes(), item.isFixed(), null));
			}
		}
		for (ItemEdit edit : newItems) {
			entries.add(new Entry(null, edit.roomPlaceId(), edit.visitDate(), edit.order(), edit.stayMinutes(),
				edit.fixed() != null && edit.fixed(), edit));
		}
		entries.sort(Comparator.comparing(Entry::visitDate).thenComparingInt(Entry::order));
		for (int i = 1; i < entries.size(); i++) {
			Entry before = entries.get(i - 1);
			Entry current = entries.get(i);
			if (before.visitDate().equals(current.visitDate()) && before.order() == current.order()) {
				throw new BusinessException(ExceptionType.SCHEDULE_ORDER_DUPLICATED,
					"%s 의 방문 순서 %d 가 중복됩니다.".formatted(current.visitDate(), current.order()));
			}
		}

		List<PlannedItem> planned = entries.stream().map(entry -> {
			RoomPlace place = places.get(entry.roomPlaceId());
			return new PlannedItem(entry.visitDate(), entry.order(), entry.stayMinutes(),
				entry.edit() == null ? null : entry.edit().arrivalAt(),
				new GeoPoint(place.getPlace().getLatitude(), place.getPlace().getLongitude()));
		}).toList();
		return new EditPlan(room.getTransportMode(), ScheduleOptions.fromMap(room.getScheduleOptions()),
			ScheduleCalculator.firstArrivals(List.copyOf(existing.values())), Set.copyOf(changedDates), entries,
			planned);
	}

	private ScheduleUpdateResponse apply(Long userId, Long roomId, ScheduleUpdateCommand command, EditPlan plan,
		ScheduleCalculator.Result result) {
		TravelRoom room = roomFinder.getForMemberForUpdate(roomId, userId).room();
		checkVersion(room, command.version());
		if (command.items().isEmpty() && command.removeItemIds().isEmpty()) {
			return response(room);
		}

		// version 이 같으면 계획을 만든 뒤로 일정이 바뀌지 않았다. 같은 항목 행을 다시 읽어 고친다.
		Map<Long, ScheduleItem> current = items(roomId).stream()
			.collect(Collectors.toMap(ScheduleItem::getId, Function.identity()));
		List<ScheduleItem> toDelete = command.removeItemIds().stream().distinct().map(current::get).toList();

		List<Placement> placements = new ArrayList<>();
		Map<LocalDate, Integer> positions = new HashMap<>();
		for (int i = 0; i < plan.entries().size(); i++) {
			Entry entry = plan.entries().get(i);
			int position = positions.merge(entry.visitDate(), 1, Integer::sum);
			placements.add(new Placement(entry.existing() == null ? null : current.get(entry.existing().getId()),
				entry.roomPlaceId(), entry.visitDate(), position, entry.stayMinutes(), entry.fixed(),
				result.timings().get(i)));
		}

		// 바뀐 날짜의 도착 지점 구간을 새 계산값으로 바꾼다. 그날 항목이 없어졌으면 구간도 없다.
		Map<LocalDate, TravelLeg> endLegs = new HashMap<>();
		plan.changedDates().forEach(date -> endLegs.put(date, result.endLegs().get(date)));
		Map<String, Object> newOptions = plan.options().withEndLegs(endLegs).toMap();

		scheduleWriter.write(roomId, toDelete, placements);
		if (newOptions.equals(room.getScheduleOptions())) {
			roomVersions.increase(room);
		} else {
			// 방 필드가 바뀌어 flush 때 @Version 이 1 오른다.
			room.updateScheduleOptions(newOptions, clock.instant());
			entityManager.flush();
		}
		return response(room);
	}

	private ScheduleUpdateResponse response(TravelRoom room) {
		return new ScheduleUpdateResponse(ScheduleViews.of(room, items(room.getId())), room.getVersion(),
			room.isScheduleNeedsRecalculation());
	}

	/** 요청 값 하나하나의 범위. 여행 날짜가 없으면 일정을 넣을 날이 없어 C012. */
	private static void validateEdit(TravelRoom room, ItemEdit edit) {
		if (edit.order() < 1) {
			throw notAllowed("items[].order: 1 이상이어야 합니다.");
		}
		if (edit.stayMinutes() < 0) {
			throw new BusinessException(ExceptionType.SCHEDULE_STAY_NEGATIVE);
		}
		if (edit.stayMinutes() == 0 || edit.stayMinutes() > ScheduleItem.MAX_STAY_MINUTES) {
			throw notAllowed("items[].stayMinutes: 1~%d분이어야 합니다.".formatted(ScheduleItem.MAX_STAY_MINUTES));
		}
		if (room.getStartDate() == null || edit.visitDate().isBefore(room.getStartDate())
			|| edit.visitDate().isAfter(room.getEndDate())) {
			throw new BusinessException(ExceptionType.INVALID_DATE_RANGE, "items[].visitDate: 여행 날짜 밖입니다.");
		}
		if (edit.arrivalAt() != null
			&& !LocalDate.ofInstant(edit.arrivalAt(), ApiDateTime.ZONE).equals(edit.visitDate())) {
			throw new BusinessException(ExceptionType.INVALID_DATE_RANGE,
				"items[].arrivalAt: visitDate 와 같은 날(Asia/Seoul)이어야 합니다.");
		}
	}

	/** 새로 배치할 후보는 이 방의 지금 후보여야 한다. 뺀 후보는 먼저 복원하거나 다시 담아야 한다. */
	private static void checkNewPlace(RoomPlace place) {
		if (place == null) {
			throw new BusinessException(ExceptionType.ROOM_PLACE_NOT_FOUND);
		}
		if (place.isRemoved()) {
			throw notAllowed("items[].roomPlaceId: 뺀 후보는 복원하거나 다시 담은 뒤 배치할 수 있습니다.");
		}
	}

	private List<ScheduleItem> items(Long roomId) {
		return scheduleItemRepository.findByRoomIdOrderByVisitDateAscPositionAsc(roomId);
	}

	private static void checkVersion(TravelRoom room, int version) {
		if (room.getVersion() != version) {
			throw new BusinessException(ExceptionType.VERSION_MISMATCH);
		}
	}

	private static BusinessException notAllowed(String message) {
		return new BusinessException(ExceptionType.NOT_ALLOWED_VALUE, message);
	}

	/**
	 * 편집 뒤 바뀐 날짜에 남을 항목.
	 *
	 * @param existing 기존 항목(계획을 만들 때 읽은 행), 새 항목이면 null
	 * @param order    요청한 순서. 저장할 때 날짜마다 1부터 다시 매긴다
	 * @param edit     요청한 항목. 그대로 두는 기존 항목이면 null
	 */
	private record Entry(ScheduleItem existing, Long roomPlaceId, LocalDate visitDate, int order, int stayMinutes,
		boolean fixed, ItemEdit edit) {
	}

	/**
	 * @param fallbackStarts 편집 전 날짜별 첫 도착 시각(일차 시작 시각이 없는 날의 기준 시각)
	 * @param entries        날짜·순서로 놓은 바뀐 날짜의 항목
	 * @param plannedItems   entries 와 같은 순서의 계산 입력
	 */
	private record EditPlan(TransportMode transportMode, ScheduleOptions options,
		Map<LocalDate, Instant> fallbackStarts, Set<LocalDate> changedDates, List<Entry> entries,
		List<PlannedItem> plannedItems) {
	}
}

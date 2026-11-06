package com.ssafy.yeogirok.domain.schedule.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.ssafy.yeogirok.domain.room.entity.RoomPlace;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.domain.room.repository.RoomPlaceRepository;
import com.ssafy.yeogirok.domain.room.repository.TravelRoomRepository;
import com.ssafy.yeogirok.domain.schedule.ScheduleOptions;
import com.ssafy.yeogirok.domain.schedule.dto.ScheduleView;
import com.ssafy.yeogirok.domain.schedule.entity.ScheduleItem;
import com.ssafy.yeogirok.domain.schedule.repository.ScheduleItemRepository;
import com.ssafy.yeogirok.domain.schedule.service.ScheduleCalculator.PlannedItem;
import com.ssafy.yeogirok.domain.schedule.service.ScheduleCalculator.Timing;
import com.ssafy.yeogirok.domain.schedule.service.ScheduleWriter.Placement;
import com.ssafy.yeogirok.domain.schedule.travel.GeoPoint;
import com.ssafy.yeogirok.domain.schedule.travel.TravelTimeProvider;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

/**
 * 여행방 날짜·이동 수단 수정(API-023)이 일정에 주는 영향.
 * <ul>
 *   <li>날짜를 줄이면 새 날짜 밖 항목(affectedItems)을 확정 일정에서 지운다. 후보 장소는 그대로 둔다.</li>
 *   <li>이동 수단을 바꾸면 남은 항목의 이동 시간·시각을 새 이동 수단으로 다시 계산한다.</li>
 * </ul>
 * 재계산은 외부 호출이라 {@link #prepare} 로 트랜잭션 밖에서 먼저 하고, 방을 잠근 트랜잭션에서 {@link #apply} 로
 * 저장한다. 그사이 일정이 바뀌었으면 방 version 이 달라져 저장 전에 C004 로 끝난다.
 */
@Component
public class ScheduleRoomChanges {

	private final ScheduleItemRepository scheduleItemRepository;
	private final RoomPlaceRepository roomPlaceRepository;
	private final TravelRoomRepository travelRoomRepository;
	private final ScheduleWriter scheduleWriter;
	private final TravelTimeProvider travelTimeProvider;
	private final TransactionTemplate readOnlyTransactionTemplate;

	public ScheduleRoomChanges(ScheduleItemRepository scheduleItemRepository,
		RoomPlaceRepository roomPlaceRepository, TravelRoomRepository travelRoomRepository,
		ScheduleWriter scheduleWriter, TravelTimeProvider travelTimeProvider,
		PlatformTransactionManager transactionManager) {
		this.scheduleItemRepository = scheduleItemRepository;
		this.roomPlaceRepository = roomPlaceRepository;
		this.travelRoomRepository = travelRoomRepository;
		this.scheduleWriter = scheduleWriter;
		this.travelTimeProvider = travelTimeProvider;
		this.readOnlyTransactionTemplate = new TransactionTemplate(transactionManager);
		this.readOnlyTransactionTemplate.setReadOnly(true);
	}

	/** 새 날짜 범위 밖 항목. 범위가 null(날짜 미정)이면 모든 항목. 항목 이름은 후보 장소 이름이다. */
	@Transactional(readOnly = true)
	public List<AffectedItem> affectedItems(Long roomId, LocalDate start, LocalDate end) {
		List<ScheduleItem> outside = items(roomId).stream()
			.filter(item -> !inRange(item.getVisitDate(), start, end))
			.toList();
		if (outside.isEmpty()) {
			return List.of();
		}
		Map<Long, String> names = places(roomId).values().stream()
			.collect(Collectors.toMap(RoomPlace::getId, place -> place.getPlace().getName()));
		return outside.stream()
			.map(item -> new AffectedItem(item.getId(), item.getRoomPlaceId(), names.get(item.getRoomPlaceId()),
				item.getVisitDate()))
			.toList();
	}

	/**
	 * 새 이동 수단으로 새 날짜 범위 안 항목의 이동 시간·시각을 계산한다. 트랜잭션 밖에서 부른다. 항목이 없으면 null.
	 */
	public Recalculation prepare(Long roomId, TransportMode mode, LocalDate start, LocalDate end) {
		Snapshot snapshot = readOnlyTransactionTemplate.execute(status -> {
			TravelRoom room = travelRoomRepository.findById(roomId)
				.orElseThrow(() -> new BusinessException(ExceptionType.ROOM_NOT_FOUND));
			Map<Long, RoomPlace> places = places(roomId);
			List<ScheduleItem> kept = items(roomId).stream()
				.filter(item -> inRange(item.getVisitDate(), start, end))
				.toList();
			List<PlannedItem> planned = kept.stream().map(item -> {
				RoomPlace place = places.get(item.getRoomPlaceId());
				return new PlannedItem(item.getVisitDate(), item.getPosition(), item.getStayMinutes(), null,
					new GeoPoint(place.getPlace().getLatitude(), place.getPlace().getLongitude()));
			}).toList();
			return new Snapshot(room.getVersion(), kept.stream().map(ScheduleItem::getId).toList(), planned,
				ScheduleOptions.fromMap(room.getScheduleOptions()).within(start, end),
				ScheduleCalculator.firstArrivals(kept));
		});
		if (snapshot.itemIds().isEmpty()) {
			return null;
		}
		ScheduleCalculator.Result result = ScheduleCalculator.calculate(snapshot.planned(), snapshot.options(),
			snapshot.firstArrivals(), mode, travelTimeProvider);
		return new Recalculation(snapshot.version(), snapshot.itemIds(), result);
	}

	/**
	 * 방을 잠근 트랜잭션에서 방 필드를 바꾸기 전에 부른다. 날짜 밖 항목을 지우고, 재계산 결과가 있으면 남은 항목에 넣는다.
	 * 방 필드는 바꾸지 않고 새 일정 옵션(JSON)을 돌려준다(부르는 쪽이 방 정보와 함께 한 번에 바꾼다).
	 *
	 * @param recalculation 이동 수단을 바꾸지 않으면 null
	 * @return 새 일정 옵션
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public Map<String, Object> apply(TravelRoom room, LocalDate start, LocalDate end, Recalculation recalculation) {
		if (recalculation != null && recalculation.version() != room.getVersion()) {
			throw new BusinessException(ExceptionType.VERSION_MISMATCH);
		}
		List<ScheduleItem> items = items(room.getId());
		List<ScheduleItem> outside = items.stream().filter(item -> !inRange(item.getVisitDate(), start, end)).toList();
		ScheduleOptions options = ScheduleOptions.fromMap(room.getScheduleOptions()).within(start, end);
		if (recalculation == null) {
			if (!outside.isEmpty()) {
				scheduleWriter.write(room.getId(), outside, List.of());
			}
			return options.toMap();
		}

		Map<Long, ScheduleItem> byId = items.stream()
			.collect(Collectors.toMap(ScheduleItem::getId, Function.identity()));
		List<Placement> placements = new ArrayList<>();
		for (int i = 0; i < recalculation.itemIds().size(); i++) {
			ScheduleItem item = byId.get(recalculation.itemIds().get(i));
			Timing timing = recalculation.result().timings().get(i);
			placements.add(new Placement(item, item.getRoomPlaceId(), item.getVisitDate(), item.getPosition(),
				item.getStayMinutes(), item.isFixed(), timing));
		}
		scheduleWriter.write(room.getId(), outside, placements);
		return options.withEndLegs(recalculation.result().endLegs()).toMap();
	}

	/** 저장한 일정. 없으면 null. */
	@Transactional(readOnly = true)
	public ScheduleView view(TravelRoom room) {
		return ScheduleViews.of(room, items(room.getId()));
	}

	private List<ScheduleItem> items(Long roomId) {
		return scheduleItemRepository.findByRoomIdOrderByVisitDateAscPositionAsc(roomId);
	}

	private Map<Long, RoomPlace> places(Long roomId) {
		return roomPlaceRepository.findByRoomIdOrderByIdAsc(roomId).stream()
			.collect(Collectors.toMap(RoomPlace::getId, Function.identity()));
	}

	private static boolean inRange(LocalDate date, LocalDate start, LocalDate end) {
		return start != null && !date.isBefore(start) && !date.isAfter(end);
	}

	/** API-023 affectedItems. */
	public record AffectedItem(Long scheduleItemId, Long roomPlaceId, String name, LocalDate visitDate) {
	}

	/**
	 * @param version 계산할 때 읽은 여행방 version
	 * @param itemIds 계산한 항목(result.timings 와 같은 순서)
	 */
	public record Recalculation(int version, List<Long> itemIds, ScheduleCalculator.Result result) {
	}

	private record Snapshot(int version, List<Long> itemIds, List<PlannedItem> planned, ScheduleOptions options,
		Map<LocalDate, Instant> firstArrivals) {
	}
}

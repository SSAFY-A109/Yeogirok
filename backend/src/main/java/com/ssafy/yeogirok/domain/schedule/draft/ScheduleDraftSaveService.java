package com.ssafy.yeogirok.domain.schedule.draft;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ssafy.yeogirok.domain.job.JobStatus;
import com.ssafy.yeogirok.domain.room.entity.RoomPlace;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.domain.room.service.RoomFinder;
import com.ssafy.yeogirok.domain.schedule.LastApplied;
import com.ssafy.yeogirok.domain.schedule.ScheduleOptions;
import com.ssafy.yeogirok.domain.schedule.ScheduleOptions.DayOption;
import com.ssafy.yeogirok.domain.schedule.ScheduleOptions.UnassignedPlace;
import com.ssafy.yeogirok.domain.schedule.dto.DraftSaveCommand;
import com.ssafy.yeogirok.domain.schedule.dto.ScheduleResponse;
import com.ssafy.yeogirok.domain.schedule.service.ScheduleCalculator;
import com.ssafy.yeogirok.domain.schedule.service.ScheduleCalculator.PlannedItem;
import com.ssafy.yeogirok.domain.schedule.service.ScheduleReader;
import com.ssafy.yeogirok.domain.schedule.service.ScheduleViews;
import com.ssafy.yeogirok.domain.schedule.service.ScheduleWriter;
import com.ssafy.yeogirok.domain.schedule.service.ScheduleWriter.Placement;
import com.ssafy.yeogirok.domain.schedule.travel.TravelTimeProvider;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.response.ApiDateTime;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * API-039 편집한 일정 최종 저장. 프론트가 로컬에서 편집한 전체 상태를 받아 검증·재계산한 뒤 확정 일정을 한 번에 바꾼다.
 *
 * <ol>
 *   <li>같은 요청이 이미 적용됐고 방 version 이 그대로면 그 결과를 돌려준다(schedule_options.lastApplied).</li>
 *   <li>초안을 잠그고(재계산과 겹치면 SC003) 초안·방 version·항목을 확인한다. 처음 만드는 중·다시 계산 중 SC003,
 *   실패한 초안 SC004, 버전이 다르면 C004.</li>
 *   <li>방 잠금 밖에서 이동 시간·시각을 계산한다. 알려진 시간으로 종료 시각을 넘기면 SC002(항목을 옮겨 성공시키지 않는다).</li>
 *   <li>방을 잠그고 version 을 다시 확인한 뒤 확정 항목·일정 옵션·방 version(+1)을 한 트랜잭션에서 바꾼다.</li>
 * </ol>
 * 실패하면 확정 일정·방 version·초안은 그대로다. 저장한 뒤에도 초안은 남지만 방 version 이 바뀌어 다시 적용할 수 없다.
 */
@Service
public class ScheduleDraftSaveService {

	private final RoomFinder roomFinder;
	private final ScheduleReader scheduleReader;
	private final ScheduleWriter scheduleWriter;
	private final ScheduleDraftStore draftStore;
	private final ScheduleDraftLock draftLock;
	private final TravelTimeProvider travelTimeProvider;
	private final JsonMapper jsonMapper;
	private final EntityManager entityManager;
	private final TransactionTemplate transaction;
	private final TransactionTemplate readOnly;
	private final Clock clock;

	public ScheduleDraftSaveService(RoomFinder roomFinder, ScheduleReader scheduleReader,
		ScheduleWriter scheduleWriter, ScheduleDraftStore draftStore, ScheduleDraftLock draftLock,
		TravelTimeProvider travelTimeProvider, JsonMapper jsonMapper, EntityManager entityManager,
		PlatformTransactionManager transactionManager, Clock clock) {
		this.roomFinder = roomFinder;
		this.scheduleReader = scheduleReader;
		this.scheduleWriter = scheduleWriter;
		this.draftStore = draftStore;
		this.draftLock = draftLock;
		this.travelTimeProvider = travelTimeProvider;
		this.jsonMapper = jsonMapper;
		this.entityManager = entityManager;
		this.transaction = new TransactionTemplate(transactionManager);
		this.readOnly = new TransactionTemplate(transactionManager);
		this.readOnly.setReadOnly(true);
		this.clock = clock;
	}

	/** 트랜잭션 밖에서 부른다. */
	public ScheduleResponse save(Long userId, Long roomId, DraftSaveCommand command, String idempotencyKey) {
		String requestHash = sha256(jsonMapper.writeValueAsString(command));
		ScheduleResponse replay = readOnly.execute(status -> replay(userId, roomId, command, requestHash));
		if (replay != null) {
			return replay;
		}

		String token = draftLock.acquire(command.draftKey());
		try {
			SavePlan plan = readOnly.execute(status -> plan(userId, roomId, command));
			ScheduleCalculator.Result result = ScheduleCalculator.calculate(plan.planned(), plan.options(),
				plan.days().getFirst().date(), plan.days().getLast().date(), plan.mode(), travelTimeProvider);
			checkSatisfiable(plan, result);
			return transaction.execute(status -> apply(userId, roomId, command, plan, result,
				new LastApplied(command.draftKey(), command.draftVersion(), command.baseVersion(), requestHash,
					sha256(idempotencyKey), 0)));
		} finally {
			draftLock.release(command.draftKey(), token);
		}
	}

	/** 저장한 일정(같은 멱등키로 다시 보낸 요청의 응답). */
	public ScheduleResponse saved(Long userId, Long roomId) {
		return readOnly.execute(status -> response(roomFinder.getForMember(roomId, userId).room()));
	}

	/* ── 재전송 ─────────────────────────────────────────── */

	/** 같은 요청을 이미 적용했으면 결과, 아니면 null. 같은 초안을 다른 내용으로 적용했으면 C004. */
	private ScheduleResponse replay(Long userId, Long roomId, DraftSaveCommand command, String requestHash) {
		TravelRoom room = roomFinder.getForMember(roomId, userId).room();
		LastApplied last = LastApplied.from(room.getScheduleOptions());
		if (last == null || !last.draftKey().equals(command.draftKey())
			|| last.draftVersion() != command.draftVersion()) {
			return null;
		}
		if (!last.requestHash().equals(requestHash) || room.getVersion() != last.resultVersion()) {
			throw new BusinessException(ExceptionType.VERSION_MISMATCH,
				"이미 적용한 초안이거나 그 뒤로 일정이 바뀌었습니다. 다시 조회해 주세요.");
		}
		return response(room);
	}

	/* ── 확인 ─────────────────────────────────────────── */

	private SavePlan plan(Long userId, Long roomId, DraftSaveCommand command) {
		TravelRoom room = roomFinder.getForMember(roomId, userId).room();
		ScheduleDraft draft = draftStore.get(roomId, command.draftKey());
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
		DraftRules.requirePlannable(room);
		List<DayOption> days = DraftRules.checkDays(room, command.days().stream()
			.map(day -> new DayOption(day.date(), day.startTime(), day.endTime())).toList());

		Map<Integer, DraftItem> original = new HashMap<>();
		draft.items().forEach(item -> original.put(item.itemId(), item));
		Map<Long, RoomPlace> places = scheduleReader.places(roomId);
		Set<Integer> seen = new HashSet<>();
		List<Entry> entries = new ArrayList<>();
		for (DraftSaveCommand.Day day : command.days().stream()
			.sorted((a, b) -> a.date().compareTo(b.date())).toList()) {
			Set<Integer> orders = new HashSet<>();
			for (DraftSaveCommand.Item item : day.items()) {
				String path = "days[" + day.date() + "].items[itemId=" + item.itemId() + "]";
				checkItem(original, places, seen, item.itemId(), item.roomPlaceId(), path);
				if (item.order() < 1) {
					throw DraftRules.notAllowed(path + ".order: 1 이상이어야 합니다.");
				}
				if (!orders.add(item.order())) {
					throw new BusinessException(ExceptionType.SCHEDULE_ORDER_DUPLICATED,
						"%s 의 방문 순서 %d 가 중복됩니다.".formatted(day.date(), item.order()));
				}
				DraftRules.checkStay(item.stayMinutes(), path + ".stayMinutes");
				entries.add(new Entry(item.roomPlaceId(), day.date(), item.order(), item.stayMinutes(),
					item.fixed()));
			}
			for (int order = 1; order <= orders.size(); order++) {
				if (!orders.contains(order)) {
					throw DraftRules.notAllowed(day.date() + ": 방문 순서는 1부터 빈 번호 없이 이어져야 합니다.");
				}
			}
		}
		List<UnassignedPlace> unassigned = new ArrayList<>();
		for (Integer itemId : command.unassignedItemIds()) {
			DraftItem item = original.get(itemId);
			checkItem(original, places, seen, itemId, item == null ? null : item.roomPlaceId(),
				"unassignedItemIds[" + itemId + "]");
			unassigned.add(new UnassignedPlace(item.roomPlaceId()));
		}
		if (!seen.equals(original.keySet())) {
			throw DraftRules.notAllowed("초안의 모든 항목이 days.items 나 unassignedItemIds 중 한 곳에 있어야 합니다.");
		}

		entries.sort((a, b) -> a.date().equals(b.date()) ? Integer.compare(a.order(), b.order())
			: a.date().compareTo(b.date()));
		List<PlannedItem> planned = entries.stream()
			.map(entry -> new PlannedItem(entry.date(), entry.order(), entry.stayMinutes(),
				ScheduleReader.location(places.get(entry.roomPlaceId()))))
			.toList();
		ScheduleOptions options = new ScheduleOptions(days, command.startPoint(), command.endPoint(), null,
			unassigned);
		return new SavePlan(room.getTransportMode(), days, options, entries, planned);
	}

	/** 원본 초안의 항목이고, 후보가 같고, 한 번만 나오고, 후보가 지금 이 방의 후보여야 한다. 아니면 C003. */
	private static void checkItem(Map<Integer, DraftItem> original, Map<Long, RoomPlace> places, Set<Integer> seen,
		Integer itemId, Long roomPlaceId, String path) {
		DraftItem item = original.get(itemId);
		if (item == null) {
			throw DraftRules.notAllowed(path + ": 원본 초안의 항목이 아닙니다.");
		}
		if (!item.roomPlaceId().equals(roomPlaceId)) {
			throw DraftRules.notAllowed(path + ": 항목의 후보를 바꿀 수 없습니다.");
		}
		if (!seen.add(itemId)) {
			throw DraftRules.notAllowed(path + ": 같은 항목이 두 번 있습니다.");
		}
		RoomPlace place = places.get(roomPlaceId);
		if (place == null || place.isRemoved()) {
			throw DraftRules.notAllowed(path + ": 이 여행방의 현재 후보가 아닙니다.");
		}
	}

	/** 알려진 시각으로 마지막 일정(마지막 날은 도착지 도착)이 그날 종료 시각을 넘기면 SC002. */
	private static void checkSatisfiable(SavePlan plan, ScheduleCalculator.Result result) {
		LocalDate lastDay = plan.days().getLast().date();
		for (DayOption day : plan.days()) {
			Instant finish = null;
			for (int i = 0; i < plan.entries().size(); i++) {
				if (plan.entries().get(i).date().equals(day.date())) {
					finish = result.timings().get(i).departureAt();
				}
			}
			if (finish == null) {
				continue;
			}
			if (day.date().equals(lastDay) && result.endLeg() != null && result.endLeg().isKnown()) {
				finish = finish.plusSeconds(result.endLeg().travelMinutes() * 60L);
			}
			if (finish.isAfter(ZonedDateTime.of(day.date(), day.endTime(), ApiDateTime.ZONE).toInstant())) {
				throw new BusinessException(ExceptionType.SCHEDULE_UNSATISFIABLE,
					"%s: 마지막 일정이 종료 시각을 넘깁니다.".formatted(day.date()));
			}
		}
	}

	/* ── 저장 ─────────────────────────────────────────── */

	private ScheduleResponse apply(Long userId, Long roomId, DraftSaveCommand command, SavePlan plan,
		ScheduleCalculator.Result result, LastApplied pending) {
		TravelRoom room = roomFinder.getForMemberForUpdate(roomId, userId).room();
		DraftRules.checkVersion(room, command.baseVersion());

		List<Placement> placements = new ArrayList<>();
		for (int i = 0; i < plan.entries().size(); i++) {
			Entry entry = plan.entries().get(i);
			placements.add(new Placement(null, entry.roomPlaceId(), entry.date(), entry.order(), entry.stayMinutes(),
				entry.fixed(), result.timings().get(i)));
		}
		scheduleWriter.write(roomId, scheduleReader.items(roomId), placements);

		int resultVersion = room.getVersion() + 1;
		LastApplied applied = new LastApplied(pending.draftKey(), pending.draftVersion(), pending.baseVersion(),
			pending.requestHash(), pending.requestKeyHash(), resultVersion);
		// 방 필드가 바뀌어(lastApplied 가 늘 달라진다) flush 때 @Version 이 1 오른다.
		room.updateScheduleOptions(applied.addTo(plan.options().withEndLeg(result.endLeg()).toMap()),
			clock.instant());
		entityManager.flush();
		if (room.getVersion() != resultVersion) {
			throw new IllegalStateException("일정 저장 뒤 방 version 이 예상과 다릅니다: " + room.getVersion());
		}
		return response(room);
	}

	private ScheduleResponse response(TravelRoom room) {
		return new ScheduleResponse(ScheduleViews.of(room, scheduleReader.items(room.getId())), room.getVersion());
	}

	private static String sha256(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/** 저장할 항목. 날짜·순서로 놓는다. */
	private record Entry(Long roomPlaceId, LocalDate date, int order, int stayMinutes, boolean fixed) {
	}

	/** @param entries planned 와 같은 순서 */
	private record SavePlan(TransportMode mode, List<DayOption> days, ScheduleOptions options, List<Entry> entries,
		List<PlannedItem> planned) {
	}
}

package com.ssafy.yeogirok.domain.room.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.ssafy.yeogirok.domain.category.CategoryNames;
import com.ssafy.yeogirok.domain.room.dto.RoomCreateCommand;
import com.ssafy.yeogirok.domain.room.dto.RoomCreateResponse;
import com.ssafy.yeogirok.domain.room.dto.RoomDetailResponse;
import com.ssafy.yeogirok.domain.room.dto.RoomListResponse;
import com.ssafy.yeogirok.domain.room.dto.RoomUpdateCommand;
import com.ssafy.yeogirok.domain.room.dto.RoomUpdateResponse;
import com.ssafy.yeogirok.domain.room.entity.RoomMember;
import com.ssafy.yeogirok.domain.room.entity.RoomMemberId;
import com.ssafy.yeogirok.domain.room.entity.RoomPlace;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.domain.room.repository.RoomMemberRepository;
import com.ssafy.yeogirok.domain.room.repository.RoomMemberView;
import com.ssafy.yeogirok.domain.room.repository.RoomPlaceCount;
import com.ssafy.yeogirok.domain.room.repository.RoomPlaceRepository;
import com.ssafy.yeogirok.domain.room.repository.TravelRoomRepository;
import com.ssafy.yeogirok.domain.room.service.RoomFinder.MemberRoom;
import com.ssafy.yeogirok.domain.schedule.service.ScheduleRoomChanges;
import com.ssafy.yeogirok.domain.schedule.service.ScheduleRoomChanges.Recalculation;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.request.PageSize;
import com.ssafy.yeogirok.global.response.ApiDateTime;

/** 여행방 생성·조회·수정·나가기(API-020·021·022·023·087). 초대는 {@link RoomInvitationService}. */
@Service
public class RoomService {

	private final TravelRoomRepository travelRoomRepository;
	private final RoomMemberRepository roomMemberRepository;
	private final RoomPlaceRepository roomPlaceRepository;
	private final CategoryNames categoryNames;
	private final RoomFinder roomFinder;
	private final ScheduleRoomChanges scheduleRoomChanges;
	private final TransactionTemplate transactionTemplate;
	private final Clock clock;

	public RoomService(TravelRoomRepository travelRoomRepository, RoomMemberRepository roomMemberRepository,
		RoomPlaceRepository roomPlaceRepository, CategoryNames categoryNames, RoomFinder roomFinder,
		ScheduleRoomChanges scheduleRoomChanges, PlatformTransactionManager transactionManager, Clock clock) {
		this.travelRoomRepository = travelRoomRepository;
		this.roomMemberRepository = roomMemberRepository;
		this.roomPlaceRepository = roomPlaceRepository;
		this.categoryNames = categoryNames;
		this.roomFinder = roomFinder;
		this.scheduleRoomChanges = scheduleRoomChanges;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	/** API-021. 방과 생성자의 참여 행을 한 트랜잭션에서 만든다. 멱등키는 컨트롤러가 이 밖에서 처리한다. */
	@Transactional
	public RoomCreateResponse create(Long userId, RoomCreateCommand command) {
		String title = normalizeTitle(command.title());
		LocalDate today = today();
		validateDates(command.startDate(), command.endDate());
		rejectPastStart(command.startDate(), today);

		Instant now = clock.instant();
		TravelRoom room = travelRoomRepository.saveAndFlush(TravelRoom.builder()
			.title(title)
			.startDate(command.startDate())
			.endDate(command.endDate())
			.transportMode(command.transportMode())
			.createdAt(now)
			.build());
		roomMemberRepository.saveAndFlush(RoomMember.creator(room.getId(), userId, now));
		return new RoomCreateResponse(room.getId(), room.getVersion(), true);
	}

	/** 같은 멱등키로 다시 보낸 생성 요청에 만든 방을 돌려준다. 그사이 나갔으면 RO002, 방이 삭제됐으면 RO001. */
	@Transactional(readOnly = true)
	public RoomCreateResponse getCreated(Long userId, Long roomId) {
		MemberRoom found = roomFinder.getForMember(roomId, userId);
		return new RoomCreateResponse(found.room().getId(), found.room().getVersion(), found.member().isCreator());
	}

	/** API-020. 현재 참여 중인 방을 최근에 만든 순(id 내림차순)으로. */
	@Transactional(readOnly = true)
	public RoomListResponse getMyRooms(Long userId, String cursor, Integer limit) {
		RoomCursor after = RoomCursor.decodeOrNull(cursor);
		int size = PageSize.resolve(limit);

		// 한 개 더 읽어서 다음 쪽이 있는지 본다.
		List<TravelRoom> rows = travelRoomRepository.findJoinedRooms(userId,
			after == null ? Long.MAX_VALUE : after.roomId(), Limit.of(size + 1));
		boolean hasNext = rows.size() > size;
		List<TravelRoom> page = hasNext ? rows.subList(0, size) : rows;
		if (page.isEmpty()) {
			return new RoomListResponse(List.of(), null);
		}

		Map<Long, List<RoomMemberView>> membersByRoom = roomMemberRepository
			.findCurrentMembers(page.stream().map(TravelRoom::getId).toList())
			.stream()
			.collect(Collectors.groupingBy(RoomMemberView::roomId));
		Map<Long, Long> placeCountByRoom = roomPlaceRepository
			.countActiveByRoomIds(page.stream().map(TravelRoom::getId).toList())
			.stream()
			.collect(Collectors.toMap(RoomPlaceCount::roomId, RoomPlaceCount::count));
		List<RoomListResponse.Item> items = page.stream()
			.map(room -> RoomListResponse.Item.of(room, membersByRoom.getOrDefault(room.getId(), List.of()),
				placeCountByRoom.getOrDefault(room.getId(), 0L).intValue(), userId))
			.toList();
		String nextCursor = hasNext ? new RoomCursor(page.getLast().getId()).encode() : null;
		return new RoomListResponse(items, nextCursor);
	}

	/** API-022. */
	@Transactional(readOnly = true)
	public RoomDetailResponse getRoom(Long userId, Long roomId) {
		MemberRoom found = roomFinder.getForMember(roomId, userId);
		List<RoomPlace> places = roomPlaceRepository.findByRoomIdAndRemovedAtIsNullOrderByIdAsc(roomId);
		List<String> categoryCodes = places.stream().map(place -> place.getPlace().getCategoryCode()).toList();
		return RoomDetailResponse.of(found.room(), roomMemberRepository.findCurrentMembers(List.of(roomId)), places,
			categoryNames.of(categoryCodes), found.member(), scheduleRoomChanges.view(found.room()));
	}

	/**
	 * API-023. previewOnly=true 면 검증만 하고 아무것도 바꾸지 않는다(새 날짜 밖으로 빠질 일정 항목만 알려 준다).
	 * 저장하면 새 날짜 밖 일정 항목을 지우고(후보 장소는 그대로), 날짜나 이동 수단을 바꿨으면 남은 일정의 이동 시간·
	 * 시각을 다시 계산한다. 일정이 있는 방의 날짜를 지우면(null) C003. version 이 다르거나 동시 수정이 @Version 에 걸리면 C004.
	 *
	 * <p>재계산은 외부 호출이라 방 잠금 밖에서 먼저 하고, 방을 잠근 뒤 version 을 다시 확인하고 저장한다.
	 */
	public RoomUpdateResponse update(Long userId, Long roomId, RoomUpdateCommand command) {
		// 요청값 오류는 조회보다 먼저 알린다.
		String newTitle = command.title() == null ? null : normalizeTitle(command.title());
		if (command.datesGiven()) {
			validateDates(command.startDate(), command.endDate());
		}

		Recalculation recalculation = null;
		if (!command.previewOnly() && (command.transportMode() != null || command.datesGiven())) {
			TravelRoom current = roomFinder.getForMember(roomId, userId).room();
			TransportMode mode = command.transportMode() == null ? current.getTransportMode() : command.transportMode();
			LocalDate startDate = command.datesGiven() ? command.startDate() : current.getStartDate();
			LocalDate endDate = command.datesGiven() ? command.endDate() : current.getEndDate();
			boolean changed = mode != current.getTransportMode()
				|| !Objects.equals(startDate, current.getStartDate()) || !Objects.equals(endDate, current.getEndDate());
			if (current.getVersion().equals(command.version()) && changed && startDate != null) {
				recalculation = scheduleRoomChanges.prepare(roomId, mode, startDate, endDate);
			}
		}
		Recalculation prepared = recalculation;
		return transactionTemplate.execute(status -> updateLocked(userId, roomId, command, newTitle, prepared));
	}

	private RoomUpdateResponse updateLocked(Long userId, Long roomId, RoomUpdateCommand command, String newTitle,
		Recalculation recalculation) {
		TravelRoom room = roomFinder.getForMemberForUpdate(roomId, userId).room();
		if (!room.getVersion().equals(command.version())) {
			throw new BusinessException(ExceptionType.VERSION_MISMATCH);
		}
		if (command.datesGiven() && !Objects.equals(command.startDate(), room.getStartDate())) {
			rejectPastStart(command.startDate(), today());
		}
		LocalDate startDate = command.datesGiven() ? command.startDate() : room.getStartDate();
		LocalDate endDate = command.datesGiven() ? command.endDate() : room.getEndDate();
		List<RoomUpdateResponse.AffectedItem> affectedItems = command.datesGiven()
			? scheduleRoomChanges.affectedItems(roomId, startDate, endDate).stream()
				.map(item -> new RoomUpdateResponse.AffectedItem(item.scheduleItemId(), item.roomPlaceId(),
					item.name(), item.visitDate()))
				.toList()
			: List.of();
		if (startDate == null && !affectedItems.isEmpty()) {
			// 일정이 있는 방의 날짜를 지우는 처리는 정책이 정해지지 않았다(명세).
			throw new BusinessException(ExceptionType.NOT_ALLOWED_VALUE, "일정이 있는 여행방의 날짜는 지울 수 없습니다.");
		}
		if (command.previewOnly()) {
			return RoomUpdateResponse.preview(room, affectedItems);
		}

		// 일정 항목을 먼저 저장하고 방 필드는 한 번에 바꾼다(version 이 한 번만 오르게).
		Map<String, Object> scheduleOptions = scheduleRoomChanges.apply(room, startDate, endDate, recalculation);
		Instant now = clock.instant();
		room.updateInfo(
			newTitle == null ? room.getTitle() : newTitle,
			startDate,
			endDate,
			command.transportMode() == null ? room.getTransportMode() : command.transportMode(),
			now);
		if (!scheduleOptions.equals(room.getScheduleOptions())) {
			room.updateScheduleOptions(scheduleOptions, now);
		}
		// 응답에 올라간 version 을 담으려고 지금 반영한다.
		travelRoomRepository.flush();
		return RoomUpdateResponse.applied(room, affectedItems, scheduleRoomChanges.view(room));
	}

	/**
	 * API-087. 본인만 나간다. 이미 나간 사람의 재요청은 바꾸는 것 없이 성공한다. 마지막 현재 참여자가 나가면
	 * 같은 트랜잭션에서 방을 소프트 삭제한다. 방 행을 잠가 API-025 참여와 한 줄로 세운다(삭제된 방에 참여하지 못하게).
	 */
	@Transactional
	public void leave(Long userId, Long roomId) {
		TravelRoom room = travelRoomRepository.findByIdForUpdate(roomId)
			.orElseThrow(() -> new BusinessException(ExceptionType.ROOM_NOT_FOUND));
		RoomMember member = roomMemberRepository.findById(new RoomMemberId(roomId, userId))
			.orElseThrow(() -> new BusinessException(
				room.isDeleted() ? ExceptionType.ROOM_NOT_FOUND : ExceptionType.ROOM_NOT_MEMBER));
		if (!member.isCurrent()) {
			return;
		}

		Instant now = clock.instant();
		member.leave(now);
		roomMemberRepository.flush();
		if (roomMemberRepository.countCurrentMembers(roomId) == 0) {
			room.softDelete(now);
		}
	}

	/** 앞뒤 공백을 지운다. 비었거나 200자를 넘으면 C003. */
	static String normalizeTitle(String title) {
		String trimmed = title.strip();
		// DB VARCHAR 길이와 같게 코드 포인트로 센다(이모지 하나는 UTF-16 으로 2칸이지만 1자다).
		int length = trimmed.codePointCount(0, trimmed.length());
		if (length == 0 || length > TravelRoom.TITLE_MAX_LENGTH) {
			throw new BusinessException(ExceptionType.NOT_ALLOWED_VALUE,
				"title: 1~%d자여야 합니다.".formatted(TravelRoom.TITLE_MAX_LENGTH));
		}
		return trimmed;
	}

	/** 둘 다 있거나 둘 다 null(날짜 미정). 한쪽만 있거나 종료일이 시작일보다 앞서면 C012. */
	private static void validateDates(LocalDate startDate, LocalDate endDate) {
		if ((startDate == null) != (endDate == null)) {
			throw new BusinessException(ExceptionType.INVALID_DATE_RANGE, "startDate 와 endDate 는 함께 정해야 합니다.");
		}
		if (startDate != null && startDate.isAfter(endDate)) {
			throw new BusinessException(ExceptionType.INVALID_DATE_RANGE, "startDate 가 endDate 보다 늦습니다.");
		}
	}

	/** 새로 정하는 출발일이 오늘(Asia/Seoul)보다 앞서면 C012. */
	private static void rejectPastStart(LocalDate startDate, LocalDate today) {
		if (startDate != null && startDate.isBefore(today)) {
			throw new BusinessException(ExceptionType.INVALID_DATE_RANGE, "startDate 가 오늘보다 앞섭니다.");
		}
	}

	private LocalDate today() {
		return LocalDate.ofInstant(clock.instant(), ApiDateTime.ZONE);
	}
}

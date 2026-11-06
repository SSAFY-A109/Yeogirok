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
import org.springframework.transaction.annotation.Transactional;

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
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.domain.room.repository.RoomMemberRepository;
import com.ssafy.yeogirok.domain.room.repository.RoomMemberView;
import com.ssafy.yeogirok.domain.room.repository.RoomPlaceCount;
import com.ssafy.yeogirok.domain.room.repository.RoomPlaceRepository;
import com.ssafy.yeogirok.domain.room.repository.TravelRoomRepository;
import com.ssafy.yeogirok.domain.room.service.RoomFinder.MemberRoom;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.response.ApiDateTime;

import lombok.RequiredArgsConstructor;

/** 여행방 생성·조회·수정·나가기(API-020·021·022·023·087). 초대는 {@link RoomInvitationService}. */
@Service
@RequiredArgsConstructor
public class RoomService {

	/** API-020 limit 기본값·최댓값. */
	static final int DEFAULT_LIST_LIMIT = 20;
	static final int MAX_LIST_LIMIT = 50;

	private final TravelRoomRepository travelRoomRepository;
	private final RoomMemberRepository roomMemberRepository;
	private final RoomPlaceRepository roomPlaceRepository;
	private final CategoryNames categoryNames;
	private final RoomFinder roomFinder;
	private final Clock clock;

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
		int size = limit == null ? DEFAULT_LIST_LIMIT : limit;
		if (size < 1 || size > MAX_LIST_LIMIT) {
			throw new BusinessException(ExceptionType.PAGE_SIZE_OUT_OF_RANGE,
				"limit: 1~%d 사이여야 합니다.".formatted(MAX_LIST_LIMIT));
		}

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
		return RoomDetailResponse.of(found.room(), roomMemberRepository.findCurrentMembers(List.of(roomId)), places,
			categoryNames.of(places.stream().map(RoomPlace::getCategoryCode).toList()), found.member());
	}

	/**
	 * API-023. previewOnly=true 면 검증만 하고 아무것도 바꾸지 않는다. 일정이 아직 없어 날짜를 줄여도 빠질 항목이 없고
	 * 재계산도 하지 않는다(일정 API 에서 이어서 만든다). version 이 다르거나 동시 수정이 @Version 에 걸리면 C004.
	 */
	@Transactional
	public RoomUpdateResponse update(Long userId, Long roomId, RoomUpdateCommand command) {
		// 요청값 오류는 조회보다 먼저 알린다.
		String newTitle = command.title() == null ? null : normalizeTitle(command.title());
		if (command.datesGiven()) {
			validateDates(command.startDate(), command.endDate());
		}

		TravelRoom room = roomFinder.getForMember(roomId, userId).room();
		if (!room.getVersion().equals(command.version())) {
			throw new BusinessException(ExceptionType.VERSION_MISMATCH);
		}
		if (command.datesGiven() && !Objects.equals(command.startDate(), room.getStartDate())) {
			rejectPastStart(command.startDate(), today());
		}
		if (command.previewOnly()) {
			return RoomUpdateResponse.of(room, false);
		}

		room.updateInfo(
			newTitle == null ? room.getTitle() : newTitle,
			command.datesGiven() ? command.startDate() : room.getStartDate(),
			command.datesGiven() ? command.endDate() : room.getEndDate(),
			command.transportMode() == null ? room.getTransportMode() : command.transportMode(),
			clock.instant());
		// 응답에 올라간 version 을 담으려고 지금 반영한다.
		travelRoomRepository.flush();
		return RoomUpdateResponse.of(room, true);
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

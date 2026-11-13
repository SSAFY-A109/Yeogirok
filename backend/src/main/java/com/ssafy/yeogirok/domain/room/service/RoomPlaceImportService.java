package com.ssafy.yeogirok.domain.room.service;

import static java.util.stream.Collectors.toMap;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.category.CategoryNames;
import com.ssafy.yeogirok.domain.place.entity.Place;
import com.ssafy.yeogirok.domain.place.repository.PlaceRepository;
import com.ssafy.yeogirok.domain.record.entity.ActualVisit;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.record.repository.ActualVisitRepository;
import com.ssafy.yeogirok.domain.record.repository.TravelRecordRepository;
import com.ssafy.yeogirok.domain.room.dto.RoomPlaceImportCommand;
import com.ssafy.yeogirok.domain.room.dto.RoomPlaceImportResponse;
import com.ssafy.yeogirok.domain.room.dto.RoomPlaceResponse;
import com.ssafy.yeogirok.domain.room.entity.RoomPlace;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.domain.room.repository.RoomPlaceRepository;
import com.ssafy.yeogirok.domain.room.repository.TravelRoomRepository;
import com.ssafy.yeogirok.domain.schedule.repository.ScheduleItemRepository;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;

/**
 * 공개된 코스에서 선택 장소 가져오기(API-030).
 *
 * <ol>
 *   <li>대상 방 행을 잠그고 참여를 확인한다(RO001·RO002).</li>
 *   <li>원본 기록 행을 잠그고 지금 게시 중인지·원본 방이 삭제되지 않았는지 본다. 아니면 CO001(공개 코스 상세 API-016 과 같은
 *       기준). 잠가 두므로 확인한 뒤 저장 전에 게시가 풀리지 않는다. 코스 수정(API-075)은 기록 행만 잠그고 방 행은 잠그지 않아
 *       방 → 기록 순서로 잠가도 서로 기다리다 멈추지 않는다.</li>
 *   <li>고른 방문이 모두 그 기록의 공개 대상 방문이고 장소 행이 있어야 한다. 하나라도 아니면 아무것도 만들지 않고 CO007.</li>
 *   <li>방문마다 그 장소로 후보를 만들고(sourceType=COURSE, sourceRecordId=원본 기록), 여행방 version 을 1 올린다.
 *       확정 일정이 있으면 재계산 필요도 켜고, 커밋 뒤 실시간 알림(ROOM_UPDATED)이 나간다.</li>
 * </ol>
 * 같은 장소를 이미 담았어도 새 후보를 만든다(API-029 와 같은 팀 확정 정책). 장소 행은 원본 방문의 것을 그대로 쓰므로 외부 호출이 없다.
 */
@Service
@RequiredArgsConstructor
public class RoomPlaceImportService {

	private final RoomFinder roomFinder;
	private final RoomVersions roomVersions;
	private final RoomPlaceRepository roomPlaceRepository;
	private final TravelRoomRepository travelRoomRepository;
	private final TravelRecordRepository travelRecordRepository;
	private final ActualVisitRepository actualVisitRepository;
	private final PlaceRepository placeRepository;
	private final ScheduleItemRepository scheduleItemRepository;
	private final CategoryNames categoryNames;
	private final Clock clock;

	/** 멱등키는 컨트롤러가 이 밖에서 처리한다. */
	@Transactional
	public RoomPlaceImportResponse importPlaces(Long userId, Long roomId, RoomPlaceImportCommand command) {
		TravelRoom room = roomFinder.getForMemberForUpdate(roomId, userId).room();
		TravelRecord record = lockPublishedRecord(command.recordId());

		Map<Long, ActualVisit> visits = actualVisitRepository.findByRecordIdOrderByVisitDateAscPositionAsc(record.getId())
			.stream().filter(ActualVisit::isPublicSelected)
			.collect(toMap(ActualVisit::getId, Function.identity()));
		List<ActualVisit> selected = command.visitIds().stream().map(visits::get).toList();
		if (selected.contains(null)) {
			throw new BusinessException(ExceptionType.COURSE_IMPORT_VISIT_NOT_IN_COURSE);
		}
		Map<Long, Place> places = placeRepository.findAllById(selected.stream().map(ActualVisit::getPlaceId).toList())
			.stream().collect(toMap(Place::getId, Function.identity()));
		if (selected.stream().anyMatch(visit -> !places.containsKey(visit.getPlaceId()))) {
			// 방문의 장소 행이 없으면(기록 방문에는 FK 가 없다) 코스 상세에도 장소가 없어 고를 수 없는 항목이다.
			throw new BusinessException(ExceptionType.COURSE_IMPORT_VISIT_NOT_IN_COURSE);
		}

		Instant now = clock.instant();
		List<RoomPlace> added = new ArrayList<>();
		for (ActualVisit visit : selected) {
			added.add(roomPlaceRepository.save(
				RoomPlace.imported(roomId, places.get(visit.getPlaceId()), userId, record.getId(), now)));
		}
		int version = roomVersions.increaseAfterPlacesChanged(room, userId,
			scheduleItemRepository.existsByRoomId(roomId));
		return new RoomPlaceImportResponse(responses(added), version);
	}

	/** 같은 멱등키로 다시 보낸 요청에 만들었던 후보(요청 순서)와 지금 여행방 version 을 돌려준다. */
	@Transactional(readOnly = true)
	public RoomPlaceImportResponse getImported(Long userId, Long roomId, List<Long> roomPlaceIds) {
		TravelRoom room = roomFinder.getForMember(roomId, userId).room();
		List<RoomPlace> added = roomPlaceIds.stream()
			.map(id -> roomPlaceRepository.findByIdAndRoomId(id, roomId)
				.orElseThrow(() -> new BusinessException(ExceptionType.ROOM_PLACE_NOT_FOUND)))
			.toList();
		return new RoomPlaceImportResponse(responses(added), room.getVersion());
	}

	/** 멱등키에 저장할 결과 ID(후보 ID 를 쉼표로 이은 값)와 그 반대. */
	public static String resultIdOf(RoomPlaceImportResponse response) {
		return response.added().stream().map(place -> place.id().toString()).collect(Collectors.joining(","));
	}

	public static List<Long> roomPlaceIdsOf(String resultId) {
		return Arrays.stream(resultId.split(",")).map(Long::valueOf).toList();
	}

	/** 없음·미게시·원본 방 삭제는 구분하지 않고 CO001. */
	private TravelRecord lockPublishedRecord(Long recordId) {
		TravelRecord record = travelRecordRepository.findByIdForUpdate(recordId)
			.filter(found -> found.getPublishedAt() != null)
			.orElseThrow(() -> new BusinessException(ExceptionType.COURSE_UNAVAILABLE));
		travelRoomRepository.findByIdAndDeletedAtIsNull(record.getRoomId())
			.orElseThrow(() -> new BusinessException(ExceptionType.COURSE_UNAVAILABLE));
		return record;
	}

	private List<RoomPlaceResponse> responses(List<RoomPlace> added) {
		Map<String, String> names = categoryNames.of(added.stream()
			.map(roomPlace -> roomPlace.getPlace().getCategoryCode()).toList());
		return added.stream().map(roomPlace -> RoomPlaceResponse.from(roomPlace, names)).toList();
	}
}

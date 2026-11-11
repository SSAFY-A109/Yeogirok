package com.ssafy.yeogirok.domain.room.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.ssafy.yeogirok.domain.category.CategoryNames;
import com.ssafy.yeogirok.domain.place.entity.Place;
import com.ssafy.yeogirok.domain.place.repository.PlaceRepository;
import com.ssafy.yeogirok.domain.room.dto.CustomPlaceCreateCommand;
import com.ssafy.yeogirok.domain.room.dto.CustomPlaceCreateResponse;
import com.ssafy.yeogirok.domain.room.dto.RoomPlaceListResponse;
import com.ssafy.yeogirok.domain.room.dto.RoomPlaceRemoveResponse;
import com.ssafy.yeogirok.domain.room.dto.RoomPlaceResponse;
import com.ssafy.yeogirok.domain.room.dto.RoomPlaceRestoreResponse;
import com.ssafy.yeogirok.domain.room.dto.RoomPlaceState;
import com.ssafy.yeogirok.domain.room.entity.RoomPlace;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.domain.room.repository.RoomPlaceRepository;
import com.ssafy.yeogirok.domain.room.service.RoomFinder.MemberRoom;
import com.ssafy.yeogirok.domain.schedule.repository.ScheduleItemRepository;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.geo.LegalDong;
import com.ssafy.yeogirok.global.geo.LegalDongResolver;

/**
 * 여행방 후보 장소 조회·직접 추가·빼기·복원(API-028·031·032·094).
 *
 * <p>후보를 바꾸는 요청은 방 행을 잠가 한 줄로 세우고, 실제로 바뀐 경우에만 여행방 version 을 1 올린다. 이미 뺀 후보 빼기·
 * 이미 있는 후보 복원은 바꾸는 것이 없어 version 을 보지도 올리지도 않고 성공한다(동시에 같은 요청이 와도 오류 없이).
 *
 * <p>API-029(검색·AI 추천 담기)는 {@link RoomPlaceAddService} 에 있다. 지금은 추천 담기(AI)만 한다.
 * TODO 검색 담기(SEARCH): 장소 검색·상세(API-026·027)가 생기면 같은 서비스에서 장소를 TourAPI 로 확인해 붙인다
 * (places 재사용·법정동 조회·version 증가는 추천 담기와 같다).
 * TODO API-030(공개 코스에서 가져오기): 여행 기록·방문·공개 코스가 생기면 만든다.
 * <ul>
 *   <li>원본 기록 행과 대상 방 행을 잠그고, 저장 직전에 게시 여부·원본 방 미삭제·방문 소속을 다시 확인한다.</li>
 *   <li>선택한 방문의 place_id 를 새 후보에 연결하고 sourceType=COURSE, sourceRecordId=원본 기록 ID 를 남긴다.
 *       API-028·방 상세의 sourceRecord 도 이때 채운다({@link com.ssafy.yeogirok.domain.room.dto.RoomPlaceListResponse}).</li>
 * </ul>
 * 029·030 공통: Idempotency-Key 를 받고, 후보를 저장한 뒤 {@link #increaseVersion} 을 불러 방 version 과
 * scheduleNeedsRecalculation(확정 일정이 있으면 true)을 한 번에 갱신한다.
 */
@Service
public class RoomPlaceService {

	private final RoomPlaceRepository roomPlaceRepository;
	private final PlaceRepository placeRepository;
	private final CategoryNames categoryNames;
	private final RoomFinder roomFinder;
	private final RoomVersions roomVersions;
	private final ScheduleItemRepository scheduleItemRepository;
	private final LegalDongResolver legalDongResolver;
	private final TransactionTemplate transactionTemplate;
	private final Clock clock;

	public RoomPlaceService(RoomPlaceRepository roomPlaceRepository, PlaceRepository placeRepository,
		CategoryNames categoryNames, RoomFinder roomFinder, RoomVersions roomVersions,
		ScheduleItemRepository scheduleItemRepository, LegalDongResolver legalDongResolver,
		PlatformTransactionManager transactionManager, Clock clock) {
		this.roomPlaceRepository = roomPlaceRepository;
		this.placeRepository = placeRepository;
		this.categoryNames = categoryNames;
		this.roomFinder = roomFinder;
		this.roomVersions = roomVersions;
		this.scheduleItemRepository = scheduleItemRepository;
		this.legalDongResolver = legalDongResolver;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	/** API-028. 담은 순서(id 오름차순). */
	@Transactional(readOnly = true)
	public RoomPlaceListResponse getPlaces(Long userId, Long roomId, RoomPlaceState state) {
		TravelRoom room = roomFinder.getForMember(roomId, userId).room();
		List<RoomPlace> places = switch (state) {
			case ACTIVE -> roomPlaceRepository.findByRoomIdAndRemovedAtIsNullOrderByIdAsc(roomId);
			case REMOVED -> roomPlaceRepository.findByRoomIdAndRemovedAtIsNotNullOrderByIdAsc(roomId);
			case ALL -> roomPlaceRepository.findByRoomIdOrderByIdAsc(roomId);
		};
		Map<String, String> names = categoryNamesOf(places);
		return new RoomPlaceListResponse(places.stream().map(place -> RoomPlaceListResponse.Item.from(place, names))
			.toList(), room.getVersion());
	}

	/**
	 * API-031. 멱등키는 컨트롤러가 이 밖에서 처리한다. 참여 확인 → 법정동 조회(외부 호출이라 트랜잭션·잠금 밖,
	 * 실패해도 regionCode·regionName 만 null) → 트랜잭션 안에서 방 잠금·참여 재확인 → 저장·version 증가.
	 * 참여자가 아닌 요청이 카카오를 부르지 않도록 먼저 한 번 확인한다.
	 */
	public CustomPlaceCreateResponse addCustomPlace(Long userId, Long roomId, CustomPlaceCreateCommand command) {
		roomFinder.getForMember(roomId, userId);
		LegalDong legalDong = legalDongResolver.resolve(command.latitude(), command.longitude());

		return transactionTemplate.execute(status -> {
			TravelRoom room = roomFinder.getForMemberForUpdate(roomId, userId).room();
			Instant now = clock.instant();
			// 직접 입력은 같은 이름·좌표여도 재사용하지 않고 새 장소 행을 만든다(명세).
			Place place = placeRepository.save(Place.manual(command.name(), command.latitude(), command.longitude(),
				command.address(), legalDong.code(), legalDong.name(), now));
			RoomPlace roomPlace = roomPlaceRepository.save(RoomPlace.manual(roomId, place, userId, now));
			// 직접 추가 장소에는 카테고리가 없다.
			return new CustomPlaceCreateResponse(RoomPlaceResponse.from(roomPlace, Map.of()), increaseVersion(room));
		});
	}

	/** 같은 멱등키로 다시 보낸 API-031 요청에 담았던 후보와 지금 여행방 version 을 돌려준다. */
	@Transactional(readOnly = true)
	public CustomPlaceCreateResponse getAddedCustomPlace(Long userId, Long roomId, Long roomPlaceId) {
		TravelRoom room = roomFinder.getForMember(roomId, userId).room();
		RoomPlace place = findInRoom(roomPlaceId, roomId);
		return new CustomPlaceCreateResponse(RoomPlaceResponse.from(place, categoryNamesOf(List.of(place))),
			room.getVersion());
	}

	/**
	 * API-032. removed_at 만 채운다. 일정 항목은 그대로 두고, 확정 일정이 있으면 재계산 필요를 켠다.
	 * 이미 뺀 후보면 version 과 상관없이 바꾸지 않고 성공한다.
	 */
	@Transactional
	public RoomPlaceRemoveResponse remove(Long userId, Long roomId, Long roomPlaceId, int version) {
		TravelRoom room = lockForMember(roomId, userId);
		RoomPlace place = findInRoom(roomPlaceId, roomId);
		if (place.isRemoved()) {
			return RoomPlaceRemoveResponse.of(room.getVersion(), room.isScheduleNeedsRecalculation());
		}
		checkVersion(room, version);
		place.remove(clock.instant());
		return RoomPlaceRemoveResponse.of(increaseVersion(room), room.isScheduleNeedsRecalculation());
	}

	/**
	 * API-094. 뺀 후보의 원래 행을 되살린다(ID·출처·추가자 그대로). 시간 제한은 없다.
	 * 이미 있는 후보면 version 과 상관없이 바꾸지 않고 성공한다.
	 */
	@Transactional
	public RoomPlaceRestoreResponse restore(Long userId, Long roomId, Long roomPlaceId, int version) {
		TravelRoom room = lockForMember(roomId, userId);
		RoomPlace place = findInRoom(roomPlaceId, roomId);
		if (!place.isRemoved()) {
			return RoomPlaceRestoreResponse.of(place.getId(), room.getVersion(), room.isScheduleNeedsRecalculation());
		}
		checkVersion(room, version);
		place.restore(clock.instant());
		return RoomPlaceRestoreResponse.of(place.getId(), increaseVersion(room), room.isScheduleNeedsRecalculation());
	}

	private TravelRoom lockForMember(Long roomId, Long userId) {
		MemberRoom found = roomFinder.getForMemberForUpdate(roomId, userId);
		return found.room();
	}

	private RoomPlace findInRoom(Long roomPlaceId, Long roomId) {
		return roomPlaceRepository.findByIdAndRoomId(roomPlaceId, roomId)
			.orElseThrow(() -> new BusinessException(ExceptionType.ROOM_PLACE_NOT_FOUND));
	}

	private Map<String, String> categoryNamesOf(List<RoomPlace> places) {
		return categoryNames.of(places.stream().map(place -> place.getPlace().getCategoryCode()).toList());
	}

	private static void checkVersion(TravelRoom room, int version) {
		if (room.getVersion() != version) {
			throw new BusinessException(ExceptionType.VERSION_MISMATCH);
		}
	}

	/** 후보 목록이 바뀌었다. 확정 일정이 있으면 재계산 필요도 켠다(같은 UPDATE 로 version 은 한 번만 오른다). */
	private int increaseVersion(TravelRoom room) {
		return roomVersions.increaseAfterPlacesChanged(room, scheduleItemRepository.existsByRoomId(room.getId()));
	}
}

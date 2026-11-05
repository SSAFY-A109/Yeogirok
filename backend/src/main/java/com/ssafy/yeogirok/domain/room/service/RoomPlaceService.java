package com.ssafy.yeogirok.domain.room.service;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.ssafy.yeogirok.domain.category.CategoryNames;
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
 * <p>TODO API-029(검색·AI 추천 담기): 투어API 장소 조회(API-026·027)와 추천 기록(recommendation_logs)이 생기면 만든다.
 * 지역은 투어API 코드(시·군·구 단위까지)를 그대로 넣지 말고, 장소 좌표(mapx·mapy)로 {@link LegalDongResolver} 를 불러
 * 직접 추가와 같은 10자리 법정동 코드·읍·면·동 이름을 저장한다(region_code 단위가 섞이지 않게).
 * TODO API-030(공개 코스에서 가져오기): 여행 기록·방문·공개 코스가 생기면 만든다.
 */
@Service
public class RoomPlaceService {

	private final RoomPlaceRepository roomPlaceRepository;
	private final CategoryNames categoryNames;
	private final RoomFinder roomFinder;
	private final RoomVersions roomVersions;
	private final LegalDongResolver legalDongResolver;
	private final TransactionTemplate transactionTemplate;
	private final Clock clock;

	public RoomPlaceService(RoomPlaceRepository roomPlaceRepository, CategoryNames categoryNames,
		RoomFinder roomFinder, RoomVersions roomVersions, LegalDongResolver legalDongResolver,
		PlatformTransactionManager transactionManager, Clock clock) {
		this.roomPlaceRepository = roomPlaceRepository;
		this.categoryNames = categoryNames;
		this.roomFinder = roomFinder;
		this.roomVersions = roomVersions;
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
			RoomPlace place = roomPlaceRepository.save(RoomPlace.manual(roomId, command.name(), command.latitude(),
				command.longitude(), command.address(), legalDong.code(), legalDong.name(), userId, clock.instant()));
			// 직접 추가 후보에는 카테고리가 없다.
			return new CustomPlaceCreateResponse(RoomPlaceResponse.from(place, Map.of()), increaseVersion(room));
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
	 * API-032. removed_at 만 채운다. 일정 항목·재계산 필요 플래그는 그대로 둔다.
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
		return categoryNames.of(places.stream().map(RoomPlace::getCategoryCode).toList());
	}

	private static void checkVersion(TravelRoom room, int version) {
		if (room.getVersion() != version) {
			throw new BusinessException(ExceptionType.VERSION_MISMATCH);
		}
	}

	private int increaseVersion(TravelRoom room) {
		return roomVersions.increase(room);
	}
}

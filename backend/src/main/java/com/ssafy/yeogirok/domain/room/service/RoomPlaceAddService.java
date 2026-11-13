package com.ssafy.yeogirok.domain.room.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.ssafy.yeogirok.domain.category.CategoryNames;
import com.ssafy.yeogirok.domain.place.entity.Place;
import com.ssafy.yeogirok.domain.place.entity.PlaceProvider;
import com.ssafy.yeogirok.domain.place.repository.PlaceRepository;
import com.ssafy.yeogirok.domain.place.tour.TourApiClient;
import com.ssafy.yeogirok.domain.place.tour.TourApiException;
import com.ssafy.yeogirok.domain.place.tour.TourPlace;
import com.ssafy.yeogirok.domain.recommendation.dto.PlaceRecommendationResponse.RecommendedPlace;
import com.ssafy.yeogirok.domain.recommendation.service.RecommendedPlaces;
import com.ssafy.yeogirok.domain.recommendation.service.RecommendedPlaces.Pick;
import com.ssafy.yeogirok.domain.recommendation.service.RecommendedPlaces.Verified;
import com.ssafy.yeogirok.domain.room.dto.RoomPlaceAddCommand;
import com.ssafy.yeogirok.domain.room.dto.RoomPlaceAddCommand.Item;
import com.ssafy.yeogirok.domain.room.dto.RoomPlaceAddResponse;
import com.ssafy.yeogirok.domain.room.dto.RoomPlaceResponse;
import com.ssafy.yeogirok.domain.room.entity.RoomPlace;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.domain.room.repository.RoomPlaceRepository;
import com.ssafy.yeogirok.domain.schedule.repository.ScheduleItemRepository;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.geo.LegalDong;
import com.ssafy.yeogirok.global.geo.LegalDongResolver;

import lombok.extern.slf4j.Slf4j;

/**
 * 검색·추천 장소 담기(API-029). 한 요청에 추천 담기(sourceType=AI)와 검색 담기(SEARCH)를 섞어 보낼 수 있다.
 *
 * <ol>
 *   <li>참여 확인 → AI 항목은 추천 기록 확인(이 방의 추천 결과에 그 순위·장소가 있었는지). 틀리면 아무것도 바꾸지 않고 C003</li>
 *   <li>장소: 같은 투어API 장소는 places 한 행을 재사용한다. 없으면 AI 는 추천 결과에 저장된 정보로, SEARCH 는 TourAPI 상세로
 *       확인한 정보로 만든다(TourAPI 에 없으면 C003, 부르지 못하면 P003). 법정동 코드·이름은 직접 추가(API-031)와 같은 단위로
 *       좌표에서 찾는다(외부 호출이라 트랜잭션·잠금 밖). 두 요청이 동시에 처음 만들면 한쪽이
 *       UNIQUE(provider, provider_place_id)에 걸리므로 새 트랜잭션에서 먼저 만든 행을 다시 읽는다.</li>
 *   <li>방 행을 잠그고 참여를 다시 확인한 뒤 후보를 요청 순서대로 만들고, 여행방 version 을 1 올리고(확정 일정이 있으면 재계산
 *       필요도 켠다), AI 후보마다 recommendation_logs ADD 를 남긴다.</li>
 * </ol>
 * 같은 장소를 새 요청으로 여러 번 담을 수 있고, 한 요청 안에 같은 장소가 두 번 있어도 후보를 두 개 만든다(팀 확정 정책).
 * 중간 항목에서 실패하면 후보·version 은 바뀌지 않는다. 그 전에 새로 만든 places 행은 남지만 다음 담기에 재사용된다.
 */
@Slf4j
@Service
public class RoomPlaceAddService {

	private final RoomFinder roomFinder;
	private final RoomVersions roomVersions;
	private final RoomPlaceRepository roomPlaceRepository;
	private final PlaceRepository placeRepository;
	private final ScheduleItemRepository scheduleItemRepository;
	private final RecommendedPlaces recommendedPlaces;
	private final LegalDongResolver legalDongResolver;
	private final TourApiClient tourApiClient;
	private final CategoryNames categoryNames;
	private final TransactionTemplate transactionTemplate;
	private final Clock clock;

	public RoomPlaceAddService(RoomFinder roomFinder, RoomVersions roomVersions,
		RoomPlaceRepository roomPlaceRepository, PlaceRepository placeRepository,
		ScheduleItemRepository scheduleItemRepository, RecommendedPlaces recommendedPlaces,
		LegalDongResolver legalDongResolver, TourApiClient tourApiClient, CategoryNames categoryNames,
		PlatformTransactionManager transactionManager, Clock clock) {
		this.tourApiClient = tourApiClient;
		this.roomFinder = roomFinder;
		this.roomVersions = roomVersions;
		this.roomPlaceRepository = roomPlaceRepository;
		this.placeRepository = placeRepository;
		this.scheduleItemRepository = scheduleItemRepository;
		this.recommendedPlaces = recommendedPlaces;
		this.legalDongResolver = legalDongResolver;
		this.categoryNames = categoryNames;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	/** 멱등키는 컨트롤러가 이 밖에서 처리한다. */
	public RoomPlaceAddResponse add(Long userId, Long roomId, RoomPlaceAddCommand command) {
		roomFinder.getForMember(roomId, userId);
		List<Item> items = command.items();
		List<Pick> picks = items.stream().filter(Item::isAi)
			.map(item -> new Pick(item.recommendationRequestId(), item.rank(), item.providerPlaceId()))
			.toList();
		// 요청 순서의 AI 항목마다 확인 결과. SEARCH 항목 자리는 null 이다.
		List<Verified> verifiedByItem = new ArrayList<>();
		Iterator<Verified> verified = (picks.isEmpty() ? List.<Verified>of()
			: recommendedPlaces.verify(roomId, picks)).iterator();
		for (Item item : items) {
			verifiedByItem.add(item.isAi() ? verified.next() : null);
		}

		Map<String, Place> places = new HashMap<>();
		for (int i = 0; i < items.size(); i++) {
			Verified pick = verifiedByItem.get(i);
			if (pick != null) {
				places.computeIfAbsent(pick.place().providerPlaceId(), id -> findOrCreate(pick.place()));
			} else {
				places.computeIfAbsent(items.get(i).providerPlaceId(), this::findOrCreateSearched);
			}
		}

		return transactionTemplate.execute(status -> {
			TravelRoom room = roomFinder.getForMemberForUpdate(roomId, userId).room();
			Instant now = clock.instant();
			List<RoomPlace> added = new ArrayList<>();
			for (int i = 0; i < items.size(); i++) {
				Verified pick = verifiedByItem.get(i);
				// 다른 트랜잭션에서 읽은 장소라 이 트랜잭션의 참조로 바꿔 연결한다.
				Place place = placeRepository.getReferenceById(places.get(items.get(i).providerPlaceId()).getId());
				if (pick != null) {
					RoomPlace roomPlace = roomPlaceRepository.save(RoomPlace.recommended(roomId, place, userId, now));
					recommendedPlaces.recordAdd(roomId, userId, pick, roomPlace.getId(), now);
					added.add(roomPlace);
				} else {
					added.add(roomPlaceRepository.save(RoomPlace.searched(roomId, place, userId, now)));
				}
			}
			int version = roomVersions.increaseAfterPlacesChanged(room, userId,
				scheduleItemRepository.existsByRoomId(roomId));
			return RoomPlaceAddResponse.of(responses(added), version);
		});
	}

	/** 같은 멱등키로 다시 보낸 요청에 담았던 후보(요청 순서)와 지금 여행방 version 을 돌려준다. */
	@Transactional(readOnly = true)
	public RoomPlaceAddResponse getAdded(Long userId, Long roomId, List<Long> roomPlaceIds) {
		TravelRoom room = roomFinder.getForMember(roomId, userId).room();
		List<RoomPlace> added = roomPlaceIds.stream()
			.map(id -> roomPlaceRepository.findByIdAndRoomId(id, roomId)
				.orElseThrow(() -> new BusinessException(ExceptionType.ROOM_PLACE_NOT_FOUND)))
			.toList();
		return RoomPlaceAddResponse.of(responses(added), room.getVersion());
	}

	/** 멱등키에 저장할 결과 ID(후보 ID 를 쉼표로 이은 값)와 그 반대. */
	public static String resultIdOf(RoomPlaceAddResponse response) {
		return response.added().stream().map(place -> place.id().toString()).collect(Collectors.joining(","));
	}

	public static List<Long> roomPlaceIdsOf(String resultId) {
		return Arrays.stream(resultId.split(",")).map(Long::valueOf).toList();
	}

	private List<RoomPlaceResponse> responses(List<RoomPlace> added) {
		Map<String, String> names = categoryNames.of(added.stream()
			.map(roomPlace -> roomPlace.getPlace().getCategoryCode()).toList());
		return added.stream().map(roomPlace -> RoomPlaceResponse.from(roomPlace, names)).toList();
	}

	/** 있으면 그 행(값을 고치지 않는다), 없으면 추천 결과의 정보로 만든다. 법정동 조회는 새로 만들 때만 한다. */
	private Place findOrCreate(RecommendedPlace recommended) {
		Place existing = transactionTemplate.execute(status -> find(recommended.providerPlaceId()));
		if (existing != null) {
			return existing;
		}
		LegalDong legalDong = legalDongResolver.resolve(recommended.latitude(), recommended.longitude());
		try {
			return transactionTemplate.execute(status -> {
				Place found = find(recommended.providerPlaceId());
				return found != null ? found : placeRepository.saveAndFlush(Place.tourApi(
					recommended.providerPlaceId(), recommended.name(), recommended.latitude(), recommended.longitude(),
					recommended.address(), legalDong.code(), legalDong.name(), recommended.categoryCode(),
					recommended.imageUrl(), recommended.imageAttribution(), clock.instant()));
			});
		} catch (DataIntegrityViolationException e) {
			log.info("같은 투어API 장소를 동시에 처음 담아 먼저 저장된 행을 씁니다. providerPlaceId={}",
				recommended.providerPlaceId());
			return transactionTemplate.execute(status -> find(recommended.providerPlaceId()));
		}
	}

	/**
	 * 검색 담기. 이미 있는 행이면 TourAPI 를 부르지 않는다. 없으면 TourAPI 상세로 장소가 실제로 있는지 확인하고 그 정보로 만든다.
	 * 없는 콘텐츠(또는 좌표 없음)면 C003, TourAPI 를 쓰지 못하면 P003.
	 */
	private Place findOrCreateSearched(String providerPlaceId) {
		Place existing = transactionTemplate.execute(status -> find(providerPlaceId));
		if (existing != null) {
			return existing;
		}
		TourPlace detail;
		try {
			detail = tourApiClient.detail(providerPlaceId).orElseThrow(() -> new BusinessException(
				ExceptionType.NOT_ALLOWED_VALUE, "items.placeRef.providerPlaceId: 담을 수 있는 투어API 장소가 아닙니다."));
		} catch (TourApiException e) {
			log.warn("검색 담기 중 TourAPI 를 쓰지 못했습니다: {}", e.getMessage());
			throw new BusinessException(ExceptionType.PLACE_PROVIDER_UNAVAILABLE);
		}
		return findOrCreate(new RecommendedPlace(PlaceProvider.TOUR_API.name(), detail.contentId(), detail.title(),
			detail.latitude(), detail.longitude(), detail.address(), detail.categoryCode(), detail.imageUrl(),
			detail.imageAttribution()));
	}

	private Place find(String providerPlaceId) {
		return placeRepository.findByProviderAndProviderPlaceId(PlaceProvider.TOUR_API, providerPlaceId).orElse(null);
	}
}

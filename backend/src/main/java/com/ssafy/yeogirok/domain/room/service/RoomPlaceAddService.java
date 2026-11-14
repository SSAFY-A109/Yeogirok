package com.ssafy.yeogirok.domain.room.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.ssafy.yeogirok.domain.category.CategoryNames;
import com.ssafy.yeogirok.domain.place.entity.Place;
import com.ssafy.yeogirok.domain.place.entity.PlaceProvider;
import com.ssafy.yeogirok.domain.place.repository.PlaceRepository;
import com.ssafy.yeogirok.domain.place.service.TourPlaces;
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

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * 검색·추천 장소 담기(API-029). 한 요청에 추천 담기(sourceType=AI)와 검색 담기(SEARCH)를 섞어 보낼 수 있다.
 *
 * <ol>
 *   <li>참여 확인 → AI 항목은 추천 기록 확인(이 방의 추천 결과에 그 순위·장소가 있었는지). 틀리면 아무것도 바꾸지 않고 C003</li>
 *   <li>장소: 같은 투어API 장소는 places 한 행을 재사용한다. 없으면 AI 는 추천 결과에 저장된 정보로, SEARCH 는 TourAPI 상세로
 *       확인한 정보로 만든다(TourAPI 에 없으면 C003, 부르지 못하면 P003). 법정동 코드·이름은 직접 추가(API-031)와 같은 단위로
 *       좌표에서 찾고, 시간 안에 못 찾으면 비워 둔다. 찾기·처음 저장(동시 저장 처리 포함)은 공식 코스 적재와 같은
 *       {@link TourPlaces} 로 한다.</li>
 *   <li>방 행을 잠그고 참여를 다시 확인한 뒤 후보를 요청 순서대로 만들고, 여행방 version 을 1 올리고(확정 일정이 있으면 재계산
 *       필요도 켠다), AI 후보마다 recommendation_logs ADD 를 남긴다.</li>
 * </ol>
 * 같은 장소를 새 요청으로 여러 번 담을 수 있고, 한 요청 안에 같은 장소가 두 번 있어도 후보를 두 개 만든다(팀 확정 정책).
 *
 * <p>외부 호출(TourAPI 상세·법정동 조회)은 트랜잭션·잠금 밖에서 동시에 하고, 둘을 합쳐 {@link #DEFAULT_EXTERNAL_TIMEOUT} 안에
 * 끝낸다. 항목이 50개여도 요청이 멱등키 '처리 중' 표시 수명(30초)을 넘지 않게 하려는 것이다. 동시 호출 수는 서비스 전체에서
 * {@link #MAX_CONCURRENT_CALLS} 개로 묶어 외부 API 호출 제한에 걸리지 않게 한다. 시간 초과로 취소한 호출이 응답을 기다리며 남아
 * 있어도 끝날 때까지 자리를 차지하므로, 남은 호출이 쌓여 외부로 나가는 연결이 늘지는 않는다.
 * TourAPI 확인은 places 행을 만들기 전에 모두 끝내므로, 확인에 실패하면 places·후보·version 모두 그대로다.
 */
@Slf4j
@Service
public class RoomPlaceAddService {

	/** 외부 호출 전체 마감 시간. 멱등키 '처리 중' 표시 수명(30초)보다 충분히 짧게 둔다. */
	static final Duration DEFAULT_EXTERNAL_TIMEOUT = Duration.ofSeconds(15);
	/** 서비스 전체에서 동시에 나가는 외부 호출 수. */
	static final int MAX_CONCURRENT_CALLS = 8;

	private final RoomFinder roomFinder;
	private final RoomVersions roomVersions;
	private final RoomPlaceRepository roomPlaceRepository;
	private final PlaceRepository placeRepository;
	private final TourPlaces tourPlaces;
	private final ScheduleItemRepository scheduleItemRepository;
	private final RecommendedPlaces recommendedPlaces;
	private final LegalDongResolver legalDongResolver;
	private final TourApiClient tourApiClient;
	private final CategoryNames categoryNames;
	private final TransactionTemplate transactionTemplate;
	private final Clock clock;
	/** 외부 동시 호출용. 호출은 대부분 응답을 기다리는 시간이라 가상 스레드를 쓴다(추천과 같은 방식). */
	private final ExecutorService externalExecutor = Executors.newVirtualThreadPerTaskExecutor();
	private final Semaphore externalCalls = new Semaphore(MAX_CONCURRENT_CALLS);
	private Duration externalTimeout = DEFAULT_EXTERNAL_TIMEOUT;

	public RoomPlaceAddService(RoomFinder roomFinder, RoomVersions roomVersions,
		RoomPlaceRepository roomPlaceRepository, PlaceRepository placeRepository, TourPlaces tourPlaces,
		ScheduleItemRepository scheduleItemRepository, RecommendedPlaces recommendedPlaces,
		LegalDongResolver legalDongResolver, TourApiClient tourApiClient, CategoryNames categoryNames,
		PlatformTransactionManager transactionManager, Clock clock) {
		this.tourApiClient = tourApiClient;
		this.roomFinder = roomFinder;
		this.roomVersions = roomVersions;
		this.roomPlaceRepository = roomPlaceRepository;
		this.placeRepository = placeRepository;
		this.tourPlaces = tourPlaces;
		this.scheduleItemRepository = scheduleItemRepository;
		this.recommendedPlaces = recommendedPlaces;
		this.legalDongResolver = legalDongResolver;
		this.categoryNames = categoryNames;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	@PreDestroy
	void shutdown() {
		externalExecutor.shutdownNow();
	}

	/** 테스트가 시간 초과를 짧게 확인할 때만 바꾼다. */
	void externalTimeout(Duration timeout) {
		this.externalTimeout = timeout;
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

		Map<String, Place> places = preparePlaces(items, verifiedByItem);

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

	/**
	 * 항목마다 연결할 places 행(providerPlaceId → 행). 있는 행은 고치지 않고 쓴다. 없는 장소는 외부 확인을 모두 끝낸 뒤 만든다.
	 * <ol>
	 *   <li>이미 있는 행 찾기(DB)</li>
	 *   <li>없는 SEARCH 장소의 TourAPI 상세(동시). 하나라도 실패하면 아무것도 만들지 않고 C003·P003</li>
	 *   <li>새로 만들 장소의 법정동(동시). 시간 안에 못 찾으면 비워 둔다</li>
	 *   <li>장소 행 만들기(DB)</li>
	 * </ol>
	 * 2·3 은 같은 마감 시각을 쓴다.
	 */
	private Map<String, Place> preparePlaces(List<Item> items, List<Verified> verifiedByItem) {
		long deadline = System.nanoTime() + externalTimeout.toNanos();
		Map<String, Place> places = new HashMap<>();
		transactionTemplate.executeWithoutResult(status -> items.stream().map(Item::providerPlaceId).distinct()
			.forEach(id -> {
				Place existing = find(id);
				if (existing != null) {
					places.put(id, existing);
				}
			}));

		// 새로 만들 장소의 정보. AI 는 추천 결과에 저장된 정보가 있어 TourAPI 를 부르지 않는다.
		Map<String, RecommendedPlace> sources = new LinkedHashMap<>();
		List<String> searchIds = new ArrayList<>();
		for (int i = 0; i < items.size(); i++) {
			String id = items.get(i).providerPlaceId();
			Verified pick = verifiedByItem.get(i);
			if (places.containsKey(id) || sources.containsKey(id)) {
				continue;
			}
			if (pick != null) {
				sources.put(id, pick.place());
			} else if (!searchIds.contains(id)) {
				searchIds.add(id);
			}
		}
		fetchDetails(searchIds, deadline).forEach((id, detail) -> sources.putIfAbsent(id, placeOf(detail)));

		Map<String, LegalDong> legalDongs = resolveLegalDongs(sources.values(), deadline);
		sources.forEach((id, source) -> places.put(id, create(source, legalDongs.get(id))));
		return places;
	}

	/** 만든다. 그사이 다른 요청이 같은 장소를 먼저 만들었으면 그 행을 쓴다. */
	private Place create(RecommendedPlace recommended, LegalDong legalDong) {
		return tourPlaces.findOrCreate(recommended.providerPlaceId(), () -> Place.tourApi(
			recommended.providerPlaceId(), recommended.name(), recommended.latitude(), recommended.longitude(),
			recommended.address(), legalDong.code(), legalDong.name(), recommended.categoryCode(),
			recommended.imageUrl(), recommended.imageAttribution(), clock.instant()));
	}

	/**
	 * 검색 담기에서 places 에 없는 장소를 TourAPI 상세로 확인한다(동시, 마감 시각까지).
	 * 없는 콘텐츠(좌표 없음·TourAPI 가 파라미터 오류로 거절한 ID 포함)면 C003, 실패·시간 초과면 P003.
	 */
	private Map<String, TourPlace> fetchDetails(List<String> contentIds, long deadline) {
		if (contentIds.isEmpty()) {
			return Map.of();
		}
		List<Future<Optional<TourPlace>>> futures = contentIds.stream()
			.map(id -> submit(() -> tourApiClient.detail(id))).toList();
		Map<String, TourPlace> details = new LinkedHashMap<>();
		try {
			for (int i = 0; i < contentIds.size(); i++) {
				Optional<TourPlace> detail = futures.get(i).get(remaining(deadline), TimeUnit.NANOSECONDS);
				details.put(contentIds.get(i), detail.orElseThrow(RoomPlaceAddService::notSearchable));
			}
			return details;
		} catch (ExecutionException e) {
			if (e.getCause() instanceof TourApiException tourApiException && tourApiException.isInvalidRequest()) {
				throw notSearchable();
			}
			log.warn("검색 담기 중 TourAPI 를 쓰지 못했습니다: {}", e.getCause().getMessage());
			throw new BusinessException(ExceptionType.PLACE_PROVIDER_UNAVAILABLE);
		} catch (TimeoutException e) {
			log.warn("검색 담기 중 TourAPI 상세 조회 시간 초과({}곳)", contentIds.size());
			throw new BusinessException(ExceptionType.PLACE_PROVIDER_UNAVAILABLE);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new BusinessException(ExceptionType.PLACE_PROVIDER_UNAVAILABLE);
		} finally {
			// 하나라도 실패하면 남은 호출은 필요 없다. 끝난 호출에는 아무 일도 하지 않는다.
			futures.forEach(future -> future.cancel(true));
		}
	}

	/**
	 * 새로 만들 장소의 법정동(동시, 마감 시각까지). 법정동은 판정 못 해도 저장하는 값이라(직접 추가와 같다) 실패·시간 초과는
	 * 오류로 만들지 않고 {@link LegalDong#EMPTY} 로 둔다.
	 */
	private Map<String, LegalDong> resolveLegalDongs(Collection<RecommendedPlace> sources, long deadline) {
		Map<String, Future<LegalDong>> futures = new LinkedHashMap<>();
		sources.forEach(source -> futures.put(source.providerPlaceId(),
			submit(() -> legalDongResolver.resolve(source.latitude(), source.longitude()))));
		Map<String, LegalDong> legalDongs = new HashMap<>();
		try {
			for (Map.Entry<String, Future<LegalDong>> entry : futures.entrySet()) {
				legalDongs.put(entry.getKey(), awaitLegalDong(entry.getKey(), entry.getValue(), deadline));
			}
			return legalDongs;
		} finally {
			futures.values().forEach(future -> future.cancel(true));
		}
	}

	private static LegalDong awaitLegalDong(String providerPlaceId, Future<LegalDong> future, long deadline) {
		try {
			return future.get(remaining(deadline), TimeUnit.NANOSECONDS);
		} catch (TimeoutException | ExecutionException e) {
			log.warn("담을 장소의 법정동을 시간 안에 찾지 못해 비워 둡니다. providerPlaceId={}", providerPlaceId);
			return LegalDong.EMPTY;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new BusinessException(ExceptionType.SERVER_ERROR, "장소 정보 확인이 중단되었습니다.");
		}
	}

	/** 동시 호출 수({@link #MAX_CONCURRENT_CALLS})를 지키며 외부 호출을 맡긴다. 자리는 호출이 실제로 끝나야 돌려준다. */
	private <T> Future<T> submit(Callable<T> call) {
		return externalExecutor.submit(() -> {
			externalCalls.acquire();
			try {
				return call.call();
			} finally {
				externalCalls.release();
			}
		});
	}

	private static long remaining(long deadline) {
		return Math.max(0, deadline - System.nanoTime());
	}

	private static BusinessException notSearchable() {
		return new BusinessException(ExceptionType.NOT_ALLOWED_VALUE,
			"items.placeRef.providerPlaceId: 담을 수 있는 투어API 장소가 아닙니다.");
	}

	/** TourAPI 상세를 장소 만들기 입력(추천 결과와 같은 모양)으로. */
	private static RecommendedPlace placeOf(TourPlace detail) {
		return new RecommendedPlace(PlaceProvider.TOUR_API.name(), detail.contentId(), detail.title(),
			detail.latitude(), detail.longitude(), detail.address(), detail.categoryCode(), detail.imageUrl(),
			detail.imageAttribution());
	}

	private Place find(String providerPlaceId) {
		return placeRepository.findByProviderAndProviderPlaceId(PlaceProvider.TOUR_API, providerPlaceId).orElse(null);
	}
}

package com.ssafy.yeogirok.domain.recommendation.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ssafy.yeogirok.domain.category.CategoryNames;
import com.ssafy.yeogirok.domain.category.TravelCategory;
import com.ssafy.yeogirok.domain.place.entity.PlaceProvider;
import com.ssafy.yeogirok.domain.place.tour.TourApiClient;
import com.ssafy.yeogirok.domain.place.tour.TourAreaQuery;
import com.ssafy.yeogirok.domain.place.tour.TourCategoryMapper;
import com.ssafy.yeogirok.domain.place.tour.TourPlace;
import com.ssafy.yeogirok.domain.recommendation.RecommendationPreferences;
import com.ssafy.yeogirok.domain.recommendation.RecommendationProperties;
import com.ssafy.yeogirok.domain.recommendation.dto.PlaceRecommendationCommand;
import com.ssafy.yeogirok.domain.recommendation.dto.PlaceRecommendationResponse;
import com.ssafy.yeogirok.domain.recommendation.dto.PlaceRecommendationResponse.Item;
import com.ssafy.yeogirok.domain.recommendation.dto.PlaceRecommendationResponse.RecommendedPlace;
import com.ssafy.yeogirok.domain.recommendation.dto.PlaceRef;
import com.ssafy.yeogirok.domain.recommendation.entity.RecommendationEventType;
import com.ssafy.yeogirok.domain.recommendation.entity.RecommendationLog;
import com.ssafy.yeogirok.domain.recommendation.entity.RecommendationMode;
import com.ssafy.yeogirok.domain.recommendation.repository.RecommendationLogRepository;
import com.ssafy.yeogirok.domain.recommendation.service.PlaceRanker.Center;
import com.ssafy.yeogirok.domain.recommendation.service.PlaceRanker.RankedPlace;
import com.ssafy.yeogirok.domain.room.entity.RoomPlace;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.domain.room.repository.PlacePopularity;
import com.ssafy.yeogirok.domain.room.repository.RoomPlaceRepository;
import com.ssafy.yeogirok.domain.room.service.RoomFinder;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

/**
 * 여행지 추천(API-034). AI 서버 없이 백엔드 규칙 점수로 만든다(11/11 결정, 점수는 {@link PlaceRanker}).
 *
 * <ol>
 *   <li>트랜잭션 안: 참여 확인, 저장된 조건(API-033) 확인, 담은 후보 좌표 읽기</li>
 *   <li>트랜잭션 밖: TourAPI 를 {@link #planQueries} 대로 동시에 부르고 확정 매핑으로 카테고리를 붙인다(외부 호출 동안 DB
 *       연결을 잡지 않는다). 전체 마감 시간({@code fetch-timeout})이 지나도 끝나지 않은 호출은 실패로 본다</li>
 *   <li>인기 집계 → 점수·순위 → recommendation_logs(REQUEST) 저장. 저장한 행 ID 가 recommendationRequestId 다</li>
 * </ol>
 * 결과는 TourAPI 응답으로만 만들고 places 행은 만들지 않는다(담기 API-029 에서 만든다). 이미 담은 장소도 빼지 않는다.
 * 일부 호출만 실패하면 나머지로 추천하고, 모두 실패하면 C014 다. TourAPI 결과가 없으면 빈 items 로 성공한다.
 */
@Slf4j
@Service
public class PlaceRecommendationService {

	static final String TOUR_IMAGE_ATTRIBUTION = TourPlace.IMAGE_ATTRIBUTION;

	private final RoomFinder roomFinder;
	private final RoomPlaceRepository roomPlaceRepository;
	private final RecommendationLogRepository recommendationLogRepository;
	private final CategoryNames categoryNames;
	private final TourApiClient tourApiClient;
	private final RecommendationProperties properties;
	private final JsonMapper jsonMapper;
	private final TransactionTemplate transactionTemplate;
	private final TransactionTemplate readOnlyTransaction;
	private final Clock clock;
	/** TourAPI 동시 호출용. 호출은 대부분 응답을 기다리는 시간이라 가상 스레드를 쓴다. */
	private final ExecutorService fetchExecutor = Executors.newVirtualThreadPerTaskExecutor();

	public PlaceRecommendationService(RoomFinder roomFinder, RoomPlaceRepository roomPlaceRepository,
		RecommendationLogRepository recommendationLogRepository, CategoryNames categoryNames,
		TourApiClient tourApiClient, RecommendationProperties properties, JsonMapper jsonMapper,
		PlatformTransactionManager transactionManager, Clock clock) {
		this.roomFinder = roomFinder;
		this.roomPlaceRepository = roomPlaceRepository;
		this.recommendationLogRepository = recommendationLogRepository;
		this.categoryNames = categoryNames;
		this.tourApiClient = tourApiClient;
		this.properties = properties;
		this.jsonMapper = jsonMapper;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.readOnlyTransaction = new TransactionTemplate(transactionManager);
		this.readOnlyTransaction.setReadOnly(true);
		this.clock = clock;
	}

	@PreDestroy
	void shutdown() {
		fetchExecutor.shutdownNow();
	}

	/** 멱등키는 컨트롤러가 이 밖에서 처리한다. 확인 순서: 방·참여(RO) → 조건 저장 여부(C001) → limit 범위(C003). */
	public PlaceRecommendationResponse recommend(Long userId, Long roomId, PlaceRecommendationCommand command) {
		RoomSnapshot snapshot = readOnlyTransaction.execute(status -> snapshot(userId, roomId));
		int limit = limitOf(command);

		Set<String> excluded = command.excludedPlaces().stream().map(PlaceRef::providerPlaceId)
			.collect(Collectors.toSet());
		Fetched fetched = fetchCandidates(snapshot.preferences(), excluded);
		Set<String> contentIds = fetched.candidates().values().stream().flatMap(List::stream)
			.map(TourPlace::contentId).collect(Collectors.toSet());

		Instant now = clock.instant();
		long popularDays = properties.popularWindow().toDays();
		RecommendationMode mode = snapshot.center() == null
			? RecommendationMode.REGIONAL_POPULAR : RecommendationMode.PERSONALIZED;

		return transactionTemplate.execute(status -> {
			// 이 방이 담은 것은 "다른 여행방이 담았다"는 인기 근거가 아니라서 센다에서 뺀다.
			Map<String, Long> popularity = contentIds.isEmpty() ? Map.of()
				: roomPlaceRepository.countOtherRoomsByProviderPlaceIds(PlaceProvider.TOUR_API, contentIds,
					now.minus(properties.popularWindow()), roomId).stream()
				.collect(Collectors.toMap(PlacePopularity::providerPlaceId, PlacePopularity::roomCount));
			List<RankedPlace> ranked = PlaceRanker.rank(fetched.candidates(), snapshot.center(),
				snapshot.radiusMeters(), popularity, popularDays,
				categoryNames.of(snapshot.preferences().categoryCodes()), limit);

			PlaceRecommendationResponse result = new PlaceRecommendationResponse(null, mode,
				ranked.stream().map(PlaceRecommendationService::toItem).toList(), now);
			RecommendationLog saved = recommendationLogRepository.save(RecommendationLog.request(roomId, userId, mode,
				input(snapshot, limit, command, fetched), toJson(result), now));
			return withId(result, saved.getId());
		});
	}

	/** 같은 멱등키로 다시 보낸 요청. 저장한 결과를 그대로 준다(TourAPI 를 다시 부르지 않는다). */
	public PlaceRecommendationResponse getRecommended(Long userId, Long roomId, Long recommendationRequestId) {
		return readOnlyTransaction.execute(status -> {
			roomFinder.getForMember(roomId, userId);
			RecommendationLog log = recommendationLogRepository
				.findByIdAndRoomIdAndEventType(recommendationRequestId, roomId, RecommendationEventType.REQUEST)
				.orElseThrow(() -> new IllegalStateException("저장한 추천 기록이 없습니다: " + recommendationRequestId));
			return withId(jsonMapper.convertValue(log.getResultPayload(), PlaceRecommendationResponse.class),
				log.getId());
		});
	}

	private int limitOf(PlaceRecommendationCommand command) {
		if (command.limit() == null) {
			return properties.defaultLimit();
		}
		if (command.limit() < 1 || command.limit() > properties.maxLimit()) {
			throw new BusinessException(ExceptionType.NOT_ALLOWED_VALUE,
				"limit: 1~" + properties.maxLimit() + " 이어야 합니다.");
		}
		return command.limit();
	}

	private RoomSnapshot snapshot(Long userId, Long roomId) {
		TravelRoom room = roomFinder.getForMember(roomId, userId).room();
		RecommendationPreferences preferences = RecommendationPreferences.from(room.getPreferences());
		if (!preferences.isReady()) {
			throw new BusinessException(ExceptionType.REQUIRED_VALUE_MISSING,
				"추천 조건(지역·카테고리)을 먼저 저장해 주세요(API-033).");
		}
		List<RoomPlace> places = roomPlaceRepository.findByRoomIdAndRemovedAtIsNullOrderByIdAsc(roomId);
		Center center = Center.average(places.stream()
			.map(place -> new Center(place.getPlace().getLatitude(), place.getPlace().getLongitude())).toList());
		int radius = room.getTransportMode() == TransportMode.TRANSIT
			? properties.transitRadiusMeters() : properties.carRadiusMeters();
		return new RoomSnapshot(preferences, center, radius, places.size(), room.getTransportMode());
	}

	/**
	 * 카테고리(선택 순서)별 후보. 조회는 동시에 하고, 합칠 때는 조회 조건 순서를 지켜 결과 순서가 늘 같게 한다.
	 * 받은 장소는 확정 매핑({@link TourCategoryMapper})으로 카테고리를 붙이고, 고른 카테고리가 아니거나 카테고리 없음이면
	 * 버린다. 같은 장소는 한 번만 넣는다.
	 */
	private Fetched fetchCandidates(RecommendationPreferences preferences, Set<String> excluded) {
		List<TravelCategory> categories = preferences.categoryCodes().stream()
			.map(code -> TravelCategory.fromCode(code).orElse(null)).filter(Objects::nonNull).toList();
		Map<String, List<TourPlace>> candidates = new LinkedHashMap<>();
		categories.forEach(category -> candidates.put(category.name(), new ArrayList<>()));

		List<TourAreaQuery> queries = planQueries(categories, preferences.regionCode(), properties.rowsPerQuery(),
			properties.maxTourApiCalls());
		List<Future<List<TourPlace>>> futures = queries.stream()
			.map(query -> fetchExecutor.submit(() -> tourApiClient.areaBasedList(query))).toList();

		long deadline = System.nanoTime() + properties.fetchTimeout().toNanos();
		Set<String> seen = new HashSet<>();
		int failures = 0;
		for (int i = 0; i < queries.size(); i++) {
			List<TourPlace> places = await(futures.get(i), queries.get(i), deadline);
			if (places == null) {
				failures++;
				continue;
			}
			for (TourPlace place : places) {
				TravelCategory category = place.category();
				List<TourPlace> bucket = category == null ? null : candidates.get(category.name());
				if (bucket != null && !excluded.contains(place.contentId()) && seen.add(place.contentId())) {
					bucket.add(place);
				}
			}
		}
		if (!queries.isEmpty() && failures == queries.size()) {
			throw new BusinessException(ExceptionType.SERVER_ERROR, "관광정보를 불러오지 못했습니다.");
		}
		return new Fetched(candidates, queries.size(), failures);
	}

	/** 마감 시간까지 기다린다. 실패·시간 초과면 로그를 남기고 null(나머지 결과로 추천한다). */
	private static List<TourPlace> await(Future<List<TourPlace>> future, TourAreaQuery query, long deadline) {
		String label = query.lclsSystm1() + (query.lclsSystm2() == null ? "" : "/" + query.lclsSystm2());
		try {
			return future.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
		} catch (TimeoutException e) {
			future.cancel(true);
			log.warn("추천 후보 조회 시간 초과(분류 {})", label);
		} catch (ExecutionException e) {
			log.warn("추천 후보 조회 실패(분류 {}): {}", label, e.getCause().getMessage());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			future.cancel(true);
			throw new BusinessException(ExceptionType.SERVER_ERROR, "관광정보 조회가 중단되었습니다.");
		}
		return null;
	}

	/**
	 * 고른 카테고리가 될 수 있는 중분류를 대분류로 묶어 TourAPI 조회 조건을 만든다.
	 * <ul>
	 *   <li>대분류 안에서 카테고리가 붙는 중분류를 모두 쓰면(예: 자연 힐링의 NA) 대분류 전체로 한 번 부른다. 받은 결과가 모두
	 *       쓸모 있어 한 번 호출로 충분하다.</li>
	 *   <li>일부만 쓰면 중분류마다 부른다. 대분류 전체로 받으면 쓰지 않는 중분류가 받을 개수를 차지해 고른 카테고리 후보가
	 *       비어 버린다(예: 랜드마크만 고르면 VE 12개 중 3개만 필요).</li>
	 *   <li>합이 {@code maxCalls} 를 넘으면 중분류가 가장 많은 대분류부터 대분류 전체 조회로 바꿔 줄인다(TourAPI 일일 한도).</li>
	 * </ul>
	 */
	public static List<TourAreaQuery> planQueries(List<TravelCategory> categories, String regionCode, int rows,
		int maxCalls) {
		Map<String, Set<String>> middlesByLarge = new LinkedHashMap<>();
		for (TravelCategory category : categories) {
			for (String middle : TourCategoryMapper.middleCodesOf(category)) {
				middlesByLarge.computeIfAbsent(middle.substring(0, 2), large -> new LinkedHashSet<>()).add(middle);
			}
		}
		Set<String> wholeLarges = new HashSet<>();
		middlesByLarge.forEach((large, middles) -> {
			if (middles.containsAll(TourCategoryMapper.middleCodesIn(large))) {
				wholeLarges.add(large);
			}
		});
		while (callCount(middlesByLarge, wholeLarges) > maxCalls) {
			String widest = middlesByLarge.entrySet().stream()
				.filter(entry -> !wholeLarges.contains(entry.getKey()))
				.max(Comparator.comparingInt(entry -> entry.getValue().size()))
				.map(Map.Entry::getKey).orElse(null);
			if (widest == null) {
				break; // 모두 대분류 전체 조회다. 더 줄일 수 없다(대분류는 많아야 8개).
			}
			wholeLarges.add(widest);
		}

		String sido = regionCode.substring(0, 2);
		String sigungu = regionCode.substring(2);
		List<TourAreaQuery> queries = new ArrayList<>();
		middlesByLarge.forEach((large, middles) -> {
			if (wholeLarges.contains(large)) {
				queries.add(new TourAreaQuery(sido, sigungu, large, null, rows));
			} else {
				middles.forEach(middle -> queries.add(new TourAreaQuery(sido, sigungu, large, middle, rows)));
			}
		});
		return queries;
	}

	private static int callCount(Map<String, Set<String>> middlesByLarge, Set<String> wholeLarges) {
		return middlesByLarge.entrySet().stream()
			.mapToInt(entry -> wholeLarges.contains(entry.getKey()) ? 1 : entry.getValue().size()).sum();
	}

	private static Item toItem(RankedPlace ranked) {
		TourPlace place = ranked.place();
		return new Item(ranked.rank(), new RecommendedPlace(PlaceProvider.TOUR_API.name(), place.contentId(),
			place.title(), place.latitude(), place.longitude(), place.address(), ranked.categoryCode(),
			place.imageUrl(), place.imageUrl() == null ? null : TOUR_IMAGE_ATTRIBUTION),
			ranked.reason(), ranked.evidence());
	}

	private static Map<String, Object> input(RoomSnapshot snapshot, int limit, PlaceRecommendationCommand command,
		Fetched fetched) {
		Map<String, Object> input = new LinkedHashMap<>();
		input.put("regionCode", snapshot.preferences().regionCode());
		input.put("categoryCodes", snapshot.preferences().categoryCodes());
		input.put("preferenceVersion", snapshot.preferences().version());
		input.put("transportMode", snapshot.transportMode().name());
		input.put("limit", limit);
		input.put("excludedPlaces", command.excludedPlaces().stream()
			.map(ref -> Map.of("provider", ref.provider(), "providerPlaceId", ref.providerPlaceId())).toList());
		input.put("roomPlaceCount", snapshot.roomPlaceCount());
		input.put("tourApiCalls", fetched.calls());
		input.put("tourApiFailures", fetched.failures());
		input.put("fetchedPlaceCount", fetched.candidates().values().stream().mapToInt(List::size).sum());
		return input;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> toJson(PlaceRecommendationResponse result) {
		return jsonMapper.convertValue(result, Map.class);
	}

	private static PlaceRecommendationResponse withId(PlaceRecommendationResponse result, Long id) {
		return new PlaceRecommendationResponse(id, result.mode(), result.items(), result.generatedAt());
	}

	/** 트랜잭션 안에서 읽은 값. roomPlaceCount 는 빼지 않은 담은 후보 수다. */
	private record RoomSnapshot(RecommendationPreferences preferences, Center center, int radiusMeters,
		int roomPlaceCount, TransportMode transportMode) {
	}

	/** 카테고리별 후보와 TourAPI 호출·실패 수(추천 기록용). */
	private record Fetched(Map<String, List<TourPlace>> candidates, int calls, int failures) {
	}
}

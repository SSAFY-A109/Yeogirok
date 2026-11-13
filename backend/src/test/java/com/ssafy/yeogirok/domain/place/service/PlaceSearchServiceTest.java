package com.ssafy.yeogirok.domain.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.ssafy.yeogirok.domain.place.dto.PlaceSearchResponse;
import com.ssafy.yeogirok.domain.place.entity.PlaceProvider;
import com.ssafy.yeogirok.domain.place.tour.TourKeywordQuery;
import com.ssafy.yeogirok.domain.place.tour.TourPlace;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.support.FakeTourApiClient;

/** API-026 조건 검사·커서·응답 변환. TourAPI 는 가짜를 쓴다. */
class PlaceSearchServiceTest {

	static final TourPlace BEACH = new TourPlace("126508", "경포해변", "강릉시 창해로 514", new BigDecimal("37.8055"),
		new BigDecimal("128.9087"), "http://img/1.jpg", "NA", "NA04", "NA040100");
	static final TourPlace NO_CATEGORY = new TourPlace("7", "경포 어딘가", null, new BigDecimal("37.8"),
		new BigDecimal("128.9"), null, null, null, null);

	final FakeTourApiClient tourApi = new FakeTourApiClient();
	final PlaceSearchService service = new PlaceSearchService(tourApi);

	private static void assertError(Runnable call, ExceptionType type) {
		assertThatThrownBy(call::run).isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(type);
	}

	@Test
	void TourAPI_검색_결과를_명세의_장소로_바꾸고_다음_쪽이_있으면_커서를_준다() {
		tourApi.willFind("경포", 45, BEACH, NO_CATEGORY);

		PlaceSearchResponse response = service.search("  경포 ", null, null, null);

		assertThat(response.items()).containsExactly(
			new PlaceSearchResponse.Item(PlaceProvider.TOUR_API, "126508", "경포해변", new BigDecimal("37.8055"),
				new BigDecimal("128.9087"), "강릉시 창해로 514", "NATURE", "http://img/1.jpg", "한국관광공사"),
			new PlaceSearchResponse.Item(PlaceProvider.TOUR_API, "7", "경포 어딘가", new BigDecimal("37.8"),
				new BigDecimal("128.9"), null, null, null, null));
		assertThat(response.totalCount()).isEqualTo(45);
		assertThat(response.nextCursor()).isEqualTo(PlaceSearchService.encodePage(2));
		assertThat(tourApi.keywordCalls()).containsExactly(new TourKeywordQuery("경포", null, null, 1, 20));
	}

	@Test
	void 커서로_다음_쪽을_받고_마지막_쪽이면_커서가_없다() {
		tourApi.willFind("경포", 45, BEACH);

		PlaceSearchResponse third = service.search("경포", null, PlaceSearchService.encodePage(3), 20);
		PlaceSearchResponse second = service.search("경포", null, PlaceSearchService.encodePage(2), 20);

		assertThat(third.nextCursor()).isNull();
		assertThat(second.nextCursor()).isEqualTo(PlaceSearchService.encodePage(3));
		assertThat(tourApi.keywordCalls()).extracting(TourKeywordQuery::pageNo).containsExactly(3, 2);
	}

	@Test
	void 결과가_없으면_빈_목록과_0건() {
		PlaceSearchResponse response = service.search("없는곳", null, null, null);

		assertThat(response.items()).isEmpty();
		assertThat(response.totalCount()).isZero();
		assertThat(response.nextCursor()).isNull();
	}

	@Test
	void regionCode_는_시도_2자리와_시군구_3자리로_나눠_보낸다() {
		service.search("카페", "51", null, 10);
		service.search("카페", "51150", null, 10);

		assertThat(tourApi.keywordCalls()).containsExactly(
			new TourKeywordQuery("카페", "51", null, 1, 10),
			new TourKeywordQuery("카페", "51", "150", 1, 10));
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = {"", "   "})
	void 검색어가_없거나_비면_P002(String q) {
		assertError(() -> service.search(q, null, null, null), ExceptionType.PLACE_SEARCH_CONDITION_INVALID);
	}

	@Test
	void 검색어는_100자까지() {
		service.search("가".repeat(100), null, null, null);
		assertError(() -> service.search("가".repeat(101), null, null, null),
			ExceptionType.PLACE_SEARCH_CONDITION_INVALID);
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "5", "511", "5115", "511501", "ab", "51-15"})
	void regionCode_가_2자리나_5자리_숫자가_아니면_P002(String regionCode) {
		assertError(() -> service.search("카페", regionCode, null, null), ExceptionType.PLACE_SEARCH_CONDITION_INVALID);
		assertThat(tourApi.keywordCalls()).isEmpty();
	}

	/** YWJj 는 "abc", MQ 는 "1"(첫 쪽은 커서 없이 받는다), MA 는 "0". */
	@ParameterizedTest
	@ValueSource(strings = {"!!", "YWJj", "MQ", "MA"})
	void 해석할_수_없는_커서는_C006(String cursor) {
		assertError(() -> service.search("카페", null, cursor, null), ExceptionType.INVALID_CURSOR);
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 51})
	void limit_이_1_50_밖이면_C007(int limit) {
		assertError(() -> service.search("카페", null, null, limit), ExceptionType.PAGE_SIZE_OUT_OF_RANGE);
	}

	@Test
	void TourAPI_를_쓰지_못하면_P003() {
		tourApi.willFailSearch();

		assertError(() -> service.search("카페", null, null, null), ExceptionType.PLACE_PROVIDER_UNAVAILABLE);
	}
}

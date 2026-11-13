package com.ssafy.yeogirok.domain.record.material;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestClient;

import com.ssafy.yeogirok.domain.place.entity.PlaceProvider;
import com.ssafy.yeogirok.domain.record.RecordProperties;
import com.ssafy.yeogirok.domain.record.RecordTestConfig;
import com.ssafy.yeogirok.domain.record.dto.RecordPlaceView;
import com.ssafy.yeogirok.global.geo.PlaceKeywordSearch;
import com.ssafy.yeogirok.global.geo.PlaceKeywordSearch.Place;
import com.ssafy.yeogirok.support.FakePlaceKeywordSearch;

/** 영수증 주소·상호로 장소 후보 고르기(specs/draft-merge.md 5절). 카카오는 가짜, 실제 호출은 미확인. */
@ExtendWith(OutputCaptureExtension.class)
class ReceiptPlaceCandidatesTest {

	static final String ADDRESS = "부산 해운대구 해운대로";
	static final String MERCHANT = "해운대 커피";
	static final Place MAIN = new Place("해운대 커피 본점", new BigDecimal("35.1631"), new BigDecimal("129.1603"),
		"부산 해운대구 해운대로 570", "부산 해운대구 우동 1411");
	static final Place OTHER_BRANCH = new Place("해운대 커피 센텀점", new BigDecimal("35.17"), new BigDecimal("129.13"),
		"부산 해운대구 센텀중앙로 55", "부산 해운대구 우동 1500");

	final FakePlaceKeywordSearch search = new FakePlaceKeywordSearch();
	final ReceiptPlaceCandidates candidates = new ReceiptPlaceCandidates(search, RecordTestConfig.RECORD_PROPERTIES);

	static OcrResult ocr(String merchant, String address) {
		return new OcrResult(merchant, null, null, address, false);
	}

	static OcrResult complete(String address) {
		return new OcrResult("가나식당", null, null, address, true);
	}

	static Place place(String name, String roadAddress, String address) {
		return new Place(name, new BigDecimal("37.65"), new BigDecimal("127.06"), roadAddress, address);
	}

	ReceiptPlaceCandidates upTo(int count) {
		return new ReceiptPlaceCandidates(search, new RecordProperties(Duration.ofHours(24), Duration.ofDays(30), count,
			RecordTestConfig.RECORD_PROPERTIES.draft()));
	}

	List<String> names(OcrResult result, Place... places) {
		search.willReturn(PlaceKeywordSearch.Result.found(List.of(places)));
		return candidates.find("m-1", result).stream().map(RecordPlaceView::name).toList();
	}

	/** 11/13 v3 주소 규격(명세 5-3). 값은 가짜다. */
	@Nested
	class 주소_모양별_같은_지점 {

		@Test
		void AC01_건물번호까지_같은_결과를_도로명만_맞는_결과보다_먼저_준다() {
			search.willReturn(PlaceKeywordSearch.Result.found(List.of(
				place("도로명만", "서울 노원구 동일로 1234", null),
				place("번호까지", "서울 노원구 동일로 123", "서울 노원구 상계동 1"))));

			assertThat(upTo(3).find("m-1", complete("서울시 노원구 동일로 123"))).extracting(RecordPlaceView::name)
				.containsExactly("번호까지", "도로명만");
		}

		@Test
		void AC02_건물번호를_잘못_읽어도_도로명이_같으면_후보다() {
			assertThat(names(complete("서울시 노원구 동일로 128"), place("가나식당 노원점", "서울 노원구 동일로 123", null)))
				.containsExactly("가나식당 노원점");
		}

		@Test
		void AC03_도로명이_다른_결과만_있으면_후보가_없다() {
			assertThat(names(complete("서울시 노원구 동일로 123"), place("다른 지점", "서울 노원구 노해로 123", null)))
				.isEmpty();
		}

		@Test
		void AC04_건물번호는_도로명_바로_다음_단어로만_같다고_본다() {
			search.willReturn(PlaceKeywordSearch.Result.found(List.of(
				place("다른 길 번호 3", "서울 노원구 동일로12길 3", null),
				place("1234", "서울 노원구 동일로 1234", null),
				place("부번", "서울 노원구 동일로 123-4", null),
				place("123", "서울 노원구 동일로 123", null))));

			// "동일로12길 3" 은 도로명도 달라 빠지고, 1234·123-4 는 도로명만 맞는 결과로 뒤에 선다.
			assertThat(upTo(3).find("m-1", complete("서울시 노원구 동일로 123"))).extracting(RecordPlaceView::name)
				.containsExactly("123", "1234", "부번");
		}

		@Test
		void 도로명은_단어_단위로_비교한다() {
			assertThat(names(ocr("가나식당", "서울시 노원구 동일로"), place("다른 도로", "서울 노원구 동일로1길 5", null)))
				.isEmpty();
		}

		@Test
		void AC05_시군구까지면_걸러진_결과가_하나일_때만_후보다() {
			Place nowon = place("가나식당 노원점", null, "서울 노원구 상계동 1");
			Place nowon2 = place("가나식당 상계점", null, "서울 노원구 상계동 99");
			Place dobong = place("가나식당 도봉점", null, "서울 도봉구 창동 1");

			assertThat(names(ocr("가나식당", "서울시 노원구"), dobong, nowon)).containsExactly("가나식당 노원점");
			assertThat(names(ocr("가나식당", "서울시 노원구"), nowon, nowon2)).isEmpty();
		}

		@Test
		void 건물번호가_붙었는데_addressComplete_false_면_도로명까지로_본다() {
			// 번호를 다 못 읽은 것일 수 있어 번호는 순서에도 쓰지 않는다.
			search.willReturn(PlaceKeywordSearch.Result.found(List.of(
				place("다른 번호", "서울 노원구 동일로 1234", null),
				place("같은 번호", "서울 노원구 동일로 123", null),
				place("다른 도로", "서울 노원구 노해로 123", null))));

			assertThat(upTo(3).find("m-1", ocr("가나식당", "서울시 노원구 동일로 123"))).extracting(RecordPlaceView::name)
				.containsExactly("다른 번호", "같은 번호");
		}

		@Test
		void 규격_밖_모양이면_후보가_없다() {
			// 읍으로 끝나는 주소.
			assertThat(names(ocr("가나식당", "경기도 양평군 양평읍"), place("가나식당", null, "경기 양평군 양평읍 1"))).isEmpty();
		}
	}

	/** 리뷰 반영: 같은 이름의 구·도로명이 다른 시·도에 있을 수 있어 시·도·시·군·구까지 맞아야 같은 지점이다. */
	@Nested
	class 같은_지역 {

		@Test
		void 도로명이_같아도_시도가_다르면_후보가_아니다() {
			assertThat(names(ocr("가나식당", "부산시 중구 중앙로"),
				place("대전점", "대전 중구 중앙로 10", null),
				place("부산점", "부산 중구 중앙로 20", null)))
				.containsExactly("부산점");
		}

		@Test
		void 시군구까지만이면_다른_시도의_같은_구는_세지_않는다() {
			assertThat(names(ocr("가나식당", "부산시 중구"), place("서울점", null, "서울 중구 명동 1"))).isEmpty();
			assertThat(names(ocr("가나식당", "부산시 중구"), place("서울점", null, "서울 중구 명동 1"),
				place("부산점", null, "부산 중구 남포동 1"))).containsExactly("부산점");
		}

		@Test
		void 시군구가_다르면_후보가_아니다() {
			assertThat(names(ocr("가나식당", "서울시 노원구 동일로"), place("도봉점", "서울 도봉구 동일로 100", null)))
				.isEmpty();
		}

		@Test
		void 시군구가_두_단어면_둘_다_맞아야_한다() {
			assertThat(names(ocr("가나식당", "경기도 수원시 장안구 정조로"),
				place("팔달구", "경기 수원시 팔달구 정조로 1", null),
				place("장안구", "경기 수원시 장안구 정조로 900", null)))
				.containsExactly("장안구");
		}

		@ParameterizedTest
		@ValueSource(strings = {"부산 중구 중앙로 20", "부산광역시 중구 중앙로 20"})
		void 시도_표기가_달라도_같은_시도로_본다(String roadAddress) {
			assertThat(names(ocr("가나식당", "부산시 중구 중앙로"), place("부산점", roadAddress, null)))
				.containsExactly("부산점");
		}

		@Test
		void 이름_표에_없는_시도면_시도는_비교하지_않고_시군구로만_맞춘다() {
			assertThat(names(ocr("가나식당", "전남광주통합특별시 목포시 영산로"),
				place("목포점", "전남 목포시 영산로 1", null)))
				.containsExactly("목포점");
		}
	}

	/** AC-07: 재원님이 준 v3 응답 예시 4개(가짜 값)를 OCR 응답 해석부터 후보 고르기까지. */
	@Nested
	class v3_응답_예시 {

		static final Place NOWON = new Place("가나식당 노원점", new BigDecimal("37.65"), new BigDecimal("127.06"),
			"서울 노원구 동일로 123", "서울 노원구 상계동 1");
		static final Place NOWON_15GIL = new Place("가나식당 노원점", new BigDecimal("37.65"), new BigDecimal("127.06"),
			"서울 노원구 노원로15길 7", "서울 노원구 상계동 2");

		OcrResult parse(String json) {
			RestClient.Builder builder = RestClient.builder();
			MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
			server.expect(MockRestRequestMatchers.requestTo("http://ocr.test/ocr/receipt"))
				.andRespond(MockRestResponseCreators.withSuccess(json, MediaType.APPLICATION_JSON));
			return new ReceiptOcrClient(builder, ReceiptOcrClientTest.PROPERTIES).recognize(new byte[] {1});
		}

		@Test
		void 건물번호까지_읽은_응답() {
			OcrResult result = parse("""
				{"status":"SUCCESS","placeName":"가나식당 노원점","date":"2026-10-20","time":"12:30","address":"서울시 노원구 동일로 123","addressComplete":true}
				""");

			assertThat(names(result, place("동일로 1234", "서울 노원구 동일로 1234", null), NOWON))
				.containsExactly("가나식당 노원점");
			assertThat(search.queries()).containsExactly("서울시 노원구 동일로 123 가나식당 노원점");
		}

		@Test
		void 도로명까지_읽은_응답() {
			OcrResult result = parse("""
				{"status":"PARTIAL","placeName":"가나식당 노원점","date":"2026-10-20","time":"12:30","address":"서울시 노원구 노원로15길","addressComplete":false}
				""");

			assertThat(names(result, NOWON, NOWON_15GIL)).containsExactly("가나식당 노원점");
			assertThat(candidates.find("m-1", result)).extracting(RecordPlaceView::address)
				.containsExactly("서울 노원구 노원로15길 7");
		}

		@Test
		void 주소를_못_읽은_응답은_검색하지_않는다() {
			OcrResult result = parse("""
				{"status":"PARTIAL","placeName":"가나식당","date":"2026-10-20","time":"12:30","address":null,"addressComplete":false}
				""");

			assertThat(names(result, NOWON)).isEmpty();
			assertThat(search.queries()).isEmpty();
		}

		@Test
		void 시군구까지만_읽은_응답() {
			OcrResult result = parse("""
				{"status":"PARTIAL","placeName":"가나식당","date":"2026-10-20","time":"12:30","address":"서울시 노원구","addressComplete":false}
				""");

			assertThat(names(result, NOWON)).containsExactly("가나식당 노원점");
			assertThat(names(result, NOWON, NOWON_15GIL)).isEmpty();
		}
	}

	@Test
	void 주소_상호로_찾아_도로명이_맞는_첫_장소를_MANUAL_후보_하나로_준다(CapturedOutput output) {
		search.willReturn(PlaceKeywordSearch.Result.found(List.of(OTHER_BRANCH, MAIN)));

		List<RecordPlaceView> found = candidates.find("m-1", ocr(MERCHANT, ADDRESS));

		assertThat(search.queries()).containsExactly(ADDRESS + " " + MERCHANT);
		assertThat(found).containsExactly(new RecordPlaceView(PlaceProvider.MANUAL, null, "해운대 커피 본점",
			new BigDecimal("35.1631"), new BigDecimal("129.1603"), "부산 해운대구 해운대로 570", null, null, null));
		assertThat(output).contains("영수증 장소 후보를 찾았습니다. materialId=m-1, count=1")
			.doesNotContain(ADDRESS).doesNotContain(MERCHANT);
	}

	@Test
	void 주소나_상호가_없으면_검색하지_않는다() {
		assertThat(candidates.find("m-1", ocr(null, ADDRESS))).isEmpty();
		assertThat(candidates.find("m-1", ocr(MERCHANT, null))).isEmpty();
		assertThat(search.queries()).isEmpty();
	}

	@Test
	void 도로명이_맞는_결과가_없으면_결과_없음(CapturedOutput output) {
		search.willReturn(PlaceKeywordSearch.Result.found(List.of(OTHER_BRANCH)));

		assertThat(candidates.find("m-1", ocr(MERCHANT, ADDRESS))).isEmpty();
		assertThat(output).contains("영수증 장소 후보 결과 없음. materialId=m-1");
	}

	@Test
	void 지번만_있어_시군구로_끝나는_주소는_지번_주소로도_맞춘다() {
		search.willReturn(PlaceKeywordSearch.Result.found(List.of(
			new Place("가게", new BigDecimal("37.6"), new BigDecimal("127.0"), null, "서울 노원구 상계동 1"))));

		assertThat(candidates.find("m-1", ocr("가게", "서울 노원구"))).extracting(RecordPlaceView::address)
			.containsExactly("서울 노원구 상계동 1");
	}

	@Test
	void 후보_개수는_설정값까지() {
		Place second = new Place("해운대 커피 2호점", new BigDecimal("35.16"), new BigDecimal("129.17"),
			"부산 해운대구 해운대로 600", null);
		search.willReturn(PlaceKeywordSearch.Result.found(List.of(MAIN, second)));
		ReceiptPlaceCandidates two = new ReceiptPlaceCandidates(search, new RecordProperties(Duration.ofHours(24),
			Duration.ofDays(30), 2, RecordTestConfig.RECORD_PROPERTIES.draft()));

		assertThat(candidates.find("m-1", ocr(MERCHANT, ADDRESS))).hasSize(1);
		assertThat(two.find("m-1", ocr(MERCHANT, ADDRESS))).extracting(RecordPlaceView::name)
			.containsExactly("해운대 커피 본점", "해운대 커피 2호점");
	}

	@Test
	void 실패_키_없음_예외는_빈_목록이고_결과를_구분해_로그를_남긴다(CapturedOutput output) {
		search.willReturn(PlaceKeywordSearch.Result.failed("HTTP 500"));
		assertThat(candidates.find("m-fail", ocr(MERCHANT, ADDRESS))).isEmpty();
		search.willReturn(PlaceKeywordSearch.Result.NO_KEY);
		assertThat(candidates.find("m-nokey", ocr(MERCHANT, ADDRESS))).isEmpty();
		search.onSearch(() -> {
			throw new IllegalStateException(ADDRESS);
		});
		assertThat(candidates.find("m-error", ocr(MERCHANT, ADDRESS))).isEmpty();

		assertThat(output).contains("영수증 장소 후보 검색 실패. materialId=m-fail, reason=HTTP 500")
			.contains("영수증 장소 후보 검색 안 함(카카오 키 없음). materialId=m-nokey")
			.contains("영수증 장소 후보 검색 실패. materialId=m-error, reason=IllegalStateException")
			.doesNotContain(ADDRESS).doesNotContain(MERCHANT);
	}
}

package com.ssafy.yeogirok.domain.record.material;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

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

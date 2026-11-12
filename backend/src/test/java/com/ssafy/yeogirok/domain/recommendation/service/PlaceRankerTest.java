package com.ssafy.yeogirok.domain.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ssafy.yeogirok.domain.place.tour.TourPlace;
import com.ssafy.yeogirok.domain.recommendation.service.PlaceRanker.Center;
import com.ssafy.yeogirok.domain.recommendation.service.PlaceRanker.RankedPlace;

/** 규칙 점수·카테고리 섞기·이유 문장. */
class PlaceRankerTest {

	/** 강릉역 근처. 위도 0.01도 ≈ 1.1km. */
	static final Center CENTER = new Center(new BigDecimal("37.7640"), new BigDecimal("128.8990"));
	static final int RADIUS = 20_000;

	static TourPlace place(String id, String lat, String lng) {
		return new TourPlace(id, "장소" + id, null, new BigDecimal(lat), new BigDecimal(lng), null, null, null, null);
	}

	static Map<String, List<TourPlace>> candidates(Object... categoryAndPlaces) {
		Map<String, List<TourPlace>> map = new LinkedHashMap<>();
		for (int i = 0; i < categoryAndPlaces.length; i += 2) {
			@SuppressWarnings("unchecked")
			List<TourPlace> places = (List<TourPlace>) categoryAndPlaces[i + 1];
			map.put((String) categoryAndPlaces[i], places);
		}
		return map;
	}

	static List<String> ids(List<RankedPlace> ranked) {
		return ranked.stream().map(r -> r.place().contentId()).toList();
	}

	@Test
	void 담은_후보가_없으면_인기순이고_인기가_같으면_TourAPI_순서를_지킨다() {
		var ranked = PlaceRanker.rank(candidates("CAFE", List.of(
				place("a", "37.70", "128.90"), place("b", "37.71", "128.90"), place("c", "37.72", "128.90"))),
			null, RADIUS, Map.of("c", 3L), 30, Map.of(), 10);

		assertThat(ids(ranked)).containsExactly("c", "a", "b");
		assertThat(ranked).extracting(RankedPlace::rank).containsExactly(1, 2, 3);
	}

	@Test
	void 담은_후보가_있으면_가까운_곳이_먼저다() {
		var ranked = PlaceRanker.rank(candidates("NATURE", List.of(
				place("far", "37.90", "128.90"), place("near", "37.765", "128.899"))),
			CENTER, RADIUS, Map.of(), 30, Map.of(), 10);

		assertThat(ids(ranked)).containsExactly("near", "far");
	}

	@Test
	void 동선이_비슷하면_인기가_순위를_가른다() {
		var ranked = PlaceRanker.rank(candidates("NATURE", List.of(
				place("quiet", "37.765", "128.899"), place("popular", "37.766", "128.899"))),
			CENTER, RADIUS, Map.of("popular", 5L), 30, Map.of(), 10);

		assertThat(ids(ranked)).containsExactly("popular", "quiet");
	}

	@Test
	void 기준거리_밖은_동선_0점이다() {
		assertThat(PlaceRanker.score(25_000.0, RADIUS, 0, 0)).isZero();
		assertThat(PlaceRanker.score(0.0, RADIUS, 0, 0)).isEqualTo(PlaceRanker.DISTANCE_WEIGHT);
		assertThat(PlaceRanker.score(null, RADIUS, 4, 4)).isEqualTo(1.0);
	}

	@Test
	void 카테고리를_선택_순서대로_번갈아_뽑고_limit_에서_멈춘다() {
		var ranked = PlaceRanker.rank(candidates(
				"CAFE", List.of(place("c1", "37.70", "128.90"), place("c2", "37.70", "128.90"), place("c3", "37.70", "128.90")),
				"FOOD", List.of(place("f1", "37.70", "128.90"))),
			null, RADIUS, Map.of(), 30, Map.of(), 4);

		assertThat(ids(ranked)).containsExactly("c1", "f1", "c2", "c3");
		assertThat(ranked).extracting(RankedPlace::categoryCode).containsExactly("CAFE", "FOOD", "CAFE", "CAFE");

		assertThat(ids(PlaceRanker.rank(candidates("CAFE", List.of(place("c1", "37.70", "128.90"),
			place("c2", "37.70", "128.90"))), null, RADIUS, Map.of(), 30, Map.of(), 1))).containsExactly("c1");
	}

	@Test
	void 여러_카테고리에_같은_장소가_있으면_처음_카테고리로_한_번만_낸다() {
		var ranked = PlaceRanker.rank(candidates(
				"ACTIVITY", List.of(place("x", "37.70", "128.90")),
				"NATURE", List.of(place("x", "37.70", "128.90"), place("y", "37.70", "128.90"))),
			null, RADIUS, Map.of(), 30, Map.of(), 10);

		assertThat(ids(ranked)).containsExactly("x", "y");
		assertThat(ranked.getFirst().categoryCode()).isEqualTo("ACTIVITY");
	}

	@Test
	void 이유는_점수에_쓴_근거만으로_만든다() {
		var withAll = PlaceRanker.rank(candidates("CAFE", List.of(place("a", "37.775", "128.899"))),
			CENTER, RADIUS, Map.of("a", 3L), 30, Map.of("CAFE", "카페"), 10).getFirst();

		assertThat(withAll.reason()).isEqualTo("카페 · 담은 장소들과 1.2km · 최근 30일 여행방 3곳이 담았어요");
		assertThat(withAll.evidence()).containsExactly("카테고리 일치: 카페", "담은 장소들과 1.2km", "최근 30일 여행방 3곳이 담음");

		var categoryOnly = PlaceRanker.rank(candidates("CAFE", List.of(place("a", "37.70", "128.90"))),
			null, RADIUS, Map.of(), 30, Map.of(), 10).getFirst();

		assertThat(categoryOnly.reason()).isEqualTo("선택한 카테고리");
		assertThat(categoryOnly.evidence()).containsExactly("카테고리 일치");
	}

	@Test
	void 거리_표시() {
		assertThat(PlaceRanker.formatDistance(347)).isEqualTo("350m");
		assertThat(PlaceRanker.formatDistance(994)).isEqualTo("990m");
		assertThat(PlaceRanker.formatDistance(999)).isEqualTo("1.0km");
		assertThat(PlaceRanker.formatDistance(1000)).isEqualTo("1.0km");
		assertThat(PlaceRanker.formatDistance(12_345)).isEqualTo("12.3km");
	}

	@Test
	void 중심점은_좌표_평균이고_좌표가_없으면_null() {
		assertThat(Center.average(List.of())).isNull();
		Center center = Center.average(List.of(new Center(new BigDecimal("37.0"), new BigDecimal("128.0")),
			new Center(new BigDecimal("38.0"), new BigDecimal("129.0"))));
		assertThat(center.latitude().doubleValue()).isEqualTo(37.5);
		assertThat(center.longitude().doubleValue()).isEqualTo(128.5);
	}
}

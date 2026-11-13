package com.ssafy.yeogirok.domain.record.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ssafy.yeogirok.domain.record.RecordProperties;
import com.ssafy.yeogirok.domain.file.session.ReceiptMaterial;
import com.ssafy.yeogirok.domain.place.entity.PlaceProvider;
import com.ssafy.yeogirok.domain.record.draft.PhotoMetadataReader.PhotoMetadata;
import com.ssafy.yeogirok.domain.record.draft.RecordDraft.Evidence;
import com.ssafy.yeogirok.domain.record.draft.RecordDraft.FieldSources;
import com.ssafy.yeogirok.domain.record.draft.RecordDraft.Item;
import com.ssafy.yeogirok.domain.record.draft.RecordDraft.SnapshotMaterial;
import com.ssafy.yeogirok.domain.record.draft.RecordDraft.SourceType;
import com.ssafy.yeogirok.domain.record.draft.RecordDraftComposer.Photo;
import com.ssafy.yeogirok.domain.record.draft.RecordDraftComposer.PlannedVisit;
import com.ssafy.yeogirok.domain.record.dto.RecordPlaceView;
import com.ssafy.yeogirok.global.response.ApiDateTime;

/**
 * 초안 항목 만들기 규칙(명세 specs/record-api-2.md 4-7 생성 규칙 1·2·4·5, specs/draft-merge.md 3절). 기준은 사진-영수증
 * 60분·500m, 사진 묶음 60분·500m, 계획-영수증 150m, 계획-사진 300m(모두 이하면 같은 곳).
 */
class RecordDraftComposerTest {

	static final RecordProperties.Draft RULES = new RecordProperties.Draft(Duration.ofMinutes(60), 500,
		Duration.ofMinutes(60), 500, 150, 300);
	/** 경포대 근처. 위도 0.01도는 약 1.1km. */
	static final BigDecimal LAT = new BigDecimal("37.7950000");
	static final BigDecimal LNG = new BigDecimal("128.8960000");
	static final RecordPlaceView CAFE = new RecordPlaceView(PlaceProvider.MANUAL, null, "안목 카페", LAT, LNG, null, null,
		null, null);
	/** 2026-11-02 12:00 KST. */
	static final Instant NOON = Instant.parse("2026-11-02T03:00:00Z");

	static SnapshotMaterial receipt(String id, Instant paidAt, RecordPlaceView place) {
		LocalTime time = paidAt.atZone(ApiDateTime.ZONE).toLocalTime();
		LocalDate date = paidAt.atZone(ApiDateTime.ZONE).toLocalDate();
		return new SnapshotMaterial(id, 3, new ReceiptMaterial.Values("가게 " + id, paidAt, date, time, place));
	}

	static Photo photo(long id, Instant takenAt, BigDecimal lat, BigDecimal lng) {
		return new Photo(id, Optional.of(new PhotoMetadata(takenAt, lat, lng)));
	}

	static Photo noExif(long id) {
		return new Photo(id, Optional.empty());
	}

	@Test
	void 영수증마다_항목_하나이고_장소와_결제_시각을_그대로_쓴다() {
		RecordDraftComposer.Result result = RecordDraftComposer.compose(List.of(receipt("m-1", NOON, CAFE)), List.of(),
			List.of(), RULES);

		assertThat(result.items()).containsExactly(new Item(1, List.of("m-1"), List.of(CAFE), LocalDate.of(2026, 11, 2),
			NOON, false, new Evidence(List.of(SourceType.RECEIPT),
				new FieldSources(SourceType.RECEIPT, SourceType.RECEIPT, SourceType.RECEIPT), List.of()),
			List.of(), null, CAFE));
		assertThat(result.warnings()).isEmpty();
	}

	@Test
	void EXIF_없는_사진은_근거로_쓰지_않고_경고를_한_번만_넣는다() {
		RecordDraftComposer.Result result = RecordDraftComposer.compose(List.of(receipt("m-1", NOON, CAFE)),
			List.of(noExif(1), noExif(2)), List.of(), RULES);

		assertThat(result.items()).singleElement().satisfies(item -> assertThat(item.sourcePhotoIds()).isEmpty());
		assertThat(result.warnings()).containsExactly(RecordDraft.PHOTO_METADATA_MISSING);
	}

	@Test
	void 결제_시각_60분_안의_사진은_영수증_근거로_붙고_필드_값은_그대로다() {
		RecordDraftComposer.Result result = RecordDraftComposer.compose(List.of(receipt("m-1", NOON, CAFE)),
			List.of(photo(7, NOON.plus(Duration.ofMinutes(60)), LAT, LNG), photo(8, NOON.minusSeconds(600), null, null)),
			List.of(), RULES);

		assertThat(result.items()).singleElement().satisfies(item -> {
			assertThat(item.sourcePhotoIds()).containsExactly(8L, 7L);
			assertThat(item.evidence().sourceTypes()).containsExactly(SourceType.RECEIPT, SourceType.PHOTO);
			assertThat(item.evidence().fieldSources().proposedVisitedAt()).isEqualTo(SourceType.RECEIPT);
			assertThat(item.proposedVisitedAt()).isEqualTo(NOON);
			assertThat(item.needsReview()).isFalse();
		});
	}

	@Test
	void 사진은_가장_가까운_영수증에_붙는다() {
		RecordDraftComposer.Result result = RecordDraftComposer.compose(
			List.of(receipt("m-1", NOON, CAFE), receipt("m-2", NOON.plus(Duration.ofMinutes(50)), CAFE)),
			List.of(photo(7, NOON.plus(Duration.ofMinutes(30)), null, null)), List.of(), RULES);

		assertThat(result.items()).extracting(Item::sourcePhotoIds).containsExactly(List.of(), List.of(7L));
	}

	@Test
	void 시간이_60분_넘게_차이나거나_500m_넘게_멀면_PHOTO_항목이_된다() {
		Instant farAway = NOON.plus(Duration.ofMinutes(10));
		RecordDraftComposer.Result result = RecordDraftComposer.compose(List.of(receipt("m-1", NOON, CAFE)),
			List.of(photo(7, NOON.plus(Duration.ofMinutes(61)), null, null),
				photo(8, farAway, LAT.add(new BigDecimal("0.0100000")), LNG)),
			List.of(), RULES);

		// 붙지 않은 두 사진은 51분 차이이고 한쪽에 GPS 가 없어 한 묶음이다.
		assertThat(result.items()).hasSize(2);
		assertThat(result.items().getFirst().sourcePhotoIds()).isEmpty();
		Item photoItem = result.items().get(1);
		assertThat(photoItem.sourcePhotoIds()).containsExactly(8L, 7L);
		assertThat(photoItem.needsReview()).isTrue();
		assertThat(photoItem.proposedPlace()).isNull();
		assertThat(photoItem.placeCandidates()).isEmpty();
		assertThat(photoItem.sourceMaterialIds()).isEmpty();
		assertThat(photoItem.proposedVisitedAt()).isEqualTo(farAway);
		assertThat(photoItem.evidence()).isEqualTo(new Evidence(List.of(SourceType.PHOTO),
			new FieldSources(null, SourceType.PHOTO, SourceType.PHOTO), List.of(RecordDraft.PHOTO_PLACE_UNRESOLVED)));
	}

	@Test
	void 날짜가_다르면_60분_안이어도_붙지_않는다() {
		Instant beforeMidnight = Instant.parse("2026-11-02T14:50:00Z");
		Instant afterMidnight = Instant.parse("2026-11-02T15:10:00Z");

		RecordDraftComposer.Result result = RecordDraftComposer.compose(List.of(receipt("m-1", beforeMidnight, CAFE)),
			List.of(photo(7, afterMidnight, null, null)), List.of(), RULES);

		assertThat(result.items()).hasSize(2);
		assertThat(result.items().get(1).visitDate()).isEqualTo(LocalDate.of(2026, 11, 3));
	}

	@Test
	void 남은_사진은_60분_또는_500m_를_넘으면_새_묶음이다() {
		Instant start = Instant.parse("2026-11-03T01:00:00Z");
		RecordDraftComposer.Result result = RecordDraftComposer.compose(List.of(), List.of(
			photo(1, start, LAT, LNG),
			photo(2, start.plus(Duration.ofMinutes(60)), LAT, LNG),
			photo(3, start.plus(Duration.ofMinutes(121)), LAT, LNG),
			photo(4, start.plus(Duration.ofMinutes(125)), LAT.add(new BigDecimal("0.0050000")), LNG),
			photo(5, start.plus(Duration.ofMinutes(126)), null, null)), List.of(), RULES);

		assertThat(result.items()).extracting(Item::sourcePhotoIds)
			.containsExactly(List.of(1L, 2L), List.of(3L), List.of(4L, 5L));
		assertThat(result.items()).extracting(Item::itemId).containsExactly(1, 2, 3);
	}

	@Test
	void 같은_장소여도_합치지_않고_시각순으로_번호를_매긴다() {
		Instant later = NOON.plus(Duration.ofHours(3));
		RecordDraftComposer.Result result = RecordDraftComposer.compose(
			List.of(receipt("m-late", later, CAFE), receipt("m-early", NOON, CAFE)), List.of(), List.of(), RULES);

		assertThat(result.items()).extracting(Item::itemId, Item::sourceMaterialIds)
			.containsExactly(tuple(1, List.of("m-early")), tuple(2, List.of("m-late")));
	}

	/* ── 참고 계획 항목(명세 specs/record-plan.md 3-4, 5절 4 A안·5) ───────────── */

	static final LocalDate DAY = LocalDate.of(2026, 11, 2);

	@Test
	void 계획_항목마다_PLAN_항목이_생기고_시각은_비우고_확인_필요다() {
		RecordDraftComposer.Result result = RecordDraftComposer.compose(List.of(), List.of(),
			List.of(new PlannedVisit(501L, DAY, CAFE, null), new PlannedVisit(502L, DAY, null, null)), RULES);

		assertThat(result.items()).containsExactly(
			new Item(1, List.of(), List.of(CAFE), DAY, null, true, new Evidence(List.of(SourceType.PLAN),
				new FieldSources(SourceType.PLAN, SourceType.PLAN, null), List.of(RecordDraft.PLAN_NOT_CONFIRMED)),
				List.of(), 501L, CAFE),
			new Item(2, List.of(), List.of(), DAY, null, true, new Evidence(List.of(SourceType.PLAN),
				new FieldSources(null, SourceType.PLAN, null), List.of(RecordDraft.PLAN_NOT_CONFIRMED)),
				List.of(), 502L, null));
	}

	@Test
	void 같은_날_같은_장소의_영수증이_있으면_계획은_영수증_항목에_합쳐지고_사진_근거도_남는다() {
		RecordDraftComposer.Result result = RecordDraftComposer.compose(List.of(receipt("m-1", NOON, CAFE)),
			List.of(photo(7, NOON.plusSeconds(600), LAT, LNG)), List.of(new PlannedVisit(501L, DAY, CAFE, null)), RULES);

		assertThat(result.items()).singleElement().satisfies(item -> {
			assertThat(item.sourceMaterialIds()).containsExactly("m-1");
			assertThat(item.plannedItemId()).isEqualTo(501L);
			assertThat(item.sourcePhotoIds()).containsExactly(7L);
			assertThat(item.evidence()).isEqualTo(new Evidence(List.of(SourceType.RECEIPT, SourceType.PHOTO, SourceType.PLAN),
				new FieldSources(SourceType.RECEIPT, SourceType.RECEIPT, SourceType.RECEIPT), List.of()));
			assertThat(item.proposedPlace()).isEqualTo(CAFE);
			assertThat(item.proposedVisitedAt()).isEqualTo(NOON);
			assertThat(item.needsReview()).isFalse();
		});
	}

	@Test
	void 날짜_먼저_정렬하고_같은_날에는_시각_있는_항목_뒤에_계획_순서대로_둔다() {
		LocalDate dayBefore = DAY.minusDays(1);
		RecordDraftComposer.Result result = RecordDraftComposer.compose(
			List.of(receipt("m-1", NOON, CAFE)), List.of(),
			List.of(new PlannedVisit(503L, DAY, null, null), new PlannedVisit(501L, DAY, null, null),
				new PlannedVisit(502L, dayBefore, null, null)), RULES);

		assertThat(result.items()).extracting(Item::itemId, Item::visitDate, Item::plannedItemId)
			.containsExactly(tuple(1, dayBefore, 502L), tuple(2, DAY, null), tuple(3, DAY, 503L), tuple(4, DAY, 501L));
	}

	/* ── 거리 기준 경계(이하면 같은 곳) ───────────── */

	/** 기준점에서 북쪽으로 meters 만큼 떨어진 위도. 좌표가 소수 7자리라 at 은 내림(기준 이하), over 는 올림(기준 초과). */
	static BigDecimal north(double meters, RoundingMode mode) {
		double degrees = Math.toDegrees(meters / 6_371_008.8);
		return LAT.add(BigDecimal.valueOf(degrees).setScale(7, mode));
	}

	static BigDecimal at(double meters) {
		return north(meters, RoundingMode.DOWN);
	}

	static BigDecimal over(double meters) {
		return north(meters, RoundingMode.UP);
	}

	@Test
	void 사진과_영수증_장소가_500m_이하면_붙고_501m_면_붙지_않는다() {
		Instant later = NOON.plus(Duration.ofHours(3));
		RecordDraftComposer.Result result = RecordDraftComposer.compose(
			List.of(receipt("m-1", NOON, CAFE), receipt("m-2", later, CAFE)),
			List.of(photo(7, NOON.plusSeconds(60), at(500), LNG), photo(8, later.plusSeconds(60), over(501), LNG)),
			List.of(), RULES);

		assertThat(result.items()).extracting(Item::sourceMaterialIds, Item::sourcePhotoIds).containsExactly(
			tuple(List.of("m-1"), List.of(7L)), tuple(List.of("m-2"), List.of()), tuple(List.of(), List.of(8L)));
	}

	@Test
	void 남은_사진은_앞_사진과_500m_이하면_같은_묶음이고_501m_면_새_묶음이다() {
		Instant start = Instant.parse("2026-11-03T01:00:00Z");
		RecordDraftComposer.Result result = RecordDraftComposer.compose(List.of(), List.of(
			photo(1, start, LAT, LNG),
			photo(2, start.plus(Duration.ofMinutes(1)), at(500), LNG),
			photo(3, start.plus(Duration.ofHours(3)), LAT, LNG),
			photo(4, start.plus(Duration.ofHours(3)).plus(Duration.ofMinutes(1)), over(501), LNG)), List.of(), RULES);

		assertThat(result.items()).extracting(Item::sourcePhotoIds)
			.containsExactly(List.of(1L, 2L), List.of(3L), List.of(4L));
	}

	/* ── 208 계획 + 영수증 합치기(specs/draft-merge.md 3-2) ───────────── */

	static RecordPlaceView tourPlace(String id, BigDecimal latitude) {
		return new RecordPlaceView(PlaceProvider.TOUR_API, id, "투어 " + id, latitude, LNG, null, null, null, null);
	}

	static RecordPlaceView manualPlace(BigDecimal latitude) {
		return new RecordPlaceView(PlaceProvider.MANUAL, null, "직접 입력", latitude, LNG, null, null, null, null);
	}

	static PlannedVisit plan(long id, RecordPlaceView place, Instant arrivalAt) {
		return new PlannedVisit(id, DAY, place, arrivalAt);
	}

	@Test
	void 같은_투어API_장소면_거리와_상관없이_합쳐지고_값과_필드_출처는_영수증_그대로다() {
		RecordPlaceView receiptPlace = tourPlace("T1", LAT);
		RecordDraftComposer.Result result = RecordDraftComposer.compose(List.of(receipt("m-1", NOON, receiptPlace)),
			List.of(), List.of(plan(501, tourPlace("T1", LAT.add(new BigDecimal("0.0200000"))), null)), RULES);

		assertThat(result.items()).containsExactly(new Item(1, List.of("m-1"), List.of(receiptPlace), DAY, NOON, false,
			new Evidence(List.of(SourceType.RECEIPT, SourceType.PLAN),
				new FieldSources(SourceType.RECEIPT, SourceType.RECEIPT, SourceType.RECEIPT), List.of()),
			List.of(), 501L, receiptPlace));
	}

	@Test
	void 직접_입력_확정_장소와_계획_장소가_150m_이하면_합쳐지고_151m_면_따로다() {
		Instant later = NOON.plus(Duration.ofHours(3));
		RecordDraftComposer.Result result = RecordDraftComposer.compose(
			List.of(receipt("m-1", NOON, manualPlace(at(150))), receipt("m-2", later, manualPlace(over(151)))),
			List.of(), List.of(plan(501, tourPlace("T1", LAT), null), plan(502, tourPlace("T2", LAT), null)), RULES);

		// 501 은 150m 영수증과 합쳐지고, 151m 영수증은 어느 계획과도 맞지 않아 502 가 PLAN 항목으로 남는다.
		assertThat(result.items()).extracting(Item::sourceMaterialIds, Item::plannedItemId).containsExactly(
			tuple(List.of("m-1"), 501L), tuple(List.of("m-2"), null), tuple(List.of(), 502L));
	}

	@Test
	void 날짜가_다르거나_장소_좌표가_없으면_합치지_않는다() {
		RecordPlaceView noCoordinate = new RecordPlaceView(PlaceProvider.MANUAL, null, "좌표 없음", null, null, null,
			null, null, null);
		RecordDraftComposer.Result result = RecordDraftComposer.compose(
			List.of(receipt("m-1", NOON, CAFE), receipt("m-2", NOON.plus(Duration.ofHours(1)), noCoordinate)),
			List.of(), List.of(new PlannedVisit(501L, DAY.plusDays(1), CAFE, null), plan(502, null, null),
				plan(503, CAFE, null)), RULES);

		// 503 만 같은 날·같은 좌표인 m-1 과 합쳐진다. 501(다른 날), 502(계획 장소 없음)는 PLAN 항목.
		assertThat(result.items()).extracting(Item::sourceMaterialIds, Item::plannedItemId).containsExactly(
			tuple(List.of("m-1"), 503L), tuple(List.of("m-2"), null), tuple(List.of(), 502L), tuple(List.of(), 501L));
	}

	@Test
	void 한_계획에_영수증이_여럿이면_도착_시각과_결제_시각이_가장_가까운_하나에만_붙는다() {
		Instant afternoon = NOON.plus(Duration.ofHours(3));
		RecordDraftComposer.Result result = RecordDraftComposer.compose(
			List.of(receipt("m-noon", NOON, CAFE), receipt("m-afternoon", afternoon, CAFE)), List.of(),
			List.of(plan(501, CAFE, afternoon.minus(Duration.ofMinutes(30)))), RULES);

		assertThat(result.items()).extracting(Item::sourceMaterialIds, Item::plannedItemId).containsExactly(
			tuple(List.of("m-noon"), null), tuple(List.of("m-afternoon"), 501L));
	}

	@Test
	void 한_영수증에_계획이_여럿이면_하나만_붙고_나머지는_PLAN_항목으로_남는다() {
		RecordDraftComposer.Result result = RecordDraftComposer.compose(List.of(receipt("m-1", NOON, CAFE)), List.of(),
			List.of(plan(501, CAFE, NOON.minus(Duration.ofHours(2))), plan(502, CAFE, NOON.minus(Duration.ofMinutes(10)))),
			RULES);

		assertThat(result.items()).extracting(Item::sourceMaterialIds, Item::plannedItemId).containsExactly(
			tuple(List.of("m-1"), 502L), tuple(List.of(), 501L));
	}

	@Test
	void 도착_시각이_없으면_더_가까운_영수증에_붙는다() {
		RecordDraftComposer.Result result = RecordDraftComposer.compose(
			List.of(receipt("m-far", NOON, manualPlace(at(100))), receipt("m-near", NOON.plus(Duration.ofHours(1)),
				manualPlace(at(10)))),
			List.of(), List.of(plan(501, tourPlace("T1", LAT), null)), RULES);

		assertThat(result.items()).extracting(Item::sourceMaterialIds, Item::plannedItemId).containsExactly(
			tuple(List.of("m-far"), null), tuple(List.of("m-near"), 501L));
	}

	/** 계획 순서대로 고르면 A 가 더 가까운 R1 을 가져가 B 가 남는다. 전체 쌍을 정렬해 B-R1(5분)·A-R2 로 맞춘다. */
	@Test
	void 앞_계획이_뒤_계획에_더_맞는_영수증을_가져가지_않는다() {
		Instant b = NOON.plus(Duration.ofHours(3));
		RecordDraftComposer.Result result = RecordDraftComposer.compose(
			List.of(receipt("r1", b.plus(Duration.ofMinutes(5)), manualPlace(LAT)),
				receipt("r2", NOON, manualPlace(at(100)))),
			List.of(), List.of(plan(501, tourPlace("A", LAT), null),
				new PlannedVisit(502L, DAY, tourPlace("B", LAT.subtract(at(100).subtract(LAT))), b)), RULES);

		assertThat(result.items()).extracting(Item::sourceMaterialIds, Item::plannedItemId).containsExactly(
			tuple(List.of("r2"), 501L), tuple(List.of("r1"), 502L));
	}

	/* ── 209 계획 + 사진 묶음 합치기(specs/draft-merge.md 3-3) ───────────── */

	/** 2026-11-02 15:00 KST. 영수증(12:00)과 60분 넘게 떨어져 사진이 영수증에 붙지 않는다. */
	static final Instant AFTERNOON = NOON.plus(Duration.ofHours(3));

	@Test
	void 계획_장소에서_300m_이하인_사진_묶음은_장소는_계획_시각은_사진으로_합쳐진다() {
		RecordPlaceView beach = tourPlace("T1", LAT);
		RecordDraftComposer.Result result = RecordDraftComposer.compose(List.of(),
			List.of(photo(7, AFTERNOON, at(300), LNG)), List.of(plan(501, beach, NOON)), RULES);

		assertThat(result.items()).containsExactly(new Item(1, List.of(), List.of(beach), DAY, AFTERNOON, true,
			new Evidence(List.of(SourceType.PHOTO, SourceType.PLAN),
				new FieldSources(SourceType.PLAN, SourceType.PHOTO, SourceType.PHOTO), List.of()),
			List.of(7L), 501L, beach));
	}

	@Test
	void 사진_묶음이_계획_장소에서_301m_면_따로다() {
		RecordDraftComposer.Result result = RecordDraftComposer.compose(List.of(),
			List.of(photo(7, AFTERNOON, over(301), LNG)), List.of(plan(501, tourPlace("T1", LAT), null)), RULES);

		assertThat(result.items()).extracting(Item::sourcePhotoIds, Item::plannedItemId)
			.containsExactly(tuple(List.of(7L), null), tuple(List.of(), 501L));
	}

	@Test
	void 묶음_거리는_계획_장소와_가장_가까운_GPS_사진으로_잰다() {
		// 두 사진은 450m 떨어져 한 묶음이다. 첫 사진은 계획에서 550m, 둘째는 100m.
		RecordDraftComposer.Result result = RecordDraftComposer.compose(List.of(),
			List.of(photo(7, AFTERNOON, at(550), LNG), photo(8, AFTERNOON.plusSeconds(600), at(100), LNG),
				photo(9, AFTERNOON.plusSeconds(700), null, null)),
			List.of(plan(501, tourPlace("T1", LAT), null)), RULES);

		assertThat(result.items()).singleElement().satisfies(item -> {
			assertThat(item.sourcePhotoIds()).containsExactly(7L, 8L, 9L);
			assertThat(item.plannedItemId()).isEqualTo(501L);
		});
	}

	@Test
	void GPS_없는_묶음이나_다른_날_묶음은_합치지_않는다() {
		RecordDraftComposer.Result result = RecordDraftComposer.compose(List.of(),
			List.of(photo(7, AFTERNOON, null, null), photo(8, AFTERNOON.plus(Duration.ofDays(1)), LAT, LNG)),
			List.of(plan(501, tourPlace("T1", LAT), null)), RULES);

		assertThat(result.items()).extracting(Item::visitDate, Item::sourcePhotoIds, Item::plannedItemId).containsExactly(
			tuple(DAY, List.of(7L), null), tuple(DAY, List.of(), 501L), tuple(DAY.plusDays(1), List.of(8L), null));
	}

	@Test
	void 한_계획은_가장_가까운_사진_묶음_하나에만_붙는다() {
		RecordDraftComposer.Result result = RecordDraftComposer.compose(List.of(),
			List.of(photo(7, NOON, at(200), LNG), photo(8, AFTERNOON, at(50), LNG)),
			List.of(plan(501, tourPlace("T1", LAT), null)), RULES);

		assertThat(result.items()).extracting(Item::sourcePhotoIds, Item::plannedItemId)
			.containsExactly(tuple(List.of(7L), null), tuple(List.of(8L), 501L));
	}

	@Test
	void 영수증에_합쳐진_계획은_사진_묶음과_다시_합치지_않는다() {
		RecordDraftComposer.Result result = RecordDraftComposer.compose(List.of(receipt("m-1", NOON, CAFE)),
			List.of(photo(7, AFTERNOON, LAT, LNG)), List.of(plan(501, CAFE, null)), RULES);

		assertThat(result.items()).extracting(Item::sourceMaterialIds, Item::sourcePhotoIds, Item::plannedItemId)
			.containsExactly(tuple(List.of("m-1"), List.of(), 501L), tuple(List.of(), List.of(7L), null));
	}

	/** 명세 3-4: 계획 5곳을 그대로 다녀오면 지금 10줄이던 초안이 6줄이 된다. */
	@Test
	void 계획_5곳_예시는_10줄이_아니라_6줄이다() {
		Instant t1 = Instant.parse("2026-11-02T01:00:00Z");
		RecordPlaceView cafe = tourPlace("cafe", LAT);
		RecordPlaceView restaurant = tourPlace("restaurant", LAT.add(new BigDecimal("0.0200000")));
		RecordPlaceView beach = tourPlace("beach", LAT.add(new BigDecimal("0.0400000")));
		RecordPlaceView view = tourPlace("view", LAT.add(new BigDecimal("0.0600000")));
		RecordPlaceView market = tourPlace("market", LAT.add(new BigDecimal("0.0800000")));
		RecordPlaceView restaurantPick = manualPlace(restaurant.latitude().add(new BigDecimal("0.0007000")));  // 약 80m

		RecordDraftComposer.Result result = RecordDraftComposer.compose(
			List.of(receipt("m-cafe", t1, cafe), receipt("m-restaurant", t1.plus(Duration.ofHours(2)), restaurantPick)),
			List.of(photo(7, t1.plus(Duration.ofHours(4)), beach.latitude().add(new BigDecimal("0.0013000")), LNG),
				photo(8, t1.plus(Duration.ofHours(6)), null, null)),
			List.of(plan(501, cafe, null), plan(502, restaurant, null), plan(503, beach, null), plan(504, view, null),
				plan(505, market, null)), RULES);

		assertThat(result.items()).extracting(Item::sourceMaterialIds, Item::sourcePhotoIds, Item::plannedItemId)
			.containsExactly(
				tuple(List.of("m-cafe"), List.of(), 501L),
				tuple(List.of("m-restaurant"), List.of(), 502L),
				tuple(List.of(), List.of(7L), 503L),
				tuple(List.of(), List.of(8L), null),
				tuple(List.of(), List.of(), 504L),
				tuple(List.of(), List.of(), 505L));
	}
}

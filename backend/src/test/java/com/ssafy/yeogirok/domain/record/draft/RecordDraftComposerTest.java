package com.ssafy.yeogirok.domain.record.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
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
import com.ssafy.yeogirok.domain.record.dto.RecordPlaceView;
import com.ssafy.yeogirok.global.response.ApiDateTime;

/** 초안 항목 만들기 규칙(명세 specs/record-api-2.md 4-7 생성 규칙 1·2·4·5). 기준은 60분·1km·60분·500m. */
class RecordDraftComposerTest {

	static final RecordProperties.Draft RULES = new RecordProperties.Draft(Duration.ofMinutes(60), 1000,
		Duration.ofMinutes(60), 500);
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
			RULES);

		assertThat(result.items()).containsExactly(new Item(1, List.of("m-1"), List.of(CAFE), LocalDate.of(2026, 11, 2),
			NOON, false, new Evidence(List.of(SourceType.RECEIPT),
				new FieldSources(SourceType.RECEIPT, SourceType.RECEIPT, SourceType.RECEIPT), List.of()),
			List.of(), null, CAFE));
		assertThat(result.warnings()).isEmpty();
	}

	@Test
	void EXIF_없는_사진은_근거로_쓰지_않고_경고를_한_번만_넣는다() {
		RecordDraftComposer.Result result = RecordDraftComposer.compose(List.of(receipt("m-1", NOON, CAFE)),
			List.of(noExif(1), noExif(2)), RULES);

		assertThat(result.items()).singleElement().satisfies(item -> assertThat(item.sourcePhotoIds()).isEmpty());
		assertThat(result.warnings()).containsExactly(RecordDraft.PHOTO_METADATA_MISSING);
	}

	@Test
	void 결제_시각_60분_안의_사진은_영수증_근거로_붙고_필드_값은_그대로다() {
		RecordDraftComposer.Result result = RecordDraftComposer.compose(List.of(receipt("m-1", NOON, CAFE)),
			List.of(photo(7, NOON.plus(Duration.ofMinutes(60)), LAT, LNG), photo(8, NOON.minusSeconds(600), null, null)),
			RULES);

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
			List.of(photo(7, NOON.plus(Duration.ofMinutes(30)), null, null)), RULES);

		assertThat(result.items()).extracting(Item::sourcePhotoIds).containsExactly(List.of(), List.of(7L));
	}

	@Test
	void 시간이_60분_넘게_차이나거나_1km_넘게_멀면_PHOTO_항목이_된다() {
		Instant farAway = NOON.plus(Duration.ofMinutes(10));
		RecordDraftComposer.Result result = RecordDraftComposer.compose(List.of(receipt("m-1", NOON, CAFE)),
			List.of(photo(7, NOON.plus(Duration.ofMinutes(61)), null, null),
				photo(8, farAway, LAT.add(new BigDecimal("0.0100000")), LNG)),
			RULES);

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
			List.of(photo(7, afterMidnight, null, null)), RULES);

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
			photo(5, start.plus(Duration.ofMinutes(126)), null, null)), RULES);

		assertThat(result.items()).extracting(Item::sourcePhotoIds)
			.containsExactly(List.of(1L, 2L), List.of(3L), List.of(4L, 5L));
		assertThat(result.items()).extracting(Item::itemId).containsExactly(1, 2, 3);
	}

	@Test
	void 같은_장소여도_합치지_않고_시각순으로_번호를_매긴다() {
		Instant later = NOON.plus(Duration.ofHours(3));
		RecordDraftComposer.Result result = RecordDraftComposer.compose(
			List.of(receipt("m-late", later, CAFE), receipt("m-early", NOON, CAFE)), List.of(), RULES);

		assertThat(result.items()).extracting(Item::itemId, Item::sourceMaterialIds)
			.containsExactly(tuple(1, List.of("m-early")), tuple(2, List.of("m-late")));
	}
}

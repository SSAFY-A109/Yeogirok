package com.ssafy.yeogirok.domain.record.draft;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.ssafy.yeogirok.domain.record.RecordProperties;
import com.ssafy.yeogirok.domain.record.draft.PhotoMetadataReader.PhotoMetadata;
import com.ssafy.yeogirok.domain.record.draft.RecordDraft.Item;
import com.ssafy.yeogirok.domain.record.draft.RecordDraft.SnapshotMaterial;
import com.ssafy.yeogirok.domain.record.dto.RecordPlaceView;
import com.ssafy.yeogirok.global.geo.GeoDistance;
import com.ssafy.yeogirok.global.response.ApiDateTime;

/**
 * 영수증·사진·참고 계획으로 초안 항목을 만든다(명세 {@code specs/record-api-2.md} 4-7 생성 규칙 1·2·4·5,
 * {@code specs/draft-merge.md} 3절). 순서: 사진을 영수증 근거로 붙임 → 남은 사진 묶기 → 같은 방문인 계획 합치기
 * ({@link DraftPlanMatcher}) → 남은 계획은 PLAN 항목 → 정렬·번호. 항목 모양은 {@link DraftItemBuilder} 가 정한다.
 */
final class RecordDraftComposer {

	private RecordDraftComposer() {
	}

	/** @param metadata 촬영 시각을 읽지 못했으면 empty */
	record Photo(Long imageId, Optional<PhotoMetadata> metadata) {
	}

	/**
	 * 참고 계획 사본의 항목 하나.
	 *
	 * @param place     사본에 장소가 없으면 null
	 * @param arrivalAt 계획 도착 시각. 없으면 null. 같은 방문 후보가 여럿일 때 고르는 데만 쓴다
	 */
	record PlannedVisit(Long plannedItemId, LocalDate date, RecordPlaceView place, Instant arrivalAt) {
	}

	record Result(List<Item> items, List<String> warnings) {
	}

	/** 촬영 시각을 읽은 사진. */
	record TimedPhoto(Long imageId, PhotoMetadata metadata) {
	}

	/** @param plans 계획 순서대로 */
	static Result compose(List<SnapshotMaterial> receipts, List<Photo> photos, List<PlannedVisit> plans,
		RecordProperties.Draft rules) {
		List<DraftItemBuilder> builders = new ArrayList<>(receipts.stream().map(DraftItemBuilder::receipt).toList());
		List<String> warnings = new ArrayList<>();
		if (photos.stream().anyMatch(photo -> photo.metadata().isEmpty())) {
			warnings.add(RecordDraft.PHOTO_METADATA_MISSING);
		}
		List<TimedPhoto> remaining = new ArrayList<>();
		photos.stream()
			.filter(photo -> photo.metadata().isPresent())
			.map(photo -> new TimedPhoto(photo.imageId(), photo.metadata().get()))
			.sorted(Comparator.comparing((TimedPhoto photo) -> photo.metadata().takenAt()).thenComparing(TimedPhoto::imageId))
			.forEach(photo -> nearestReceipt(builders, photo, rules)
				.ifPresentOrElse(receipt -> receipt.attach(photo.imageId()), () -> remaining.add(photo)));
		List<DraftItemBuilder> receiptItems = List.copyOf(builders);
		List<DraftItemBuilder> photoItems = groups(remaining, rules).stream().map(DraftItemBuilder::photos).toList();
		builders.addAll(photoItems);
		Set<Integer> merged = new HashSet<>(DraftPlanMatcher.mergeIntoReceipts(plans, receiptItems, rules));
		merged.addAll(DraftPlanMatcher.mergeIntoPhotos(plans, merged, photoItems, rules));
		for (int i = 0; i < plans.size(); i++) {
			if (!merged.contains(i)) {
				builders.add(DraftItemBuilder.plan(plans.get(i)));
			}
		}

		// 안정 정렬이라 같은 날 시각이 없는 계획 항목은 영수증·사진 항목 뒤에 계획 순서대로 남는다.
		List<DraftItemBuilder> sorted = builders.stream()
			.sorted(Comparator.comparing(DraftItemBuilder::visitDate, Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(DraftItemBuilder::visitedAt, Comparator.nullsLast(Comparator.naturalOrder())))
			.toList();
		List<Item> items = new ArrayList<>();
		for (int i = 0; i < sorted.size(); i++) {
			items.add(sorted.get(i).build(i + 1));
		}
		return new Result(items, warnings);
	}

	/** 같은 날이고 결제 시각 앞뒤 기준 안인 영수증 중 가장 가까운 것. 둘 다 좌표가 있고 기준보다 멀면 붙이지 않는다. */
	private static Optional<DraftItemBuilder> nearestReceipt(List<DraftItemBuilder> builders, TimedPhoto photo,
		RecordProperties.Draft rules) {
		Instant takenAt = photo.metadata().takenAt();
		return builders.stream()
			.filter(builder -> builder.visitedAt() != null && builder.visitDate().equals(seoulDate(takenAt)))
			.filter(builder -> !Duration.between(builder.visitedAt(), takenAt).abs().minus(rules.receiptPhotoWindow())
				.isPositive())
			.filter(builder -> !farther(photo.metadata(), builder.place(), rules.receiptPhotoMaxMeters()))
			.min(Comparator.comparing(builder -> Duration.between(builder.visitedAt(), takenAt).abs()));
	}

	/** 시각순 사진을 앞 사진과의 시간 차·거리로 끊는다. */
	private static List<List<TimedPhoto>> groups(List<TimedPhoto> photos, RecordProperties.Draft rules) {
		List<List<TimedPhoto>> groups = new ArrayList<>();
		TimedPhoto previous = null;
		for (TimedPhoto photo : photos) {
			boolean split = previous == null
				|| Duration.between(previous.metadata().takenAt(), photo.metadata().takenAt())
				.compareTo(rules.photoGroupGap()) > 0
				|| farther(previous.metadata(), photo.metadata(), rules.photoGroupMaxMeters());
			if (split) {
				groups.add(new ArrayList<>());
			}
			groups.getLast().add(photo);
			previous = photo;
		}
		return groups;
	}

	private static boolean farther(PhotoMetadata photo, RecordPlaceView place, int maxMeters) {
		return photo.hasLocation() && place != null && place.latitude() != null && place.longitude() != null
			&& GeoDistance.meters(photo.latitude(), photo.longitude(), place.latitude(), place.longitude()) > maxMeters;
	}

	private static boolean farther(PhotoMetadata a, PhotoMetadata b, int maxMeters) {
		return a.hasLocation() && b.hasLocation()
			&& GeoDistance.meters(a.latitude(), a.longitude(), b.latitude(), b.longitude()) > maxMeters;
	}

	static LocalDate seoulDate(Instant instant) {
		return instant.atZone(ApiDateTime.ZONE).toLocalDate();
	}
}

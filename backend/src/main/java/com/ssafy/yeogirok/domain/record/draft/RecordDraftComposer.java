package com.ssafy.yeogirok.domain.record.draft;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.ssafy.yeogirok.domain.record.draft.PhotoMetadataReader.PhotoMetadata;
import com.ssafy.yeogirok.domain.record.draft.RecordDraft.Evidence;
import com.ssafy.yeogirok.domain.record.draft.RecordDraft.FieldSources;
import com.ssafy.yeogirok.domain.record.draft.RecordDraft.Item;
import com.ssafy.yeogirok.domain.record.draft.RecordDraft.SnapshotMaterial;
import com.ssafy.yeogirok.domain.record.draft.RecordDraft.SourceType;
import com.ssafy.yeogirok.domain.record.dto.RecordPlaceView;
import com.ssafy.yeogirok.global.geo.GeoDistance;
import com.ssafy.yeogirok.global.response.ApiDateTime;

/**
 * 영수증·사진으로 초안 항목을 만든다(명세 {@code specs/record-api-2.md} 4-7 생성 규칙 1·2·4·5). 계획 항목은 057 계획 사본 전까지
 * 없다. 같은 장소라고 항목을 합치지 않고, 사진만으로 장소나 시각을 지어내지 않는다.
 */
final class RecordDraftComposer {

	private RecordDraftComposer() {
	}

	/** @param metadata 촬영 시각을 읽지 못했으면 empty */
	record Photo(Long imageId, Optional<PhotoMetadata> metadata) {
	}

	record Result(List<Item> items, List<String> warnings) {
	}

	static Result compose(List<SnapshotMaterial> receipts, List<Photo> photos, RecordDraftProperties.Draft rules) {
		List<ItemBuilder> builders = new ArrayList<>(receipts.stream().map(ItemBuilder::receipt).toList());
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
		groups(remaining, rules).forEach(group -> builders.add(ItemBuilder.photos(group)));

		List<ItemBuilder> sorted = builders.stream()
			.sorted(Comparator.comparing(ItemBuilder::visitedAt, Comparator.nullsLast(Comparator.naturalOrder())))
			.toList();
		List<Item> items = new ArrayList<>();
		for (int i = 0; i < sorted.size(); i++) {
			items.add(sorted.get(i).build(i + 1));
		}
		return new Result(items, warnings);
	}

	/** 같은 날이고 결제 시각 앞뒤 기준 안인 영수증 중 가장 가까운 것. 둘 다 좌표가 있고 기준보다 멀면 붙이지 않는다. */
	private static Optional<ItemBuilder> nearestReceipt(List<ItemBuilder> builders, TimedPhoto photo,
		RecordDraftProperties.Draft rules) {
		Instant takenAt = photo.metadata().takenAt();
		return builders.stream()
			.filter(builder -> builder.visitedAt != null && builder.visitDate.equals(seoulDate(takenAt)))
			.filter(builder -> !Duration.between(builder.visitedAt, takenAt).abs().minus(rules.receiptPhotoWindow())
				.isPositive())
			.filter(builder -> !farther(photo.metadata(), builder.place, rules.receiptPhotoMaxMeters()))
			.min(Comparator.comparing(builder -> Duration.between(builder.visitedAt, takenAt).abs()));
	}

	/** 시각순 사진을 앞 사진과의 시간 차·거리로 끊는다. */
	private static List<List<TimedPhoto>> groups(List<TimedPhoto> photos, RecordDraftProperties.Draft rules) {
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

	private static LocalDate seoulDate(Instant instant) {
		return instant.atZone(ApiDateTime.ZONE).toLocalDate();
	}

	private record TimedPhoto(Long imageId, PhotoMetadata metadata) {
	}

	/** 항목을 만드는 중간 값. 영수증 항목은 사진을 근거로 더 붙일 수 있다(필드 값은 바꾸지 않는다, RECEIPT > PHOTO). */
	private static final class ItemBuilder {

		private final List<String> materialIds;
		private final RecordPlaceView place;
		private final LocalDate visitDate;
		private final Instant visitedAt;
		private final boolean photoOnly;
		private final List<Long> photoIds = new ArrayList<>();

		private ItemBuilder(List<String> materialIds, RecordPlaceView place, LocalDate visitDate, Instant visitedAt,
			boolean photoOnly) {
			this.materialIds = materialIds;
			this.place = place;
			this.visitDate = visitDate;
			this.visitedAt = visitedAt;
			this.photoOnly = photoOnly;
		}

		static ItemBuilder receipt(SnapshotMaterial material) {
			return new ItemBuilder(List.of(material.materialId()), material.values().place(),
				material.values().paidDate(), material.values().paidAt(), false);
		}

		static ItemBuilder photos(List<TimedPhoto> group) {
			Instant first = group.getFirst().metadata().takenAt();
			ItemBuilder builder = new ItemBuilder(List.of(), null, seoulDate(first), first, true);
			group.forEach(photo -> builder.attach(photo.imageId()));
			return builder;
		}

		Instant visitedAt() {
			return visitedAt;
		}

		void attach(Long imageId) {
			photoIds.add(imageId);
		}

		Item build(int itemId) {
			if (photoOnly) {
				return new Item(itemId, List.of(), List.of(), visitDate, visitedAt, true,
					new Evidence(List.of(SourceType.PHOTO), new FieldSources(null, SourceType.PHOTO, SourceType.PHOTO),
						List.of(RecordDraft.PHOTO_PLACE_UNRESOLVED)),
					List.copyOf(photoIds), null, null);
			}
			List<SourceType> sources = photoIds.isEmpty() ? List.of(SourceType.RECEIPT)
				: List.of(SourceType.RECEIPT, SourceType.PHOTO);
			return new Item(itemId, materialIds, place == null ? List.of() : List.of(place), visitDate, visitedAt, false,
				new Evidence(sources, new FieldSources(SourceType.RECEIPT, SourceType.RECEIPT, SourceType.RECEIPT),
					List.of()),
				List.copyOf(photoIds), null, place);
		}
	}
}

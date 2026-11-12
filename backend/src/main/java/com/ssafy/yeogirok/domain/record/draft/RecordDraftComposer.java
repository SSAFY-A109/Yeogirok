package com.ssafy.yeogirok.domain.record.draft;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.ssafy.yeogirok.domain.record.RecordProperties;
import com.ssafy.yeogirok.domain.record.draft.PhotoMetadataReader.PhotoMetadata;
import com.ssafy.yeogirok.domain.record.draft.RecordDraft.Evidence;
import com.ssafy.yeogirok.domain.record.draft.RecordDraft.FieldSources;
import com.ssafy.yeogirok.domain.record.draft.RecordDraft.Item;
import com.ssafy.yeogirok.domain.record.draft.RecordDraft.SnapshotMaterial;
import com.ssafy.yeogirok.domain.record.draft.RecordDraft.SourceType;
import com.ssafy.yeogirok.domain.place.entity.PlaceProvider;
import com.ssafy.yeogirok.domain.record.dto.RecordPlaceView;
import com.ssafy.yeogirok.global.geo.GeoDistance;
import com.ssafy.yeogirok.global.response.ApiDateTime;

/**
 * 영수증·사진·참고 계획으로 초안 항목을 만든다(명세 {@code specs/record-api-2.md} 4-7 생성 규칙 1·2·4·5,
 * {@code specs/draft-merge.md} 3절). 같은 방문인 계획은 영수증 항목(208), 남으면 사진 묶음 항목(209)에 합치고, 이름은
 * 비교하지 않으며, 사진·계획만으로 시각을 지어내지 않는다.
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

	/** @param plans 계획 순서대로 */
	static Result compose(List<SnapshotMaterial> receipts, List<Photo> photos, List<PlannedVisit> plans,
		RecordProperties.Draft rules) {
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
		List<ItemBuilder> receiptItems = List.copyOf(builders);
		List<ItemBuilder> photoItems = groups(remaining, rules).stream().map(ItemBuilder::photos).toList();
		builders.addAll(photoItems);
		Set<Integer> merged = new HashSet<>(mergePlansIntoReceipts(plans, receiptItems, rules));
		merged.addAll(mergePlansIntoPhotos(plans, merged, photoItems, rules));
		for (int i = 0; i < plans.size(); i++) {
			if (!merged.contains(i)) {
				builders.add(ItemBuilder.plan(plans.get(i)));
			}
		}

		// 안정 정렬이라 같은 날 시각이 없는 계획 항목은 영수증·사진 항목 뒤에 계획 순서대로 남는다.
		List<ItemBuilder> sorted = builders.stream()
			.sorted(Comparator.comparing(ItemBuilder::visitDate, Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(ItemBuilder::visitedAt, Comparator.nullsLast(Comparator.naturalOrder())))
			.toList();
		List<Item> items = new ArrayList<>();
		for (int i = 0; i < sorted.size(); i++) {
			items.add(sorted.get(i).build(i + 1));
		}
		return new Result(items, warnings);
	}

	/** 같은 날이고 결제 시각 앞뒤 기준 안인 영수증 중 가장 가까운 것. 둘 다 좌표가 있고 기준보다 멀면 붙이지 않는다. */
	private static Optional<ItemBuilder> nearestReceipt(List<ItemBuilder> builders, TimedPhoto photo,
		RecordProperties.Draft rules) {
		Instant takenAt = photo.metadata().takenAt();
		return builders.stream()
			.filter(builder -> builder.visitedAt != null && builder.visitDate.equals(seoulDate(takenAt)))
			.filter(builder -> !Duration.between(builder.visitedAt, takenAt).abs().minus(rules.receiptPhotoWindow())
				.isPositive())
			.filter(builder -> !farther(photo.metadata(), builder.place, rules.receiptPhotoMaxMeters()))
			.min(Comparator.comparing(builder -> Duration.between(builder.visitedAt, takenAt).abs()));
	}

	/**
	 * 같은 날짜 + 같은 장소(투어API 같은 장소, 아니면 확정 장소 좌표 거리 기준 이하)인 계획을 영수증 항목에 1대1로 붙인다. 시간은 조건이
	 * 아니고 후보가 여럿일 때 도착 시각과 결제 시각이 가까운 쌍을 먼저 고른다.
	 *
	 * @return 영수증에 붙은 계획의 순번
	 */
	private static Set<Integer> mergePlansIntoReceipts(List<PlannedVisit> plans, List<ItemBuilder> receipts,
		RecordProperties.Draft rules) {
		List<PlanMatch> matches = new ArrayList<>();
		for (int p = 0; p < plans.size(); p++) {
			PlannedVisit plan = plans.get(p);
			for (int r = 0; r < receipts.size(); r++) {
				ItemBuilder receipt = receipts.get(r);
				Double meters = samePlaceMeters(plan.place(), receipt.place, rules.planReceiptMaxMeters());
				if (meters != null && plan.date().equals(receipt.visitDate)) {
					Duration gap = plan.arrivalAt() == null || receipt.visitedAt == null ? null
						: Duration.between(plan.arrivalAt(), receipt.visitedAt).abs();
					matches.add(new PlanMatch(p, receipt, r, gap, meters));
				}
			}
		}
		return assignOneToOne(matches, plans, Comparator.comparing(PlanMatch::timeGap,
			Comparator.nullsLast(Comparator.naturalOrder())).thenComparingDouble(PlanMatch::meters));
	}

	/**
	 * 영수증에 안 붙은 계획을 같은 날짜 사진 묶음에 1대1로 붙인다. 묶음 거리 = 묶음 안 GPS 사진 중 계획 장소와 가장 가까운 사진. 시간은
	 * 쓰지 않는다(계획 시각은 실제 시각이 아니다). GPS 사진이 없는 묶음·좌표 없는 계획은 맞추지 않는다.
	 *
	 * @param taken 이미 영수증에 붙은 계획의 순번
	 */
	private static Set<Integer> mergePlansIntoPhotos(List<PlannedVisit> plans, Set<Integer> taken,
		List<ItemBuilder> photoGroups, RecordProperties.Draft rules) {
		List<PlanMatch> matches = new ArrayList<>();
		for (int p = 0; p < plans.size(); p++) {
			PlannedVisit plan = plans.get(p);
			if (taken.contains(p) || plan.place() == null || plan.place().latitude() == null
				|| plan.place().longitude() == null) {
				continue;
			}
			int planIndex = p;
			for (int g = 0; g < photoGroups.size(); g++) {
				ItemBuilder group = photoGroups.get(g);
				int groupIndex = g;
				if (!plan.date().equals(group.visitDate)) {
					continue;
				}
				group.nearestPhotoMeters(plan.place())
					.filter(meters -> meters <= rules.planPhotoMaxMeters())
					.ifPresent(meters -> matches.add(new PlanMatch(planIndex, group, groupIndex, null, meters)));
			}
		}
		return assignOneToOne(matches, plans, Comparator.comparingDouble(PlanMatch::meters));
	}

	/** 같은 장소면 거리(투어API 같은 장소는 0), 아니면 null. 좌표가 한쪽에라도 없으면 위치로 맞추지 않는다. */
	private static Double samePlaceMeters(RecordPlaceView plan, RecordPlaceView other, int maxMeters) {
		if (plan == null || other == null) {
			return null;
		}
		if (plan.provider() == PlaceProvider.TOUR_API && other.provider() == PlaceProvider.TOUR_API
			&& plan.providerPlaceId() != null && plan.providerPlaceId().equals(other.providerPlaceId())) {
			return 0.0;
		}
		if (plan.latitude() == null || plan.longitude() == null || other.latitude() == null
			|| other.longitude() == null) {
			return null;
		}
		double meters = GeoDistance.meters(plan.latitude(), plan.longitude(), other.latitude(), other.longitude());
		return meters > maxMeters ? null : meters;
	}

	/**
	 * 가능한 (계획, 항목) 쌍을 정렬 키 순서로 보며 둘 다 아직 안 쓴 쌍만 확정한다. 계획 순서대로 고르면 앞 계획이 뒤 계획에 더 맞는
	 * 항목을 가져갈 수 있어서다. 같은 키면 계획 순서 → 항목 순서.
	 *
	 * @return 확정된 계획의 순번
	 */
	private static Set<Integer> assignOneToOne(List<PlanMatch> matches, List<PlannedVisit> plans,
		Comparator<PlanMatch> order) {
		Set<Integer> usedPlans = new HashSet<>();
		Set<ItemBuilder> usedItems = Collections.newSetFromMap(new IdentityHashMap<>());
		matches.stream()
			.sorted(order.thenComparingInt(PlanMatch::planIndex).thenComparingInt(PlanMatch::itemIndex))
			.filter(match -> !usedPlans.contains(match.planIndex()) && !usedItems.contains(match.item()))
			.forEach(match -> {
				usedPlans.add(match.planIndex());
				usedItems.add(match.item());
				match.item().linkPlan(plans.get(match.planIndex()));
			});
		return usedPlans;
	}

	/** @param timeGap 계획 도착 시각과 항목 시각 차이. 둘 중 하나라도 없으면 null */
	private record PlanMatch(int planIndex, ItemBuilder item, int itemIndex, Duration timeGap, double meters) {
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

	private static LocalDate seoulDate(Instant instant) {
		return instant.atZone(ApiDateTime.ZONE).toLocalDate();
	}

	private record TimedPhoto(Long imageId, PhotoMetadata metadata) {
	}

	/**
	 * 항목을 만드는 중간 값. 영수증 항목은 사진을 근거로 더 붙일 수 있다(필드 값은 바꾸지 않는다, RECEIPT > PHOTO).
	 * 계획 항목은 시각이 없어 사진이 붙지 않는다.
	 */
	private static final class ItemBuilder {

		private final SourceType kind;
		private final List<String> materialIds;
		private final RecordPlaceView place;
		private final LocalDate visitDate;
		private final Instant visitedAt;
		private Long plannedItemId;
		/** 사진 묶음에 붙은 계획의 장소(209). 영수증 항목은 장소를 바꾸지 않아 쓰지 않는다. */
		private RecordPlaceView plannedPlace;
		private final List<Long> photoIds = new ArrayList<>();
		/** 사진 묶음 안 GPS 가 있는 사진(209 거리 계산). */
		private final List<PhotoMetadata> located = new ArrayList<>();

		private ItemBuilder(SourceType kind, List<String> materialIds, RecordPlaceView place, LocalDate visitDate,
			Instant visitedAt, Long plannedItemId) {
			this.kind = kind;
			this.materialIds = materialIds;
			this.place = place;
			this.visitDate = visitDate;
			this.visitedAt = visitedAt;
			this.plannedItemId = plannedItemId;
		}

		static ItemBuilder receipt(SnapshotMaterial material) {
			return new ItemBuilder(SourceType.RECEIPT, List.of(material.materialId()), material.values().place(),
				material.values().paidDate(), material.values().paidAt(), null);
		}

		static ItemBuilder photos(List<TimedPhoto> group) {
			Instant first = group.getFirst().metadata().takenAt();
			ItemBuilder builder = new ItemBuilder(SourceType.PHOTO, List.of(), null, seoulDate(first), first, null);
			group.forEach(photo -> {
				builder.attach(photo.imageId());
				if (photo.metadata().hasLocation()) {
					builder.located.add(photo.metadata());
				}
			});
			return builder;
		}

		/** 계획 시각은 실제 방문 시각으로 쓰지 않는다(노션 064). */
		static ItemBuilder plan(PlannedVisit plan) {
			return new ItemBuilder(SourceType.PLAN, List.of(), plan.place(), plan.date(), null, plan.plannedItemId());
		}

		LocalDate visitDate() {
			return visitDate;
		}

		Instant visitedAt() {
			return visitedAt;
		}

		void attach(Long imageId) {
			photoIds.add(imageId);
		}

		/** 같은 방문으로 판단된 계획을 붙인다. 영수증 항목은 필드 값을 바꾸지 않고, 사진 묶음은 장소를 계획 장소로 제안한다. */
		void linkPlan(PlannedVisit plan) {
			this.plannedItemId = plan.plannedItemId();
			this.plannedPlace = plan.place();
		}

		/** 묶음 안 GPS 사진 중 place 와 가장 가까운 거리. GPS 사진이 없으면 empty. */
		Optional<Double> nearestPhotoMeters(RecordPlaceView place) {
			return located.stream()
				.map(photo -> GeoDistance.meters(photo.latitude(), photo.longitude(), place.latitude(), place.longitude()))
				.min(Double::compare);
		}

		Item build(int itemId) {
			if (kind == SourceType.PLAN) {
				return new Item(itemId, List.of(), place == null ? List.of() : List.of(place), visitDate, null, true,
					new Evidence(List.of(SourceType.PLAN),
						new FieldSources(place == null ? null : SourceType.PLAN, SourceType.PLAN, null),
						List.of(RecordDraft.PLAN_NOT_CONFIRMED)),
					List.of(), plannedItemId, place);
			}
			if (kind == SourceType.PHOTO && plannedItemId != null) {
				// 장소는 계획, 날짜·시각은 사진. 계획 장소는 확인이 필요해 needsReview 는 그대로 true(209).
				return new Item(itemId, List.of(), List.of(plannedPlace), visitDate, visitedAt, true,
					new Evidence(List.of(SourceType.PHOTO, SourceType.PLAN),
						new FieldSources(SourceType.PLAN, SourceType.PHOTO, SourceType.PHOTO), List.of()),
					List.copyOf(photoIds), plannedItemId, plannedPlace);
			}
			if (kind == SourceType.PHOTO) {
				return new Item(itemId, List.of(), List.of(), visitDate, visitedAt, true,
					new Evidence(List.of(SourceType.PHOTO), new FieldSources(null, SourceType.PHOTO, SourceType.PHOTO),
						List.of(RecordDraft.PHOTO_PLACE_UNRESOLVED)),
					List.copyOf(photoIds), null, null);
			}
			List<SourceType> sources = new ArrayList<>(List.of(SourceType.RECEIPT));
			if (!photoIds.isEmpty()) {
				sources.add(SourceType.PHOTO);
			}
			if (plannedItemId != null) {
				sources.add(SourceType.PLAN);
			}
			return new Item(itemId, materialIds, place == null ? List.of() : List.of(place), visitDate, visitedAt, false,
				new Evidence(List.copyOf(sources),
					new FieldSources(SourceType.RECEIPT, SourceType.RECEIPT, SourceType.RECEIPT), List.of()),
				List.copyOf(photoIds), plannedItemId, place);
		}
	}
}

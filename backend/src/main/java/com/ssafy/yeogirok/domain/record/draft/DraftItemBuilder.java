package com.ssafy.yeogirok.domain.record.draft;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.ssafy.yeogirok.domain.record.draft.PhotoMetadataReader.PhotoMetadata;
import com.ssafy.yeogirok.domain.record.draft.RecordDraft.Evidence;
import com.ssafy.yeogirok.domain.record.draft.RecordDraft.FieldSources;
import com.ssafy.yeogirok.domain.record.draft.RecordDraft.Item;
import com.ssafy.yeogirok.domain.record.draft.RecordDraft.SnapshotMaterial;
import com.ssafy.yeogirok.domain.record.draft.RecordDraft.SourceType;
import com.ssafy.yeogirok.domain.record.draft.RecordDraftComposer.PlannedVisit;
import com.ssafy.yeogirok.domain.record.draft.RecordDraftComposer.TimedPhoto;
import com.ssafy.yeogirok.domain.record.dto.RecordPlaceView;
import com.ssafy.yeogirok.global.geo.GeoDistance;

/**
 * 초안 항목을 만드는 중간 값. 영수증 항목은 사진을 근거로 더 붙일 수 있다(필드 값은 바꾸지 않는다, RECEIPT > PHOTO).
 * 같은 방문인 계획이 붙으면(208·209) {@link #build} 가 출처·장소를 정한다. 계획 항목은 시각이 없어 사진이 붙지 않는다.
 */
final class DraftItemBuilder {

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

	private DraftItemBuilder(SourceType kind, List<String> materialIds, RecordPlaceView place, LocalDate visitDate,
		Instant visitedAt, Long plannedItemId) {
		this.kind = kind;
		this.materialIds = materialIds;
		this.place = place;
		this.visitDate = visitDate;
		this.visitedAt = visitedAt;
		this.plannedItemId = plannedItemId;
	}

	static DraftItemBuilder receipt(SnapshotMaterial material) {
		return new DraftItemBuilder(SourceType.RECEIPT, List.of(material.materialId()), material.values().place(),
			material.values().paidDate(), material.values().paidAt(), null);
	}

	static DraftItemBuilder photos(List<TimedPhoto> group) {
		Instant first = group.getFirst().metadata().takenAt();
		DraftItemBuilder builder = new DraftItemBuilder(SourceType.PHOTO, List.of(), null,
			RecordDraftComposer.seoulDate(first), first, null);
		group.forEach(photo -> {
			builder.attach(photo.imageId());
			if (photo.metadata().hasLocation()) {
				builder.located.add(photo.metadata());
			}
		});
		return builder;
	}

	/** 계획 시각은 실제 방문 시각으로 쓰지 않는다(노션 064). */
	static DraftItemBuilder plan(PlannedVisit plan) {
		return new DraftItemBuilder(SourceType.PLAN, List.of(), plan.place(), plan.date(), null, plan.plannedItemId());
	}

	RecordPlaceView place() {
		return place;
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
	Optional<Double> nearestPhotoMeters(RecordPlaceView target) {
		return located.stream()
			.map(photo -> GeoDistance.meters(photo.latitude(), photo.longitude(), target.latitude(), target.longitude()))
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

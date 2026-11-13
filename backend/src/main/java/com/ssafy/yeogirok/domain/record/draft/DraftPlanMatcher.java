package com.ssafy.yeogirok.domain.record.draft;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import com.ssafy.yeogirok.domain.place.entity.PlaceProvider;
import com.ssafy.yeogirok.domain.record.RecordProperties;
import com.ssafy.yeogirok.domain.record.draft.RecordDraftComposer.PlannedVisit;
import com.ssafy.yeogirok.domain.record.dto.RecordPlaceView;
import com.ssafy.yeogirok.global.geo.GeoDistance;

/**
 * 같은 방문인 계획을 영수증 항목(208), 남으면 사진 묶음 항목(209)에 1대1로 붙인다(명세 {@code specs/draft-merge.md} 3절).
 * 날짜는 늘 같아야 하고, 좌표가 없는 쪽은 위치로 맞추지 않으며, 이름은 비교하지 않는다.
 */
final class DraftPlanMatcher {

	private DraftPlanMatcher() {
	}

	/**
	 * 같은 날짜 + 같은 장소(투어API 같은 장소, 아니면 확정 장소 좌표 거리 기준 이하)인 계획을 영수증 항목에 붙인다. 시간은 조건이
	 * 아니고 후보가 여럿일 때 도착 시각과 결제 시각이 가까운 쌍을 먼저 고른다.
	 *
	 * @return 영수증에 붙은 계획의 순번
	 */
	static Set<Integer> mergeIntoReceipts(List<PlannedVisit> plans, List<DraftItemBuilder> receipts,
		RecordProperties.Draft rules) {
		List<PlanMatch> matches = new ArrayList<>();
		for (int p = 0; p < plans.size(); p++) {
			PlannedVisit plan = plans.get(p);
			for (int r = 0; r < receipts.size(); r++) {
				DraftItemBuilder receipt = receipts.get(r);
				Double meters = samePlaceMeters(plan.place(), receipt.place(), rules.planReceiptMaxMeters());
				if (meters != null && plan.date().equals(receipt.visitDate())) {
					Duration gap = plan.arrivalAt() == null || receipt.visitedAt() == null ? null
						: Duration.between(plan.arrivalAt(), receipt.visitedAt()).abs();
					matches.add(new PlanMatch(p, receipt, r, gap, meters));
				}
			}
		}
		return assignOneToOne(matches, plans, Comparator.comparing(PlanMatch::timeGap,
			Comparator.nullsLast(Comparator.naturalOrder())).thenComparingDouble(PlanMatch::meters));
	}

	/**
	 * 영수증에 안 붙은 계획을 같은 날짜 사진 묶음에 붙인다. 묶음 거리 = 묶음 안 GPS 사진 중 계획 장소와 가장 가까운 사진. 시간은
	 * 쓰지 않는다(계획 시각은 실제 시각이 아니다). GPS 사진이 없는 묶음·좌표 없는 계획은 맞추지 않는다.
	 *
	 * @param taken 이미 영수증에 붙은 계획의 순번
	 * @return 사진 묶음에 붙은 계획의 순번
	 */
	static Set<Integer> mergeIntoPhotos(List<PlannedVisit> plans, Set<Integer> taken,
		List<DraftItemBuilder> photoGroups, RecordProperties.Draft rules) {
		List<PlanMatch> matches = new ArrayList<>();
		for (int p = 0; p < plans.size(); p++) {
			PlannedVisit plan = plans.get(p);
			if (taken.contains(p) || plan.place() == null || plan.place().latitude() == null
				|| plan.place().longitude() == null) {
				continue;
			}
			int planIndex = p;
			for (int g = 0; g < photoGroups.size(); g++) {
				DraftItemBuilder group = photoGroups.get(g);
				int groupIndex = g;
				if (!plan.date().equals(group.visitDate())) {
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
		Set<DraftItemBuilder> usedItems = Collections.newSetFromMap(new IdentityHashMap<>());
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
	private record PlanMatch(int planIndex, DraftItemBuilder item, int itemIndex, Duration timeGap, double meters) {
	}
}

package com.ssafy.yeogirok.domain.course.service;

import java.util.List;
import java.util.Map;

import com.ssafy.yeogirok.domain.record.entity.ActualVisit;
import com.ssafy.yeogirok.domain.record.entity.VisitImage;

/**
 * 기록의 공개 대상 원본: 실제 방문 전체(날짜·순서대로)와 방문에 연결된 사진(방문별 순서대로). 이야기·영수증·미연결 사진은 없다.
 */
public record CourseSnapshot(List<ActualVisit> visits, Map<Long, List<VisitImage>> photosByVisit) {

	/** @return 방문 순서 → 방문 안 순서로 늘어놓은 연결 사진 */
	public List<VisitImage> linkedPhotos() {
		return visits.stream().flatMap(visit -> photosOf(visit.getId()).stream()).toList();
	}

	public List<VisitImage> photosOf(Long visitId) {
		return photosByVisit.getOrDefault(visitId, List.of());
	}

	/** API-075 에서 includePhotos 를 생략했을 때 지금 사진을 공개하고 있는지. */
	public boolean hasPublicPhoto() {
		return linkedPhotos().stream().anyMatch(VisitImage::isPublicSelected);
	}
}

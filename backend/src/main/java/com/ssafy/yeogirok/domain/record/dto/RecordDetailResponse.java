package com.ssafy.yeogirok.domain.record.dto;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ssafy.yeogirok.domain.record.entity.RecordStatus;

/**
 * API-059 응답.
 * <ul>
 *   <li>plannedSchedule: 일정 기능 머지 전이라 항상 null</li>
 *   <li>uploads: 업로드 세션을 기록·업로더로 찾을 수 없어 빈 배열(명세 5절 5, 구조는 노션 그대로 둔다)</li>
 *   <li>materials: 요청자 본인 영수증 자료 요약(API-063 과 같은 계산)</li>
 *   <li>publishedCourseId: 게시됐으면 기록 ID, 아니면 null</li>
 * </ul>
 */
public record RecordDetailResponse(
	Long id,
	String title,
	RecordStatus status,
	Long roomId,
	List<Member> members,
	LocalDate startDate,
	LocalDate endDate,
	RecordProgress progress,
	Object plannedSchedule,
	List<Object> uploads,
	List<RecordPhotoView> photoPool,
	List<ActualVisitView> actualVisits,
	List<MaterialSummary> materials,
	Long publishedCourseId,
	Integer version
) {

	/** 현재 참여자(탈퇴자 제외). */
	public record Member(Long userId, String nickname, @JsonProperty("isCreator") boolean isCreator) {
	}
}

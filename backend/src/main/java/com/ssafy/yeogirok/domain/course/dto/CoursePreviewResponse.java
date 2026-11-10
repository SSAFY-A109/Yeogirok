package com.ssafy.yeogirok.domain.course.dto;

import java.util.List;

/**
 * API-072 응답. excluded·warnings 는 기록 전체를 공개하는 방식이라 지금은 항상 빈 배열(명세 course-publish-api.md 5절 6).
 */
public record CoursePreviewResponse(Preview preview, String selectionHash, List<Excluded> excluded,
	List<String> warnings) {

	/** 게시하면 보일 모양(노션 016 의 title·authors·description·days). */
	public record Preview(String title, List<PublicAuthor> authors, String description, List<CourseDay> days) {
	}

	public record Excluded(Long id, String reason) {
	}
}

package com.ssafy.yeogirok.domain.course.dto;

/**
 * 코스 공개 요청을 검사한 값(API-072·073·075). 멱등키 요청 해시에도 쓴다.
 *
 * @param title         공백을 정리한 제목. 075 에서 생략하면 null(지금 값 유지)
 * @param description   설명. 생략하면 null(지금 값 사용), 빈 문자열은 설명 비우기
 * @param includePhotos 075 에서 생략하면 null
 * @param selectionHash 072 는 받지 않아 null, 075 는 생략하면 null
 * @param published     075 만. 생략하면 null(게시 여부 유지)
 */
public record CourseInput(
	int version,
	String title,
	String description,
	Boolean includePhotos,
	String selectionHash,
	Boolean published
) {

	/** 075 에서 제목·설명·사진 공개 중 하나라도 보냈는지. */
	public boolean changesContent() {
		return title != null || description != null || includePhotos != null;
	}
}

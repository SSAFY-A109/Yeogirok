package com.ssafy.yeogirok.domain.course.dto;

/**
 * 공개 코스 사진(노션 016 PublicPhoto). 기록 사진은 출처 표기가 없어 attribution 은 항상 null.
 *
 * @param url 조회용 서명 URL(저장하지 않는다)
 */
public record PublicPhoto(Long imageId, String url, String attribution) {
}

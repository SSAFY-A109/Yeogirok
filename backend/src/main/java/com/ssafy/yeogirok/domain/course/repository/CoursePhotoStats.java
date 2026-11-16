package com.ssafy.yeogirok.domain.course.repository;

/**
 * 공개 코스 하나의 사진 통계(API-015 고르기, 명세 {@code specs/main-api.md} 227). 공개 사진은 방문에 연결되고 공개 선택된 사진이다.
 *
 * @param visits       방문 수
 * @param photoVisits  공개 사진이 하나 이상 연결된 방문 수
 * @param publicPhotos 공개 사진 수
 */
public record CoursePhotoStats(Long recordId, long visits, long photoVisits, long publicPhotos) {
}

package com.ssafy.yeogirok.domain.course.repository;

import java.time.LocalDate;

/** 공개 선택된 사진이 연결된 방문의 날짜(API-015 노출 조건). */
public record PublicPhotoDate(Long recordId, LocalDate visitDate) {
}

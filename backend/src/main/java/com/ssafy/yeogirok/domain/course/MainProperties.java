package com.ssafy.yeogirok.domain.course;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

/**
 * 메인 화면 설정.
 *
 * @param includeOfficialCourses 메인 인기 장소(API-093)에 공식 코스를 "그 장소를 담은 여행방 1곳"으로 세어 넣는다.
 *                               사용자가 가져간 기록이 충분히 쌓이면 끈다(S15P31A109-231)
 */
@Validated
@ConfigurationProperties("yeogirok.main")
public record MainProperties(@NotNull Boolean includeOfficialCourses) {
}

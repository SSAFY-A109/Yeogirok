package com.ssafy.yeogirok.domain.footprint;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 발자취 작성 제한(11/4 팀 결정, 노션 API-041). 정해진 값이라 공통 설정(application.yaml)에 둔다.
 *
 * @param sameDayRadiusMeters 같은 날(Asia/Seoul 04:00 기준) 이미 남긴 내 발자취와 이 거리 이내(경계 포함)면 새 작성을 거부한다(F003)
 */
@Validated
@ConfigurationProperties("yeogirok.footprint")
public record FootprintProperties(@NotNull @Positive Integer sameDayRadiusMeters) {
}

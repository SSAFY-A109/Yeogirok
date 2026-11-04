package com.ssafy.yeogirok.domain.footprint;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 발자취 작성 제한. 정해진 값이라 공통 설정(application.yaml)에 둔다.
 * @param sameDayRadiusMeters 같은 날(Asia/Seoul 04:00 기준) 내 발자취와 이 거리(m, 경계 포함) 이내면 F003
 */
@Validated
@ConfigurationProperties("yeogirok.footprint")
public record FootprintProperties(@NotNull @Positive Integer sameDayRadiusMeters) {
}

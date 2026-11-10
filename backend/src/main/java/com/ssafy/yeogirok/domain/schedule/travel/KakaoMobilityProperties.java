package com.ssafy.yeogirok.domain.schedule.travel;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * 카카오모빌리티 자동차 길찾기 주소. 키·제한 시간은 카카오 로컬 설정({@code yeogirok.kakao.local})과 같은 값을 쓴다.
 */
@Validated
@ConfigurationProperties("yeogirok.kakao.mobility")
public record KakaoMobilityProperties(@NotBlank String baseUrl) {
}

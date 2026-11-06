package com.ssafy.yeogirok.domain.schedule.travel;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ssafy.yeogirok.global.geo.KakaoLocalProperties;

/** 일정이 쓰는 {@link TravelTimeProvider}: 카카오 길찾기를 캐시로 감싼다. */
@Configuration
public class TravelTimeConfig {

	@Bean
	TravelTimeProvider travelTimeProvider(KakaoLocalProperties localProperties,
		KakaoMobilityProperties mobilityProperties, Clock clock) {
		return new CachingTravelTimeProvider(new KakaoTravelTimeProvider(localProperties, mobilityProperties, clock),
			clock);
	}
}

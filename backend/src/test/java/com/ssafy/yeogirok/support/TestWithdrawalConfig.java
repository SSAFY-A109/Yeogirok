package com.ssafy.yeogirok.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.ssafy.yeogirok.domain.statistics.PreservedVisitCounts;

/**
 * 회원 탈퇴(API-009)를 실제로 부르는 통합 테스트용 빈. 탈퇴가 발자취를 footprint_statistics 에 합산하는데, 운영 SQL
 * ({@code ON CONFLICT DO UPDATE})은 H2 에 없어 JPA 구현으로 바꾼다.
 */
@TestConfiguration
public class TestWithdrawalConfig {

	@Bean
	@Primary
	public PreservedVisitCounts jpaPreservedVisitCounts() {
		return new JpaPreservedVisitCounts();
	}
}

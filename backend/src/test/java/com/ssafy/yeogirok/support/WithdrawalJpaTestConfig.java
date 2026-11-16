package com.ssafy.yeogirok.support;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import com.ssafy.yeogirok.domain.statistics.VisitStatisticsProperties;

import tools.jackson.databind.json.JsonMapper;

/**
 * 탈퇴 정리 @DataJpaTest 공용 빈. 보존분 저장은 H2 라 JPA 구현을 쓴다. 방문 분포 설정 값은
 * {@code @TestPropertySource(locations = "classpath:withdrawal-test.properties")} 로 함께 넣는다. 탈퇴 API 를 부르는 통합
 * 테스트(@SpringBootTest)는 {@link TestWithdrawalConfig} 를 쓴다(앱의 JsonMapper 가 이미 있다).
 */
@TestConfiguration
@EnableConfigurationProperties(VisitStatisticsProperties.class)
public class WithdrawalJpaTestConfig {

	@Bean
	public JpaPreservedVisitCounts jpaPreservedVisitCounts() {
		return new JpaPreservedVisitCounts();
	}

	@Bean
	public JsonMapper jsonMapper() {
		return JsonMapper.builder().build();
	}
}

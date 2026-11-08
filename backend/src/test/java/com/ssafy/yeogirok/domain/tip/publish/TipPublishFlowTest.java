package com.ssafy.yeogirok.domain.tip.publish;

import static com.ssafy.yeogirok.domain.tip.TipFixture.LAT;
import static com.ssafy.yeogirok.domain.tip.TipFixture.LNG;
import static com.ssafy.yeogirok.domain.tip.TipFixture.NOW;
import static com.ssafy.yeogirok.domain.tip.TipFixture.locationAt;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.file.session.UploadSessionStore;
import com.ssafy.yeogirok.domain.tip.TipProperties;
import com.ssafy.yeogirok.domain.tip.dto.TipCreateRequest.TipCreateCommand;
import com.ssafy.yeogirok.domain.tip.dto.TipCreateResponse;
import com.ssafy.yeogirok.domain.tip.dto.TipSearchRequest.TipSearchQuery;
import com.ssafy.yeogirok.domain.tip.dto.TipSearchResponse;
import com.ssafy.yeogirok.domain.tip.service.MyTipFinder;
import com.ssafy.yeogirok.domain.tip.service.TipCreateService;
import com.ssafy.yeogirok.domain.tip.service.TipPlacements;
import com.ssafy.yeogirok.domain.tip.service.TipSearchService;
import com.ssafy.yeogirok.global.config.AsyncConfig;
import com.ssafy.yeogirok.global.location.LocationEvidencePolicy;
import com.ssafy.yeogirok.global.location.LocationEvidenceProperties;
import com.ssafy.yeogirok.support.InMemoryTipPublishStates;
import com.ssafy.yeogirok.support.InMemoryUploadSessions;
import com.ssafy.yeogirok.support.MutableClock;

import tools.jackson.databind.json.JsonMapper;

/** 049 커밋 → @Async AFTER_COMMIT 게시 → 047 에 보이기까지 실제 이벤트 연결로 확인한다(Redis 는 맵). */
@DataJpaTest
@Import({AsyncConfig.class, TipCreateService.class, MyTipFinder.class, TipPublisher.class, TipSearchService.class, TipPlacements.class,
	LocationEvidencePolicy.class, TipPublishFlowTest.Config.class})
class TipPublishFlowTest {

	static final JsonMapper MAPPER = JsonMapper.builder().build();
	static final InMemoryTipPublishStates STATES = new InMemoryTipPublishStates();

	@TestConfiguration
	@EnableConfigurationProperties({TipProperties.class, LocationEvidenceProperties.class})
	static class Config {

		@Bean
		MutableClock clock() {
			return new MutableClock(NOW);
		}

		@Bean
		JsonMapper jsonMapper() {
			return MAPPER;
		}

		@Bean
		UploadSessionStore uploadSessionStore() {
			return new InMemoryUploadSessions().store;
		}

		@Bean
		TipPublishStore tipPublishStore() {
			return STATES.store;
		}
	}

	@Autowired
	TipCreateService createService;

	@Autowired
	TipSearchService searchService;

	@Autowired
	DataSource dataSource;

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void 등록하면_커밋_뒤_비동기로_게시돼_주변_탐색에_보인다() throws Exception {
		TipCreateResponse created = createService.create(5001L, new TipCreateCommand("TIP", "줄 서는 곳은 왼쪽",
			List.of(), locationAt(LAT, LNG),
			MAPPER.readTree("{\"provider\":\"ARCORE_CLOUD_ANCHOR\",\"referenceId\":\"a\",\"expiresAt\":\"2026-11-09 12:00:00+09\"}"),
			MAPPER.readTree("{\"schemaVersion\":1}")));
		try {
			for (int i = 0; i < 100 && !succeeded(created.tipId()); i++) {
				Thread.sleep(50);
			}

			assertThat(STATES.states.get(created.tipId()).operationStatus())
				.isEqualTo(TipPublishState.OperationStatus.SUCCEEDED);
			TipSearchResponse found = searchService.search(new TipSearchQuery(locationAt(LAT, LNG), null, null, null));
			assertThat(found.items()).extracting(TipSearchResponse.Item::id).containsExactly(created.tipId());
		} finally {
			new JdbcTemplate(dataSource).update("DELETE FROM ar_tips WHERE id = ?", created.tipId());
		}
	}

	private static boolean succeeded(Long tipId) {
		TipPublishState state = STATES.states.get(tipId);
		return state != null && state.operationStatus() == TipPublishState.OperationStatus.SUCCEEDED;
	}
}

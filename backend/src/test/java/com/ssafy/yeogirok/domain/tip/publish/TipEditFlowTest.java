package com.ssafy.yeogirok.domain.tip.publish;

import static com.ssafy.yeogirok.domain.tip.TipFixture.NOW;
import static com.ssafy.yeogirok.domain.tip.TipFixture.published;
import static org.assertj.core.api.Assertions.assertThat;

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
import com.ssafy.yeogirok.domain.tip.dto.TipUpdateRequest.TipUpdateCommand;
import com.ssafy.yeogirok.domain.tip.entity.ArTip;
import com.ssafy.yeogirok.domain.tip.entity.ArTipCategory;
import com.ssafy.yeogirok.domain.tip.repository.ArTipRepository;
import com.ssafy.yeogirok.domain.tip.service.TipPhotoClaims;
import com.ssafy.yeogirok.domain.tip.service.MyTipFinder;
import com.ssafy.yeogirok.domain.tip.service.PublicTipFinder;
import com.ssafy.yeogirok.domain.tip.service.TipPhotoUploads;
import com.ssafy.yeogirok.domain.tip.service.TipPlacements;
import com.ssafy.yeogirok.domain.tip.service.TipRelocations;
import com.ssafy.yeogirok.domain.tip.service.TipUpdateService;
import com.ssafy.yeogirok.global.config.AsyncConfig;
import com.ssafy.yeogirok.global.location.LocationEvidencePolicy;
import com.ssafy.yeogirok.global.location.LocationEvidenceProperties;
import com.ssafy.yeogirok.support.InMemoryTipEditStore;
import com.ssafy.yeogirok.support.InMemoryTipPhotoClaims;
import com.ssafy.yeogirok.support.InMemoryTipPublishStates;
import com.ssafy.yeogirok.support.InMemoryUploadSessions;
import com.ssafy.yeogirok.support.MutableClock;

import tools.jackson.databind.json.JsonMapper;

/** 054 요청 → @Async 적용까지 실제 프록시 연결로 확인한다(Redis 는 맵). */
@DataJpaTest
@Import({AsyncConfig.class, TipUpdateService.class, TipEditor.class, MyTipFinder.class, PublicTipFinder.class,
	TipPhotoUploads.class, TipRelocations.class, TipPlacements.class, LocationEvidencePolicy.class,
	TipEditFlowTest.Config.class})
class TipEditFlowTest {

	static final InMemoryTipPhotoClaims CLAIMS = new InMemoryTipPhotoClaims();
	static final InMemoryTipPublishStates STATES = new InMemoryTipPublishStates();
	static final InMemoryTipEditStore EDITS = new InMemoryTipEditStore();

	@TestConfiguration
	@EnableConfigurationProperties({TipProperties.class, LocationEvidenceProperties.class})
	static class Config {

		@Bean
		MutableClock clock() {
			return new MutableClock(NOW.plusSeconds(60));
		}

		@Bean
		JsonMapper jsonMapper() {
			return JsonMapper.builder().build();
		}

		@Bean
		UploadSessionStore uploadSessionStore() {
			return new InMemoryUploadSessions().store;
		}

		@Bean
		TipPhotoClaims tipPhotoClaims() {
			return CLAIMS.store;
		}

		@Bean
		TipPublishStore tipPublishStore() {
			return STATES.store;
		}

		@Bean
		TipEditStore tipEditStore() {
			return EDITS.store;
		}
	}

	@Autowired
	TipUpdateService service;

	@Autowired
	ArTipRepository arTipRepository;

	@Autowired
	DataSource dataSource;

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void 수정을_요청하면_비동기로_적용돼_SUCCEEDED가_된다() throws Exception {
		ArTip saved = arTipRepository.save(published(12901L));
		try {
			service.update(12901L, saved.getId(), new TipUpdateCommand(saved.getVersion(), "TMI", null, null, null,
				null, null));
			for (int i = 0; i < 100 && !succeeded(saved.getId()); i++) {
				Thread.sleep(50);
			}

			assertThat(STATES.states.get(saved.getId()).operationStatus())
				.isEqualTo(TipPublishState.OperationStatus.SUCCEEDED);
			assertThat(arTipRepository.findById(saved.getId()).orElseThrow().getCategory())
				.isEqualTo(ArTipCategory.TMI);
		} finally {
			new JdbcTemplate(dataSource).update("DELETE FROM ar_tips WHERE id = ?", saved.getId());
		}
	}

	private static boolean succeeded(Long tipId) {
		TipPublishState state = STATES.states.get(tipId);
		return state != null && state.operationStatus() == TipPublishState.OperationStatus.SUCCEEDED;
	}
}

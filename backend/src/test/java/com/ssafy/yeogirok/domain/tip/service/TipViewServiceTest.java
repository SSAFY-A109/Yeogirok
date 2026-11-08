package com.ssafy.yeogirok.domain.tip.service;

import static com.ssafy.yeogirok.domain.tip.TipFixture.NOW;
import static com.ssafy.yeogirok.domain.tip.TipFixture.northOf;
import static com.ssafy.yeogirok.domain.tip.TipFixture.published;
import static com.ssafy.yeogirok.domain.tip.TipFixture.registering;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.file.FileProperties;
import com.ssafy.yeogirok.domain.file.storage.FileStorage;
import com.ssafy.yeogirok.domain.tip.TipProperties;
import com.ssafy.yeogirok.domain.tip.dto.TipViewResponse;
import com.ssafy.yeogirok.domain.tip.entity.ArTip;
import com.ssafy.yeogirok.domain.tip.entity.ArTipImage;
import com.ssafy.yeogirok.domain.tip.entity.ArTipReaction;
import com.ssafy.yeogirok.domain.tip.entity.TipReaction;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.location.LocationEvidencePolicy;
import com.ssafy.yeogirok.global.location.LocationEvidenceProperties;
import com.ssafy.yeogirok.support.MutableClock;
import com.ssafy.yeogirok.support.TestFileProperties;
import com.ssafy.yeogirok.support.TestUsers;

/** API-048 규칙. 팁은 기준 위치에 있고, 요청 위치는 북쪽으로 약 1.11m 단위로 옮긴다. S3 서명은 가짜다. */
@DataJpaTest
@Import({TipViewService.class, PublicTipFinder.class, LocationEvidencePolicy.class, TipViewServiceTest.Config.class})
class TipViewServiceTest {

	static final Long AUTHOR = 7001L;
	static final Long VIEWER = 7002L;
	static final FileStorage STORAGE = mock(FileStorage.class);

	@TestConfiguration
	@EnableConfigurationProperties({TipProperties.class, LocationEvidenceProperties.class})
	static class Config {

		@Bean
		MutableClock clock() {
			return new MutableClock(NOW);
		}

		@Bean
		FileStorage fileStorage() {
			return STORAGE;
		}

		@Bean
		FileProperties fileProperties() {
			return TestFileProperties.VALUE;
		}
	}

	@Autowired
	TipViewService service;

	@Autowired
	TestEntityManager em;

	@Test
	void 사진_작성자_반응까지_돌려준다() {
		TestUsers.insertActive(em.getEntityManager(), AUTHOR);
		Long tipId = em.persistAndGetId(published(AUTHOR), Long.class);
		Long imageId = em.persistAndGetId(ArTipImage.of(tipId, "tip-photo/abc", NOW), Long.class);
		em.persist(ArTipReaction.of(tipId, VIEWER, TipReaction.LIKE, NOW));
		em.persist(ArTipReaction.of(tipId, 9999L, TipReaction.DISLIKE, NOW));
		given(STORAGE.presignGet("photos", "tip-photo/abc", Duration.ofMinutes(15))).willReturn("https://signed/abc");

		TipViewResponse response = service.view(VIEWER, tipId, northOf(4));

		assertThat(response.id()).isEqualTo(tipId);
		assertThat(response.body()).isEqualTo("창가 자리가 좋아요");
		assertThat(response.publishedAt()).isEqualTo(NOW);
		assertThat(response.photos()).containsExactly(new TipViewResponse.Photo(imageId, "https://signed/abc", null));
		assertThat(response.author()).isEqualTo(new TipViewResponse.Author(AUTHOR, "테스트"));
		assertThat(response.reactions()).isEqualTo(new TipViewResponse.Reactions(1, 1, TipReaction.LIKE));
		assertThat(response.spatialReference().referenceId()).isEqualTo("anchor-1");
		assertThat(response.placement()).containsEntry("anchorType", "CLOUD");
	}

	@Test
	void 사진이_없으면_빈_배열이고_반응이_없으면_myReaction은_null() {
		TestUsers.insertActive(em.getEntityManager(), AUTHOR);
		Long tipId = em.persistAndGetId(published(AUTHOR), Long.class);

		TipViewResponse response = service.view(VIEWER, tipId, northOf(0));

		assertThat(response.photos()).isEmpty();
		assertThat(response.reactions()).isEqualTo(new TipViewResponse.Reactions(0, 0, null));
	}

	@Test
	void 탈퇴했거나_없는_작성자는_익명으로_보인다() {
		TestUsers.insert(em.getEntityManager(), AUTHOR, "WITHDRAWN");
		Long withdrawn = em.persistAndGetId(published(AUTHOR), Long.class);
		Long missing = em.persistAndGetId(published(8888L), Long.class);

		assertThat(service.view(VIEWER, withdrawn, northOf(0)).author())
			.isEqualTo(new TipViewResponse.Author(null, "탈퇴한 사용자"));
		assertThat(service.view(VIEWER, missing, northOf(0)).author())
			.isEqualTo(new TipViewResponse.Author(null, "탈퇴한 사용자"));
	}

	@Test
	void 공개_팁이_아니면_T001_5m_밖이면_T004() {
		Long registeringId = em.persistAndGetId(registering(AUTHOR), Long.class);
		Long tipId = em.persistAndGetId(published(AUTHOR), Long.class);

		expect(registeringId, ExceptionType.TIP_NOT_FOUND, 0);
		expect(tipId, ExceptionType.TIP_OUT_OF_RANGE, 5);  // 약 5.6m
	}

	@Test
	void 공간_참조가_만료됐으면_5m_안이어도_T005() {
		ArTip tip = published(AUTHOR);
		Long tipId = em.persistAndGetId(tip, Long.class);
		em.getEntityManager().createNativeQuery("UPDATE ar_tips SET spatial_expires_at = :at WHERE id = :id")
			.setParameter("at", NOW).setParameter("id", tipId).executeUpdate();
		em.clear();

		expect(tipId, ExceptionType.TIP_SPATIAL_EXPIRED, 0);
	}

	private void expect(Long tipId, ExceptionType type, int stepsNorth) {
		assertThatThrownBy(() -> service.view(VIEWER, tipId, northOf(stepsNorth)))
			.extracting("exceptionType").isEqualTo(type);
	}
}

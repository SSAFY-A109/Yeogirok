package com.ssafy.yeogirok.domain.tip.service;

import static com.ssafy.yeogirok.domain.tip.TipFixture.northOf;
import static com.ssafy.yeogirok.domain.tip.TipFixture.published;
import static com.ssafy.yeogirok.domain.tip.TipFixture.registering;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.tip.TipProperties;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.location.LocationEvidence;

@DataJpaTest
@Import(PublicTipFinder.class)
@EnableConfigurationProperties(TipProperties.class)
class PublicTipFinderTest {

	@Autowired
	PublicTipFinder finder;

	@Autowired
	TipProperties properties;

	@Autowired
	TestEntityManager em;

	@Test
	void 상호작용_거리는_5m_경계를_포함한다() {
		assertThat(properties.canInteract(5.0)).isTrue();
		assertThat(properties.canInteract(5.000001)).isFalse();
		assertThat(properties.inSearchRadius(50.0)).isTrue();
		assertThat(properties.inSearchRadius(50.000001)).isFalse();
	}

	@Test
	void 공개_팁은_5m_안에서_돌려주고_밖이면_T004() {
		Long id = em.persistAndGetId(published(1L), Long.class);

		assertThat(finder.getInteractable(id, northOf(4)).getId()).isEqualTo(id);  // 약 4.4m
		expect(id, northOf(5), ExceptionType.TIP_OUT_OF_RANGE);  // 약 5.6m
	}

	@Test
	void 게시_전_삭제_없는_팁은_거리와_상관없이_T001() {
		Long registering = em.persistAndGetId(registering(1L), Long.class);
		Long deleted = em.persistAndGetId(published(1L), Long.class);
		em.getEntityManager().createNativeQuery("UPDATE ar_tips SET deleted_at = published_at WHERE id = " + deleted)
			.executeUpdate();

		expect(registering, northOf(0), ExceptionType.TIP_NOT_FOUND);
		expect(deleted, northOf(0), ExceptionType.TIP_NOT_FOUND);
		expect(deleted + 1000, northOf(100), ExceptionType.TIP_NOT_FOUND);
	}

	private void expect(Long tipId, LocationEvidence location, ExceptionType type) {
		assertThatThrownBy(() -> finder.getInteractable(tipId, location)).extracting("exceptionType").isEqualTo(type);
	}
}

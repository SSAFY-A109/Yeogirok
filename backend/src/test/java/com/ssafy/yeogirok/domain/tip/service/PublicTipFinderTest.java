package com.ssafy.yeogirok.domain.tip.service;

import static com.ssafy.yeogirok.domain.tip.TipFixture.northOf;
import static com.ssafy.yeogirok.domain.tip.TipFixture.published;
import static com.ssafy.yeogirok.domain.tip.TipFixture.registering;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.tip.TipProperties;
import com.ssafy.yeogirok.domain.tip.entity.ArTip;
import com.ssafy.yeogirok.global.exception.ExceptionType;

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

		ArTip tip = finder.getPublished(id);

		assertThatCode(() -> finder.checkInteractable(tip, northOf(4))).doesNotThrowAnyException();  // 약 4.4m
		assertThatThrownBy(() -> finder.checkInteractable(tip, northOf(5)))  // 약 5.6m
			.extracting("exceptionType").isEqualTo(ExceptionType.TIP_OUT_OF_RANGE);
	}

	@Test
	void 게시_전_삭제_없는_팁은_T001() {
		Long registering = em.persistAndGetId(registering(1L), Long.class);
		Long deleted = em.persistAndGetId(published(1L), Long.class);
		em.getEntityManager().createNativeQuery("UPDATE ar_tips SET deleted_at = published_at WHERE id = " + deleted)
			.executeUpdate();

		for (Long id : new Long[] {registering, deleted, deleted + 1000}) {
			assertThatThrownBy(() -> finder.getPublished(id)).extracting("exceptionType")
				.isEqualTo(ExceptionType.TIP_NOT_FOUND);
		}
	}
}

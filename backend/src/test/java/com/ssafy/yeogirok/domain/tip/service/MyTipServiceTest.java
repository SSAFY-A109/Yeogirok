package com.ssafy.yeogirok.domain.tip.service;

import static com.ssafy.yeogirok.domain.tip.TipFixture.NOW;
import static com.ssafy.yeogirok.domain.tip.TipFixture.publish;
import static com.ssafy.yeogirok.domain.tip.TipFixture.registeringAt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.tip.dto.MyTipListResponse;
import com.ssafy.yeogirok.domain.tip.entity.ArTip;
import com.ssafy.yeogirok.domain.tip.entity.TipStatus;
import com.ssafy.yeogirok.global.exception.ExceptionType;

/** API-053 규칙. 팁 등록 시각은 NOW 기준 분 단위로 다르게 둔다. */
@DataJpaTest
@Import(MyTipService.class)
class MyTipServiceTest {

	static final Long ME = 8001L;
	static final Long OTHER = 8002L;

	@Autowired
	MyTipService service;

	@Autowired
	TestEntityManager em;

	@Test
	void 내_팁만_최신_등록순으로_주고_삭제된_팁은_뺀다() {
		Long old = persist(registeringAt(ME, NOW.minusSeconds(120)));
		Long newest = persist(publish(registeringAt(ME, NOW)));
		Long deleted = persist(registeringAt(ME, NOW.minusSeconds(60)));
		em.getEntityManager().createNativeQuery("UPDATE ar_tips SET deleted_at = created_at WHERE id = " + deleted)
			.executeUpdate();
		persist(registeringAt(OTHER, NOW));

		MyTipListResponse response = service.getMyTips(ME, null, null, null);

		assertThat(ids(response)).containsExactly(newest, old);
		assertThat(response.items().getFirst()).isEqualTo(new MyTipListResponse.Item(newest,
			response.items().getFirst().category(), TipStatus.PUBLISHED, NOW, NOW));
		assertThat(response.nextCursor()).isNull();
	}

	@Test
	void 등록_시각이_같으면_id_큰_순이다() {
		Long first = persist(registeringAt(ME, NOW));
		Long second = persist(registeringAt(ME, NOW));

		assertThat(ids(service.getMyTips(ME, null, null, null))).containsExactly(second, first);
	}

	@Test
	void limit만큼_나눠_주고_마지막_쪽의_nextCursor는_null() {
		Long a = persist(registeringAt(ME, NOW.minusSeconds(3)));
		Long b = persist(registeringAt(ME, NOW.minusSeconds(2)));
		Long c = persist(registeringAt(ME, NOW.minusSeconds(1)));

		MyTipListResponse first = service.getMyTips(ME, null, null, 2);
		MyTipListResponse second = service.getMyTips(ME, null, first.nextCursor(), 2);

		assertThat(ids(first)).containsExactly(c, b);
		assertThat(first.nextCursor()).isNotNull();
		assertThat(ids(second)).containsExactly(a);
		assertThat(second.nextCursor()).isNull();
	}

	@Test
	void status로_거른다() {
		Long registering = persist(registeringAt(ME, NOW.minusSeconds(2)));
		Long published = persist(publish(registeringAt(ME, NOW.minusSeconds(1))));
		ArTip failedTip = registeringAt(ME, NOW);
		failedTip.fail(NOW);
		Long failed = persist(failedTip);

		assertThat(ids(service.getMyTips(ME, "REGISTERING", null, null))).containsExactly(registering);
		assertThat(ids(service.getMyTips(ME, "PUBLISHED", null, null))).containsExactly(published);
		assertThat(ids(service.getMyTips(ME, "FAILED", null, null))).containsExactly(failed);
	}

	@ParameterizedTest
	@ValueSource(strings = {"DELETED", "published", ""})
	void 목록에서_고를_수_없는_status는_C003(String status) {
		assertThatThrownBy(() -> service.getMyTips(ME, status, null, null))
			.extracting("exceptionType").isEqualTo(ExceptionType.NOT_ALLOWED_VALUE);
	}

	@Test
	void limit_범위_밖은_C007_잘못된_커서는_C006() {
		assertThatThrownBy(() -> service.getMyTips(ME, null, null, 51))
			.extracting("exceptionType").isEqualTo(ExceptionType.PAGE_SIZE_OUT_OF_RANGE);
		assertThatThrownBy(() -> service.getMyTips(ME, null, "%%%", null))
			.extracting("exceptionType").isEqualTo(ExceptionType.INVALID_CURSOR);
	}

	private Long persist(ArTip tip) {
		return em.persistAndGetId(tip, Long.class);
	}

	private static List<Long> ids(MyTipListResponse response) {
		return response.items().stream().map(MyTipListResponse.Item::id).toList();
	}
}

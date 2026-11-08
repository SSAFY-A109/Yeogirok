package com.ssafy.yeogirok.domain.tip.service;

import static com.ssafy.yeogirok.domain.tip.TipFixture.LAT;
import static com.ssafy.yeogirok.domain.tip.TipFixture.LNG;
import static com.ssafy.yeogirok.domain.tip.TipFixture.NOW;
import static com.ssafy.yeogirok.domain.tip.TipFixture.locationAt;
import static com.ssafy.yeogirok.domain.tip.TipFixture.northOf;
import static com.ssafy.yeogirok.domain.tip.TipFixture.publish;
import static com.ssafy.yeogirok.domain.tip.TipFixture.registering;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.tip.TipProperties;
import com.ssafy.yeogirok.domain.tip.dto.TipSearchRequest.TipSearchQuery;
import com.ssafy.yeogirok.domain.tip.dto.TipSearchResponse;
import com.ssafy.yeogirok.domain.tip.entity.ArTip;
import com.ssafy.yeogirok.domain.tip.entity.ArTipCategory;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.geo.GeoDistance;
import com.ssafy.yeogirok.global.location.LocationEvidence;
import com.ssafy.yeogirok.global.location.LocationEvidencePolicy;
import com.ssafy.yeogirok.global.location.LocationEvidenceProperties;
import com.ssafy.yeogirok.support.MutableClock;

/** API-047 규칙. 팁은 기준 위치(LAT, LNG)에서 북쪽으로 1e-5도(약 1.11m) 단위로 놓는다. */
@DataJpaTest
@Import({TipSearchService.class, LocationEvidencePolicy.class, TipSearchServiceTest.Config.class})
class TipSearchServiceTest {

	static final Long AUTHOR = 1L;

	@TestConfiguration
	@EnableConfigurationProperties({TipProperties.class, LocationEvidenceProperties.class})
	static class Config {

		@Bean
		MutableClock clock() {
			return new MutableClock(NOW);
		}
	}

	@Autowired
	TipSearchService service;

	@Autowired
	TestEntityManager em;

	@Test
	void 반경_50m_경계는_포함하고_밖은_뺀다() {
		// 북쪽 위도 차이로 정확히 50m 가 되는 위치에 팁을 둔다
		BigDecimal fiftyMeters = latitudeAtMeters(50);
		Long inside = tipAt(locationAt(fiftyMeters, LNG), ArTipCategory.CAFE);
		tipAt(locationAt(latitudeAtMeters(50.5), LNG), ArTipCategory.CAFE);

		TipSearchResponse response = service.search(query(locationAt(LAT, LNG), null, null));

		assertThat(response.items()).extracting(TipSearchResponse.Item::id).containsExactly(inside);
		assertThat(response.items().getFirst().distanceMeters()).isLessThanOrEqualTo(50.0);
	}

	@Test
	void canInteract는_5m_경계를_포함한다() {
		Long near = tipAt(northOf(4), ArTipCategory.CAFE);  // 약 4.4m
		Long far = tipAt(northOf(5), ArTipCategory.CAFE);   // 약 5.6m

		List<TipSearchResponse.Item> items = service.search(query(locationAt(LAT, LNG), null, null)).items();

		assertThat(items).extracting(TipSearchResponse.Item::id).containsExactly(near, far);
		assertThat(items).extracting(TipSearchResponse.Item::canInteract).containsExactly(true, false);
		assertThat(items).allSatisfy(item -> assertThat(item.canInteract()).isEqualTo(item.distanceMeters() <= 5));
	}

	@Test
	void 게시_전_삭제된_팁은_빼고_category로_거른다() {
		em.persist(registering(AUTHOR));
		Long deleted = tipAt(northOf(1), ArTipCategory.CAFE);
		em.getEntityManager().createNativeQuery("UPDATE ar_tips SET deleted_at = published_at WHERE id = " + deleted)
			.executeUpdate();
		Long cafe = tipAt(northOf(2), ArTipCategory.CAFE);
		Long photo = tipAt(northOf(3), ArTipCategory.PHOTO);

		assertThat(ids(service.search(query(locationAt(LAT, LNG), null, null)))).containsExactly(cafe, photo);
		TipSearchResponse photos = service.search(query(locationAt(LAT, LNG), ArTipCategory.PHOTO, null));
		assertThat(ids(photos)).containsExactly(photo);
		assertThat(photos.totalCount()).isEqualTo(1);
	}

	@Test
	void 열한_개면_가까운_열_개와_전체_개수를_주고_nextCursor는_null() {
		for (int i = 11; i >= 1; i--) {
			tipAt(northOf(i), ArTipCategory.TIP);
		}

		TipSearchResponse response = service.search(query(locationAt(LAT, LNG), null, null));

		assertThat(response.items()).hasSize(10);
		assertThat(response.items()).extracting(TipSearchResponse.Item::distanceMeters).isSorted();
		assertThat(response.totalCount()).isEqualTo(11);
		assertThat(response.nextCursor()).isNull();
		assertThat(response.appliedRadiusMeters()).isEqualTo(50);
		assertThat(response.interactionRadiusMeters()).isEqualTo(5);
		assertThat(service.search(query(locationAt(LAT, LNG), null, 3L)).items()).hasSize(3);
	}

	@Test
	void 거리가_같으면_id_순이다() {
		Long first = tipAt(northOf(2), ArTipCategory.TIP);
		Long second = tipAt(northOf(2), ArTipCategory.TIP);

		assertThat(ids(service.search(query(locationAt(LAT, LNG), null, null)))).containsExactly(first, second);
	}

	@ParameterizedTest
	@ValueSource(longs = {0, 11, -1})
	void limit이_1에서_10_밖이면_C007(long limit) {
		assertThatThrownBy(() -> service.search(query(locationAt(LAT, LNG), null, limit)))
			.extracting("exceptionType").isEqualTo(ExceptionType.PAGE_SIZE_OUT_OF_RANGE);
	}

	@ParameterizedTest
	@ValueSource(longs = {49, 51, 0})
	void radiusMeters가_50이_아니면_C003(long radius) {
		TipSearchQuery query = new TipSearchQuery(locationAt(LAT, LNG), radius, null, null);

		assertThatThrownBy(() -> service.search(query))
			.extracting("exceptionType").isEqualTo(ExceptionType.NOT_ALLOWED_VALUE);
	}

	@Test
	void 위치_정확도와_유효_시각을_검사한다() {
		LocationEvidence inaccurate = new LocationEvidence(LAT, LNG, new BigDecimal("30.5"), NOW);
		LocationEvidence stale = new LocationEvidence(LAT, LNG, BigDecimal.ONE, NOW.minus(Duration.ofHours(1)));

		assertThatThrownBy(() -> service.search(query(inaccurate, null, null)))
			.extracting("exceptionType").isEqualTo(ExceptionType.LOCATION_ACCURACY_INSUFFICIENT);
		assertThatThrownBy(() -> service.search(query(stale, null, null)))
			.extracting("exceptionType").isEqualTo(ExceptionType.LOCATION_EVIDENCE_EXPIRED);
	}

	private Long tipAt(LocationEvidence location, ArTipCategory category) {
		ArTip tip = publish(registering(AUTHOR, category, location));
		return em.persistAndGetId(tip, Long.class);
	}

	/** 기준 위치에서 북쪽으로 {@code meters} 떨어진 위도(소수 7자리, 넘지 않게 내림). */
	private static BigDecimal latitudeAtMeters(double meters) {
		BigDecimal step = new BigDecimal("0.0000001");
		BigDecimal latitude = LAT;
		while (GeoDistance.meters(LAT, LNG, latitude.add(step), LNG) <= meters) {
			latitude = latitude.add(step);
		}
		return latitude;
	}

	private static TipSearchQuery query(LocationEvidence location, ArTipCategory category, Long limit) {
		return new TipSearchQuery(location, null, category, limit);
	}

	private static List<Long> ids(TipSearchResponse response) {
		return response.items().stream().map(TipSearchResponse.Item::id).toList();
	}
}

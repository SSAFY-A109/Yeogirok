package com.ssafy.yeogirok.domain.tip.service;

import static com.ssafy.yeogirok.domain.tip.TipFixture.LAT;
import static com.ssafy.yeogirok.domain.tip.TipFixture.LNG;
import static com.ssafy.yeogirok.domain.tip.TipFixture.NOW;
import static com.ssafy.yeogirok.domain.tip.TipFixture.locationAt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import com.ssafy.yeogirok.domain.file.FilePurpose;
import com.ssafy.yeogirok.domain.file.session.UploadSession;
import com.ssafy.yeogirok.domain.file.session.UploadSessionStore;
import com.ssafy.yeogirok.domain.tip.TipProperties;
import com.ssafy.yeogirok.domain.tip.dto.TipCreateRequest.TipCreateCommand;
import com.ssafy.yeogirok.domain.tip.dto.TipCreateResponse;
import com.ssafy.yeogirok.domain.tip.entity.ArTip;
import com.ssafy.yeogirok.domain.tip.entity.ArTipCategory;
import com.ssafy.yeogirok.domain.tip.entity.ArTipImage;
import com.ssafy.yeogirok.domain.tip.entity.TipStatus;
import com.ssafy.yeogirok.domain.tip.publish.TipPublishRequested;
import com.ssafy.yeogirok.domain.tip.publish.TipPublishState;
import com.ssafy.yeogirok.domain.tip.publish.TipPublishStore;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.location.LocationEvidence;
import com.ssafy.yeogirok.global.location.LocationEvidencePolicy;
import com.ssafy.yeogirok.global.location.LocationEvidenceProperties;
import com.ssafy.yeogirok.support.InMemoryTipPublishStates;
import com.ssafy.yeogirok.support.InMemoryUploadSessions;
import com.ssafy.yeogirok.support.MutableClock;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** API-049 규칙. 커밋 뒤 게시는 TipPublisherTest 가 본다. */
@DataJpaTest
@RecordApplicationEvents
@Import({TipCreateService.class, MyTipFinder.class, TipPlacements.class, LocationEvidencePolicy.class, TipCreateServiceTest.Config.class})
class TipCreateServiceTest {

	static final Long AUTHOR = 3001L;
	static final Long OTHER = 3002L;
	static final String UPLOAD_ID = "550e8400-e29b-41d4-a716-446655440010";
	static final JsonMapper MAPPER = JsonMapper.builder().build();
	static final InMemoryUploadSessions SESSIONS = new InMemoryUploadSessions();
	static final InMemoryTipPublishStates STATES = new InMemoryTipPublishStates();
	static final String SPATIAL =
		"{\"provider\":\"ARCORE_CLOUD_ANCHOR\",\"referenceId\":\"anchor-1\",\"expiresAt\":\"2026-11-09 12:00:00+09\"}";
	static final String PLACEMENT = "{\"schemaVersion\":1,\"anchorType\":\"CLOUD\",\"authorizationMode\":\"KEYLESS\"}";

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
			return SESSIONS.store;
		}

		@Bean
		TipPublishStore tipPublishStore() {
			return STATES.store;
		}
	}

	@Autowired
	TipCreateService service;

	@Autowired
	TestEntityManager em;

	@Autowired
	ApplicationEvents events;

	@BeforeEach
	void clear() {
		SESSIONS.sessions.clear();
		STATES.states.clear();
	}

	@Test
	void 사진_없이_등록하면_REGISTERING으로_저장하고_media_SKIPPED_작업을_남긴다() {
		TipCreateResponse response = service.create(AUTHOR, command("  창가 자리\r\n좋아요 ", List.of(), SPATIAL));

		ArTip tip = em.find(ArTip.class, response.tipId());
		assertThat(response.status()).isEqualTo(TipStatus.REGISTERING);
		assertThat(tip.getStatus()).isEqualTo(TipStatus.REGISTERING);
		assertThat(tip.getBody()).isEqualTo("  창가 자리\n좋아요 ");
		assertThat(tip.getCategory()).isEqualTo(ArTipCategory.RESTAURANT);
		assertThat(tip.getLatitude()).isEqualByComparingTo(LAT);
		assertThat(tip.getAccuracyMeters()).isEqualByComparingTo("8");
		assertThat(tip.getPlacement()).containsEntry("anchorType", "CLOUD");
		assertThat(tip.spatialReference().referenceId()).isEqualTo("anchor-1");
		assertThat(STATES.states.get(response.tipId())).isEqualTo(TipPublishState.queued(response.jobId(), false));
		assertThat(events.stream(TipPublishRequested.class))
			.containsExactly(new TipPublishRequested(response.tipId(), response.jobId(), null));
	}

	@Test
	void 사진이_있으면_확정_경로를_게시_작업에_넘긴다() {
		SESSIONS.put(readyPhoto(AUTHOR, FilePurpose.TIP_PHOTO));

		TipCreateResponse response = service.create(AUTHOR, command("좋아요", List.of(UPLOAD_ID), SPATIAL));

		assertThat(STATES.states.get(response.tipId()).steps().media()).isEqualTo(TipPublishState.StepStatus.PENDING);
		assertThat(events.stream(TipPublishRequested.class))
			.containsExactly(new TipPublishRequested(response.tipId(), response.jobId(), "tip-photo/" + UPLOAD_ID));
	}

	@Test
	void 사진_오류는_M004_M005_M006_M007() {
		expect(command("좋아요", List.of(UPLOAD_ID), SPATIAL), ExceptionType.FILE_NOT_FOUND);

		SESSIONS.put(readyPhoto(OTHER, FilePurpose.TIP_PHOTO));
		expect(command("좋아요", List.of(UPLOAD_ID), SPATIAL), ExceptionType.FILE_ACCESS_DENIED);

		SESSIONS.put(uploading(AUTHOR));
		expect(command("좋아요", List.of(UPLOAD_ID), SPATIAL), ExceptionType.FILE_NOT_UPLOADED);

		SESSIONS.put(readyPhoto(AUTHOR, FilePurpose.RECORD_PHOTO));
		expect(command("좋아요", List.of(UPLOAD_ID), SPATIAL), ExceptionType.FILE_PURPOSE_MISMATCH);
		assertThat(events.stream(TipPublishRequested.class)).isEmpty();
	}

	@Test
	void 이미_다른_팁에_붙은_사진은_M005() {
		SESSIONS.put(readyPhoto(AUTHOR, FilePurpose.TIP_PHOTO));
		em.persist(ArTipImage.of(999L, "tip-photo/" + UPLOAD_ID, NOW));

		expect(command("좋아요", List.of(UPLOAD_ID), SPATIAL), ExceptionType.FILE_ACCESS_DENIED);
	}

	@Test
	void 사진_두_장은_C003() {
		expect(command("좋아요", List.of(UPLOAD_ID, "550e8400-e29b-41d4-a716-446655440011"), SPATIAL),
			ExceptionType.NOT_ALLOWED_VALUE);
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource(delimiter = '|', textBlock = """
		본문 공백만        | '   '  | RESTAURANT | C003
		본문 121자         | LONG   | RESTAURANT | C003
		category 목록 밖   | 좋아요 | FOOD       | C003
		""")
	void 본문_종류_오류(String name, String body, String category, String code) {
		TipCreateCommand command = new TipCreateCommand(category, body.equals("LONG") ? "가".repeat(121) : body,
			List.of(), locationAt(LAT, LNG), json(SPATIAL), json(PLACEMENT));

		assertThatThrownBy(() -> service.create(AUTHOR, command))
			.extracting("exceptionType").extracting("code").isEqualTo(code);
	}

	@Test
	void expiresAt이_없거나_지났거나_placement가_8KB를_넘으면_T007() {
		expect(command("좋아요", List.of(),
			"{\"provider\":\"ARCORE_CLOUD_ANCHOR\",\"referenceId\":\"a\"}"), ExceptionType.TIP_SPATIAL_INVALID);
		expect(command("좋아요", List.of(), SPATIAL.replace("2026-11-09", "2026-11-07")),
			ExceptionType.TIP_SPATIAL_INVALID);

		TipCreateCommand bigPlacement = new TipCreateCommand("TIP", "좋아요", List.of(), locationAt(LAT, LNG),
			json(SPATIAL), MAPPER.valueToTree(Map.of("k", "a".repeat(8192))));
		expect(bigPlacement, ExceptionType.TIP_SPATIAL_INVALID);
	}

	@Test
	void 위치_증거가_오래됐으면_C009() {
		LocationEvidence stale = new LocationEvidence(LAT, LNG, BigDecimal.ONE, NOW.minus(Duration.ofHours(1)));

		expect(new TipCreateCommand("TIP", "좋아요", List.of(), stale, json(SPATIAL), json(PLACEMENT)),
			ExceptionType.LOCATION_EVIDENCE_EXPIRED);
	}

	@Test
	void 멱등키_재전송은_같은_tipId와_jobId를_준다() {
		TipCreateResponse created = service.create(AUTHOR, command("좋아요", List.of(), SPATIAL));

		assertThat(service.getCreated(AUTHOR, created.tipId())).isEqualTo(created);
	}

	private void expect(TipCreateCommand command, ExceptionType type) {
		assertThatThrownBy(() -> service.create(AUTHOR, command)).extracting("exceptionType").isEqualTo(type);
	}

	private static TipCreateCommand command(String body, List<String> uploadIds, String spatial) {
		return new TipCreateCommand("RESTAURANT", body, uploadIds, locationAt(LAT, LNG), json(spatial), json(PLACEMENT));
	}

	private static JsonNode json(String value) {
		return MAPPER.readTree(value);
	}

	private static UploadSession uploading(Long owner) {
		return UploadSession.start(UPLOAD_ID, owner, FilePurpose.TIP_PHOTO, null, "photos", "image/png", 1024, NOW,
			NOW.plus(Duration.ofMinutes(10)));
	}

	private static UploadSession readyPhoto(Long owner, FilePurpose purpose) {
		return UploadSession.start(UPLOAD_ID, owner, purpose, null, "photos", "image/png", 1024, NOW,
			NOW.plus(Duration.ofMinutes(10))).ready(purpose.confirmedKey(UPLOAD_ID), 1024, null, null);
	}
}

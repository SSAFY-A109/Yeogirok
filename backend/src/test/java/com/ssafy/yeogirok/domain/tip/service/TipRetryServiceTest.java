package com.ssafy.yeogirok.domain.tip.service;

import static com.ssafy.yeogirok.domain.tip.TipFixture.LAT;
import static com.ssafy.yeogirok.domain.tip.TipFixture.LNG;
import static com.ssafy.yeogirok.domain.tip.TipFixture.NOW;
import static com.ssafy.yeogirok.domain.tip.TipFixture.northOf;
import static com.ssafy.yeogirok.domain.tip.TipFixture.published;
import static com.ssafy.yeogirok.domain.tip.TipFixture.registering;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import com.ssafy.yeogirok.domain.tip.dto.TipRetryRequest.TipRetryCommand;
import com.ssafy.yeogirok.domain.tip.dto.TipRetryResponse;
import com.ssafy.yeogirok.domain.tip.entity.ArTip;
import com.ssafy.yeogirok.domain.tip.entity.ArTipCategory;
import com.ssafy.yeogirok.domain.tip.entity.SpatialReference;
import com.ssafy.yeogirok.domain.tip.entity.TipStatus;
import com.ssafy.yeogirok.domain.tip.publish.TipEditDraft;
import com.ssafy.yeogirok.domain.tip.publish.TipEditDraft.PhotoChange;
import com.ssafy.yeogirok.domain.tip.publish.TipEditStore;
import com.ssafy.yeogirok.domain.tip.publish.TipEditor;
import com.ssafy.yeogirok.domain.tip.publish.TipPublishRequested;
import com.ssafy.yeogirok.domain.tip.publish.TipPublishState;
import com.ssafy.yeogirok.domain.tip.publish.TipPublishState.Operation;
import com.ssafy.yeogirok.domain.tip.publish.TipPublishState.OperationStatus;
import com.ssafy.yeogirok.domain.tip.publish.TipPublishStore;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.location.LocationEvidence;
import com.ssafy.yeogirok.global.location.LocationEvidencePolicy;
import com.ssafy.yeogirok.global.location.LocationEvidenceProperties;
import com.ssafy.yeogirok.support.InMemoryTipEditStore;
import com.ssafy.yeogirok.support.InMemoryTipPhotoClaims;
import com.ssafy.yeogirok.support.InMemoryTipPublishStates;
import com.ssafy.yeogirok.support.InMemoryUploadSessions;
import com.ssafy.yeogirok.support.MutableClock;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * API-051 규칙. 최초 게시 재시도는 커밋 뒤 게시 이벤트를 남기는지까지, 수정 재시도는 TipEditor 가 같은 스레드에서 적용한 결과까지 본다
 * (DataJpaTest 에는 @EnableAsync 가 없다).
 */
@DataJpaTest
@RecordApplicationEvents
@Import({TipRetryService.class, TipEditor.class, MyTipFinder.class, PublicTipFinder.class, TipPhotoUploads.class,
	TipRelocations.class, TipPlacements.class, LocationEvidencePolicy.class, TipRetryServiceTest.Config.class})
class TipRetryServiceTest {

	static final Long AUTHOR = 13001L;
	static final Long OTHER = 13002L;
	static final String UPLOAD_ID = "550e8400-e29b-41d4-a716-446655440010";
	static final JsonMapper MAPPER = JsonMapper.builder().build();
	static final InMemoryUploadSessions SESSIONS = new InMemoryUploadSessions();
	static final InMemoryTipPhotoClaims CLAIMS = new InMemoryTipPhotoClaims();
	static final InMemoryTipPublishStates STATES = new InMemoryTipPublishStates();
	static final InMemoryTipEditStore EDITS = new InMemoryTipEditStore();
	static final String SPATIAL =
		"{\"provider\":\"ARCORE_CLOUD_ANCHOR\",\"referenceId\":\"anchor-2\",\"expiresAt\":\"2026-11-09 12:00:00+09\"}";
	static final TipRetryCommand NOTHING = new TipRetryCommand(null, null, null, null);

	@TestConfiguration
	@EnableConfigurationProperties({TipProperties.class, LocationEvidenceProperties.class})
	static class Config {

		@Bean
		MutableClock clock() {
			return new MutableClock(NOW.plusSeconds(60));
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
	TipRetryService service;

	@Autowired
	TestEntityManager em;

	@Autowired
	ApplicationEvents events;

	@BeforeEach
	void clear() {
		SESSIONS.sessions.clear();
		STATES.states.clear();
		CLAIMS.claims.clear();
		EDITS.clear();
	}

	@Test
	void 실패한_최초_게시는_지난_사진으로_다시_게시_작업을_넣는다() {
		ArTip tip = failedTip();
		STATES.states.put(tip.getId(), failedCreate("tip-photo/old"));

		TipRetryResponse response = service.retry(AUTHOR, tip.getId(), NOTHING);
		em.flush();
		em.clear();

		assertThat(response).isEqualTo(new TipRetryResponse(tip.getId(), response.jobId(), TipStatus.REGISTERING,
			Operation.CREATE, OperationStatus.QUEUED));
		assertThat(em.find(ArTip.class, tip.getId()).getStatus()).isEqualTo(TipStatus.REGISTERING);
		assertThat(STATES.states.get(tip.getId())).isEqualTo(TipPublishState.queued(response.jobId(), "tip-photo/old"));
		assertThat(events.stream(TipPublishRequested.class))
			.containsExactly(new TipPublishRequested(tip.getId(), response.jobId(), "tip-photo/old"));
	}

	@Test
	void replacementUploadIds로_사진을_바꾸거나_빼고_재시도한다() {
		ArTip tip = failedTip();
		SESSIONS.put(readyPhoto());

		TipRetryResponse replaced = service.retry(AUTHOR, tip.getId(), photos(List.of(UPLOAD_ID)));

		assertThat(events.stream(TipPublishRequested.class))
			.containsExactly(new TipPublishRequested(tip.getId(), replaced.jobId(), "tip-photo/" + UPLOAD_ID));

		ArTip another = failedTip();
		TipRetryResponse withoutPhoto = service.retry(AUTHOR, another.getId(), photos(List.of()));
		assertThat(STATES.states.get(another.getId()).steps().media()).isEqualTo(TipPublishState.StepStatus.SKIPPED);
		assertThat(withoutPhoto.operation()).isEqualTo(Operation.CREATE);
	}

	@Test
	void 지난_게시_작업_정보가_없고_사진도_다시_지정하지_않으면_T008() {
		ArTip tip = failedTip();

		expect(() -> service.retry(AUTHOR, tip.getId(), NOTHING), ExceptionType.TIP_RETRY_UNAVAILABLE);
	}

	@Test
	void 공간_참조가_만료됐으면_새_재배치가_없을_때_T007이고_있으면_거리_검사_없이_바꾼다() {
		ArTip tip = failedTip();
		STATES.states.put(tip.getId(), failedCreate(null));
		em.getEntityManager().createNativeQuery("UPDATE ar_tips SET spatial_expires_at = :at WHERE id = :id")
			.setParameter("at", NOW).setParameter("id", tip.getId()).executeUpdate();
		em.clear();

		expect(() -> service.retry(AUTHOR, tip.getId(), NOTHING), ExceptionType.TIP_SPATIAL_INVALID);

		LocationEvidence farAway = new LocationEvidence(LAT.add(new BigDecimal("0.0003")), LNG, BigDecimal.ONE,
			NOW.plusSeconds(60));  // 약 33m
		service.retry(AUTHOR, tip.getId(), new TipRetryCommand(null, farAway, json(SPATIAL), json("{\"v\":2}")));
		em.flush();
		em.clear();
		ArTip after = em.find(ArTip.class, tip.getId());
		assertThat(after.getLatitude()).isEqualByComparingTo(farAway.latitude());
		assertThat(after.spatialReference().referenceId()).isEqualTo("anchor-2");
	}

	@Test
	void 공개_팁의_실패한_수정은_남은_수정안으로_다시_적용한다() {
		ArTip tip = em.persistFlushFind(published(AUTHOR));
		failUpdate(tip, draft(tip.getVersion(), null));

		TipRetryResponse response = service.retry(AUTHOR, tip.getId(), NOTHING);
		em.flush();
		em.clear();

		assertThat(response).isEqualTo(new TipRetryResponse(tip.getId(), response.jobId(), TipStatus.PUBLISHED,
			Operation.UPDATE, OperationStatus.QUEUED));
		assertThat(em.find(ArTip.class, tip.getId()).getCategory()).isEqualTo(ArTipCategory.TMI);
		assertThat(STATES.states.get(tip.getId()).operationStatus()).isEqualTo(OperationStatus.SUCCEEDED);
		assertThat(EDITS.drafts).as("지난 수정안과 새 수정안 모두 정리된다").isEmpty();
		assertThat(EDITS.locks).isEmpty();
	}

	@Test
	void 수정안이_없으면_T008_기준_version이_바뀌었으면_C004() {
		ArTip tip = em.persistFlushFind(published(AUTHOR));
		STATES.states.put(tip.getId(), TipPublishState.queuedUpdate("job-old", false, false).running()
			.failed("C014", "x", true));
		expect(() -> service.retry(AUTHOR, tip.getId(), NOTHING), ExceptionType.TIP_RETRY_UNAVAILABLE);

		failUpdate(tip, draft(tip.getVersion() + 1, null));
		expect(() -> service.retry(AUTHOR, tip.getId(), NOTHING), ExceptionType.VERSION_MISMATCH);
	}

	@Test
	void 수정안의_재배치는_위치_증거가_오래됐으면_C009_공간_참조가_만료됐으면_T007() {
		ArTip tip = em.persistFlushFind(published(AUTHOR));
		LocationEvidence stale = new LocationEvidence(LAT, LNG, BigDecimal.ONE, NOW.minus(Duration.ofHours(1)));
		failUpdate(tip, draft(tip.getVersion(), new TipEditDraft.Relocation(stale, "test-v1",
			new SpatialReference(SpatialReference.PROVIDER, "a", NOW.plusSeconds(3600)), Map.of())));
		expect(() -> service.retry(AUTHOR, tip.getId(), NOTHING), ExceptionType.LOCATION_EVIDENCE_EXPIRED);

		failUpdate(tip, draft(tip.getVersion(), new TipEditDraft.Relocation(northOf(1), "test-v1",
			new SpatialReference(SpatialReference.PROVIDER, "a", NOW), Map.of())));
		expect(() -> service.retry(AUTHOR, tip.getId(), NOTHING), ExceptionType.TIP_SPATIAL_INVALID);
	}

	@Test
	void 할_일이_없는_공개_팁은_200용_결과이고_진행_중이면_T010() {
		ArTip noState = em.persistFlushFind(published(AUTHOR));
		ArTip running = em.persistFlushFind(published(AUTHOR));
		STATES.states.put(running.getId(), TipPublishState.queuedUpdate("job-1", false, false).running());
		ArTip registeringTip = em.persistFlushFind(registering(AUTHOR));

		assertThat(service.retry(AUTHOR, noState.getId(), NOTHING))
			.isEqualTo(TipRetryResponse.done(noState.getId(), TipStatus.PUBLISHED));
		expect(() -> service.retry(AUTHOR, running.getId(), NOTHING), ExceptionType.TIP_EDIT_IN_PROGRESS);
		expect(() -> service.retry(AUTHOR, registeringTip.getId(), NOTHING), ExceptionType.TIP_EDIT_IN_PROGRESS);
	}

	@Test
	void 다른_수정이_예약돼_있으면_T010이고_수정안은_그대로다() {
		ArTip tip = em.persistFlushFind(published(AUTHOR));
		failUpdate(tip, draft(tip.getVersion(), null));
		EDITS.locks.put(tip.getId(), "other");

		expect(() -> service.retry(AUTHOR, tip.getId(), NOTHING), ExceptionType.TIP_EDIT_IN_PROGRESS);
		assertThat(EDITS.drafts).containsKey(tip.getId() + ":job-old");
	}

	@Test
	void 남의_팁_T002_삭제된_팁_T012() {
		ArTip tip = failedTip();
		ArTip deleted = failedTip();
		em.getEntityManager().createNativeQuery("UPDATE ar_tips SET deleted_at = created_at WHERE id = "
			+ deleted.getId()).executeUpdate();

		expect(() -> service.retry(OTHER, tip.getId(), NOTHING), ExceptionType.TIP_NOT_OWNER);
		expect(() -> service.retry(AUTHOR, deleted.getId(), NOTHING), ExceptionType.TIP_NOT_EXIST);
	}

	@Test
	void 멱등키_재전송은_결과_ID로_다시_만든다() {
		ArTip tip = em.persistFlushFind(published(AUTHOR));
		STATES.states.put(tip.getId(), TipPublishState.queuedUpdate("job-1", false, false));

		assertThat(service.getRetried(AUTHOR, tip.getId(), TipRetryService.NOTHING_TO_RETRY))
			.isEqualTo(TipRetryResponse.done(tip.getId(), TipStatus.PUBLISHED));
		assertThat(service.getRetried(AUTHOR, tip.getId(), "job-1")).isEqualTo(new TipRetryResponse(tip.getId(),
			"job-1", TipStatus.PUBLISHED, Operation.UPDATE, OperationStatus.QUEUED));
	}

	private ArTip failedTip() {
		ArTip tip = registering(AUTHOR);
		tip.fail(NOW);
		return em.persistFlushFind(tip);
	}

	private void failUpdate(ArTip tip, TipEditDraft draft) {
		EDITS.drafts.put(tip.getId() + ":job-old", draft);
		STATES.states.put(tip.getId(), TipPublishState.queuedUpdate("job-old", false, draft.relocation() != null)
			.running().failed("C014", "x", true));
	}

	private static TipPublishState failedCreate(String imagePath) {
		return TipPublishState.queued("job-old", imagePath).running().failed("C014", "x", true);
	}

	private static TipEditDraft draft(int baseVersion, TipEditDraft.Relocation relocation) {
		return new TipEditDraft(AUTHOR, baseVersion, ArTipCategory.TMI, null, PhotoChange.KEEP, null, relocation);
	}

	private static TipRetryCommand photos(List<String> uploadIds) {
		return new TipRetryCommand(uploadIds, null, null, null);
	}

	private static JsonNode json(String value) {
		return MAPPER.readTree(value);
	}

	private static void expect(Runnable action, ExceptionType type) {
		assertThatThrownBy(action::run).extracting("exceptionType").isEqualTo(type);
	}

	private static UploadSession readyPhoto() {
		return UploadSession.start(UPLOAD_ID, AUTHOR, FilePurpose.TIP_PHOTO, null, "photos", "image/png", 1024, NOW,
			NOW.plus(Duration.ofMinutes(10))).ready(FilePurpose.TIP_PHOTO.confirmedKey(UPLOAD_ID), 1024, null, null);
	}
}

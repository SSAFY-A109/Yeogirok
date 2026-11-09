package com.ssafy.yeogirok.domain.tip.service;

import static com.ssafy.yeogirok.domain.tip.TipFixture.NOW;
import static com.ssafy.yeogirok.domain.tip.TipFixture.northOf;
import static com.ssafy.yeogirok.domain.tip.TipFixture.published;
import static com.ssafy.yeogirok.domain.tip.TipFixture.registering;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.file.FilePurpose;
import com.ssafy.yeogirok.domain.file.session.UploadSession;
import com.ssafy.yeogirok.domain.file.session.UploadSessionStore;
import com.ssafy.yeogirok.domain.tip.TipProperties;
import com.ssafy.yeogirok.domain.tip.dto.TipUpdateRequest.TipUpdateCommand;
import com.ssafy.yeogirok.domain.tip.dto.TipUpdateResponse;
import com.ssafy.yeogirok.domain.tip.entity.ArTip;
import com.ssafy.yeogirok.domain.tip.entity.ArTipCategory;
import com.ssafy.yeogirok.domain.tip.entity.ArTipImage;
import com.ssafy.yeogirok.domain.tip.entity.TipStatus;
import com.ssafy.yeogirok.domain.tip.publish.TipEditStore;
import com.ssafy.yeogirok.domain.tip.publish.TipEditor;
import com.ssafy.yeogirok.domain.tip.publish.TipPublishState;
import com.ssafy.yeogirok.domain.tip.publish.TipPublishState.OperationStatus;
import com.ssafy.yeogirok.domain.tip.publish.TipPublishState.StepStatus;
import com.ssafy.yeogirok.domain.tip.publish.TipPublishStore;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.location.LocationEvidence;
import com.ssafy.yeogirok.global.location.LocationEvidencePolicy;
import com.ssafy.yeogirok.global.location.LocationEvidenceProperties;
import com.ssafy.yeogirok.support.InMemoryTipEditStore;
import com.ssafy.yeogirok.support.InMemoryTipPublishStates;
import com.ssafy.yeogirok.support.InMemoryUploadSessions;
import com.ssafy.yeogirok.support.MutableClock;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * API-054 규칙. DataJpaTest 에는 @EnableAsync 가 없어 applyAsync 가 같은 스레드에서 바로 돈다. 그래서 요청부터 적용까지 한 번에 본다.
 */
@DataJpaTest
@Import({TipUpdateService.class, TipEditor.class, MyTipFinder.class, PublicTipFinder.class, TipPhotoUploads.class,
	TipRelocations.class, TipPlacements.class, LocationEvidencePolicy.class, TipUpdateServiceTest.Config.class})
class TipUpdateServiceTest {

	static final Long AUTHOR = 11001L;
	static final Long OTHER = 11002L;
	static final String UPLOAD_ID = "550e8400-e29b-41d4-a716-446655440010";
	static final JsonMapper MAPPER = JsonMapper.builder().build();
	static final InMemoryUploadSessions SESSIONS = new InMemoryUploadSessions();
	static final InMemoryTipPublishStates STATES = new InMemoryTipPublishStates();
	static final InMemoryTipEditStore EDITS = new InMemoryTipEditStore();
	static final String SPATIAL =
		"{\"provider\":\"ARCORE_CLOUD_ANCHOR\",\"referenceId\":\"anchor-2\",\"expiresAt\":\"2026-11-09 12:00:00+09\"}";

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

		@Bean
		TipEditStore tipEditStore() {
			return EDITS.store;
		}
	}

	@Autowired
	TipUpdateService service;

	@Autowired
	MutableClock clock;

	@Autowired
	TestEntityManager em;

	@BeforeEach
	void clear() {
		clock.set(NOW.plusSeconds(60));  // 팁 생성 시각(NOW) 뒤에 수정한다
		SESSIONS.sessions.clear();
		STATES.states.clear();
		EDITS.clear();
	}

	@Test
	void 종류와_본문을_바꾸면_공개_상태와_게시_시각은_그대로이고_version이_오른다() {
		ArTip saved = em.persistFlushFind(published(AUTHOR));
		int version = saved.getVersion();

		TipUpdateResponse response = service.update(AUTHOR, saved.getId(),
			command(version, "TMI", "  새 본문\r\n", null, null));
		em.flush();
		em.clear();

		ArTip tip = em.find(ArTip.class, saved.getId());
		assertThat(response).isEqualTo(new TipUpdateResponse(saved.getId(), response.jobId(), TipStatus.PUBLISHED,
			TipPublishState.Operation.UPDATE, OperationStatus.QUEUED, version));
		assertThat(tip.getCategory()).isEqualTo(ArTipCategory.TMI);
		assertThat(tip.getBody()).isEqualTo("  새 본문\n");
		assertThat(tip.getStatus()).isEqualTo(TipStatus.PUBLISHED);
		assertThat(tip.getPublishedAt()).isEqualTo(NOW);
		assertThat(tip.getVersion()).isEqualTo(version + 1);
		TipPublishState state = STATES.states.get(saved.getId());
		assertThat(state.operationStatus()).isEqualTo(OperationStatus.SUCCEEDED);
		assertThat(state.steps().media()).isEqualTo(StepStatus.SKIPPED);
		assertThat(state.steps().spatial()).isEqualTo(StepStatus.SKIPPED);
		assertThat(EDITS.locks).as("끝나면 예약을 푼다").isEmpty();
		assertThat(EDITS.drafts).as("성공하면 수정안을 지운다").isEmpty();
	}

	@Test
	void 사진을_바꾸면_새_사진_행이_생기고_빈_배열이면_사진을_지운다() {
		ArTip saved = em.persistFlushFind(published(AUTHOR));
		Long oldImageId = em.persistAndGetId(ArTipImage.of(saved.getId(), "tip-photo/old", NOW), Long.class);
		SESSIONS.put(readyPhoto(AUTHOR, FilePurpose.TIP_PHOTO));
		int version = saved.getVersion();

		service.update(AUTHOR, saved.getId(), command(version, null, null, List.of(UPLOAD_ID), null));
		em.flush();
		em.clear();
		List<ArTipImage> replaced = images(saved.getId());
		assertThat(replaced).extracting(ArTipImage::getImagePath).containsExactly("tip-photo/" + UPLOAD_ID);
		assertThat(replaced.getFirst().getId()).isNotEqualTo(oldImageId);
		assertThat(STATES.states.get(saved.getId()).steps().media()).isEqualTo(StepStatus.SUCCEEDED);

		assertThat(em.find(ArTip.class, saved.getId()).getVersion()).as("사진만 바꿔도 version 이 오른다")
			.isEqualTo(version + 1);
		clock.advance(Duration.ofSeconds(1));
		service.update(AUTHOR, saved.getId(), command(version + 1, null, null, List.of(), null));
		em.flush();
		em.clear();
		assertThat(images(saved.getId())).isEmpty();
	}

	@Test
	void 기존_팁_5m_안에서_재배치하면_좌표와_공간_참조가_바뀌고_밖이면_T004() {
		ArTip saved = em.persistFlushFind(published(AUTHOR));

		service.update(AUTHOR, saved.getId(), relocate(saved.getVersion(), northOf(4)));  // 약 4.4m
		em.flush();
		em.clear();
		ArTip tip = em.find(ArTip.class, saved.getId());
		assertThat(tip.getLatitude()).isEqualByComparingTo(northOf(4).latitude());
		assertThat(tip.spatialReference().referenceId()).isEqualTo("anchor-2");
		assertThat(tip.getPlacement()).containsEntry("schemaVersion", 2);
		assertThat(STATES.states.get(saved.getId()).steps().spatial()).isEqualTo(StepStatus.SUCCEEDED);

		expect(() -> service.update(AUTHOR, saved.getId(), relocate(tip.getVersion(), northOf(10))),
			ExceptionType.TIP_OUT_OF_RANGE);  // 새 좌표에서 약 6.7m
	}

	@Test
	void 재배치에서_spatialReference나_placement가_빠지면_C001() {
		ArTip saved = em.persistFlushFind(published(AUTHOR));

		expect(() -> service.update(AUTHOR, saved.getId(), new TipUpdateCommand(saved.getVersion(), null, null, null,
			northOf(1), null, json("{\"schemaVersion\":2}"))), ExceptionType.REQUIRED_VALUE_MISSING);
		expect(() -> service.update(AUTHOR, saved.getId(), new TipUpdateCommand(saved.getVersion(), null, null, null,
			northOf(1), json(SPATIAL), null)), ExceptionType.REQUIRED_VALUE_MISSING);
	}

	@Test
	void 공개_팁이_아니면_T010_version이_다르면_C004_바꿀_게_없으면_C003() {
		ArTip registeringTip = em.persistFlushFind(registering(AUTHOR));
		ArTip failedTip = registering(AUTHOR);
		failedTip.fail(NOW);
		ArTip failed = em.persistFlushFind(failedTip);
		ArTip saved = em.persistFlushFind(published(AUTHOR));

		expect(() -> service.update(AUTHOR, registeringTip.getId(), command(registeringTip.getVersion(), "TMI", null,
			null, null)), ExceptionType.TIP_EDIT_IN_PROGRESS);
		expect(() -> service.update(AUTHOR, failed.getId(), command(failed.getVersion(), "TMI", null, null, null)),
			ExceptionType.TIP_EDIT_IN_PROGRESS);
		expect(() -> service.update(AUTHOR, saved.getId(), command(saved.getVersion() + 1, "TMI", null, null, null)),
			ExceptionType.VERSION_MISMATCH);
		expect(() -> service.update(AUTHOR, saved.getId(), command(saved.getVersion(), null, null, null, null)),
			ExceptionType.NOT_ALLOWED_VALUE);
	}

	@Test
	void 남의_팁_T002_삭제된_팁_T012_값_오류는_049와_같다() {
		ArTip saved = em.persistFlushFind(published(AUTHOR));
		ArTip deleted = em.persistFlushFind(published(AUTHOR));
		em.getEntityManager().createNativeQuery("UPDATE ar_tips SET deleted_at = published_at WHERE id = "
			+ deleted.getId()).executeUpdate();
		SESSIONS.put(readyPhoto(AUTHOR, FilePurpose.RECORD_PHOTO));

		expect(() -> service.update(OTHER, saved.getId(), command(saved.getVersion(), "TMI", null, null, null)),
			ExceptionType.TIP_NOT_OWNER);
		expect(() -> service.update(AUTHOR, deleted.getId(), command(1, "TMI", null, null, null)),
			ExceptionType.TIP_NOT_EXIST);
		expect(() -> service.update(AUTHOR, saved.getId(), command(saved.getVersion(), null, "   ", null, null)),
			ExceptionType.NOT_ALLOWED_VALUE);
		expect(() -> service.update(AUTHOR, saved.getId(), command(saved.getVersion(), "FOOD", null, null, null)),
			ExceptionType.NOT_ALLOWED_VALUE);
		expect(() -> service.update(AUTHOR, saved.getId(), command(saved.getVersion(), null, null,
			List.of(UPLOAD_ID), null)), ExceptionType.FILE_PURPOSE_MISMATCH);
	}

	@Test
	void 다른_수정이_진행_중이면_T010이고_팁은_그대로다() {
		ArTip saved = em.persistFlushFind(published(AUTHOR));
		EDITS.locks.put(saved.getId(), "other-job");

		expect(() -> service.update(AUTHOR, saved.getId(), command(saved.getVersion(), "TMI", null, null, null)),
			ExceptionType.TIP_EDIT_IN_PROGRESS);
		em.clear();
		assertThat(em.find(ArTip.class, saved.getId()).getCategory()).isEqualTo(ArTipCategory.CAFE);
		assertThat(EDITS.drafts).isEmpty();
	}

	@Test
	void 멱등키_재전송은_같은_jobId와_그때의_작업_상태를_준다() {
		ArTip saved = em.persistFlushFind(published(AUTHOR));
		TipUpdateResponse first = service.update(AUTHOR, saved.getId(), command(saved.getVersion(), "TMI", null, null,
			null));
		em.flush();

		TipUpdateResponse again = service.getQueued(AUTHOR, saved.getId(), first.jobId());

		assertThat(again.jobId()).isEqualTo(first.jobId());
		assertThat(again.operationStatus()).isEqualTo(OperationStatus.SUCCEEDED);
		assertThat(service.getQueued(AUTHOR, saved.getId(), "other-job").operationStatus()).isNull();
	}

	private List<ArTipImage> images(Long tipId) {
		return em.getEntityManager().createQuery("select i from ArTipImage i where i.tipId = :tipId", ArTipImage.class)
			.setParameter("tipId", tipId).getResultList();
	}

	private static void expect(Runnable action, ExceptionType type) {
		assertThatThrownBy(action::run).extracting("exceptionType").isEqualTo(type);
	}

	private static TipUpdateCommand command(int version, String category, String body, List<String> uploadIds,
		LocationEvidence location) {
		return new TipUpdateCommand(version, category, body, uploadIds, location, null, null);
	}

	private static TipUpdateCommand relocate(int version, LocationEvidence location) {
		return new TipUpdateCommand(version, null, null, null, location, json(SPATIAL), json("{\"schemaVersion\":2}"));
	}

	private static JsonNode json(String value) {
		return MAPPER.readTree(value);
	}

	private static UploadSession readyPhoto(Long owner, FilePurpose purpose) {
		return UploadSession.start(UPLOAD_ID, owner, purpose, null, "photos", "image/png", 1024, NOW,
			NOW.plus(Duration.ofMinutes(10))).ready(purpose.confirmedKey(UPLOAD_ID), 1024, null, null);
	}
}

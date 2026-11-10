package com.ssafy.yeogirok.domain.record.material;

import static com.ssafy.yeogirok.domain.record.RecordTestConfig.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.reset;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.file.session.ReceiptMaterial;
import com.ssafy.yeogirok.domain.file.session.ReceiptMaterialStore;
import com.ssafy.yeogirok.domain.file.storage.FileStorage;
import com.ssafy.yeogirok.domain.job.JobStatus;
import com.ssafy.yeogirok.domain.job.service.JobStore;
import com.ssafy.yeogirok.domain.place.entity.PlaceProvider;
import com.ssafy.yeogirok.domain.record.dto.MaterialRetryResponse;
import com.ssafy.yeogirok.domain.record.dto.RecordPlaceView;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.room.entity.RoomMember;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.support.HoldableExecutor;
import com.ssafy.yeogirok.support.MutableClock;
import com.ssafy.yeogirok.support.TestRedis;

/** API-092(명세 specs/record-api-2.md 4-6). 요청 형식 오류는 RecordMaterialControllerTest 가 본다. */
@DataJpaTest
@Import(MaterialTestConfig.class)
class MaterialRetryServiceTest {

	static final Long ME = 9401L;
	static final Long FRIEND = 9402L;
	static final Long LEFT = 9403L;
	static final String MATERIAL_ID = "m-1";

	@Autowired
	MaterialRetryService retryService;
	@Autowired
	ReceiptMaterials materials;
	@Autowired
	ReceiptMaterialStore materialStore;
	@Autowired
	ReceiptOcrRunner ocrRunner;
	@Autowired
	ReceiptOcrClient ocrClient;
	@Autowired
	JobStore jobStore;
	@Autowired
	FileStorage fileStorage;
	@Autowired
	HoldableExecutor executor;
	@Autowired
	TestEntityManager em;
	@Autowired
	MutableClock clock;

	Long recordId;
	String firstJobId;

	@BeforeEach
	void setUp() {
		TestRedis.flushAll();
		clock.set(NOW);
		executor.reset();
		reset(ocrClient);
		given(fileStorage.readAll(anyString(), anyString(), anyLong())).willReturn(new byte[] {1});
		TravelRoom room = em.persist(TravelRoom.builder().title("강릉 여행").transportMode(TransportMode.CAR)
			.createdAt(NOW).build());
		em.persist(RoomMember.creator(room.getId(), ME, NOW));
		em.persist(RoomMember.joined(room.getId(), FRIEND, NOW));
		em.persist(RoomMember.joined(room.getId(), LEFT, NOW)).leave(NOW);
		recordId = em.persist(TravelRecord.create(room.getId(), "강릉 여행", NOW)).getId();
		em.flush();
	}

	/** 062 처럼 연결하고 OCR 을 맡긴다. 실행기가 바로 돌리므로 OCR 결과까지 저장된다. */
	private void linked() {
		firstJobId = ocrRunner.newJobId();
		ReceiptMaterial material = ReceiptMaterial.uploaded(recordId, ME, "receipt/u", NOW,
			NOW.plus(Duration.ofHours(24))).linked("RECEIPT", firstJobId, NOW);
		materialStore.save(MATERIAL_ID, material);
		ocrRunner.submit(MATERIAL_ID, firstJobId, material);
	}

	private void ocrFails() {
		given(ocrClient.recognize(any())).willThrow(new IllegalStateException("status=500"));
	}

	private ReceiptMaterial stored() {
		return materialStore.find(MATERIAL_ID).orElseThrow();
	}

	private static void assertError(Runnable call, ExceptionType type) {
		assertThatThrownBy(call::run).isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(type);
	}

	@Test
	void 실패한_자료를_새_작업으로_다시_맡기고_version_을_올린다() {
		ocrFails();
		linked();
		executor.hold();

		MaterialRetryResponse response = retryService.retry(ME, recordId, MATERIAL_ID, 2);

		assertThat(response).isEqualTo(MaterialRetryResponse.queued(MATERIAL_ID, 3, response.jobId()));
		assertThat(response.jobId()).isNotEqualTo(firstJobId);
		ReceiptMaterial queued = stored();
		assertThat(queued.jobId()).isEqualTo(response.jobId());
		assertThat(queued.processStatus()).isEqualTo(ReceiptMaterial.Status.UPLOADED);
		assertThat(queued.error()).isNull();
		assertThat(jobStore.find(response.jobId()).orElseThrow().status()).isEqualTo(JobStatus.QUEUED);
		executor.reset();
	}

	@Test
	void 다시_맡긴_OCR_이_성공하면_READY() {
		ocrFails();
		linked();
		willReturn(new OcrResult("가게", LocalDate.of(2026, 11, 1), LocalTime.NOON)).given(ocrClient).recognize(any());

		retryService.retry(ME, recordId, MATERIAL_ID, 2);

		assertThat(stored().processStatus()).isEqualTo(ReceiptMaterial.Status.READY);
		assertThat(stored().extracted().merchantName()).isEqualTo("가게");
		assertThat(stored().version()).isEqualTo(4);
	}

	@Test
	void 처리_중이면_RE022_작업이_끊겼으면_다시_맡길_수_있다() {
		executor.hold();
		linked();
		assertError(() -> retryService.retry(ME, recordId, MATERIAL_ID, 1), ExceptionType.RECEIPT_NOT_READY);

		TestRedis.template().delete(TestRedis.template().keys("job:*"));

		assertThat(retryService.retry(ME, recordId, MATERIAL_ID, 1).version()).isEqualTo(2);
		executor.reset();
	}

	@Test
	void version_이_다르면_C004() {
		ocrFails();
		linked();

		assertError(() -> retryService.retry(ME, recordId, MATERIAL_ID, 1), ExceptionType.VERSION_MISMATCH);
	}

	@Test
	void 원본_보관_기한이_지났으면_M004_이고_바꾸지_않는다() {
		ocrFails();
		linked();
		clock.set(NOW.plus(Duration.ofHours(24)));

		assertError(() -> retryService.retry(ME, recordId, MATERIAL_ID, 2), ExceptionType.FILE_NOT_FOUND);
		assertThat(stored().version()).isEqualTo(2);
		assertThat(stored().jobId()).isEqualTo(firstJobId);
	}

	@Test
	void 없거나_연결_전_자료는_RE008_업로더가_아니면_M005_권한이_없으면_RE001_RE003() {
		assertError(() -> retryService.retry(ME, recordId, MATERIAL_ID, 1), ExceptionType.MATERIAL_NOT_FOUND);
		materialStore.save(MATERIAL_ID, ReceiptMaterial.uploaded(recordId, ME, "receipt/u", NOW,
			NOW.plus(Duration.ofHours(24))));
		assertError(() -> retryService.retry(ME, recordId, MATERIAL_ID, 1), ExceptionType.MATERIAL_NOT_FOUND);

		ocrFails();
		linked();
		assertError(() -> retryService.retry(FRIEND, recordId, MATERIAL_ID, 2), ExceptionType.FILE_ACCESS_DENIED);
		assertError(() -> retryService.retry(LEFT, recordId, MATERIAL_ID, 2), ExceptionType.RECORD_EDIT_FORBIDDEN);
		assertError(() -> retryService.retry(ME, recordId + 1000, MATERIAL_ID, 2), ExceptionType.RECORD_NOT_FOUND);
	}

	@Test
	void 늦게_끝난_이전_작업은_다시_맡긴_자료를_바꾸지_않는다() {
		executor.hold();
		linked();
		TestRedis.template().delete(TestRedis.template().keys("job:*"));
		MaterialRetryResponse retried = retryService.retry(ME, recordId, MATERIAL_ID, 1);
		given(ocrClient.recognize(any())).willReturn(new OcrResult("늦은 결과", null, null));

		ocrRunner.recognize(MATERIAL_ID, firstJobId);

		assertThat(stored().jobId()).isEqualTo(retried.jobId());
		assertThat(stored().processStatus()).isEqualTo(ReceiptMaterial.Status.UPLOADED);
		assertThat(stored().extracted()).isNull();
		executor.reset();
	}

	@Test
	void 사용자가_확정한_값은_다시_맡겨도_그대로다() {
		ocrFails();
		linked();
		RecordPlaceView place = new RecordPlaceView(PlaceProvider.MANUAL, null, "안목 카페",
			new BigDecimal("37.7700000"), new BigDecimal("128.9470000"), null, null, null, null);
		ReceiptMaterial.Values confirmed = new ReceiptMaterial.Values("안목 카페", Instant.parse("2026-11-01T03:00:00Z"),
			LocalDate.of(2026, 11, 1), LocalTime.NOON, place);
		materials.update(MATERIAL_ID, material -> material.reviewed(confirmed));
		executor.hold();

		retryService.retry(ME, recordId, MATERIAL_ID, 3);

		assertThat(stored().reviewedValues()).isEqualTo(confirmed);
		assertThat(stored().values()).isEqualTo(confirmed);
		executor.reset();
	}

	@Test
	void 재전송은_처음_jobId_와_지금_version_으로_답한다() {
		ocrFails();
		linked();
		executor.hold();
		MaterialRetryResponse first = retryService.retry(ME, recordId, MATERIAL_ID, 2);

		assertThat(retryService.getRetried(ME, recordId, MATERIAL_ID, first.jobId())).isEqualTo(first);
		assertError(() -> retryService.getRetried(FRIEND, recordId, MATERIAL_ID, first.jobId()),
			ExceptionType.FILE_ACCESS_DENIED);
		executor.reset();
	}
}

package com.ssafy.yeogirok.domain.record.material;

import static com.ssafy.yeogirok.domain.record.RecordTestConfig.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.reset;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.file.FilePurpose;
import com.ssafy.yeogirok.domain.file.session.ReceiptMaterial;
import com.ssafy.yeogirok.domain.file.session.ReceiptMaterialStore;
import com.ssafy.yeogirok.domain.file.session.UploadSession;
import com.ssafy.yeogirok.domain.file.session.UploadSessionStore;
import com.ssafy.yeogirok.domain.file.storage.FileStorage;
import com.ssafy.yeogirok.domain.job.JobStatus;
import com.ssafy.yeogirok.domain.job.service.JobStore;
import com.ssafy.yeogirok.domain.record.dto.MaterialLinkCommand;
import com.ssafy.yeogirok.domain.record.dto.MaterialLinkCommand.Type;
import com.ssafy.yeogirok.domain.record.dto.MaterialLinkResponse;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.room.entity.RoomMember;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.support.HoldableExecutor;
import com.ssafy.yeogirok.support.MutableClock;
import com.ssafy.yeogirok.support.TestRedis;

/** API-062(명세 specs/record-api-2.md 4-3). 요청 형식 오류는 RecordMaterialControllerTest 가 본다. */
@DataJpaTest
@Import(MaterialTestConfig.class)
class MaterialLinkServiceTest {

	static final Long ME = 9101L;
	static final Long FRIEND = 9102L;
	static final Long LEFT = 9103L;
	static final Long OUTSIDER = 9104L;
	static final String UPLOAD_ID = "0a1b2c3d-0000-4000-8000-000000000001";
	static final String MATERIAL_ID = ReceiptMaterial.idOf(UPLOAD_ID);

	@Autowired
	MaterialLinkService materialLinkService;
	@Autowired
	ReceiptMaterialStore materialStore;
	@Autowired
	UploadSessionStore uploadSessionStore;
	@Autowired
	JobStore jobStore;
	@Autowired
	FileStorage fileStorage;
	@Autowired
	ReceiptOcrClient ocrClient;
	@Autowired
	HoldableExecutor executor;
	@Autowired
	TestEntityManager em;
	@Autowired
	MutableClock clock;

	Long recordId;
	Long otherRecordId;

	@BeforeEach
	void setUp() {
		TestRedis.flushAll();
		clock.set(NOW);
		executor.reset();
		reset(ocrClient);
		given(fileStorage.readAll(anyString(), anyString(), anyLong())).willReturn(new byte[] {1});
		given(ocrClient.recognize(any())).willReturn(new OcrResult("가게", LocalDate.of(2026, 11, 2), LocalTime.NOON));
		recordId = record(true);
		otherRecordId = record(false);
		em.flush();
	}

	/** @param joined ME·FRIEND 가 참여하는 방이면 true. 아니면 ME 는 참여하지 않는다 */
	private Long record(boolean joined) {
		TravelRoom room = em.persist(TravelRoom.builder().title("강릉 여행").transportMode(TransportMode.CAR)
			.createdAt(NOW).build());
		em.persist(RoomMember.creator(room.getId(), joined ? ME : OUTSIDER, NOW));
		if (joined) {
			em.persist(RoomMember.joined(room.getId(), FRIEND, NOW));
			em.persist(RoomMember.joined(room.getId(), LEFT, NOW)).leave(NOW);
		}
		return em.persist(TravelRecord.create(room.getId(), "강릉 여행", NOW)).getId();
	}

	/** API-086 이 영수증을 확정한 뒤의 세션·자료. */
	private void uploadedReceipt(String uploadId, Long uploader, Long forRecord) {
		uploadSessionStore.save(session(uploadId, uploader, FilePurpose.RECEIPT, forRecord)
			.ready("receipt/" + uploadId, 100, null, ReceiptMaterial.idOf(uploadId)));
		materialStore.save(ReceiptMaterial.idOf(uploadId), ReceiptMaterial.uploaded(forRecord, uploader,
			"receipt/" + uploadId, NOW, NOW.plus(Duration.ofHours(24))));
	}

	private static UploadSession session(String uploadId, Long uploader, FilePurpose purpose, Long forRecord) {
		return UploadSession.start(uploadId, uploader, purpose, forRecord, "bucket", "image/jpeg", 100, NOW,
			NOW.plus(Duration.ofMinutes(10)));
	}

	private MaterialLinkResponse link(Long user, Long record, Type type, String uploadId) {
		return materialLinkService.link(user, record, new MaterialLinkCommand(type, uploadId));
	}

	private ReceiptMaterial stored() {
		return materialStore.find(MATERIAL_ID).orElseThrow();
	}

	private static void assertError(Runnable call, ExceptionType type) {
		assertThatThrownBy(call::run).isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(type);
	}

	@Test
	void 연결하면_version_1_로_목록에_넣고_OCR_을_맡긴다() {
		executor.hold();
		uploadedReceipt(UPLOAD_ID, ME, recordId);

		MaterialLinkResponse response = link(ME, recordId, Type.RECEIPT, UPLOAD_ID);

		assertThat(response).isEqualTo(MaterialLinkResponse.queued(MATERIAL_ID, recordId, response.jobId()));
		ReceiptMaterial linked = stored();
		assertThat(linked.linkedAt()).isEqualTo(NOW);
		assertThat(linked.version()).isEqualTo(1);
		assertThat(linked.jobId()).isEqualTo(response.jobId());
		assertThat(linked.reviewStatus()).isEqualTo(ReceiptMaterial.ReviewStatus.PENDING);
		assertThat(materialStore.indexed(recordId, ME)).extracting("materialId").containsExactly(MATERIAL_ID);
		var job = jobStore.find(response.jobId()).orElseThrow();
		assertThat(job.status()).isEqualTo(JobStatus.QUEUED);
		assertThat(job.type()).isEqualTo(ReceiptOcrRunner.TYPE);
		assertThat(job.requesterId()).isEqualTo(ME);
		assertThat(job.resourceId()).isEqualTo(recordId.toString());
		executor.reset();
	}

	@Test
	void OCR_이_끝나면_자료가_READY_가_된다() {
		uploadedReceipt(UPLOAD_ID, ME, recordId);

		MaterialLinkResponse response = link(ME, recordId, Type.RECEIPT, UPLOAD_ID);

		assertThat(stored().processStatus()).isEqualTo(ReceiptMaterial.Status.READY);
		assertThat(stored().version()).isEqualTo(2);
		assertThat(jobStore.find(response.jobId()).orElseThrow().status()).isEqualTo(JobStatus.SUCCEEDED);
	}

	@Test
	void 결제_자료도_같은_흐름이고_type_만_PAYMENT() {
		uploadedReceipt(UPLOAD_ID, ME, recordId);

		link(ME, recordId, Type.PAYMENT, UPLOAD_ID);

		assertThat(stored().type()).isEqualTo("PAYMENT");
		assertThat(stored().processStatus()).isEqualTo(ReceiptMaterial.Status.READY);
	}

	@Test
	void 재전송_응답은_처음_모양에_지금_자료의_jobId() {
		uploadedReceipt(UPLOAD_ID, ME, recordId);
		MaterialLinkResponse first = link(ME, recordId, Type.RECEIPT, UPLOAD_ID);

		assertThat(materialLinkService.getLinked(ME, recordId, MATERIAL_ID)).isEqualTo(first);
	}

	@Test
	void 재전송도_권한을_다시_확인한다() {
		uploadedReceipt(UPLOAD_ID, ME, recordId);
		link(ME, recordId, Type.RECEIPT, UPLOAD_ID);

		assertError(() -> materialLinkService.getLinked(FRIEND, recordId, MATERIAL_ID),
			ExceptionType.FILE_ACCESS_DENIED);
		assertError(() -> materialLinkService.getLinked(LEFT, recordId, MATERIAL_ID),
			ExceptionType.RECORD_EDIT_FORBIDDEN);
		assertError(() -> materialLinkService.getLinked(ME, recordId, "missing"), ExceptionType.MATERIAL_NOT_FOUND);
	}

	@Test
	void 다른_요청으로_같은_자료를_다시_연결하면_RE009() {
		uploadedReceipt(UPLOAD_ID, ME, recordId);
		link(ME, recordId, Type.RECEIPT, UPLOAD_ID);

		assertError(() -> link(ME, recordId, Type.RECEIPT, UPLOAD_ID), ExceptionType.MATERIAL_ALREADY_LINKED);
	}

	@Test
	void 기록이_없으면_RE001_퇴장했으면_RE003() {
		uploadedReceipt(UPLOAD_ID, LEFT, recordId);

		assertError(() -> link(ME, recordId + 1000, Type.RECEIPT, UPLOAD_ID), ExceptionType.RECORD_NOT_FOUND);
		assertError(() -> link(LEFT, recordId, Type.RECEIPT, UPLOAD_ID), ExceptionType.RECORD_EDIT_FORBIDDEN);
	}

	@Test
	void 남이_올렸거나_다른_기록에_올린_자료는_M005() {
		uploadedReceipt(UPLOAD_ID, FRIEND, recordId);
		assertError(() -> link(ME, recordId, Type.RECEIPT, UPLOAD_ID), ExceptionType.FILE_ACCESS_DENIED);

		String other = "0a1b2c3d-0000-4000-8000-000000000002";
		uploadedReceipt(other, ME, otherRecordId);
		assertError(() -> link(ME, recordId, Type.RECEIPT, other), ExceptionType.FILE_ACCESS_DENIED);
	}

	@Test
	void 원본_보관_기한이_지났으면_M004_이고_연결하지_않는다() {
		uploadedReceipt(UPLOAD_ID, ME, recordId);
		clock.set(NOW.plus(Duration.ofHours(24)));

		assertError(() -> link(ME, recordId, Type.RECEIPT, UPLOAD_ID), ExceptionType.FILE_NOT_FOUND);
		assertThat(stored().isLinked()).isFalse();
		assertThat(materialStore.indexed(recordId, ME)).isEmpty();
	}

	@Test
	void 자료가_없으면_업로드_세션으로_이유를_가린다() {
		assertError(() -> link(ME, recordId, Type.RECEIPT, UPLOAD_ID), ExceptionType.FILE_NOT_FOUND);

		uploadSessionStore.save(session(UPLOAD_ID, FRIEND, FilePurpose.RECEIPT, recordId));
		assertError(() -> link(ME, recordId, Type.RECEIPT, UPLOAD_ID), ExceptionType.FILE_ACCESS_DENIED);

		uploadSessionStore.save(session(UPLOAD_ID, ME, FilePurpose.RECORD_PHOTO, recordId).ready("p", 1, 1L, null));
		assertError(() -> link(ME, recordId, Type.RECEIPT, UPLOAD_ID), ExceptionType.FILE_PURPOSE_MISMATCH);

		uploadSessionStore.save(session(UPLOAD_ID, ME, FilePurpose.RECEIPT, recordId));
		assertError(() -> link(ME, recordId, Type.RECEIPT, UPLOAD_ID), ExceptionType.FILE_NOT_UPLOADED);

		uploadSessionStore.save(session(UPLOAD_ID, ME, FilePurpose.RECEIPT, recordId).ready("r", 1, null, "x"));
		assertError(() -> link(ME, recordId, Type.RECEIPT, UPLOAD_ID), ExceptionType.FILE_NOT_FOUND);
	}
}

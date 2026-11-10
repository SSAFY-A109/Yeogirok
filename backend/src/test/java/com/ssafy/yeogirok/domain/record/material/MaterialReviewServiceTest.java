package com.ssafy.yeogirok.domain.record.material;

import static com.ssafy.yeogirok.domain.record.RecordTestConfig.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.reset;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.file.session.ReceiptMaterial;
import com.ssafy.yeogirok.domain.file.session.ReceiptMaterialStore;
import com.ssafy.yeogirok.domain.file.storage.FileStorage;
import com.ssafy.yeogirok.domain.place.entity.Place;
import com.ssafy.yeogirok.domain.place.entity.PlaceProvider;
import com.ssafy.yeogirok.domain.record.dto.MaterialReviewCommand;
import com.ssafy.yeogirok.domain.record.dto.MaterialReviewResponse;
import com.ssafy.yeogirok.domain.record.dto.MaterialView;
import com.ssafy.yeogirok.domain.record.dto.RecordPlaceView;
import com.ssafy.yeogirok.domain.record.dto.VisitPlaceInput;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.room.entity.RoomMember;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.support.HoldableExecutor;
import com.ssafy.yeogirok.support.MutableClock;
import com.ssafy.yeogirok.support.TestRedis;

/** API-091(명세 specs/record-api-2.md 4-5). 요청 형식 오류는 RecordMaterialControllerTest 가 본다. */
@DataJpaTest
@Import(MaterialTestConfig.class)
class MaterialReviewServiceTest {

	static final Long ME = 9301L;
	static final Long FRIEND = 9302L;
	static final Long LEFT = 9303L;
	static final String MATERIAL_ID = "m-1";
	static final OffsetDateTime PAID_AT = OffsetDateTime.of(2026, 11, 2, 0, 30, 15, 0, ZoneOffset.ofHours(9));
	static final VisitPlaceInput.CustomPlace CUSTOM = new VisitPlaceInput.CustomPlace("안목 카페",
		new BigDecimal("37.7700000"), new BigDecimal("128.9470000"), null);

	@Autowired
	MaterialReviewService reviewService;
	@Autowired
	ReceiptMaterials materials;
	@Autowired
	ReceiptMaterialStore materialStore;
	@Autowired
	ReceiptOcrRunner ocrRunner;
	@Autowired
	ReceiptOcrClient ocrClient;
	@Autowired
	FileStorage fileStorage;
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
		given(ocrClient.recognize(any())).willReturn(new OcrResult("가게", LocalDate.of(2026, 11, 1), LocalTime.NOON));
		recordId = record();
		otherRecordId = record();
		em.flush();
	}

	private Long record() {
		TravelRoom room = em.persist(TravelRoom.builder().title("강릉 여행").transportMode(TransportMode.CAR)
			.createdAt(NOW).build());
		em.persist(RoomMember.creator(room.getId(), ME, NOW));
		em.persist(RoomMember.joined(room.getId(), FRIEND, NOW));
		em.persist(RoomMember.joined(room.getId(), LEFT, NOW)).leave(NOW);
		return em.persist(TravelRecord.create(room.getId(), "강릉 여행", NOW)).getId();
	}

	/** 062 처럼 연결하고 OCR 을 맡긴다(실행기가 바로 돌려 OCR 까지 끝난다). */
	private void linked(Long uploader) {
		String jobId = ocrRunner.newJobId();
		ReceiptMaterial material = ReceiptMaterial.uploaded(recordId, uploader, "receipt/u", NOW,
			NOW.plus(Duration.ofHours(24))).linked("RECEIPT", jobId, NOW);
		materialStore.save(MATERIAL_ID, material);
		ocrRunner.submit(MATERIAL_ID, jobId, material);
	}

	private MaterialReviewResponse review(Long user, Long record, int version, VisitPlaceInput place) {
		return reviewService.review(user, record, MATERIAL_ID,
			new MaterialReviewCommand(version, "안목 카페", PAID_AT, place));
	}

	private ReceiptMaterial stored() {
		return materialStore.find(MATERIAL_ID).orElseThrow();
	}

	private static void assertError(Runnable call, ExceptionType type) {
		assertThatThrownBy(call::run).isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(type);
	}

	@Test
	void 직접_입력_장소로_확정하면_CONFIRMED_이고_초안에_쓸_수_있다() {
		linked(ME);

		MaterialReviewResponse response = review(ME, recordId, 2, CUSTOM);

		RecordPlaceView place = new RecordPlaceView(PlaceProvider.MANUAL, null, "안목 카페",
			new BigDecimal("37.7700000"), new BigDecimal("128.9470000"), null, null, null, null);
		assertThat(response).isEqualTo(new MaterialReviewResponse(MATERIAL_ID, 3,
			ReceiptMaterial.ReviewStatus.CONFIRMED, false, List.of(),
			new MaterialView.Values("안목 카페", PAID_AT.toInstant(), place, LocalDate.of(2026, 11, 2),
				LocalTime.of(0, 30, 15)),
			ReceiptMaterial.Status.READY, true));
		assertThat(stored().extracted().merchantName()).isEqualTo("가게");
		assertThat(stored().reviewedValues().place()).isEqualTo(place);
	}

	@Test
	void 투어API_장소는_places_에_있는_행으로_정한다() {
		Long placeId = em.persistAndGetId(Place.manual("경포대", new BigDecimal("37.7950000"),
			new BigDecimal("128.8960000"), "강릉시", null, null, NOW), Long.class);
		em.flush();
		em.getEntityManager().createNativeQuery(
				"update places set provider = 'TOUR_API', provider_place_id = 'T-1' where id = ?1")
			.setParameter(1, placeId).executeUpdate();
		em.clear();
		linked(ME);

		MaterialReviewResponse response = review(ME, recordId, 2, new VisitPlaceInput.TourPlace("T-1"));

		assertThat(response.values().place().provider()).isEqualTo(PlaceProvider.TOUR_API);
		assertThat(response.values().place().providerPlaceId()).isEqualTo("T-1");
		assertThat(response.values().place().name()).isEqualTo("경포대");
	}

	@Test
	void 저장되지_않은_투어API_장소는_C003_이고_바꾸지_않는다() {
		linked(ME);

		assertError(() -> review(ME, recordId, 2, new VisitPlaceInput.TourPlace("none")),
			ExceptionType.NOT_ALLOWED_VALUE);
		assertThat(stored().version()).isEqualTo(2);
	}

	@Test
	void OCR_이_실패한_자료도_직접_입력해_확정한다() {
		given(ocrClient.recognize(any())).willThrow(new BusinessException(ExceptionType.FILE_VERIFICATION_FAILED));
		linked(ME);

		MaterialReviewResponse response = review(ME, recordId, 2, CUSTOM);

		assertThat(response.status()).isEqualTo(ReceiptMaterial.Status.READY);
		assertThat(response.canUseForDraft()).isTrue();
		assertThat(stored().error()).isNull();
	}

	@Test
	void OCR_처리_중이면_RE022() {
		executor.hold();
		linked(ME);

		assertError(() -> review(ME, recordId, 1, CUSTOM), ExceptionType.RECEIPT_NOT_READY);
		executor.reset();
	}

	@Test
	void 작업이_끊긴_자료는_처리_중이_아니라서_확정할_수_있다() {
		executor.hold();
		linked(ME);
		TestRedis.template().delete(TestRedis.template().keys("job:*"));

		assertThat(review(ME, recordId, 1, CUSTOM).status()).isEqualTo(ReceiptMaterial.Status.READY);
		executor.reset();
	}

	@Test
	void version_이_다르면_C004() {
		linked(ME);

		assertError(() -> review(ME, recordId, 1, CUSTOM), ExceptionType.VERSION_MISMATCH);
	}

	@Test
	void 없거나_다른_기록이거나_연결_전_자료는_RE008_업로더가_아니면_M005() {
		assertError(() -> review(ME, recordId, 1, CUSTOM), ExceptionType.MATERIAL_NOT_FOUND);

		materialStore.save(MATERIAL_ID, ReceiptMaterial.uploaded(recordId, ME, "receipt/u", NOW,
			NOW.plus(Duration.ofHours(24))));
		assertError(() -> review(ME, recordId, 1, CUSTOM), ExceptionType.MATERIAL_NOT_FOUND);

		linked(ME);
		assertError(() -> review(ME, otherRecordId, 2, CUSTOM), ExceptionType.MATERIAL_NOT_FOUND);
		assertError(() -> review(FRIEND, recordId, 2, CUSTOM), ExceptionType.FILE_ACCESS_DENIED);
	}

	@Test
	void 기록이_없으면_RE001_퇴장했으면_RE003() {
		linked(ME);

		assertError(() -> review(ME, recordId + 1000, 2, CUSTOM), ExceptionType.RECORD_NOT_FOUND);
		assertError(() -> review(LEFT, recordId, 2, CUSTOM), ExceptionType.RECORD_EDIT_FORBIDDEN);
	}

	@Test
	void 확정한_뒤_다시_OCR_해도_확정_값은_그대로다() {
		linked(ME);
		review(ME, recordId, 2, CUSTOM);
		String retryJob = ocrRunner.newJobId();
		ReceiptMaterial queued = materials.update(MATERIAL_ID, material -> material.retryQueued(retryJob)).orElseThrow();
		given(ocrClient.recognize(any())).willReturn(new OcrResult("다른 가게", null, null));

		ocrRunner.submit(MATERIAL_ID, retryJob, queued);

		ReceiptMaterial after = stored();
		assertThat(after.extracted().merchantName()).isEqualTo("다른 가게");
		assertThat(after.values().merchantName()).isEqualTo("안목 카페");
		assertThat(after.reviewStatus()).isEqualTo(ReceiptMaterial.ReviewStatus.CONFIRMED);
		assertThat(after.canUseForDraft()).isTrue();
		assertThat(after.version()).isEqualTo(5);
	}

	@Test
	void 재전송은_권한을_다시_보고_지금_값으로_답한다() {
		linked(ME);
		MaterialReviewResponse first = review(ME, recordId, 2, CUSTOM);

		assertThat(reviewService.getReviewed(ME, recordId, MATERIAL_ID)).isEqualTo(first);
		assertError(() -> reviewService.getReviewed(FRIEND, recordId, MATERIAL_ID), ExceptionType.FILE_ACCESS_DENIED);
		assertError(() -> reviewService.getReviewed(LEFT, recordId, MATERIAL_ID), ExceptionType.RECORD_EDIT_FORBIDDEN);
	}

	@Test
	void 원본_보관_기한은_늘지_않는다() {
		linked(ME);
		Instant accepted = stored().uploadAcceptedAt();

		review(ME, recordId, 2, CUSTOM);

		assertThat(stored().uploadAcceptedAt()).isEqualTo(accepted);
		assertThat(stored().deleteAfter()).isEqualTo(accepted.plus(Duration.ofHours(24)));
	}
}

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
import com.ssafy.yeogirok.domain.place.entity.PlaceProvider;
import com.ssafy.yeogirok.domain.record.dto.MaterialSummary;
import com.ssafy.yeogirok.domain.record.dto.MaterialView;
import com.ssafy.yeogirok.domain.record.dto.RecordPlaceView;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.record.service.RecordFinder;
import com.ssafy.yeogirok.domain.room.entity.RoomMember;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.support.HoldableExecutor;
import com.ssafy.yeogirok.support.MutableClock;
import com.ssafy.yeogirok.support.TestRedis;

/** API-063·059 materials(명세 specs/record-api-2.md 4-4). 응답 JSON 모양은 RecordMaterialControllerTest 가 본다. */
@DataJpaTest
@Import(MaterialTestConfig.class)
class MaterialQueryServiceTest {

	static final Long ME = 9201L;
	static final Long FRIEND = 9202L;
	static final Long STRANGER = 9203L;
	static final RecordPlaceView PLACE = new RecordPlaceView(PlaceProvider.MANUAL, null, "안목 카페",
		new BigDecimal("37.7700000"), new BigDecimal("128.9470000"), null, null, null, null);

	@Autowired
	RecordFinder recordFinder;
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

	MaterialQueryService service;
	Long recordId;
	int uploads;

	@BeforeEach
	void setUp() {
		TestRedis.flushAll();
		clock.set(NOW);
		executor.reset();
		reset(ocrClient);
		given(fileStorage.readAll(anyString(), anyString(), anyLong())).willReturn(new byte[] {1});
		service = new MaterialQueryService(recordFinder, materialStore, materials, clock);
		TravelRoom room = em.persist(TravelRoom.builder().title("강릉 여행").transportMode(TransportMode.CAR)
			.createdAt(NOW).build());
		em.persist(RoomMember.creator(room.getId(), ME, NOW));
		em.persist(RoomMember.joined(room.getId(), FRIEND, NOW));
		recordId = em.persist(TravelRecord.create(room.getId(), "강릉 여행", NOW)).getId();
		em.flush();
	}

	/** 062 와 같은 순서로 연결하고 OCR 작업을 맡긴다. 업로드 접수 시각은 부를 때마다 1분씩 늦다. */
	private String linked(Long uploader) {
		String materialId = "m-" + (++uploads);
		Instant acceptedAt = NOW.minus(Duration.ofHours(1)).plus(Duration.ofMinutes(uploads));
		String jobId = ocrRunner.newJobId();
		ReceiptMaterial material = ReceiptMaterial.uploaded(recordId, uploader, "receipt/" + materialId, acceptedAt,
			acceptedAt.plus(Duration.ofHours(24))).linked("RECEIPT", jobId, NOW);
		materialStore.save(materialId, material);
		materialStore.addToIndex(recordId, uploader, materialId);
		ocrRunner.submit(materialId, jobId, material);
		return materialId;
	}

	private MaterialView only() {
		return service.list(ME, recordId).items().getFirst();
	}

	@Test
	void OCR_대기_중이면_PENDING_이고_extracted_는_빈_객체() {
		executor.hold();
		String materialId = linked(ME);

		MaterialView view = only();

		assertThat(view.materialId()).isEqualTo(materialId);
		assertThat(view.status()).isEqualTo(ReceiptMaterial.Status.UPLOADED);
		assertThat(view.reviewStatus()).isEqualTo(ReceiptMaterial.ReviewStatus.PENDING);
		assertThat(view.needsReview()).isFalse();
		assertThat(view.canUseForDraft()).isFalse();
		assertThat(view.extracted()).isEqualTo(new MaterialView.Extracted(null, List.of(), null, null, null, null));
		assertThat(view.missingFields()).containsExactly("merchantName", "paidDate", "paidTime", "place");
		assertThat(view.version()).isEqualTo(1);
		assertThat(view.originalAvailable()).isTrue();
		executor.reset();
	}

	@Test
	void OCR_이_성공하면_제안이_values_가_되고_장소가_없어_보정이_필요하다() {
		given(ocrClient.recognize(any())).willReturn(new OcrResult("가게", LocalDate.of(2026, 11, 2), LocalTime.NOON));
		linked(ME);

		MaterialView view = only();

		assertThat(view.status()).isEqualTo(ReceiptMaterial.Status.READY);
		assertThat(view.version()).isEqualTo(2);
		assertThat(view.extracted().merchantName()).isEqualTo("가게");
		assertThat(view.values()).isEqualTo(new MaterialView.Values("가게", Instant.parse("2026-11-02T03:00:00Z"),
			null, LocalDate.of(2026, 11, 2), LocalTime.NOON));
		assertThat(view.reviewStatus()).isEqualTo(ReceiptMaterial.ReviewStatus.REQUIRED);
		assertThat(view.needsReview()).isTrue();
		assertThat(view.missingFields()).containsExactly("place");
		assertThat(view.canUseForDraft()).isFalse();
		assertThat(view.error()).isNull();
	}

	@Test
	void OCR_이_실패하면_FAILED_와_오류를_준다() {
		given(ocrClient.recognize(any())).willThrow(new BusinessException(ExceptionType.FILE_VERIFICATION_FAILED));
		linked(ME);

		MaterialView view = only();

		assertThat(view.status()).isEqualTo(ReceiptMaterial.Status.FAILED);
		assertThat(view.error().code()).isEqualTo("M003");
		assertThat(view.reviewStatus()).isEqualTo(ReceiptMaterial.ReviewStatus.REQUIRED);
		assertThat(view.canUseForDraft()).isFalse();
	}

	@Test
	void 사용자가_확정하면_그_값을_쓰고_초안에_쓸_수_있다() {
		given(ocrClient.recognize(any())).willReturn(new OcrResult("가게", null, null));
		String materialId = linked(ME);
		ReceiptMaterial.Values confirmed = new ReceiptMaterial.Values("안목 카페",
			Instant.parse("2026-11-02T03:00:00Z"), LocalDate.of(2026, 11, 2), LocalTime.NOON, PLACE);
		materials.update(materialId, material -> material.reviewed(confirmed));

		MaterialView view = only();

		assertThat(view.reviewStatus()).isEqualTo(ReceiptMaterial.ReviewStatus.CONFIRMED);
		assertThat(view.values().place()).isEqualTo(PLACE);
		assertThat(view.extracted().merchantName()).isEqualTo("가게");
		assertThat(view.missingFields()).isEmpty();
		assertThat(view.canUseForDraft()).isTrue();
		assertThat(view.version()).isEqualTo(3);
	}

	@Test
	void 작업이_사라지면_실패로_보인다() {
		executor.hold();
		linked(ME);
		TestRedis.template().delete(TestRedis.template().keys("job:*"));

		MaterialView view = only();

		assertThat(view.status()).isEqualTo(ReceiptMaterial.Status.FAILED);
		assertThat(view.error().code()).isEqualTo("C014");
		executor.reset();
	}

	@Test
	void 원본_보관_기한이_지나면_originalAvailable_false() {
		executor.hold();
		linked(ME);
		clock.set(NOW.plus(Duration.ofHours(24)));

		MaterialView view = only();

		assertThat(view.originalAvailable()).isFalse();
		assertThat(view.originalDeleteAfter()).isEqualTo(view.uploadAcceptedAt().plus(Duration.ofHours(24)));
		executor.reset();
	}

	@Test
	void 본인_자료만_업로드_접수_순으로_보이고_다른_참여자_자료는_안_보인다() {
		executor.hold();
		String first = linked(ME);
		linked(FRIEND);
		String third = linked(ME);

		assertThat(service.list(ME, recordId).items()).extracting(MaterialView::materialId)
			.containsExactly(first, third);
		assertThat(MaterialQueryService.summaries(service.mine(recordId, ME))).extracting(MaterialSummary::materialId)
			.containsExactly(first, third);
		assertThat(service.list(FRIEND, recordId).items()).hasSize(1);
		executor.reset();
	}

	@Test
	void 연결_전_자료는_목록에_넣지_않는다() {
		materialStore.save("m-x", ReceiptMaterial.uploaded(recordId, ME, "receipt/x", NOW,
			NOW.plus(Duration.ofHours(24))));
		materialStore.addToIndex(recordId, ME, "m-x");

		assertThat(service.list(ME, recordId).items()).isEmpty();
	}

	@Test
	void 요약은_목록과_같은_계산이다() {
		given(ocrClient.recognize(any())).willReturn(new OcrResult("가게", LocalDate.of(2026, 11, 2), null));
		String materialId = linked(ME);

		assertThat(MaterialQueryService.summaries(service.mine(recordId, ME))).containsExactly(new MaterialSummary(materialId, 2, "RECEIPT",
			ReceiptMaterial.Status.READY, ReceiptMaterial.ReviewStatus.REQUIRED, true, List.of("paidTime", "place"),
			false, null));
	}

	@Test
	void 참여자가_아니면_RE002_기록이_없으면_RE001() {
		assertThatThrownBy(() -> service.list(STRANGER, recordId)).isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(ExceptionType.RECORD_NOT_MEMBER);
		assertThatThrownBy(() -> service.list(ME, recordId + 1000)).isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(ExceptionType.RECORD_NOT_FOUND);
	}
}

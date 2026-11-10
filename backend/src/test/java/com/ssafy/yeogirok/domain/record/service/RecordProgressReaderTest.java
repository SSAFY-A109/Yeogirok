package com.ssafy.yeogirok.domain.record.service;

import static com.ssafy.yeogirok.domain.record.RecordTestConfig.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.file.session.ReceiptMaterial;
import com.ssafy.yeogirok.domain.file.session.ReceiptMaterialStore;
import com.ssafy.yeogirok.domain.place.entity.PlaceProvider;
import com.ssafy.yeogirok.domain.record.RecordTestConfig;
import com.ssafy.yeogirok.domain.record.draft.RecordDraft;
import com.ssafy.yeogirok.domain.record.draft.RecordDraftStore;
import com.ssafy.yeogirok.domain.record.dto.RecordListResponse;
import com.ssafy.yeogirok.domain.record.dto.RecordPlaceView;
import com.ssafy.yeogirok.domain.record.dto.RecordProgress;
import com.ssafy.yeogirok.domain.record.dto.RecordProgress.Stage;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.record.entity.VisitImage;
import com.ssafy.yeogirok.domain.room.entity.RoomMember;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.support.MutableClock;
import com.ssafy.yeogirok.support.TestRedis;
import com.ssafy.yeogirok.support.TestUsers;

/**
 * 058·059 progress 를 여러 기록에 대해 한 번에 읽는다(명세 specs/record-api-2.md 4-10). 단계 판정 규칙 자체는
 * RecordProgressTest 가 본다. 영수증 단계는 요청자 본인 자료로만 정해진다.
 */
@DataJpaTest
@Import(RecordTestConfig.class)
class RecordProgressReaderTest {

	static final Long ME = 9701L;
	static final Long FRIEND = 9702L;
	static final RecordPlaceView CAFE = new RecordPlaceView(PlaceProvider.MANUAL, null, "카페",
		new BigDecimal("37.7700000"), new BigDecimal("128.9470000"), null, null, null, null);

	@Autowired
	RecordProgressReader reader;
	@Autowired
	RecordQueryService recordQueryService;
	@Autowired
	RecordDraftStore draftStore;
	@Autowired
	ReceiptMaterialStore materialStore;
	@Autowired
	TestEntityManager em;
	@Autowired
	MutableClock clock;

	@BeforeEach
	void setUp() {
		TestRedis.flushAll();
		clock.set(NOW);
		TestUsers.insertActive(em.getEntityManager(), ME, FRIEND);
	}

	private TravelRecord record(String title) {
		TravelRoom room = em.persist(TravelRoom.builder().title(title).transportMode(TransportMode.CAR).createdAt(NOW)
			.build());
		em.persist(RoomMember.creator(room.getId(), ME, NOW));
		em.persist(RoomMember.joined(room.getId(), FRIEND, NOW));
		return em.persist(TravelRecord.create(room.getId(), title, NOW));
	}

	private void usableReceipt(String materialId, TravelRecord record, Long uploader) {
		materialStore.save(materialId, ReceiptMaterial.uploaded(record.getId(), uploader, "receipt/u", NOW,
				NOW.plus(Duration.ofHours(24))).linked("RECEIPT", "job-ocr", NOW)
			.ocrFailed(new ReceiptMaterial.Error("M003", "x"))
			.reviewed(new ReceiptMaterial.Values("카페", NOW, LocalDate.of(2026, 11, 9), LocalTime.NOON, CAFE)));
		materialStore.addToIndex(record.getId(), uploader, materialId);
	}

	private RecordDraft draft(String key, TravelRecord record) {
		RecordDraft draft = RecordDraft.queued(key, "job-" + key, record.getId(), FRIEND, 1,
			new RecordDraft.SourceSnapshot(List.of(), List.of(), List.of()), NOW, NOW.plus(Duration.ofMinutes(12)));
		draftStore.save(draft);
		draftStore.savePointer(draft);
		return draft;
	}

	@Test
	void 여러_기록의_단계를_한_번에_읽고_영수증_단계는_본인_자료로만_정한다() {
		TravelRecord ready = record("영수증·사진 있음");
		em.persist(VisitImage.unlinked(ready.getId(), FRIEND, "record-photo/a", NOW));
		usableReceipt("m-1", ready, ME);
		TravelRecord processing = record("초안 만드는 중");
		draft("d-processing", processing);
		TravelRecord review = record("초안 확인 전");
		draftStore.save(draft("d-review", review).succeeded(List.of(), List.of()));
		TravelRecord completed = record("완성");
		completed.complete(NOW);
		em.flush();
		List<TravelRecord> records = List.of(ready, processing, review, completed);

		Map<Long, RecordProgress> mine = reader.read(ME, records, Map.of());
		Map<Long, RecordProgress> friends = reader.read(FRIEND, records, Map.of());

		assertThat(mine.get(ready.getId()).stage()).isEqualTo(Stage.READY_TO_GENERATE);
		assertThat(friends.get(ready.getId()).stage()).isEqualTo(Stage.UPLOAD);
		assertThat(mine.get(processing.getId()))
			.isEqualTo(new RecordProgress(Stage.PROCESSING, "d-processing", "job-d-processing"));
		assertThat(friends.get(review.getId()))
			.isEqualTo(new RecordProgress(Stage.REVIEW, "d-review", "job-d-review"));
		assertThat(mine.get(completed.getId()).stage()).isEqualTo(Stage.COMPLETED);
	}

	@Test
	void 기한이_지난_진행_중_초안은_실패로_보고_다음_단계로_간다() {
		TravelRecord record = record("멈춘 초안");
		draft("d-stuck", record);
		em.flush();

		clock.advance(Duration.ofMinutes(13));

		assertThat(reader.read(ME, List.of(record), Map.of(record.getId(), 2)).get(record.getId()).stage())
			.isEqualTo(Stage.EDIT);
	}

	@Test
	void 목록과_상세가_같은_progress_를_준다() {
		TravelRecord record = record("초안 만드는 중");
		draft("d-1", record);
		em.flush();

		RecordListResponse list = recordQueryService.getList(ME, null, null, null, null);

		assertThat(list.items()).singleElement().satisfies(item -> assertThat(item.progress())
			.isEqualTo(new RecordProgress(Stage.PROCESSING, "d-1", "job-d-1")));
		assertThat(recordQueryService.getDetail(ME, record.getId()).progress())
			.isEqualTo(new RecordProgress(Stage.PROCESSING, "d-1", "job-d-1"));
	}
}

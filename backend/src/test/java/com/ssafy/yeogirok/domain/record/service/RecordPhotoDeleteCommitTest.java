package com.ssafy.yeogirok.domain.record.service;

import static com.ssafy.yeogirok.domain.record.RecordTestConfig.NOW;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.ssafy.yeogirok.domain.file.storage.FileStorage;
import com.ssafy.yeogirok.domain.record.RecordTestConfig;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.record.entity.VisitImage;
import com.ssafy.yeogirok.domain.room.entity.RoomMember;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import jakarta.persistence.EntityManager;

/**
 * API-089 의 파일 삭제는 DB 커밋 뒤에만 한다. 커밋이 필요해 테스트 트랜잭션을 쓰지 않고, 만든 행은 끝나고 지운다.
 * 저장소는 RecordTestConfig 의 mock 이다.
 */
@DataJpaTest
@Import(RecordTestConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RecordPhotoDeleteCommitTest {

	static final Long ME = 9501L;
	static final LocalDate DAY1 = LocalDate.parse("2026-11-01");

	@Autowired
	VisitPhotoService visitPhotoService;
	@Autowired
	FileStorage fileStorage;
	@Autowired
	EntityManager em;
	@Autowired
	PlatformTransactionManager transactionManager;
	@Autowired
	DataSource dataSource;

	Long roomId;
	Long recordId;
	Long imageId;

	@BeforeEach
	void setUp() {
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			TravelRoom room = TravelRoom.builder().title("삭제").startDate(DAY1).endDate(DAY1)
				.transportMode(TransportMode.CAR).createdAt(NOW).build();
			em.persist(room);
			roomId = room.getId();
			em.persist(RoomMember.creator(roomId, ME, NOW));
			TravelRecord record = TravelRecord.create(roomId, "삭제", NOW);
			em.persist(record);
			recordId = record.getId();
			VisitImage image = VisitImage.unlinked(recordId, ME, "record-photo/commit", NOW);
			em.persist(image);
			imageId = image.getId();
		});
		clearInvocations(fileStorage);
	}

	@AfterEach
	void tearDown() {
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		jdbc.update("DELETE FROM visit_images WHERE record_id = ?", recordId);
		jdbc.update("DELETE FROM travel_records WHERE id = ?", recordId);
		jdbc.update("DELETE FROM room_members WHERE room_id = ?", roomId);
		jdbc.update("DELETE FROM travel_rooms WHERE id = ?", roomId);
		// 같은 컨텍스트를 쓰는 다른 테스트의 presignGet 설정은 남기고 delete 실패 설정만 되돌린다.
		willDoNothing().given(fileStorage).delete(anyString(), anyString());
	}

	@Test
	void 커밋되면_사진_버킷의_그_파일을_지운다() {
		visitPhotoService.delete(ME, recordId, imageId, 1);

		verify(fileStorage).delete("photos", "record-photo/commit");
	}

	@Test
	void 실패해_롤백되면_파일을_지우지_않는다() {
		assertThatThrownBy(() -> visitPhotoService.delete(ME, recordId, imageId, 2))
			.isInstanceOf(BusinessException.class).extracting("exceptionType")
			.isEqualTo(ExceptionType.VERSION_MISMATCH);

		verify(fileStorage, never()).delete(anyString(), anyString());
	}

	@Test
	void 파일_삭제가_실패해도_요청은_성공한다() {
		willThrow(new BusinessException(ExceptionType.FILE_STORAGE_UNAVAILABLE)).given(fileStorage)
			.delete(anyString(), anyString());

		assertThatCode(() -> visitPhotoService.delete(ME, recordId, imageId, 1)).doesNotThrowAnyException();
	}
}

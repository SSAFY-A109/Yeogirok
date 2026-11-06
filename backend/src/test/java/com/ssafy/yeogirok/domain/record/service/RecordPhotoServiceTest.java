package com.ssafy.yeogirok.domain.record.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.record.entity.VisitImage;
import com.ssafy.yeogirok.support.MutableClock;

/** AC-05 업로드한 기록 사진은 방문에 연결하지 않은 채 보관함에 한 번만 들어간다(visit_images DDL 의 미연결 조건). */
@DataJpaTest
@Import({RecordPhotoService.class, RecordPhotoServiceTest.Config.class})
class RecordPhotoServiceTest {

	static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");

	@TestConfiguration
	static class Config {
		@Bean
		MutableClock clock() {
			return new MutableClock(NOW);
		}
	}

	@Autowired
	RecordPhotoService recordPhotoService;
	@Autowired
	TestEntityManager em;

	Long recordId;

	@BeforeEach
	void setUp() {
		recordId = em.persist(TravelRecord.create(1L, "가을 여행", NOW)).getId();
	}

	@Test
	void 방문에_연결하지_않은_공개_미선택_사진으로_저장한다() {
		Long imageId = recordPhotoService.saveUploaded(recordId, 7L, "record-photo/abc");
		em.flush();
		em.clear();

		VisitImage saved = em.find(VisitImage.class, imageId);
		assertThat(saved.getRecordId()).isEqualTo(recordId);
		assertThat(saved.getUploadedBy()).isEqualTo(7L);
		assertThat(saved.getImagePath()).isEqualTo("record-photo/abc");
		assertThat(saved.getVisitId()).isNull();
		assertThat(saved.getPosition()).isNull();
		assertThat(saved.isPublicSelected()).isFalse();
		assertThat(saved.getCreatedAt()).isEqualTo(NOW);
	}

	@Test
	void 같은_파일을_다시_저장하면_처음_사진을_돌려준다() {
		Long first = recordPhotoService.saveUploaded(recordId, 7L, "record-photo/abc");
		em.flush();

		assertThat(recordPhotoService.saveUploaded(recordId, 7L, "record-photo/abc")).isEqualTo(first);
		assertThat(em.getEntityManager().createQuery("select count(i) from VisitImage i", Long.class)
			.getSingleResult()).isEqualTo(1L);
	}
}

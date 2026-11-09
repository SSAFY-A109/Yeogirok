package com.ssafy.yeogirok.domain.record.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class TravelRecordTest {

	static final Instant NOW = Instant.parse("2026-11-09T00:00:00Z");

	@Test
	void 완성한_기록을_고치면_작성_중으로_돌아가고_version이_오르고_게시_시각은_그대로다() {
		TravelRecord record = TravelRecord.create(1L, "가을 여행", NOW);
		record.complete(NOW.plusSeconds(10));
		assertThat(record.getStatus()).isEqualTo(RecordStatus.COMPLETED);
		assertThat(record.getVersion()).isEqualTo(2);

		record.markEdited(NOW.plusSeconds(20));

		assertThat(record.getStatus()).isEqualTo(RecordStatus.DRAFT);
		assertThat(record.getVersion()).isEqualTo(3);
		assertThat(record.getUpdatedAt()).isEqualTo(NOW.plusSeconds(20));
		assertThat(record.getPublishedAt()).isNull();
	}

	@Test
	void 같은_시각에_두_번_고쳐도_version이_매번_오른다() {
		TravelRecord record = TravelRecord.create(1L, "가을 여행", NOW);
		record.markEdited(NOW);
		record.markEdited(NOW);

		assertThat(record.getVersion()).isEqualTo(3);
	}
}

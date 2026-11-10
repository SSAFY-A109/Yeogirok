package com.ssafy.yeogirok.domain.record.draft;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/** 사진 앞부분에서 촬영 시각·GPS 를 읽는다. 테스트는 가짜 구현을 쓴다. */
public interface PhotoMetadataReader {

	/** @return 촬영 시각을 읽지 못하면 empty(그 사진은 근거로 쓰지 않는다) */
	Optional<PhotoMetadata> read(byte[] head);

	/** @param latitude GPS 가 없으면 latitude·longitude 둘 다 null */
	record PhotoMetadata(Instant takenAt, BigDecimal latitude, BigDecimal longitude) {

		public boolean hasLocation() {
			return latitude != null && longitude != null;
		}
	}
}

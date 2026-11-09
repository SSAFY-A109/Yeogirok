package com.ssafy.yeogirok.domain.record.dto;

import com.ssafy.yeogirok.domain.record.entity.RecordStatus;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;

/** API-057 응답. */
public record RecordCreateResponse(Long recordId, Long roomId, RecordStatus status, Integer version) {

	public static RecordCreateResponse from(TravelRecord record) {
		return new RecordCreateResponse(record.getId(), record.getRoomId(), record.getStatus(), record.getVersion());
	}
}

package com.ssafy.yeogirok.domain.record.dto;

import com.ssafy.yeogirok.domain.record.entity.RecordStatus;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;

/** API-071 응답. */
public record RecordCompleteResponse(Long recordId, RecordStatus status, Integer version) {

	public static RecordCompleteResponse from(TravelRecord record) {
		return new RecordCompleteResponse(record.getId(), record.getStatus(), record.getVersion());
	}
}

package com.ssafy.yeogirok.domain.room.dto;

import static com.ssafy.yeogirok.global.request.JsonFields.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import tools.jackson.databind.JsonNode;

/**
 * API-030 요청 {@code { recordId, visitIds }}.
 * <ul>
 *   <li>recordId: 생략·null C001, 정수 아님 C002</li>
 *   <li>visitIds: 생략·null·빈 배열 CO006(가져올 장소를 고르지 않음), 배열 아님 C002, 원소가 정수 아님 C002,
 *       같은 방문이 두 번 있으면 C003</li>
 * </ul>
 * 방문이 그 코스에 속하는지는 서비스가 본다(CO007).
 */
public class RoomPlaceImportRequest {

	@JsonProperty("recordId")
	private JsonNode recordId;

	@JsonProperty("visitIds")
	private JsonNode visitIds;

	public RoomPlaceImportCommand toCommand() {
		long record = requiredLong(recordId, "recordId");
		if (isAbsent(visitIds)) {
			throw new BusinessException(ExceptionType.COURSE_IMPORT_NOTHING_SELECTED);
		}
		if (!visitIds.isArray()) {
			throw invalidFormat("visitIds", "배열이어야 합니다.");
		}
		if (visitIds.isEmpty()) {
			throw new BusinessException(ExceptionType.COURSE_IMPORT_NOTHING_SELECTED);
		}
		List<Long> parsed = new ArrayList<>();
		Set<Long> seen = new HashSet<>();
		for (JsonNode visitId : visitIds) {
			if (isAbsent(visitId)) {
				throw invalidFormat("visitIds", "정수 배열이어야 합니다.");
			}
			long id = optionalLong(visitId, "visitIds");
			if (!seen.add(id)) {
				throw new BusinessException(ExceptionType.NOT_ALLOWED_VALUE, "visitIds: 같은 방문이 두 번 있습니다.");
			}
			parsed.add(id);
		}
		return new RoomPlaceImportCommand(record, parsed);
	}
}

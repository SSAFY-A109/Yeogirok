package com.ssafy.yeogirok.domain.tip.service;

import static com.ssafy.yeogirok.global.request.JsonFields.isAbsent;
import static com.ssafy.yeogirok.global.request.JsonFields.missing;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.domain.tip.TipProperties;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** placement 검사. 앱이 정한 구조라 서버는 JSON 객체인지와 크기만 본다(specs/ar-tip-api.md 5절 4). */
@Component
@RequiredArgsConstructor
public class TipPlacements {

	private final JsonMapper jsonMapper;
	private final TipProperties properties;

	/** 없거나 null C001, 객체 아님·{@code placementMaxBytes} 초과 T007. */
	public Map<String, Object> toMap(JsonNode placement) {
		if (isAbsent(placement)) {
			throw missing("placement");
		}
		if (!placement.isObject()) {
			throw invalid("JSON 객체여야 합니다.");
		}
		if (jsonMapper.writeValueAsBytes(placement).length > properties.placementMaxBytes()) {
			throw invalid("최대 %d바이트까지 보낼 수 있습니다.".formatted(properties.placementMaxBytes()));
		}
		return jsonMapper.convertValue(placement, new TypeReference<>() {
		});
	}

	private static BusinessException invalid(String reason) {
		return new BusinessException(ExceptionType.TIP_SPATIAL_INVALID, "placement: " + reason);
	}
}

package com.ssafy.yeogirok.domain.schedule;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 마지막으로 확정 일정에 적용한 초안 저장(API-039) 요청. {@code travel_rooms.schedule_options} 의 {@code lastApplied} 키에
 * 둔다. 같은 요청을 다시 보내면(Redis 멱등키가 사라졌거나 초안이 만료된 뒤라도) 방 version 이 resultVersion 그대로일 때
 * 저장 결과를 다시 돌려준다.
 *
 * <p>일정 편집(API-040)·여행방 수정(API-023)이 일정 옵션을 다시 쓰면 이 값은 사라진다. 그때는 방 version 도 바뀌어 같은
 * 요청을 다시 보내도 C004 이므로 결과가 같다.
 *
 * @param requestHash    정규화한 요청 전체(draftKey·draftVersion·baseVersion·days·unassignedItemIds·지점)의 해시
 * @param requestKeyHash Idempotency-Key 의 해시(키 원문은 남기지 않는다)
 * @param resultVersion  저장한 뒤의 방 version
 */
public record LastApplied(String draftKey, int draftVersion, int baseVersion, String requestHash,
	String requestKeyHash, int resultVersion) {

	private static final String KEY = "lastApplied";

	/** 일정 옵션 맵에서 읽는다. 없으면 null. */
	@SuppressWarnings("unchecked")
	public static LastApplied from(Map<String, Object> options) {
		if (options == null || !(options.get(KEY) instanceof Map<?, ?> raw)) {
			return null;
		}
		Map<String, Object> map = (Map<String, Object>)raw;
		return new LastApplied((String)map.get("draftKey"), ((Number)map.get("draftVersion")).intValue(),
			((Number)map.get("baseVersion")).intValue(), (String)map.get("requestHash"),
			(String)map.get("requestKeyHash"), ((Number)map.get("resultVersion")).intValue());
	}

	/** 일정 옵션 맵에 이 값을 더한 새 맵. */
	public Map<String, Object> addTo(Map<String, Object> options) {
		Map<String, Object> value = new LinkedHashMap<>();
		value.put("draftKey", draftKey);
		value.put("draftVersion", draftVersion);
		value.put("baseVersion", baseVersion);
		value.put("requestHash", requestHash);
		value.put("requestKeyHash", requestKeyHash);
		value.put("resultVersion", resultVersion);
		Map<String, Object> result = new LinkedHashMap<>(options);
		result.put(KEY, value);
		return result;
	}
}

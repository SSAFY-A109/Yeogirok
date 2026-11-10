package com.ssafy.yeogirok.domain.file.session;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.idempotency.IdempotencyProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

/**
 * 영수증 자료 저장소(Redis {@code material:{materialId}}). 원본 삭제를 확인하기 전에는 수명을 걸지 않고(노션 Redis 4절),
 * 확인한 뒤에는 멱등키 보관 기간만큼 둔다. 그동안 OCR·보정 결과로 기록 초안을 만들 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReceiptMaterialStore {

	private static final String PREFIX = "material:";
	/** 기록·업로더별 연결한 자료 목록(API-063, 명세 {@code specs/record-api-2.md} 2-4). 수명 없음, 읽을 때 만료된 자료를 뺀다. */
	private static final String INDEX_PREFIX = "material-index:";

	private final StringRedisTemplate redis;
	private final JsonMapper jsonMapper;
	private final IdempotencyProperties idempotencyProperties;

	public void save(String materialId, ReceiptMaterial material) {
		try {
			redis.opsForValue().set(PREFIX + materialId, jsonMapper.writeValueAsString(material));
		} catch (DataAccessException e) {
			log.error("영수증 자료를 저장하지 못했습니다.", e);
			throw new BusinessException(ExceptionType.FILE_STORAGE_UNAVAILABLE);
		}
	}

	/**
	 * 이미 있는 자료를 바꿔 저장한다. 원본 삭제 뒤 걸린 수명은 그대로 둔다(연결·OCR·보정이 수명을 없애지 않게).
	 * 호출하는 쪽이 자료 잠금을 쥐고 부른다.
	 */
	public void replace(String materialId, ReceiptMaterial material) {
		String key = PREFIX + materialId;
		try {
			Long ttl = redis.getExpire(key, TimeUnit.MILLISECONDS);
			String value = jsonMapper.writeValueAsString(material);
			if (ttl != null && ttl > 0) {
				redis.opsForValue().set(key, value, ttl, TimeUnit.MILLISECONDS);
			} else {
				redis.opsForValue().set(key, value);
			}
		} catch (DataAccessException e) {
			log.error("영수증 자료를 저장하지 못했습니다.", e);
			throw new BusinessException(ExceptionType.FILE_STORAGE_UNAVAILABLE);
		}
	}

	public void addToIndex(Long recordId, Long userId, String materialId) {
		redis.opsForSet().add(indexKey(recordId, userId), materialId);
	}

	/** 그 기록에 그 사용자가 연결한 자료. 키가 만료된 자료는 목록에서 빼고 지운다. */
	public List<ReceiptMaterialEntry> indexed(Long recordId, Long userId) {
		String indexKey = indexKey(recordId, userId);
		Set<String> ids = redis.opsForSet().members(indexKey);
		List<ReceiptMaterialEntry> found = new ArrayList<>();
		if (ids == null) {
			return found;
		}
		for (String id : ids) {
			find(id).ifPresentOrElse(material -> found.add(new ReceiptMaterialEntry(id, material)),
				() -> redis.opsForSet().remove(indexKey, id));
		}
		return found;
	}

	private static String indexKey(Long recordId, Long userId) {
		return INDEX_PREFIX + recordId + ":" + userId;
	}

	/** 원본을 지웠다고 표시한다. 자료가 없으면(이미 만료) 아무것도 하지 않는다. */
	public void markOriginalDeleted(String materialId) {
		find(materialId).ifPresent(material -> redis.opsForValue().set(PREFIX + materialId,
			jsonMapper.writeValueAsString(material.deleted()), idempotencyProperties.retention()));
	}

	public Optional<ReceiptMaterial> find(String materialId) {
		return Optional.ofNullable(redis.opsForValue().get(PREFIX + materialId))
			.map(value -> jsonMapper.readValue(value, ReceiptMaterial.class));
	}
}

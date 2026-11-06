package com.ssafy.yeogirok.domain.file.session;

import java.util.Optional;

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

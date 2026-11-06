package com.ssafy.yeogirok.domain.file.session;

import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

/**
 * 영수증 자료 저장소(Redis {@code material:{materialId}}). 키는 원본 정리 작업(4-5)이 삭제를 확인한 뒤 짧은 수명을 건다.
 * 그 전에는 수명을 걸지 않는다(삭제 대상을 확인 전에 TTL 로 없애지 않는다, 노션 Redis 4절).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReceiptMaterialStore {

	private static final String PREFIX = "material:";

	private final StringRedisTemplate redis;
	private final JsonMapper jsonMapper;

	public void save(String materialId, ReceiptMaterial material) {
		try {
			redis.opsForValue().set(PREFIX + materialId, jsonMapper.writeValueAsString(material));
		} catch (DataAccessException e) {
			log.error("영수증 자료를 저장하지 못했습니다.", e);
			throw new BusinessException(ExceptionType.FILE_STORAGE_UNAVAILABLE);
		}
	}

	public Optional<ReceiptMaterial> find(String materialId) {
		return Optional.ofNullable(redis.opsForValue().get(PREFIX + materialId))
			.map(value -> jsonMapper.readValue(value, ReceiptMaterial.class));
	}
}

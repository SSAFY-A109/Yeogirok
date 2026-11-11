package com.ssafy.yeogirok.domain.file.session;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.idempotency.IdempotencyProperties;
import com.ssafy.yeogirok.global.lock.RedisLock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

/**
 * 영수증 자료 저장소(Redis {@code material:{materialId}}). 원본 삭제를 확인하기 전에는 수명을 걸지 않고(노션 Redis 4절),
 * 확인한 뒤에는 기록에 연결한 자료는 부르는 쪽이 넘긴 기간({@code yeogirok.record.material-retention}, 30일), 연결 안 된 자료는
 * 멱등키 보관 기간만큼 둔다(명세 {@code specs/record-api-2.md} 5절 19). 그동안 정제 값으로 기록 초안을 만들 수 있다.
 *
 * <p>바꾸는 쓰기는 모두 {@code material-lock:{materialId}} 잠금 안에서 읽기 → 검사 → 쓰기를 한다(API 쪽 쓰기와 원본 정리
 * 작업이 서로 덮지 않게). 잠금을 못 잡으면 몇 번 다시 시도하고 그래도 안 되면 C018(명세 5절 20).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReceiptMaterialStore {

	private static final String PREFIX = "material:";
	/**
	 * 기록·업로더별 연결한 자료 목록(API-063, 명세 {@code specs/record-api-2.md} 2-4). 읽을 때 만료된 자료를 빼고, 자료 원본을
	 * 지우면 자료와 같은 기간의 수명을 건다(새로 연결하면 수명을 없앤다). 그래서 아무도 안 읽어도 마지막 자료와 함께 사라진다.
	 */
	private static final String INDEX_PREFIX = "material-index:";
	private static final String LOCK_PREFIX = "material-lock:";
	/** 잠금 수명. 잠금 안에서는 Redis 읽기·쓰기만 한다(OCR·S3 호출은 잠금 밖). */
	private static final Duration LOCK_TTL = Duration.ofSeconds(5);
	private static final int LOCK_ATTEMPTS = 5;
	private static final long LOCK_RETRY_MILLIS = 100;

	private final StringRedisTemplate redis;
	private final JsonMapper jsonMapper;
	private final IdempotencyProperties idempotencyProperties;
	private final RedisLock redisLock;

	public void save(String materialId, ReceiptMaterial material) {
		try {
			redis.opsForValue().set(PREFIX + materialId, jsonMapper.writeValueAsString(material));
		} catch (DataAccessException e) {
			log.error("영수증 자료를 저장하지 못했습니다.", e);
			throw new BusinessException(ExceptionType.FILE_STORAGE_UNAVAILABLE);
		}
	}

	/**
	 * 잠금 안에서 {@code condition} 을 만족하는 자료를 바꿔 저장한다. {@code change} 가 예외를 던지면 저장하지 않는다.
	 * @return 바꾼 자료. 자료가 없거나 조건이 맞지 않으면 empty
	 */
	public Optional<ReceiptMaterial> update(String materialId, Predicate<ReceiptMaterial> condition,
		UnaryOperator<ReceiptMaterial> change) {
		return locked(materialId, () -> {
			Optional<ReceiptMaterial> changed = find(materialId).filter(condition).map(change);
			changed.ifPresent(material -> replace(materialId, material));
			return changed;
		});
	}

	/** 이미 있는 자료를 바꿔 저장한다. 원본 삭제 뒤 걸린 수명은 그대로 둔다(연결·OCR·보정이 수명을 없애지 않게). 잠금 안에서 부른다. */
	private void replace(String materialId, ReceiptMaterial material) {
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

	/** 새 자료의 원본은 아직 안 지웠으니 앞 자료 때 걸린 수명을 없앤다(새 자료보다 목록이 먼저 만료되지 않게). */
	public void addToIndex(Long recordId, Long userId, String materialId) {
		String indexKey = indexKey(recordId, userId);
		redis.opsForSet().add(indexKey, materialId);
		redis.persist(indexKey);
	}

	/** 그 기록에 그 사용자가 연결한 자료. */
	public List<ReceiptMaterialEntry> indexed(Long recordId, Long userId) {
		return indexedAll(List.of(recordId), userId).getOrDefault(recordId, List.of());
	}

	private static String indexKey(Long recordId, Long userId) {
		return INDEX_PREFIX + recordId + ":" + userId;
	}

	/**
	 * 원본을 지웠다고 표시하고 수명을 건다(기록에 연결한 자료 {@code linkedRetention}, 아니면 멱등키 보관 기간). 연결한 자료면
	 * 목록에도 같은 수명을 건다. 자료가 없으면(이미 만료) 아무것도 하지 않는다. 잠금 안에서 해 API 쓰기(OCR 결과·보정)와 서로
	 * 덮지 않는다.
	 */
	public void markOriginalDeleted(String materialId, Duration linkedRetention) {
		locked(materialId, () -> {
			find(materialId).ifPresent(material -> {
				Duration retention = material.isLinked() ? linkedRetention : idempotencyProperties.retention();
				redis.opsForValue().set(PREFIX + materialId, jsonMapper.writeValueAsString(material.deleted()), retention);
				if (material.isLinked()) {
					redis.expire(indexKey(material.recordId(), material.uploadedBy()), retention);
				}
			});
			return null;
		});
	}

	private <T> T locked(String materialId, Supplier<T> work) {
		String lockKey = LOCK_PREFIX + materialId;
		String token = acquire(lockKey);
		try {
			return work.get();
		} finally {
			redisLock.release(lockKey, token);
		}
	}

	private String acquire(String lockKey) {
		for (int attempt = 1; attempt <= LOCK_ATTEMPTS; attempt++) {
			Optional<String> token = redisLock.tryAcquire(lockKey, LOCK_TTL);
			if (token.isPresent()) {
				return token.get();
			}
			if (attempt < LOCK_ATTEMPTS) {
				pause();
			}
		}
		throw new BusinessException(ExceptionType.IDEMPOTENT_REQUEST_IN_PROGRESS,
			"같은 자료를 처리하고 있습니다. 잠시 후 다시 시도해 주세요.");
	}

	private static void pause() {
		try {
			Thread.sleep(LOCK_RETRY_MILLIS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("자료 잠금을 기다리다 중단됐습니다.", e);
		}
	}

	public Optional<ReceiptMaterial> find(String materialId) {
		return Optional.ofNullable(redis.opsForValue().get(PREFIX + materialId))
			.map(value -> jsonMapper.readValue(value, ReceiptMaterial.class));
	}

	/**
	 * 여러 기록의 그 사용자 자료를 한 번에(API-058·059·063). 목록은 파이프라인 한 번, 자료는 MGET 한 번으로 읽는다. 만료된 자료는
	 * 빼고 목록에서도 지운다.
	 */
	public Map<Long, List<ReceiptMaterialEntry>> indexedAll(List<Long> recordIds, Long userId) {
		Map<Long, List<ReceiptMaterialEntry>> byRecord = new HashMap<>();
		if (recordIds.isEmpty()) {
			return byRecord;
		}
		List<Object> members = redis.executePipelined((RedisCallback<Object>)connection -> {
			recordIds.forEach(recordId -> connection.setCommands()
				.sMembers(indexKey(recordId, userId).getBytes(StandardCharsets.UTF_8)));
			return null;
		});
		List<String> materialIds = new ArrayList<>();
		Map<String, Long> recordOf = new HashMap<>();
		for (int i = 0; i < recordIds.size(); i++) {
			for (Object id : (Set<?>)members.get(i)) {
				materialIds.add(id.toString());
				recordOf.put(id.toString(), recordIds.get(i));
			}
		}
		if (materialIds.isEmpty()) {
			return byRecord;
		}
		List<String> values = redis.opsForValue().multiGet(materialIds.stream().map(id -> PREFIX + id).toList());
		for (int i = 0; i < materialIds.size(); i++) {
			String id = materialIds.get(i);
			String value = values == null ? null : values.get(i);
			if (value != null) {
				byRecord.computeIfAbsent(recordOf.get(id), key -> new ArrayList<>())
					.add(new ReceiptMaterialEntry(id, jsonMapper.readValue(value, ReceiptMaterial.class)));
			} else if (values != null) {
				redis.opsForSet().remove(indexKey(recordOf.get(id), userId), id);
			}
		}
		return byRecord;
	}
}

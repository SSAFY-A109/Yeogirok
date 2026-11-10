package com.ssafy.yeogirok.domain.record.draft;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * 통합 기록 초안 저장소. {@code draft:{draftKey}} 와 기록별 최근 초안 포인터 {@code record-draft:{recordId}}(명세 2-5·2-6).
 * 둘 다 초안을 만든 시각부터 보관 기간만큼 두고, 다시 저장해도 기한은 늘지 않는다.
 */
@Component
@RequiredArgsConstructor
public class RecordDraftStore {

	private static final String PREFIX = "draft:";
	private static final String POINTER_PREFIX = "record-draft:";

	private final StringRedisTemplate redis;
	private final JsonMapper jsonMapper;
	private final RecordDraftProperties properties;
	private final Clock clock;

	/** 최근 초안 포인터. 059 progress·앱 재시작 후 이어하기에 쓴다. */
	public record Pointer(String draftKey, String jobId) {
	}

	/** 기한이 이미 지난 초안은 저장하지 않는다. */
	public void save(RecordDraft draft) {
		remaining(draft).ifPresent(ttl ->
			redis.opsForValue().set(PREFIX + draft.draftKey(), jsonMapper.writeValueAsString(draft), ttl));
	}

	public Optional<RecordDraft> find(String draftKey) {
		return Optional.ofNullable(redis.opsForValue().get(PREFIX + draftKey))
			.map(value -> jsonMapper.readValue(value, RecordDraft.class));
	}

	/** 그 기록의 최근 초안으로 덮어쓴다(API-064 접수). */
	public void savePointer(RecordDraft draft) {
		remaining(draft).ifPresent(ttl -> redis.opsForValue().set(POINTER_PREFIX + draft.recordId(),
			jsonMapper.writeValueAsString(new Pointer(draft.draftKey(), draft.jobId())), ttl));
	}

	public Optional<Pointer> findPointer(Long recordId) {
		return Optional.ofNullable(redis.opsForValue().get(POINTER_PREFIX + recordId))
			.map(value -> jsonMapper.readValue(value, Pointer.class));
	}

	private Optional<Duration> remaining(RecordDraft draft) {
		Duration ttl = Duration.between(clock.instant(), draft.createdAt().plus(properties.draftRetention()));
		return ttl.isNegative() || ttl.isZero() ? Optional.empty() : Optional.of(ttl);
	}
}

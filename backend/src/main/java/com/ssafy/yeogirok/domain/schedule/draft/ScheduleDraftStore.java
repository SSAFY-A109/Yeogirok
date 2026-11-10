package com.ssafy.yeogirok.domain.schedule.draft;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.domain.schedule.ScheduleProperties;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * 일정 초안 저장소(Redis {@code schedule-draft:{draftKey}}). 키는 초안을 만든 시각부터 보관 기간만큼 둔다. 다시 계산해
 * 저장해도 기한은 늘지 않는다.
 */
@Component
@RequiredArgsConstructor
public class ScheduleDraftStore {

	private static final String PREFIX = "schedule-draft:";

	private final StringRedisTemplate redis;
	private final JsonMapper jsonMapper;
	private final ScheduleProperties properties;
	private final Clock clock;

	/** 기한이 이미 지난 초안은 저장하지 않는다. */
	public void save(ScheduleDraft draft) {
		Duration ttl = Duration.between(clock.instant(), draft.createdAt().plus(properties.draftRetention()));
		if (ttl.isNegative() || ttl.isZero()) {
			return;
		}
		redis.opsForValue().set(PREFIX + draft.draftKey(), jsonMapper.writeValueAsString(draft), ttl);
	}

	public Optional<ScheduleDraft> find(String draftKey) {
		return Optional.ofNullable(redis.opsForValue().get(PREFIX + draftKey))
			.map(value -> jsonMapper.readValue(value, ScheduleDraft.class));
	}

	/** 이 방의 초안. 없거나 만료됐거나 다른 방의 초안이면 SC001. 지금 상태({@link ScheduleDraft#asOf})로 돌려준다. */
	public ScheduleDraft get(Long roomId, String draftKey) {
		return find(draftKey)
			.filter(draft -> draft.roomId().equals(roomId))
			.map(draft -> draft.asOf(clock.instant()))
			.orElseThrow(() -> new BusinessException(ExceptionType.SCHEDULE_DRAFT_NOT_FOUND));
	}
}

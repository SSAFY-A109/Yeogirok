package com.ssafy.yeogirok.domain.user.unlink;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.global.oauth.OAuthProvider;

import lombok.RequiredArgsConstructor;

/**
 * 실패한 제공자 연결 해제 목록. 노션 저장 원칙(작업 상태는 Redis)에 따라 Redis 에 둔다.
 *
 * <ul>
 *   <li>{@code auth:unlink-task:{userId}} — provider·providerSubject·attempts·lastError·updatedAt.
 *       수명 없이 둔다. 최대 횟수를 넘긴 작업도 운영자가 확인하도록 남긴다.</li>
 *   <li>{@code auth:unlink-tasks} — 작업이 있는 userId 목록(Set).</li>
 * </ul>
 *
 * <p>서버 Redis 는 디스크에 기록하고(appendonly) 메모리가 차도 키를 지우지 않으므로(noeviction) 재시작 뒤에도 남는다.
 */
@Component
@RequiredArgsConstructor
public class ProviderUnlinkTaskStore {

	private static final String TASK_KEY = "auth:unlink-task:";
	private static final String TASKS_KEY = "auth:unlink-tasks";
	private static final int MAX_ERROR_LENGTH = 500;

	private final StringRedisTemplate redis;
	private final Clock clock;

	/** 처음 실패한 연결 해제를 남긴다(시도 1회). */
	public void saveFailed(UserWithdrawnEvent event, String error) {
		redis.opsForHash().putAll(TASK_KEY + event.userId(), Map.of(
			"provider", event.provider().name(),
			"providerSubject", event.providerSubject(),
			"attempts", "1",
			"lastError", truncate(error),
			"updatedAt", clock.instant().toString()));
		redis.opsForSet().add(TASKS_KEY, event.userId().toString());
	}

	public void recordFailure(Long userId, String error) {
		String key = TASK_KEY + userId;
		redis.opsForHash().increment(key, "attempts", 1);
		redis.opsForHash().putAll(key, Map.of(
			"lastError", truncate(error),
			"updatedAt", clock.instant().toString()));
	}

	public void delete(Long userId) {
		redis.delete(TASK_KEY + userId);
		redis.opsForSet().remove(TASKS_KEY, userId.toString());
	}

	/** 남아 있는 모든 작업. userId 순이다. */
	public List<ProviderUnlinkTask> findAll() {
		Set<String> userIds = redis.opsForSet().members(TASKS_KEY);
		if (userIds == null) {
			return List.of();
		}

		return userIds.stream()
			.map(Long::valueOf)
			.sorted()
			.map(this::find)
			.flatMap(Optional::stream)
			.toList();
	}

	private Optional<ProviderUnlinkTask> find(Long userId) {
		Map<Object, Object> fields = redis.opsForHash().entries(TASK_KEY + userId);
		if (fields.isEmpty()) {
			return Optional.empty();
		}

		return Optional.of(new ProviderUnlinkTask(
			userId,
			OAuthProvider.valueOf((String) fields.get("provider")),
			(String) fields.get("providerSubject"),
			Integer.parseInt((String) fields.get("attempts")),
			(String) fields.get("lastError")));
	}

	private static String truncate(String error) {
		if (error == null) {
			return "";
		}
		return error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
	}
}

package com.ssafy.yeogirok.domain.tip.service;

import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.idempotency.IdempotencyProperties;
import com.ssafy.yeogirok.global.lock.RedisLock;

import lombok.RequiredArgsConstructor;

/**
 * 같은 사진을 팁 두 개에 붙이지 않게 사진(uploadId)마다 팁 하나를 Redis {@code tip-photo-use:{uploadId}} 에 표시한다(PR #64 리뷰).
 * 게시 전에는 ar_tip_images 에 행이 없어 DB 확인만으로는 막지 못한다. 보관 기간은 업로드 세션과 같아 그 뒤로는 사진을 새로 쓸 수 없다.
 */
@Component
@RequiredArgsConstructor
public class TipPhotoClaims {

	private static final String KEY_PREFIX = "tip-photo-use:";

	private final StringRedisTemplate redis;
	private final RedisLock redisLock;
	private final IdempotencyProperties idempotencyProperties;

	/**
	 * 사진이 없으면 아무것도 하지 않는다. 이미 이 팁 것이면 통과(051·054 재시도), 다른 팁 것이면 M005.
	 * @return 이번에 새로 걸었으면 true. 이어지는 저장이 실패하면 {@link #release} 로 푼다
	 */
	public boolean claim(Long tipId, List<String> uploadIds) {
		if (uploadIds == null || uploadIds.isEmpty()) {
			return false;
		}
		String key = KEY_PREFIX + uploadIds.getFirst();
		String owner = tipId.toString();
		if (Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, owner, idempotencyProperties.retention()))) {
			return true;
		}
		if (owner.equals(redis.opsForValue().get(key))) {
			return false;
		}
		throw new BusinessException(ExceptionType.FILE_ACCESS_DENIED, "이미 다른 팁에 붙인 사진입니다.");
	}

	/** 표시를 건 채로 저장한다. 저장이 실패하면 이번에 건 표시를 푼다(054·051). */
	public void claimWhile(Long tipId, List<String> uploadIds, Runnable save) {
		boolean claimed = claim(tipId, uploadIds);
		try {
			save.run();
		} catch (RuntimeException e) {
			if (claimed) {
				release(tipId, uploadIds);
			}
			throw e;
		}
	}

	/** 이 팁이 건 표시만 푼다. */
	public void release(Long tipId, List<String> uploadIds) {
		redisLock.release(KEY_PREFIX + uploadIds.getFirst(), tipId.toString());
	}
}

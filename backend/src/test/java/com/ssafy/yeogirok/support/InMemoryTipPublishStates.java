package com.ssafy.yeogirok.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.springframework.data.redis.RedisConnectionFailureException;

import com.ssafy.yeogirok.domain.tip.publish.TipPublishState;
import com.ssafy.yeogirok.domain.tip.publish.TipPublishStore;

/** 서비스 테스트용 팁 게시 작업 상태 저장소. 맵에 두고 그대로 읽는다(Redis 동작은 TipPublishStoreTest 가 본다). */
public final class InMemoryTipPublishStates {

	public final Map<Long, TipPublishState> states = new ConcurrentHashMap<>();
	public final TipPublishStore store = mock(TipPublishStore.class);
	private final AtomicBoolean failNextSave = new AtomicBoolean();

	public InMemoryTipPublishStates() {
		doAnswer(invocation -> {
			if (failNextSave.getAndSet(false)) {
				throw new RedisConnectionFailureException("test");
			}
			states.put(invocation.getArgument(0), invocation.getArgument(1));
			return null;
		}).when(store).save(anyLong(), any());
		given(store.imagePaths()).willAnswer(invocation -> states.values().stream()
			.map(TipPublishState::imagePath).filter(Objects::nonNull).collect(Collectors.toSet()));
		given(store.find(anyLong()))
			.willAnswer(invocation -> Optional.ofNullable(states.get(invocation.<Long>getArgument(0))));
	}

	/** 다음 save 한 번을 Redis 장애처럼 실패시킨다. */
	public void failNextSave() {
		failNextSave.set(true);
	}
}

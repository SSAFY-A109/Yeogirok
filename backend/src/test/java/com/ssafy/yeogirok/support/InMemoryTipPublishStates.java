package com.ssafy.yeogirok.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.ssafy.yeogirok.domain.tip.publish.TipPublishState;
import com.ssafy.yeogirok.domain.tip.publish.TipPublishStore;

/** 서비스 테스트용 팁 게시 작업 상태 저장소. 맵에 두고 그대로 읽는다(Redis 동작은 TipPublishStoreTest 가 본다). */
public final class InMemoryTipPublishStates {

	public final Map<Long, TipPublishState> states = new ConcurrentHashMap<>();
	public final TipPublishStore store = mock(TipPublishStore.class);

	public InMemoryTipPublishStates() {
		doAnswer(invocation -> {
			states.put(invocation.getArgument(0), invocation.getArgument(1));
			return null;
		}).when(store).save(anyLong(), any());
		given(store.find(anyLong()))
			.willAnswer(invocation -> Optional.ofNullable(states.get(invocation.<Long>getArgument(0))));
	}
}

package com.ssafy.yeogirok.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.ssafy.yeogirok.domain.tip.publish.TipEditDraft;
import com.ssafy.yeogirok.domain.tip.publish.TipEditStore;

/** 서비스 테스트용 팁 수정안·수정 예약 저장소. 맵에 둔다(Redis 동작은 TipEditStoreTest 가 본다). */
public final class InMemoryTipEditStore {

	public final Map<String, TipEditDraft> drafts = new ConcurrentHashMap<>();
	/** tipId → 예약 토큰 */
	public final Map<Long, String> locks = new ConcurrentHashMap<>();
	public final TipEditStore store = mock(TipEditStore.class);

	public InMemoryTipEditStore() {
		doAnswer(invocation -> {
			drafts.put(key(invocation.getArgument(0), invocation.getArgument(1)), invocation.getArgument(2));
			return null;
		}).when(store).save(anyLong(), anyString(), any());
		given(store.find(anyLong(), anyString())).willAnswer(invocation ->
			Optional.ofNullable(drafts.get(key(invocation.getArgument(0), invocation.getArgument(1)))));
		doAnswer(invocation -> drafts.remove(key(invocation.getArgument(0), invocation.getArgument(1))))
			.when(store).delete(anyLong(), anyString());
		given(store.reserve(anyLong())).willAnswer(invocation -> {
			String token = UUID.randomUUID().toString();
			return locks.putIfAbsent(invocation.getArgument(0), token) == null ? Optional.of(token) : Optional.empty();
		});
		doAnswer(invocation -> locks.remove(invocation.<Long>getArgument(0), invocation.getArgument(1)))
			.when(store).release(anyLong(), anyString());
	}

	public void clear() {
		drafts.clear();
		locks.clear();
	}

	private static String key(Long tipId, String jobId) {
		return tipId + ":" + jobId;
	}
}

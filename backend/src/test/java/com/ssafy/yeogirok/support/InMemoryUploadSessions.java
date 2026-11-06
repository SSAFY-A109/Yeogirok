package com.ssafy.yeogirok.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.ssafy.yeogirok.domain.file.session.UploadSession;
import com.ssafy.yeogirok.domain.file.session.UploadSessionStore;

/** 서비스 테스트용 업로드 세션 저장소. 저장한 세션을 맵에 두고 그대로 읽는다(Redis 동작은 UploadSessionStoreTest 가 본다). */
public final class InMemoryUploadSessions {

	public final Map<String, UploadSession> sessions = new HashMap<>();
	public final UploadSessionStore store = mock(UploadSessionStore.class);

	public InMemoryUploadSessions() {
		doAnswer(invocation -> {
			UploadSession session = invocation.getArgument(0);
			sessions.put(session.uploadId(), session);
			return null;
		}).when(store).save(any());
		given(store.find(anyString()))
			.willAnswer(invocation -> Optional.ofNullable(sessions.get(invocation.<String>getArgument(0))));
	}

	public void put(UploadSession session) {
		sessions.put(session.uploadId(), session);
	}
}

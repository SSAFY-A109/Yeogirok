package com.ssafy.yeogirok.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willCallRealMethod;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.ssafy.yeogirok.domain.tip.service.TipPhotoClaims;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

/** 서비스 테스트용 팁 사진 표시. 맵에 둔다(Redis 동작은 TipPhotoClaimsTest 가 본다). */
public final class InMemoryTipPhotoClaims {

	/** uploadId → tipId */
	public final Map<String, Long> claims = new ConcurrentHashMap<>();
	public final TipPhotoClaims store = mock(TipPhotoClaims.class);

	public InMemoryTipPhotoClaims() {
		given(store.claim(anyLong(), any())).willAnswer(invocation -> {
			List<String> uploadIds = invocation.getArgument(1);
			if (uploadIds == null || uploadIds.isEmpty()) {
				return false;
			}
			Long tipId = invocation.getArgument(0);
			Long owner = claims.putIfAbsent(uploadIds.getFirst(), tipId);
			if (owner != null && !owner.equals(tipId)) {
				throw new BusinessException(ExceptionType.FILE_ACCESS_DENIED);
			}
			return owner == null;
		});
		doAnswer(invocation -> claims.remove(invocation.<List<String>>getArgument(1).getFirst(),
			invocation.getArgument(0))).when(store).release(anyLong(), any());
		willCallRealMethod().given(store).claimWhile(anyLong(), any(), any());
	}
}

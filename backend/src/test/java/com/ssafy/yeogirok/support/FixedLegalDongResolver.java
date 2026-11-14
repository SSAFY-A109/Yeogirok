package com.ssafy.yeogirok.support;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import com.ssafy.yeogirok.global.geo.LegalDong;
import com.ssafy.yeogirok.global.geo.LegalDongResolver;

/**
 * 실제 카카오를 부르지 않고 정해 둔 법정동을 돌려준다. 호출 횟수를 센다.
 * 담기(API-029)가 여러 장소를 동시에 조회하므로 상태는 스레드 안전하게 둔다.
 */
public class FixedLegalDongResolver implements LegalDongResolver {

	private volatile LegalDong result;
	private volatile Duration delay = Duration.ZERO;
	private final AtomicInteger calls = new AtomicInteger();

	public FixedLegalDongResolver(LegalDong result) {
		this.result = result;
	}

	/** 돌려줄 값을 바꾸고 지연·호출 횟수를 되돌린다. 빈이 테스트 사이에 재사용되므로 각 테스트 시작에 부른다. */
	public void willReturn(LegalDong result) {
		this.result = result;
		this.delay = Duration.ZERO;
		this.calls.set(0);
	}

	/** 응답 전에 기다린다(마감 시간 확인용). */
	public void willDelay(Duration delay) {
		this.delay = delay;
	}

	public int calls() {
		return calls.get();
	}

	@Override
	public LegalDong resolve(BigDecimal latitude, BigDecimal longitude) {
		calls.incrementAndGet();
		if (!delay.isZero()) {
			try {
				Thread.sleep(delay);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return LegalDong.EMPTY;
			}
		}
		return result;
	}
}

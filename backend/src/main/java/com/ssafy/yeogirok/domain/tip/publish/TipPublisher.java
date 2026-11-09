package com.ssafy.yeogirok.domain.tip.publish;

import java.time.Clock;
import java.time.Instant;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import com.ssafy.yeogirok.domain.tip.entity.ArTip;
import com.ssafy.yeogirok.domain.tip.entity.ArTipImage;
import com.ssafy.yeogirok.domain.tip.entity.TipStatus;
import com.ssafy.yeogirok.domain.tip.repository.ArTipImageRepository;
import com.ssafy.yeogirok.domain.tip.repository.ArTipRepository;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.extern.slf4j.Slf4j;

/**
 * 049 커밋 뒤 비동기 게시(11/8 결정 A): 사진 연결 → PUBLISHED. 실패하면 팁을 FAILED 로 두고 공개하지 않는다.
 * 서버가 처리 중에 멈추면 작업이 RUNNING 으로 남는다(감시는 범위 밖, specs/ar-tip-api.md 5절 6).
 */
@Slf4j
@Component
public class TipPublisher {

	private final ArTipRepository arTipRepository;
	private final ArTipImageRepository arTipImageRepository;
	private final TipPublishStore publishStore;
	private final TransactionTemplate transactionTemplate;
	private final Clock clock;

	public TipPublisher(ArTipRepository arTipRepository, ArTipImageRepository arTipImageRepository,
		TipPublishStore publishStore, PlatformTransactionManager transactionManager, Clock clock) {
		this.arTipRepository = arTipRepository;
		this.arTipImageRepository = arTipImageRepository;
		this.publishStore = publishStore;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onRequested(TipPublishRequested event) {
		publish(event);
	}

	public void publish(TipPublishRequested event) {
		TipPublishState running = TipPublishState.queued(event.jobId(), event.imagePath() != null).running();
		publishStore.save(event.tipId(), running);
		try {
			transactionTemplate.executeWithoutResult(status -> link(event, clock.instant()));
			publishStore.save(event.tipId(), running.succeeded());
		} catch (RuntimeException e) {
			log.error("팁 게시 실패 tipId={} jobId={}", event.tipId(), event.jobId(), e);
			transactionTemplate.executeWithoutResult(status -> arTipRepository.findForUpdateById(event.tipId())
				.filter(tip -> tip.getStatus() == TipStatus.REGISTERING)
				.ifPresent(tip -> tip.fail(clock.instant())));
			publishStore.save(event.tipId(),
				running.failed(ExceptionType.SERVER_ERROR.getCode(), ExceptionType.SERVER_ERROR.getMessage()));
		}
	}

	/** 게시 전(REGISTERING) 팁만 게시한다. 행을 잠가 다시 읽어 그사이 삭제(API-055)된 팁은 되살리지 않는다. */
	private void link(TipPublishRequested event, Instant now) {
		ArTip tip = arTipRepository.findForUpdateById(event.tipId())
			.filter(found -> found.getStatus() == TipStatus.REGISTERING && !found.isDeleted())
			.orElseThrow(() -> new IllegalStateException("게시할 수 없는 팁 tipId=" + event.tipId()));
		if (event.imagePath() != null) {
			arTipImageRepository.save(ArTipImage.of(tip.getId(), event.imagePath(), now));
		}
		tip.publish(now);
	}
}

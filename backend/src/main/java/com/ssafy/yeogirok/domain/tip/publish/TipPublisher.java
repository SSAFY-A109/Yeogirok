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
 * 049·051 커밋 뒤 비동기 게시(11/8 결정 A): 사진 연결 → PUBLISHED. 실패하면 팁을 FAILED 로 두고 공개하지 않는다.
 * 끊겨서 REGISTERING 에 남은 팁은 {@link StuckTipRecoveryJob} 이 FAILED 로 돌린다.
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
		TipPublishState running = TipPublishState.queued(event.jobId(), event.imagePath()).running();
		try {
			switch (transactionTemplate.execute(status -> link(event, running, clock.instant()))) {
				case PUBLISHED -> publishStore.save(event.tipId(), running.succeeded());
				case GONE -> {
					// 그사이 삭제(API-055)됐거나 이미 게시된 팁. 정상 흐름이라 ERROR 로 남기지 않고, 다시 해도 소용없어 재시도 불가.
					log.warn("게시할 수 없는 팁이라 건너뜀 tipId={} jobId={}", event.tipId(), event.jobId());
					publishStore.save(event.tipId(),
						running.failed(ExceptionType.TIP_NOT_EXIST.getCode(), "게시할 수 없는 팁입니다.", false));
				}
				// 051 로 새 작업이 생겼거나 정리 작업이 이미 FAILED 로 돌린 팁. 최신 작업 상태를 덮어쓰지 않는다.
				case STALE -> log.warn("지난 게시 작업이라 건너뜀 tipId={} jobId={}", event.tipId(), event.jobId());
			}
		} catch (RuntimeException e) {
			log.error("팁 게시 실패 tipId={} jobId={}", event.tipId(), event.jobId(), e);
			transactionTemplate.executeWithoutResult(status -> arTipRepository.findForUpdateById(event.tipId())
				.filter(tip -> tip.getStatus() == TipStatus.REGISTERING)
				.ifPresent(tip -> tip.fail(clock.instant())));
			publishStore.save(event.tipId(),
				running.failed(ExceptionType.SERVER_ERROR.getCode(), ExceptionType.SERVER_ERROR.getMessage(), true));
		}
	}

	private enum Outcome { PUBLISHED, GONE, STALE }

	/**
	 * 행을 잠가 다시 읽고, 이 작업이 그 팁의 최신 작업(Redis jobId)이고 팁이 게시 전(REGISTERING)일 때만 게시한다.
	 * 그사이 삭제(API-055)된 팁은 되살리지 않는다.
	 */
	private Outcome link(TipPublishRequested event, TipPublishState running, Instant now) {
		ArTip tip = arTipRepository.findForUpdateById(event.tipId()).orElse(null);
		// 잠근 뒤에 읽어야 051 이 같은 잠금 안에서 바꾼 최신 jobId 를 본다.
		boolean current = publishStore.find(event.tipId())
			.filter(state -> state.jobId().equals(event.jobId()))
			.isPresent();
		if (!current || (tip != null && tip.getStatus() == TipStatus.FAILED)) {
			return Outcome.STALE;
		}
		if (tip == null || tip.isDeleted() || tip.getStatus() != TipStatus.REGISTERING) {
			return Outcome.GONE;
		}
		publishStore.save(event.tipId(), running);
		if (event.imagePath() != null) {
			arTipImageRepository.save(ArTipImage.of(tip.getId(), event.imagePath(), now));
		}
		tip.publish(now);
		return Outcome.PUBLISHED;
	}
}

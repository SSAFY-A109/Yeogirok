package com.ssafy.yeogirok.domain.tip.publish;

import java.time.Clock;
import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ssafy.yeogirok.domain.tip.TipProperties;
import com.ssafy.yeogirok.domain.tip.entity.TipStatus;
import com.ssafy.yeogirok.domain.tip.repository.ArTipRepository;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.extern.slf4j.Slf4j;

/**
 * 게시 작업이 끊겨(재시작으로 @Async 작업 유실 등) REGISTERING 에 남은 팁을 FAILED 로 돌려 API-051 로 다시 시도하게 한다(PR #64 리뷰).
 * 작업 상태는 jobId·사진 경로를 그대로 두고 FAILED·retryable 로 바꾼다. Redis 에 작업 정보가 없으면 팁만 바꾼다(051 이 T008 로 사진을 다시 받음).
 */
@Slf4j
@Component
public class StuckTipRecoveryJob {

	private final ArTipRepository arTipRepository;
	private final TipPublishStore publishStore;
	private final TipProperties tipProperties;
	private final TransactionTemplate transactionTemplate;
	private final Clock clock;

	public StuckTipRecoveryJob(ArTipRepository arTipRepository, TipPublishStore publishStore,
		TipProperties tipProperties, PlatformTransactionManager transactionManager, Clock clock) {
		this.arTipRepository = arTipRepository;
		this.publishStore = publishStore;
		this.tipProperties = tipProperties;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${yeogirok.tip.stuck-check-interval}",
		initialDelayString = "${yeogirok.tip.stuck-check-interval}")
	public void recover() {
		Instant cutoff = clock.instant().minus(tipProperties.stuckRegisteringTimeout());
		for (Long tipId : arTipRepository.findStuckRegisteringIds(cutoff)) {
			try {
				recover(tipId, cutoff);
			} catch (RuntimeException e) {
				log.warn("게시가 끝나지 않은 팁 정리에 실패해 다음 주기에 다시 시도합니다 tipId={}: {}", tipId,
					e.getClass().getSimpleName());
			}
		}
	}

	/** 행을 잠가 다시 확인한다. 그사이 게시·삭제됐으면 건드리지 않는다. */
	private void recover(Long tipId, Instant cutoff) {
		boolean failed = Boolean.TRUE.equals(transactionTemplate.execute(status -> arTipRepository
			.findForUpdateById(tipId)
			.filter(tip -> tip.getStatus() == TipStatus.REGISTERING && !tip.isDeleted()
				&& tip.getUpdatedAt().isBefore(cutoff))
			.map(tip -> {
				tip.fail(clock.instant());
				return true;
			})
			.orElse(false)));
		if (!failed) {
			return;
		}
		publishStore.find(tipId).ifPresent(state -> publishStore.save(tipId,
			state.failed(ExceptionType.SERVER_ERROR.getCode(), "게시가 제한 시간 안에 끝나지 않았습니다.", true)));
		log.warn("게시가 끝나지 않은 팁을 실패로 돌렸습니다 tipId={}", tipId);
	}
}

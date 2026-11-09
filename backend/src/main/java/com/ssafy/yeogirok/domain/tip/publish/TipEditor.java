package com.ssafy.yeogirok.domain.tip.publish;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ssafy.yeogirok.domain.tip.entity.ArTip;
import com.ssafy.yeogirok.domain.tip.entity.ArTipImage;
import com.ssafy.yeogirok.domain.tip.entity.TipStatus;
import com.ssafy.yeogirok.domain.tip.publish.TipEditDraft.PhotoChange;
import com.ssafy.yeogirok.domain.tip.repository.ArTipImageRepository;
import com.ssafy.yeogirok.domain.tip.repository.ArTipRepository;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.extern.slf4j.Slf4j;

/**
 * API-054 수정안 적용(비동기). 팁 행을 잠가 삭제 여부·작성자·기준 version 을 다시 본 뒤 한 트랜잭션에서 교체한다.
 * 실패하면 팁은 그대로 두고 작업만 FAILED 다. 끝나면 수정 예약을 푼다.
 */
@Slf4j
@Component
public class TipEditor {

	private final ArTipRepository arTipRepository;
	private final ArTipImageRepository arTipImageRepository;
	private final TipPublishStore publishStore;
	private final TipEditStore editStore;
	private final TransactionTemplate transactionTemplate;
	private final Clock clock;

	public TipEditor(ArTipRepository arTipRepository, ArTipImageRepository arTipImageRepository,
		TipPublishStore publishStore, TipEditStore editStore, PlatformTransactionManager transactionManager,
		Clock clock) {
		this.arTipRepository = arTipRepository;
		this.arTipImageRepository = arTipImageRepository;
		this.publishStore = publishStore;
		this.editStore = editStore;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	@Async
	public void applyAsync(Long tipId, String jobId, String lockToken) {
		apply(tipId, jobId, lockToken);
	}

	public void apply(Long tipId, String jobId, String lockToken) {
		try {
			Optional<TipEditDraft> found = editStore.find(tipId, jobId);
			if (found.isEmpty()) {
				// 수정안이 사라지면 기존 공개본으로 바뀐 내용을 짐작해 복원하지 않고 새 수정 요청을 받는다(노션 054).
				publishStore.save(tipId, TipPublishState.queuedUpdate(jobId, false, false).running()
					.failed(ExceptionType.SERVER_ERROR.getCode(), "수정안을 찾을 수 없습니다. 다시 수정해 주세요.", false));
				return;
			}
			TipEditDraft draft = found.get();
			TipPublishState running = TipPublishState.queuedUpdate(jobId, draft.photoChanged(),
				draft.relocation() != null).running();
			publishStore.save(tipId, running);
			try {
				transactionTemplate.executeWithoutResult(status -> replace(tipId, draft, clock.instant()));
				publishStore.save(tipId, running.succeeded());
				editStore.delete(tipId, jobId);
			} catch (BusinessException e) {
				publishStore.save(tipId, running.failed(e.getExceptionType().getCode(), e.getMessage(), false));
			} catch (RuntimeException e) {
				log.error("팁 수정 실패 tipId={} jobId={}", tipId, jobId, e);
				publishStore.save(tipId, running.failed(ExceptionType.SERVER_ERROR.getCode(),
					ExceptionType.SERVER_ERROR.getMessage(), true));
			}
		} finally {
			editStore.release(tipId, lockToken);
		}
	}

	/** 삭제됐으면 T012, 그사이 version 이 바뀌었거나 공개 팁이 아니면 C004(다시 조회해 새로 수정). */
	private void replace(Long tipId, TipEditDraft draft, Instant now) {
		ArTip tip = arTipRepository.findForUpdateById(tipId)
			.filter(found -> !found.isDeleted() && found.getAuthorId().equals(draft.authorId()))
			.orElseThrow(() -> new BusinessException(ExceptionType.TIP_NOT_EXIST));
		if (tip.getVersion() != draft.baseVersion() || tip.getStatus() != TipStatus.PUBLISHED) {
			throw new BusinessException(ExceptionType.VERSION_MISMATCH);
		}
		tip.edit(draft.category(), draft.body(), now);
		if (draft.relocation() != null) {
			TipEditDraft.Relocation relocation = draft.relocation();
			tip.relocate(relocation.location(), relocation.locationPolicyVersion(), relocation.spatialReference(),
				relocation.placement());
		}
		if (draft.photoChanged()) {
			// tip_id 가 UNIQUE 라 기존 행을 먼저 지우고 반영한 뒤 새 행을 넣는다(새 imageId).
			arTipImageRepository.deleteByTipId(tipId);
			arTipImageRepository.flush();
			if (draft.photo() == PhotoChange.REPLACE) {
				arTipImageRepository.save(ArTipImage.of(tipId, draft.imagePath(), now));
			}
		}
	}
}

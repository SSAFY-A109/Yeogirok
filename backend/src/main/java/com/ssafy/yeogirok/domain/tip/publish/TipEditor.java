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
 *
 * <p>작업 상태는 Redis 의 최신 jobId 가 자기 것일 때만 저장한다. 실행기 대기로 예약 수명(30초)이 지나 다음 054 가 새 작업을
 * 만들었다면, 늦게 돈 이 작업은 팁도 작업 상태도 건드리지 않고 끝난다(PR #64 리뷰).
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
				publishStore.saveIfCurrent(tipId, TipPublishState.queuedUpdate(jobId, false, false).running()
					.failed(ExceptionType.SERVER_ERROR.getCode(), "수정안을 찾을 수 없습니다. 다시 수정해 주세요.", false));
				return;
			}
			TipEditDraft draft = found.get();
			TipPublishState running = TipPublishState.queuedUpdate(jobId, draft.photoChanged(),
				draft.relocation() != null).running();
			if (!publishStore.saveIfCurrent(tipId, running)) {
				skipStale(tipId, jobId);
				return;
			}
			try {
				if (!transactionTemplate.execute(status -> replace(tipId, jobId, draft, clock.instant()))) {
					skipStale(tipId, jobId);
					return;
				}
				publishStore.saveIfCurrent(tipId, running.succeeded());
				editStore.delete(tipId, jobId);
			} catch (BusinessException e) {
				publishStore.saveIfCurrent(tipId, running.failed(e.getExceptionType().getCode(), e.getMessage(), false));
			} catch (RuntimeException e) {
				log.error("팁 수정 실패 tipId={} jobId={}", tipId, jobId, e);
				publishStore.saveIfCurrent(tipId, running.failed(ExceptionType.SERVER_ERROR.getCode(),
					ExceptionType.SERVER_ERROR.getMessage(), true));
			}
		} finally {
			editStore.release(tipId, lockToken);
		}
	}

	/** 다시 적용할 일이 없는 수정안은 지운다(사진 정리 대상이 되게). */
	private void skipStale(Long tipId, String jobId) {
		log.warn("지난 수정 작업이라 건너뜀 tipId={} jobId={}", tipId, jobId);
		editStore.delete(tipId, jobId);
	}

	/**
	 * 삭제됐으면 T012, 그사이 version 이 바뀌었거나 공개 팁이 아니면 C004(다시 조회해 새로 수정).
	 * @return 최신 작업이 아니라 건너뛰었으면 false
	 */
	private boolean replace(Long tipId, String jobId, TipEditDraft draft, Instant now) {
		ArTip tip = arTipRepository.findForUpdateById(tipId)
			.filter(found -> !found.isDeleted() && found.getAuthorId().equals(draft.authorId()))
			.orElseThrow(() -> new BusinessException(ExceptionType.TIP_NOT_EXIST));
		// 잠근 뒤에 읽어야 같은 팁의 다른 작업과 순서가 맞는다(TipPublisher 와 같음).
		if (publishStore.find(tipId).filter(state -> state.jobId().equals(jobId)).isEmpty()) {
			return false;
		}
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
		return true;
	}
}

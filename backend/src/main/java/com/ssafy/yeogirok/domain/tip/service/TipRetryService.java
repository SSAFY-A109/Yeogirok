package com.ssafy.yeogirok.domain.tip.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.ssafy.yeogirok.domain.tip.dto.TipRetryRequest.TipRetryCommand;
import com.ssafy.yeogirok.domain.tip.dto.TipRetryResponse;
import com.ssafy.yeogirok.domain.tip.entity.ArTip;
import com.ssafy.yeogirok.domain.tip.entity.TipBody;
import com.ssafy.yeogirok.domain.tip.entity.TipStatus;
import com.ssafy.yeogirok.domain.tip.publish.TipEditDraft;
import com.ssafy.yeogirok.domain.tip.publish.TipEditDraft.PhotoChange;
import com.ssafy.yeogirok.domain.tip.publish.TipEditStore;
import com.ssafy.yeogirok.domain.tip.publish.TipEditor;
import com.ssafy.yeogirok.domain.tip.publish.TipPublishRequested;
import com.ssafy.yeogirok.domain.tip.publish.TipPublishState;
import com.ssafy.yeogirok.domain.tip.publish.TipPublishState.Operation;
import com.ssafy.yeogirok.domain.tip.publish.TipPublishState.OperationStatus;
import com.ssafy.yeogirok.domain.tip.publish.TipPublishStore;
import com.ssafy.yeogirok.domain.tip.repository.ArTipRepository;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

/**
 * API-051 실패한 팁 재시도. 멱등키는 컨트롤러가 이 밖에서 처리한다.
 * FAILED 팁은 최초 게시(CREATE)를, 공개 팁의 실패한 수정(UPDATE)은 남은 수정안을 다시 실행한다. 실패한 작업이 없는 공개 팁은 200.
 * 작업이 진행 중이면 T010(11/9 결정).
 */
@Service
public class TipRetryService {

	/** 할 일이 없던 요청의 멱등키 결과 ID. */
	public static final String NOTHING_TO_RETRY = "-";

	private final MyTipFinder myTipFinder;
	private final ArTipRepository arTipRepository;
	private final TipPhotoUploads tipPhotoUploads;
	private final TipPhotoClaims tipPhotoClaims;
	private final TipRelocations tipRelocations;
	private final TipPublishStore publishStore;
	private final TipEditStore editStore;
	private final TipEditor tipEditor;
	private final ApplicationEventPublisher events;
	private final TransactionTemplate transactionTemplate;
	private final Clock clock;

	public TipRetryService(MyTipFinder myTipFinder, ArTipRepository arTipRepository, TipPhotoUploads tipPhotoUploads,
		TipPhotoClaims tipPhotoClaims, TipRelocations tipRelocations, TipPublishStore publishStore, TipEditStore editStore,
		TipEditor tipEditor, ApplicationEventPublisher events, PlatformTransactionManager transactionManager, Clock clock) {
		this.myTipFinder = myTipFinder;
		this.arTipRepository = arTipRepository;
		this.tipPhotoUploads = tipPhotoUploads;
		this.tipPhotoClaims = tipPhotoClaims;
		this.tipRelocations = tipRelocations;
		this.publishStore = publishStore;
		this.editStore = editStore;
		this.tipEditor = tipEditor;
		this.events = events;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	/** 남의 팁 T002, 없거나 삭제 T012. */
	public TipRetryResponse retry(Long userId, Long tipId, TipRetryCommand command) {
		Instant now = clock.instant();
		ArTip tip = myTipFinder.getOwn(tipId, userId);
		Optional<TipPublishState> state = publishStore.find(tipId);
		return switch (tip.getStatus()) {
			case FAILED -> retryCreate(userId, tip, state, command, now);
			case PUBLISHED -> state.filter(found -> found.operation() == Operation.UPDATE)
				.map(found -> switch (found.operationStatus()) {
					case FAILED -> retryUpdate(userId, tip, found, command, now);
					case QUEUED, RUNNING -> throw inProgress();
					case SUCCEEDED -> TipRetryResponse.done(tipId, TipStatus.PUBLISHED);
				})
				.orElseGet(() -> TipRetryResponse.done(tipId, TipStatus.PUBLISHED));
			default -> throw inProgress();
		};
	}

	/** 같은 멱등키로 다시 보낸 요청. 그때의 작업 상태를 준다(Redis 에 없으면 null). */
	@Transactional(readOnly = true)
	public TipRetryResponse getRetried(Long userId, Long tipId, String resultId) {
		ArTip tip = myTipFinder.getOwn(tipId, userId);
		if (NOTHING_TO_RETRY.equals(resultId)) {
			return TipRetryResponse.done(tipId, tip.getStatus());
		}
		Optional<TipPublishState> state = publishStore.find(tipId).filter(found -> found.jobId().equals(resultId));
		return new TipRetryResponse(tipId, resultId, tip.getStatus(),
			state.map(TipPublishState::operation).orElse(null),
			state.map(TipPublishState::operationStatus).orElse(null));
	}

	/**
	 * 저장된 본문이 유효하지 않으면 C003. 사진은 replacementUploadIds 가 없으면 지난 게시 작업의 사진을 다시 쓰고, 그 정보가
	 * Redis 에 없으면 T008. 공간 참조가 만료됐으면 새 재배치(거리 검사 없음, 049 와 같음)를 받고 없으면 T007.
	 */
	private TipRetryResponse retryCreate(Long userId, ArTip tip, Optional<TipPublishState> state,
		TipRetryCommand command, Instant now) {
		TipBody.normalize(tip.getBody());
		String imagePath = command.replacementUploadIds() != null
			? tipPhotoUploads.confirmedPath(userId, command.replacementUploadIds())
			: state.filter(found -> found.operation() == Operation.CREATE)
				.orElseThrow(() -> new BusinessException(ExceptionType.TIP_RETRY_UNAVAILABLE,
					"지난 게시 작업 정보가 없습니다. replacementUploadIds 로 사진을 다시 지정해 주세요."))
				.imagePath();
		TipEditDraft.Relocation relocation = command.relocating() ? tipRelocations.validate(tip, command.location(),
			command.spatialReference(), command.placement(), now, false) : null;
		if (relocation == null) {
			TipRelocations.checkNotExpired(tip.spatialReference(), now);
		}

		String jobId = UUID.randomUUID().toString();
		// 사진을 새로 고른 재시도만 표시를 건다. 지난 작업의 사진은 이미 이 팁 것이다.
		tipPhotoClaims.claimWhile(tip.getId(), command.replacementUploadIds(),
			() -> transactionTemplate.executeWithoutResult(status -> {
			// 행을 잠가 다시 읽는다. 동시에 온 다른 재시도가 먼저 바꿨으면 진행 중(T010)이다.
			ArTip locked = arTipRepository.findForUpdateById(tip.getId())
				.filter(found -> !found.isDeleted())
				.orElseThrow(() -> new BusinessException(ExceptionType.TIP_NOT_EXIST));
			if (locked.getStatus() != TipStatus.FAILED) {
				throw inProgress();
			}
			locked.retryCreate(now);
			if (relocation != null) {
				locked.relocate(relocation.location(), relocation.locationPolicyVersion(),
					relocation.spatialReference(), relocation.placement());
			}
			publishStore.save(tip.getId(), TipPublishState.queued(jobId, imagePath));
			events.publishEvent(new TipPublishRequested(tip.getId(), jobId, imagePath));
		}));
		return new TipRetryResponse(tip.getId(), jobId, TipStatus.REGISTERING, Operation.CREATE,
			OperationStatus.QUEUED);
	}

	/**
	 * 수정안이 없으면 T008, 기준 version 이 바뀌었으면 C004. replacementUploadIds·새 재배치가 있으면 수정안을 그것으로 바꾼다.
	 * 수정안의 재배치는 위치 증거(C009)·공간 참조(T007)가 아직 유효해야 한다. 다른 작업이 예약돼 있으면 T010.
	 */
	private TipRetryResponse retryUpdate(Long userId, ArTip tip, TipPublishState failed, TipRetryCommand command,
		Instant now) {
		TipEditDraft draft = editStore.find(tip.getId(), failed.jobId())
			.orElseThrow(() -> new BusinessException(ExceptionType.TIP_RETRY_UNAVAILABLE,
				"수정안이 없습니다. 다시 수정해 주세요."));
		if (draft.baseVersion() != tip.getVersion()) {
			throw new BusinessException(ExceptionType.VERSION_MISMATCH);
		}
		TipBody.normalize(draft.body() != null ? draft.body() : tip.getBody());
		PhotoChange photo = draft.photo();
		String imagePath = draft.imagePath();
		if (command.replacementUploadIds() != null) {
			imagePath = tipPhotoUploads.confirmedPath(userId, command.replacementUploadIds());
			photo = imagePath == null ? PhotoChange.REMOVE : PhotoChange.REPLACE;
		}
		TipEditDraft.Relocation relocation = draft.relocation();
		if (command.relocating()) {
			relocation = tipRelocations.validate(tip, command.location(), command.spatialReference(),
				command.placement(), now, true);
		} else if (relocation != null) {
			tipRelocations.checkStillValid(relocation, now);
		}
		TipEditDraft retried = new TipEditDraft(draft.authorId(), draft.baseVersion(), draft.category(), draft.body(),
			photo, imagePath, relocation);

		String lockToken = editStore.reserve(tip.getId()).orElseThrow(TipRetryService::inProgress);
		String jobId = UUID.randomUUID().toString();
		try {
			tipPhotoClaims.claimWhile(tip.getId(), command.replacementUploadIds(), () -> {
				editStore.save(tip.getId(), jobId, retried);
				publishStore.save(tip.getId(), TipPublishState.queuedUpdate(jobId, retried.photoChanged(),
					retried.relocation() != null));
				editStore.delete(tip.getId(), failed.jobId());
			});
		} catch (RuntimeException e) {
			editStore.release(tip.getId(), lockToken);
			throw e;
		}
		tipEditor.applyAsync(tip.getId(), jobId, lockToken);
		return new TipRetryResponse(tip.getId(), jobId, TipStatus.PUBLISHED, Operation.UPDATE, OperationStatus.QUEUED);
	}

	private static BusinessException inProgress() {
		return new BusinessException(ExceptionType.TIP_EDIT_IN_PROGRESS);
	}
}

package com.ssafy.yeogirok.domain.tip.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.tip.dto.TipUpdateRequest.TipUpdateCommand;
import com.ssafy.yeogirok.domain.tip.dto.TipUpdateResponse;
import com.ssafy.yeogirok.domain.tip.entity.ArTip;
import com.ssafy.yeogirok.domain.tip.entity.ArTipCategory;
import com.ssafy.yeogirok.domain.tip.entity.TipBody;
import com.ssafy.yeogirok.domain.tip.entity.TipStatus;
import com.ssafy.yeogirok.domain.tip.publish.TipEditDraft;
import com.ssafy.yeogirok.domain.tip.publish.TipEditDraft.PhotoChange;
import com.ssafy.yeogirok.domain.tip.publish.TipEditStore;
import com.ssafy.yeogirok.domain.tip.publish.TipEditor;
import com.ssafy.yeogirok.domain.tip.publish.TipPublishState;
import com.ssafy.yeogirok.domain.tip.publish.TipPublishState.Operation;
import com.ssafy.yeogirok.domain.tip.publish.TipPublishState.OperationStatus;
import com.ssafy.yeogirok.domain.tip.publish.TipPublishStore;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;

/**
 * API-054 내 팁 수정. 멱등키는 컨트롤러가 이 밖에서 처리한다. 요청 안에서는 검증과 수정안 저장만 하고 팁은 바꾸지 않는다.
 * 교체는 {@link TipEditor} 가 비동기로 한다. 본문·사진·종류는 원격, 재배치는 기존 팁 5m 안에서만(11/9 결정).
 */
@Service
@RequiredArgsConstructor
public class TipUpdateService {

	private final MyTipFinder myTipFinder;
	private final TipPhotoUploads tipPhotoUploads;
	private final TipRelocations tipRelocations;
	private final TipEditStore editStore;
	private final TipPublishStore publishStore;
	private final TipEditor tipEditor;
	private final Clock clock;

	/**
	 * 남의 팁 T002, 없거나 삭제 T012, 공개 팁이 아니면(게시 중·실패) T010, version 불일치 C004, 바꿀 게 없으면 C003,
	 * 다른 수정이 진행 중이면 T010. 값 오류는 049 와 같다(C003·T007·C008~C011·M004~M007), 재배치가 5m 밖이면 T004.
	 */
	public TipUpdateResponse update(Long userId, Long tipId, TipUpdateCommand command) {
		Instant now = clock.instant();
		ArTip tip = myTipFinder.getOwn(tipId, userId);
		if (tip.getStatus() != TipStatus.PUBLISHED) {
			throw new BusinessException(ExceptionType.TIP_EDIT_IN_PROGRESS,
				"게시 중이거나 실패한 팁은 등록 재시도(API-051)로 고칩니다.");
		}
		if (tip.getVersion() != command.version()) {
			throw new BusinessException(ExceptionType.VERSION_MISMATCH);
		}
		if (!command.hasChanges()) {
			throw new BusinessException(ExceptionType.NOT_ALLOWED_VALUE, "바꿀 항목이 없습니다.");
		}
		TipEditDraft draft = new TipEditDraft(userId, tip.getVersion(),
			command.category() == null ? null : ArTipCategory.fromCode(command.category()),
			command.body() == null ? null : TipBody.normalize(command.body()),
			photoChange(command), photoPath(userId, command), relocation(tip, command, now));

		String lockToken = editStore.reserve(tipId)
			.orElseThrow(() -> new BusinessException(ExceptionType.TIP_EDIT_IN_PROGRESS));
		String jobId = UUID.randomUUID().toString();
		try {
			editStore.save(tipId, jobId, draft);
			publishStore.save(tipId, TipPublishState.queuedUpdate(jobId, draft.photoChanged(),
				draft.relocation() != null));
		} catch (RuntimeException e) {
			editStore.release(tipId, lockToken);
			throw e;
		}
		tipEditor.applyAsync(tipId, jobId, lockToken);
		return new TipUpdateResponse(tipId, jobId, tip.getStatus(), Operation.UPDATE, OperationStatus.QUEUED,
			tip.getVersion());
	}

	/** 같은 멱등키로 다시 보낸 요청. 그사이 삭제했으면 T012. */
	@Transactional(readOnly = true)
	public TipUpdateResponse getQueued(Long userId, Long tipId, String jobId) {
		ArTip tip = myTipFinder.getOwn(tipId, userId);
		OperationStatus operationStatus = publishStore.find(tipId)
			.filter(state -> state.jobId().equals(jobId))
			.map(TipPublishState::operationStatus)
			.orElse(null);
		return new TipUpdateResponse(tipId, jobId, tip.getStatus(), Operation.UPDATE, operationStatus,
			tip.getVersion());
	}

	private static PhotoChange photoChange(TipUpdateCommand command) {
		if (command.uploadIds() == null) {
			return PhotoChange.KEEP;
		}
		return command.uploadIds().isEmpty() ? PhotoChange.REMOVE : PhotoChange.REPLACE;
	}

	private String photoPath(Long userId, TipUpdateCommand command) {
		return command.uploadIds() == null ? null : tipPhotoUploads.confirmedPath(userId, command.uploadIds());
	}

	private TipEditDraft.Relocation relocation(ArTip tip, TipUpdateCommand command, Instant now) {
		return command.relocating() ? tipRelocations.validate(tip, command.location(), command.spatialReference(),
			command.placement(), now, true) : null;
	}
}

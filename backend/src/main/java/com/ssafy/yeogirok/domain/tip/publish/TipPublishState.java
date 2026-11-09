package com.ssafy.yeogirok.domain.tip.publish;

/**
 * 팁 게시·수정 작업 상태(Redis {@code tip-publish:{tipId}}, 노션 Redis 설계). 공개 여부의 기준은 RDB ar_tips.status 다.
 * 팁마다 마지막 작업 하나(최초 게시 CREATE 또는 수정 UPDATE)를 둔다.
 *
 * @param error     실패하면 {@code {code, message}}, 아니면 null
 * @param imagePath 최초 게시(CREATE)에 붙일 사진 확정 경로. 051 재시도가 기존 사진을 다시 쓰려고 둔다. 050 응답에는 나가지 않는다
 */
public record TipPublishState(
	String jobId,
	Operation operation,
	OperationStatus operationStatus,
	Steps steps,
	Error error,
	boolean retryable,
	String imagePath
) {

	public enum Operation { CREATE, UPDATE }

	public enum OperationStatus { QUEUED, RUNNING, SUCCEEDED, FAILED }

	public enum StepStatus { PENDING, SUCCEEDED, FAILED, SKIPPED }

	/** 사진 연결(media)·공간 참조(spatial)·본문(content). spatial·content 는 049 요청 안에서 검증을 마친다. */
	public record Steps(StepStatus media, StepStatus spatial, StepStatus content) {
	}

	public record Error(String code, String message) {
	}

	/** @param imagePath 사진이 없으면 null 이고 media 는 SKIPPED */
	public static TipPublishState queued(String jobId, String imagePath) {
		return new TipPublishState(jobId, Operation.CREATE, OperationStatus.QUEUED,
			new Steps(imagePath != null ? StepStatus.PENDING : StepStatus.SKIPPED, StepStatus.SUCCEEDED,
				StepStatus.SUCCEEDED),
			null, false, imagePath);
	}

	/**
	 * API-054 수정. 본문·종류는 요청 안에서 검증을 마친다. 재배치가 없으면 spatial, 사진을 바꾸지 않으면 media 가 SKIPPED.
	 */
	public static TipPublishState queuedUpdate(String jobId, boolean photoChanged, boolean relocated) {
		return new TipPublishState(jobId, Operation.UPDATE, OperationStatus.QUEUED,
			new Steps(photoChanged ? StepStatus.PENDING : StepStatus.SKIPPED,
				relocated ? StepStatus.SUCCEEDED : StepStatus.SKIPPED, StepStatus.SUCCEEDED),
			null, false, null);
	}

	public TipPublishState running() {
		return new TipPublishState(jobId, operation, OperationStatus.RUNNING, steps, null, false, imagePath);
	}

	public TipPublishState succeeded() {
		return new TipPublishState(jobId, operation, OperationStatus.SUCCEEDED, media(StepStatus.SUCCEEDED), null, false,
			imagePath);
	}

	/** @param retryable 같은 작업을 다시 시도할 수 있는지(051). 버전 충돌·삭제처럼 다시 해도 안 되는 실패는 false */
	public TipPublishState failed(String code, String message, boolean retryable) {
		return new TipPublishState(jobId, operation, OperationStatus.FAILED, media(StepStatus.FAILED),
			new Error(code, message), retryable, imagePath);
	}

	/** 아직 끝나지 않은(PENDING) media 단계만 바꾼다. */
	private Steps media(StepStatus status) {
		return steps.media() == StepStatus.PENDING ? new Steps(status, steps.spatial(), steps.content()) : steps;
	}
}

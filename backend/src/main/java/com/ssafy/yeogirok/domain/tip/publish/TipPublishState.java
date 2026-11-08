package com.ssafy.yeogirok.domain.tip.publish;

/**
 * 팁 게시 작업 상태(Redis {@code tip-publish:{tipId}}, 노션 Redis 설계). 공개 여부의 기준은 RDB ar_tips.status 다.
 * 이번 범위의 작업은 최초 게시(CREATE)뿐이다.
 *
 * @param error 실패하면 {@code {code, message}}, 아니면 null
 */
public record TipPublishState(
	String jobId,
	Operation operation,
	OperationStatus operationStatus,
	Steps steps,
	Error error,
	boolean retryable
) {

	public enum Operation { CREATE }

	public enum OperationStatus { QUEUED, RUNNING, SUCCEEDED, FAILED }

	public enum StepStatus { PENDING, SUCCEEDED, FAILED, SKIPPED }

	/** 사진 연결(media)·공간 참조(spatial)·본문(content). spatial·content 는 049 요청 안에서 검증을 마친다. */
	public record Steps(StepStatus media, StepStatus spatial, StepStatus content) {
	}

	public record Error(String code, String message) {
	}

	/** 사진이 없으면 media 는 SKIPPED. */
	public static TipPublishState queued(String jobId, boolean hasPhoto) {
		return new TipPublishState(jobId, Operation.CREATE, OperationStatus.QUEUED,
			new Steps(hasPhoto ? StepStatus.PENDING : StepStatus.SKIPPED, StepStatus.SUCCEEDED, StepStatus.SUCCEEDED),
			null, false);
	}

	public TipPublishState running() {
		return new TipPublishState(jobId, operation, OperationStatus.RUNNING, steps, null, false);
	}

	public TipPublishState succeeded() {
		return new TipPublishState(jobId, operation, OperationStatus.SUCCEEDED, media(StepStatus.SUCCEEDED), null, false);
	}

	public TipPublishState failed(String code, String message) {
		return new TipPublishState(jobId, operation, OperationStatus.FAILED, media(StepStatus.FAILED),
			new Error(code, message), true);
	}

	/** 아직 끝나지 않은(PENDING) media 단계만 바꾼다. */
	private Steps media(StepStatus status) {
		return steps.media() == StepStatus.PENDING ? new Steps(status, steps.spatial(), steps.content()) : steps;
	}
}

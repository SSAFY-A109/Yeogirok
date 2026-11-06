package com.ssafy.yeogirok.domain.schedule.draft;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.ssafy.yeogirok.domain.job.Job;
import com.ssafy.yeogirok.domain.job.JobError;
import com.ssafy.yeogirok.domain.job.JobStatus;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.schedule.ScheduleOptions.DayOption;
import com.ssafy.yeogirok.domain.schedule.ScheduleOptions.NamedPoint;
import com.ssafy.yeogirok.domain.schedule.travel.TravelLeg;

/**
 * 일정 초안(Redis {@code schedule-draft:{draftKey}}). API-037 이 만들고 038 이 보여 주고 084 가 다시 계산하며 039 가
 * 확정 일정으로 저장한다. 초안은 확정 일정을 바꾸지 않는다.
 *
 * <ul>
 *   <li>status: 처음 만드는 중이거나 084 로 다시 계산하는 중이면 RUNNING, 계산을 마쳤으면 SUCCEEDED, 처음 만들기에
 *   실패했으면 FAILED. 084 가 실패하면 마지막 성공 내용 그대로 SUCCEEDED 로 돌아간다.</li>
 *   <li>draftVersion: 처음 계산을 마치면 1, 084 가 성공할 때마다 1 씩 오른다. 계산 전에는 0.</li>
 * </ul>
 *
 * @param baseVersion   계산의 기준이 된 여행방 version
 * @param transportMode 계산에 쓴 이동 수단(여행방 값)
 * @param days          일차별 시작·종료 시각(여행방의 모든 날짜)
 * @param items         배치·미배치 항목 전체. itemId 순서로 둔다
 * @param endLeg        마지막 날 마지막 항목 → 도착지 구간. 없으면 null
 * @param error         처음 만들기에 실패했을 때만
 * @param deadline      RUNNING 이 이 시각을 넘기면 서버가 처리 도중 재시작된 것으로 본다
 */
public record ScheduleDraft(String draftKey, Long roomId, Long requesterId, JobStatus status, int baseVersion,
	int draftVersion, TransportMode transportMode, List<DayOption> days, NamedPoint startPoint, NamedPoint endPoint,
	List<DraftItem> items, TravelLeg endLeg, JobError error, Instant createdAt, Instant updatedAt,
	Instant deadline) {

	public ScheduleDraft {
		Objects.requireNonNull(draftKey, "draftKey");
		Objects.requireNonNull(roomId, "roomId");
		Objects.requireNonNull(status, "status");
		days = days == null ? List.of() : List.copyOf(days);
		items = items == null ? List.of() : List.copyOf(items);
	}

	/** 계산 전 초안. 조건만 담는다. */
	public static ScheduleDraft start(String draftKey, Long roomId, Long requesterId, int baseVersion,
		TransportMode transportMode, List<DayOption> days, NamedPoint startPoint, NamedPoint endPoint,
		List<DraftItem> items, Instant now, Duration runTimeout) {
		return new ScheduleDraft(draftKey, roomId, requesterId, JobStatus.RUNNING, baseVersion, 0, transportMode, days,
			startPoint, endPoint, items, null, null, now, now, now.plus(runTimeout));
	}

	/** 계산을 마친 새 내용. draftVersion 이 1 오른다. */
	public ScheduleDraft computed(List<DayOption> newDays, NamedPoint newStart, NamedPoint newEnd,
		List<DraftItem> newItems, TravelLeg newEndLeg, Instant now) {
		return new ScheduleDraft(draftKey, roomId, requesterId, JobStatus.SUCCEEDED, baseVersion, draftVersion + 1,
			transportMode, newDays, newStart, newEnd, newItems, newEndLeg, null, createdAt, now, null);
	}

	/** 다시 계산을 시작한다(API-084). 내용은 마지막 성공 그대로 둔다. */
	public ScheduleDraft recalculating(Instant now, Duration runTimeout) {
		return withStatus(JobStatus.RUNNING, null, now, now.plus(runTimeout));
	}

	/** 처음 만들기에 실패했다. */
	public ScheduleDraft failed(JobError failure, Instant now) {
		return withStatus(JobStatus.FAILED, failure, now, null);
	}

	/** 다시 계산이 실패했다. 마지막 성공 내용으로 돌아간다. */
	public ScheduleDraft recalculationFailed(Instant now) {
		return withStatus(JobStatus.SUCCEEDED, null, now, null);
	}

	/**
	 * 지금 보여 줄 상태. 계산 중인데 기한이 지났으면 처음 만들기는 실패로, 다시 계산은 마지막 성공으로 본다. 저장값은 바꾸지
	 * 않는다.
	 */
	public ScheduleDraft asOf(Instant now) {
		if (status != JobStatus.RUNNING || deadline == null || !now.isAfter(deadline)) {
			return this;
		}
		return draftVersion == 0 ? failed(Job.TIMED_OUT, updatedAt) : recalculationFailed(updatedAt);
	}

	private ScheduleDraft withStatus(JobStatus newStatus, JobError newError, Instant now, Instant newDeadline) {
		return new ScheduleDraft(draftKey, roomId, requesterId, newStatus, baseVersion, draftVersion, transportMode,
			days, startPoint, endPoint, items, endLeg, newError, createdAt, now, newDeadline);
	}
}

package com.ssafy.yeogirok.domain.schedule.draft;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import com.ssafy.yeogirok.domain.job.JobStatus;
import com.ssafy.yeogirok.domain.schedule.ScheduleOptions.DayOption;
import com.ssafy.yeogirok.domain.schedule.dto.DraftResponse;
import com.ssafy.yeogirok.domain.schedule.service.ScheduleViews;
import com.ssafy.yeogirok.domain.schedule.service.ScheduleViews.DayParts;
import com.ssafy.yeogirok.domain.schedule.service.ScheduleViews.Timed;
import com.ssafy.yeogirok.domain.schedule.travel.TravelLeg;

/** 저장한 초안으로 API-038 응답을 만든다. 외부 호출 없이 저장한 값만 쓴다. */
public final class DraftViews {

	private DraftViews() {
	}

	public static DraftResponse of(ScheduleDraft draft) {
		DraftResponse.Conditions conditions = new DraftResponse.Conditions(
			draft.days().stream().map(day -> new DraftResponse.DayCondition(day.date(), day.startTime(), day.endTime()))
				.toList(),
			ScheduleViews.point(draft.startPoint()), ScheduleViews.point(draft.endPoint()));
		boolean computed = draft.draftVersion() > 0 && draft.status() != JobStatus.FAILED;
		return new DraftResponse(draft.status(), draft.baseVersion(), draft.draftVersion(), conditions,
			computed ? schedule(draft) : null, draft.transportMode(), computed ? source(draft) : null, draft.error());
	}

	private static DraftResponse.Schedule schedule(ScheduleDraft draft) {
		DayOption last = draft.days().getLast();
		List<DraftResponse.Day> days = draft.days().stream().map(day -> {
			List<DraftItem> items = draft.items().stream()
				.filter(item -> day.date().equals(item.visitDate()))
				.sorted(Comparator.comparing(DraftItem::order))
				.toList();
			TravelLeg endLeg = day.equals(last) && !items.isEmpty() ? draft.endLeg() : null;
			DayParts parts = ScheduleViews.dayParts(day.date(), day, items.stream()
				.map(item -> new Timed((long)item.itemId(), item.arrivalAt(), item.departureAt(),
					item.legFromPrevious()))
				.toList(), endLeg, draft.transportMode());
			return new DraftResponse.Day(day.date(), day.startTime(), day.endTime(), items.stream()
				.map(item -> new DraftResponse.Item(item.itemId(), item.roomPlaceId(), item.order(), item.arrivalAt(),
					item.departureAt(), item.stayMinutes(), item.fixed()))
				.toList(), parts.legs(), parts.warnings());
		}).toList();
		List<DraftResponse.Unassigned> unassigned = draft.items().stream()
			.filter(item -> !item.placed())
			.map(item -> new DraftResponse.Unassigned(item.itemId(), item.roomPlaceId()))
			.toList();
		return new DraftResponse.Schedule(days, unassigned, List.of(), ScheduleViews.point(draft.startPoint()),
			ScheduleViews.point(draft.endPoint()));
	}

	/** 이동 시간을 받은 첫 구간의 출처. */
	private static String source(ScheduleDraft draft) {
		return Stream.concat(draft.items().stream().map(DraftItem::legFromPrevious), Stream.of(draft.endLeg()))
			.filter(Objects::nonNull)
			.filter(TravelLeg::isKnown)
			.map(TravelLeg::source)
			.findFirst()
			.orElse(null);
	}
}

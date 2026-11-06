package com.ssafy.yeogirok.domain.schedule.draft;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.domain.schedule.ScheduleOptions.DayOption;
import com.ssafy.yeogirok.domain.schedule.entity.ScheduleItem;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

/** 초안 API(037·039·084)가 함께 쓰는 값 규칙. */
public final class DraftRules {

	private DraftRules() {
	}

	/** 날짜가 정해지고 이동 수단이 있어야 초안을 만든다. 아니면 C003. */
	public static void requirePlannable(TravelRoom room) {
		if (room.getStartDate() == null) {
			throw notAllowed("여행 날짜가 정해지지 않은 방입니다.");
		}
		if (room.getTransportMode() == null) {
			throw notAllowed("여행방 이동 수단이 정해지지 않았습니다.");
		}
	}

	/**
	 * 여행의 모든 날짜를 중복 없이 담고 날마다 시작 < 종료여야 한다. 아니면 C012.
	 *
	 * @return 날짜 순으로 정렬한 일차
	 */
	public static List<DayOption> checkDays(TravelRoom room, List<DayOption> days) {
		Set<LocalDate> seen = new HashSet<>();
		for (DayOption day : days) {
			if (day.date().isBefore(room.getStartDate()) || day.date().isAfter(room.getEndDate())) {
				throw dateRange("days: 여행 날짜 밖입니다(" + day.date() + ").");
			}
			if (!seen.add(day.date())) {
				throw dateRange("days: 같은 날짜가 두 번 있습니다(" + day.date() + ").");
			}
			if (!day.startTime().isBefore(day.endTime())) {
				throw dateRange("days: 시작 시각은 종료 시각보다 앞이어야 합니다(" + day.date() + ").");
			}
		}
		long tripDays = room.getStartDate().datesUntil(room.getEndDate().plusDays(1)).count();
		if (seen.size() != tripDays) {
			throw dateRange("days: 여행의 모든 날짜가 있어야 합니다.");
		}
		List<DayOption> sorted = new ArrayList<>(days);
		sorted.sort(Comparator.comparing(DayOption::date));
		return sorted;
	}

	/** 1분 단위 1~720분. 음수 SC006, 0·720 초과 C003. */
	public static void checkStay(int minutes, String field) {
		if (minutes < 0) {
			throw new BusinessException(ExceptionType.SCHEDULE_STAY_NEGATIVE, field + ": 음수일 수 없습니다.");
		}
		if (minutes == 0 || minutes > ScheduleItem.MAX_STAY_MINUTES) {
			throw notAllowed("%s: 1~%d분이어야 합니다.".formatted(field, ScheduleItem.MAX_STAY_MINUTES));
		}
	}

	public static void checkVersion(TravelRoom room, int baseVersion) {
		if (room.getVersion() != baseVersion) {
			throw new BusinessException(ExceptionType.VERSION_MISMATCH);
		}
	}

	public static BusinessException notAllowed(String message) {
		return new BusinessException(ExceptionType.NOT_ALLOWED_VALUE, message);
	}

	public static BusinessException dateRange(String message) {
		return new BusinessException(ExceptionType.INVALID_DATE_RANGE, message);
	}
}

import { assertId } from "../../lib/contract";
import { parseLocalDate, parseServerDateTime, type LocalDate } from "../../lib/date";

// 가정(TBD): API-036 여행방 일정 조회의 응답 상세는 명세 미확정이다.
// 이번 범위에서는 W06-E2(날짜를 줄이면 후보로 돌아가는 장소 미리보기)에 필요한 필드만 가정했다.
// 일정이 없으면 빈 성공 응답(data: null)이 온다고 가정한다.

export type RoomScheduleDto = {
  createdAt: string;
  days: { date: string; places: { roomPlaceId: number; name: string }[] }[];
};

export type ScheduleDay = { date: LocalDate; places: { id: number; name: string }[] };
export type RoomSchedule = { createdAt: Date; days: ScheduleDay[] };

export function toRoomSchedule(dto: RoomScheduleDto): RoomSchedule {
  return {
    createdAt: parseServerDateTime(dto.createdAt, "createdAt"),

    days: dto.days.map((day) => {
      return {
        date: parseLocalDate(day.date, "days.date"),

        places: day.places.map((place) => {
          return { id: assertId(place.roomPlaceId, "days.places.roomPlaceId"), name: place.name };
        }),
      };
    }),
  };
}

/** 새 여행 기간 밖으로 빠지는 날의 장소. 기간이 없으면(날짜 미정) 모든 장소가 빠진다. */
export function getPlacesOutsideRange(schedule: RoomSchedule, range: { startDate: LocalDate; endDate: LocalDate } | null): ScheduleDay["places"] {
  return schedule.days
    .filter((day) => {
      return range === null || day.date < range.startDate || day.date > range.endDate;
    })
    .flatMap((day) => {
      return day.places;
    });
}

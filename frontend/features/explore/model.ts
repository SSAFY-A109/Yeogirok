import type { CourseSearchCondition } from "@/features/course/api/course.api";
import { COURSE_DURATIONS, COURSE_TRANSPORT_MODES, COURSE_TRANSPORT_MODE_LABEL, type CourseTransportModeDto } from "@/features/course/model";

export type Room = {
  id: string;
  name: string;
  dates: string;
  memberCount: number;
  placeCount: number;
};

export type AddPlacesInput = { roomId: string; placeIds: string[] };

export type AddPlacesResult = { roomName: string; addedCount: number };

// #region 코스 검색 조건

/** 지역은 서버 지역 코드(regionCode)다. 목록은 공통 지역 기준 데이터에서 받는다. */
export type RegionFilter = "all" | string;

export type DurationFilter = "all" | (typeof COURSE_DURATIONS)[number];

export type TransportFilter = "all" | CourseTransportModeDto;

export type CourseFilters = { region: RegionFilter; duration: DurationFilter; transport: TransportFilter };

export type ConditionGroup = keyof CourseFilters;

export type RegionOption = { code: string; name: string };

export const defaultCourseFilters: CourseFilters = { region: "all", duration: "all", transport: "all" };

export const durationFilterOptions: { value: DurationFilter; label: string }[] = [
  { value: "all", label: "전체" },
  { value: "DAY_TRIP", label: "당일" },
  { value: "ONE_NIGHT_TWO_DAYS", label: "1박 2일" },
  { value: "TWO_NIGHTS_OR_MORE", label: "2박 이상" },
];

export const transportFilterOptions: { value: CourseTransportModeDto; label: string }[] = COURSE_TRANSPORT_MODES.map((mode) => {
  return { value: mode, label: COURSE_TRANSPORT_MODE_LABEL[mode] };
});

const REGION_CODE_PATTERN = /^[0-9A-Za-z_-]{1,20}$/;

export function isDefaultFilters(filters: CourseFilters): boolean {
  return filters.region === "all" && filters.duration === "all" && filters.transport === "all";
}

/** 지역 코드의 표시 이름. 기준 데이터를 아직 못 받았거나 목록에 없으면 "선택한 지역"이다. */
function regionLabel(region: RegionFilter, regions: readonly RegionOption[]): string {
  if (region === "all") {
    return "전체";
  }

  return regions.find((option) => {
    return option.code === region;
  })?.name ?? "선택한 지역";
}

/** 조건 바·최근 검색에 쓰는 그룹별 표시 문구. 선택하지 않은 그룹은 "전체"다. */
export function filterLabels(filters: CourseFilters, regions: readonly RegionOption[]): Record<ConditionGroup, string> {
  return {
    region: regionLabel(filters.region, regions),

    duration: durationFilterOptions.find((option) => {
      return option.value === filters.duration;
    })?.label ?? "전체",

    transport: transportFilterOptions.find((option) => {
      return option.value === filters.transport;
    })?.label ?? "전체",
  };
}

/** 고른 조건만 " · "로 이어 붙인다. 모두 전체면 빈 문자열이다. */
export function describeFilters(filters: CourseFilters, regions: readonly RegionOption[]): string {
  const labels = filterLabels(filters, regions);

  return (Object.keys(labels) as ConditionGroup[]).filter((group) => {
    return filters[group] !== "all";
  }).map((group) => {
    return labels[group];
  }).join(" · ");
}

/** 검색어와 화면 조건을 코스 검색 API 조건으로 바꾼다. "전체"는 보내지 않는다. */
export function toCourseSearchCondition(query: string, filters: CourseFilters): CourseSearchCondition {
  return {
    query: query.trim(),
    regionCode: filters.region === "all" ? null : filters.region,
    duration: filters.duration === "all" ? null : filters.duration,
    transportMode: filters.transport === "all" ? null : filters.transport,
  };
}

function pickOption<T extends string>(value: unknown, options: readonly T[], fallback: T): T {
  return options.find((option) => {
    return option === value;
  }) ?? fallback;
}

/** 라우트 파라미터는 신뢰하지 않고, 알려진 값과 코드 형식만 조건으로 받는다. */
export function filtersFromParams(params: Partial<Record<ConditionGroup, unknown>>): CourseFilters {
  return {
    region: typeof params.region === "string" && REGION_CODE_PATTERN.test(params.region) ? params.region : "all",

    duration: pickOption(params.duration, durationFilterOptions.map((option) => {
      return option.value;
    }), "all"),

    transport: pickOption<TransportFilter>(params.transport, ["all", ...COURSE_TRANSPORT_MODES], "all"),
  };
}

// #endregion

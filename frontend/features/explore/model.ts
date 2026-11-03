export type CategoryId = "NATURE" | "CULTURE" | "SIGHT" | "FOOD" | "CAFE" | "ACTIVITY" | "SHOPPING" | "EVENT";

export type Category = {
  id: CategoryId;
  label: string;
  icon: string;
};

export type FeaturedCourse = {
  id: string;
  title: string;
  placeCount: number;
  duration: string;
  transport: string;
  author: string;
  highlights: { day: number; name: string; tag: string }[];
};

export type RankedPlace = {
  id: string;
  name: string;
  area: string;
  courseCount: number;
  subcategory: string;
  lat: number;
  lng: number;
};

export type CourseSummary = {
  id: string;
  title: string;
  region: string;
  /** 지역 조건에 쓰는 광역 단위(regionOptions 중 하나). */
  province: string;
  placeCount: number;
  duration: string;
  transport: string;
  forkCount: number;
  summary: string;
};

export type CoursePlace = {
  id: string;
  day: number;
  name: string;
  category: CategoryId;
  lat: number;
  lng: number;
};

export type CourseDetail = CourseSummary & {
  description: string;
  travelers: string[];
  places: CoursePlace[];
};

export type HomeData = {
  featured: FeaturedCourse[];
  categories: Category[];
  subcategories: Record<CategoryId, string[]>;
};

export type DayGroup = { day: number; places: CoursePlace[] };

export function groupPlacesByDay(places: CoursePlace[]): DayGroup[] {
  const days = [...new Set(places.map((place) => {
    return place.day;
  }))].sort((a, b) => {
    return a - b;
  });

  return days.map((day) => {
    return { day,

      places: places.filter((place) => {
        return place.day === day;
      }) };
  });
}

/** 선택한 장소의 코스 내 순서(1부터)를 유지한 채 id 목록으로 돌려준다. */
export function orderedSelection(places: CoursePlace[], selected: ReadonlySet<string>): string[] {
  return places.filter((place) => {
    return selected.has(place.id);
  }).map((place) => {
    return place.id;
  });
}

export type Room = {
  id: string;
  name: string;
  dates: string;
  memberCount: number;
  placeCount: number;
};

export type AddPlacesInput = { roomId: string; placeIds: string[] };

export type AddPlacesResult = { roomName: string; addedCount: number };

export const regionOptions = ["서울", "인천·경기", "강원", "충청", "전라", "경상", "부산", "제주"] as const;

export type RegionFilter = "all" | (typeof regionOptions)[number];

export type DurationFilter = "all" | "day" | "one-night" | "two-nights" | "three-nights-plus";

export type TransportFilter = "all" | "car" | "public";

export type CourseFilters = { region: RegionFilter; duration: DurationFilter; transport: TransportFilter };

export type ConditionGroup = keyof CourseFilters;

export const defaultCourseFilters: CourseFilters = { region: "all", duration: "all", transport: "all" };

export const regionFilterOptions: { value: RegionFilter; label: string }[] = [
  { value: "all", label: "전체" },
  ...regionOptions.map((region) => {
    return { value: region, label: region };
  }),
];

export const durationFilterOptions: { value: DurationFilter; label: string }[] = [
  { value: "all", label: "전체" },
  { value: "day", label: "당일" },
  { value: "one-night", label: "1박 2일" },
  { value: "two-nights", label: "2박 3일" },
  { value: "three-nights-plus", label: "3박 이상" },
];

export const transportFilterOptions: { value: Exclude<TransportFilter, "all">; label: string }[] = [
  { value: "car", label: "자차" },
  { value: "public", label: "대중교통" },
];

export function isDefaultFilters(filters: CourseFilters): boolean {
  return filters.region === "all" && filters.duration === "all" && filters.transport === "all";
}

/** 조건 바·최근 검색에 쓰는 그룹별 표시 문구. 선택하지 않은 그룹은 "전체"다. */
export function filterLabels(filters: CourseFilters): Record<ConditionGroup, string> {
  return {
    region: filters.region === "all" ? "전체" : filters.region,

    duration: durationFilterOptions.find((option) => {
      return option.value === filters.duration;
    })?.label ?? "전체",

    transport: transportFilterOptions.find((option) => {
      return option.value === filters.transport;
    })?.label ?? "전체",
  };
}

/** 고른 조건만 " · "로 이어 붙인다. 모두 전체면 빈 문자열이다. */
export function describeFilters(filters: CourseFilters): string {
  const labels = filterLabels(filters);

  return (Object.keys(labels) as ConditionGroup[]).filter((group) => {
    return filters[group] !== "all";
  }).map((group) => {
    return labels[group];
  }).join(" · ");
}

function nightsOf(duration: string): number {
  const nights = /(\d+)박/.exec(duration)?.[1];

  return nights ? Number(nights) : 0;
}

// 이동 수단은 자차와 그 밖(대중교통·기차·도보)으로만 나눈다. 서버 분류가 정해지면 매핑을 바꾼다(DEC-20).
function transportOf(transport: string): Exclude<TransportFilter, "all"> {
  return transport === "자차" ? "car" : "public";
}

export function matchesFilters(course: CourseSummary, filters: CourseFilters): boolean {
  const nights = nightsOf(course.duration);

  const durationMatched = {
    all: true,
    day: nights === 0,
    "one-night": nights === 1,
    "two-nights": nights === 2,
    "three-nights-plus": nights >= 3,
  }[filters.duration];

  const regionMatched = filters.region === "all" || course.province === filters.region;
  const transportMatched = filters.transport === "all" || transportOf(course.transport) === filters.transport;

  return regionMatched && durationMatched && transportMatched;
}

function pickOption<T extends string>(value: unknown, options: readonly T[], fallback: T): T {
  return options.find((option) => {
    return option === value;
  }) ?? fallback;
}

/** 라우트 파라미터는 신뢰하지 않고, 알려진 값만 조건으로 받는다. */
export function filtersFromParams(params: Partial<Record<ConditionGroup, unknown>>): CourseFilters {
  return {
    region: pickOption(params.region, regionFilterOptions.map((option) => {
      return option.value;
    }), "all"),

    duration: pickOption(params.duration, durationFilterOptions.map((option) => {
      return option.value;
    }), "all"),

    transport: pickOption(params.transport, ["all", ...transportFilterOptions.map((option) => {
      return option.value;
    })], "all"),
  };
}

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

export type DurationFilter = "all" | "day" | "one-night" | "two-nights-plus";

export type CourseFilters = { duration: DurationFilter; byCar: boolean };

export const defaultCourseFilters: CourseFilters = { duration: "all", byCar: false };

export const durationFilterOptions: { value: DurationFilter; label: string }[] = [
  { value: "all", label: "기간 전체" },
  { value: "day", label: "당일" },
  { value: "one-night", label: "1박 2일" },
  { value: "two-nights-plus", label: "2박 3일 이상" },
];

function nightsOf(duration: string): number {
  const nights = /(\d+)박/.exec(duration)?.[1];

  return nights ? Number(nights) : 0;
}

export function matchesFilters(course: CourseSummary, filters: CourseFilters): boolean {
  const nights = nightsOf(course.duration);

  const durationMatched = {
    all: true,
    day: nights === 0,
    "one-night": nights === 1,
    "two-nights-plus": nights >= 2,
  }[filters.duration];

  return durationMatched && (!filters.byCar || course.transport === "자차");
}

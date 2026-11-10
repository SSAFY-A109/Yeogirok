import { delay, http } from "msw";

import { findCategory } from "../fixtures/common";
import { findMockCourse, importCountBasisFixture, mockCourses, popularPlaceFixtures, type MockCourse } from "../fixtures/course";
import { fail, MOCK_DELAY_MS, ok, readId } from "./respond";

const DEFAULT_PAGE_SIZE = 20;
const MAX_PAGE_SIZE = 50;
const MAX_POPULAR_PLACES = 5;
const DURATIONS = ["ALL", "DAY_TRIP", "ONE_NIGHT_TWO_DAYS", "TWO_NIGHTS_OR_MORE"];
const TRANSPORT_MODES = ["CAR", "TRANSIT"];
const SORTS = ["POPULAR"];

type Page<T> = { items: T[]; nextCursor: string | null } | "invalid-cursor" | "invalid-limit";

/** 오프셋을 문자열 커서로 쓰는 목 페이지네이션. 실제 커서 형식은 서버가 정한다. */
function paginate<T>(list: T[], url: URL): Page<T> {
  const limit = Number(url.searchParams.get("limit") ?? DEFAULT_PAGE_SIZE);
  const offset = Number(url.searchParams.get("cursor") ?? 0);

  if (!Number.isInteger(limit) || limit < 1 || limit > MAX_PAGE_SIZE) {
    return "invalid-limit";
  }

  if (!Number.isInteger(offset) || offset < 0) {
    return "invalid-cursor";
  }

  const items = list.slice(offset, offset + limit);
  const next = offset + items.length;
  return { items, nextCursor: next < list.length ? String(next) : null };
}

function pageError(page: "invalid-cursor" | "invalid-limit") {
  return page === "invalid-cursor" ? fail(400, "C010", "커서가 올바르지 않습니다.") : fail(400, "C011", "페이지 크기 범위를 벗어났습니다.");
}

function matchesDuration(course: MockCourse, duration: string): boolean {
  const nights = course.summary.nightCount;

  return {
    ALL: true,
    DAY_TRIP: nights === 0,
    ONE_NIGHT_TWO_DAYS: nights === 1,
    TWO_NIGHTS_OR_MORE: nights >= 2,
  }[duration] ?? false;
}

function isUnknownCategory(category: string | null): boolean {
  return category !== null && findCategory(category) === null;
}

function getCourseAuthError(request: Request) {
  const authorization = request.headers.get("Authorization");

  if (authorization === null) {
    return fail(401, "A001", "서비스 액세스 토큰이 없습니다.");
  }

  if (!/^Bearer \S+$/i.test(authorization)) {
    return fail(401, "A003", "서비스 액세스 토큰이 유효하지 않습니다.");
  }
}

export const courseHandlers = [
  http.get("*/api/v1/courses/recommendations", async ({ request }) => {
    await delay(MOCK_DELAY_MS);
    const authError = getCourseAuthError(request);

    if (authError) {
      return authError;
    }

    const url = new URL(request.url);
    const category = url.searchParams.get("category");
    const regionCode = url.searchParams.get("regionCode");

    if (isUnknownCategory(category)) {
      return fail(400, "CO004", "지원하지 않는 코스 추천 카테고리");
    }

    const matched = mockCourses.filter((course) => {
      return course.featuredEligible
        && (category === null || course.categoryCodes.includes(category))
        && (regionCode === null || course.regionCode === regionCode);
    }).sort((a, b) => {
      return b.main.importCount30d - a.main.importCount30d || b.main.id - a.main.id;
    }).map((course) => {
      return course.main;
    });

    const page = paginate(matched, url);

    if (typeof page === "string") {
      return pageError(page);
    }

    return ok({ ...page, basis: importCountBasisFixture });
  }),

  http.get("*/api/v1/courses/:courseId", async ({ params, request }) => {
    await delay(MOCK_DELAY_MS);
    const authError = getCourseAuthError(request);

    if (authError) {
      return authError;
    }

    const courseId = readId(params.courseId);
    const course = courseId === null ? null : findMockCourse(courseId);

    if (course === null) {
      return fail(404, "CO001", "요청한 공개 코스를 이용할 수 없습니다.");
    }

    return ok(course.detail);
  }),

  http.get("*/api/v1/courses", async ({ request }) => {
    await delay(MOCK_DELAY_MS);
    const authError = getCourseAuthError(request);

    if (authError) {
      return authError;
    }

    const url = new URL(request.url);
    const keyword = url.searchParams.get("q")?.trim().toLowerCase() ?? "";
    const regionCode = url.searchParams.get("regionCode");
    const category = url.searchParams.get("category");
    const sort = url.searchParams.get("sort") ?? "POPULAR";
    const duration = url.searchParams.get("duration") ?? "ALL";
    const transportMode = url.searchParams.get("transportMode");

    if (!SORTS.includes(sort)) {
      return fail(400, "CO003", "지원하지 않는 코스 정렬 조건");
    }

    if (!DURATIONS.includes(duration) || (transportMode !== null && !TRANSPORT_MODES.includes(transportMode)) || isUnknownCategory(category)) {
      return fail(400, "CO002", "지원하지 않는 코스 검색 필터");
    }

    const matched = mockCourses.filter((course) => {
      return (keyword === "" || course.searchText.some((text) => {
        return text.toLowerCase().includes(keyword);
      }))
        && (regionCode === null || course.regionCode === regionCode)
        && (category === null || course.categoryCodes.includes(category))
        && (transportMode === null || course.summary.transportMode === transportMode)
        && matchesDuration(course, duration);
    }).sort((a, b) => {
      return b.summary.importCount - a.summary.importCount || b.summary.id - a.summary.id;
    }).map((course) => {
      return course.summary;
    });

    const page = paginate(matched, url);

    if (typeof page === "string") {
      return pageError(page);
    }

    return ok({ ...page, totalCount: matched.length });
  }),

  http.get("*/api/v1/main/popular-places", async ({ request }) => {
    await delay(MOCK_DELAY_MS);
    const authError = getCourseAuthError(request);

    if (authError) {
      return authError;
    }

    const category = new URL(request.url).searchParams.get("category");

    if (isUnknownCategory(category)) {
      return fail(400, "C003", "지원하지 않는 카테고리");
    }

    const items = popularPlaceFixtures.filter((item) => {
      return category === null || item.place.categoryCode === category;
    }).sort((a, b) => {
      return b.importCount30d - a.importCount30d || (a.place.providerPlaceId ?? "").localeCompare(b.place.providerPlaceId ?? "");
    }).slice(0, MAX_POPULAR_PLACES).map((item, index) => {
      return { rank: index + 1, ...item };
    });

    return ok({ items, basis: importCountBasisFixture });
  }),
];

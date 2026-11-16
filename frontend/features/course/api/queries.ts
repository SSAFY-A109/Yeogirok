import { infiniteQueryOptions, queryOptions, useInfiniteQuery, useQuery, type QueryClient } from "@tanstack/react-query";

import { getCourse, getCourseRecommendations, getPopularPlaces, searchCourses, type CourseSearchCondition } from "@/features/course/api/course.api";

const COURSE_LIST_STALE_MS = 60_000;

export const courseKeys = {
  all: ["course"] as const,

  search: (condition: CourseSearchCondition) => {
    return [...courseKeys.all, "search", condition] as const;
  },

  recommendations: () => {
    return [...courseKeys.all, "recommendations"] as const;
  },

  detail: (courseId: number) => {
    return [...courseKeys.all, "detail", courseId] as const;
  },

  popularPlaces: (category: string | null) => {
    return [...courseKeys.all, "popular-places", category] as const;
  },
};

const [, RECOMMENDATIONS_SEGMENT] = courseKeys.recommendations();

/**
 * 코스가 바뀐 뒤(코스에서 장소 가져오기, 기록을 코스로 공개) 코스 검색·상세·인기 장소를 다시 받게 한다.
 * 메인 추천 코스 배너는 앱을 켜 있는 동안 그대로 둔다(`courseRecommendationsOptions`).
 */
export function invalidateCoursesExceptBanner(queryClient: QueryClient) {
  return queryClient.invalidateQueries({
    queryKey: courseKeys.all,

    predicate: (query) => {
      return query.queryKey[1] !== RECOMMENDATIONS_SEGMENT;
    },
  });
}

export function courseSearchOptions(condition: CourseSearchCondition) {
  return infiniteQueryOptions({
    queryKey: courseKeys.search(condition),

    queryFn: ({ pageParam, signal }) => {
      return searchCourses({ ...condition, cursor: pageParam }, signal);
    },

    initialPageParam: null as string | null,

    getNextPageParam: (lastPage) => {
      return lastPage.nextCursor;
    },

    staleTime: COURSE_LIST_STALE_MS,
  });
}

/**
 * 메인 추천 코스 배너. 카테고리와 상관없이 받는다(카테고리는 아래 인기 장소만 바꾼다, 2026-11-16 사용자 결정).
 * 서버는 부를 때마다 무작위 8개를 주므로, 앱을 켜 있는 동안 한 번만 받고 다시 받지 않는다(앱을 다시 켜면 새로 받는다).
 */
export function courseRecommendationsOptions() {
  return infiniteQueryOptions({
    queryKey: courseKeys.recommendations(),

    queryFn: ({ pageParam, signal }) => {
      return getCourseRecommendations({ cursor: pageParam ?? undefined }, signal);
    },

    initialPageParam: null as string | null,

    getNextPageParam: (lastPage) => {
      return lastPage.nextCursor;
    },

    staleTime: Infinity,
    gcTime: Infinity,
  });
}

export function courseDetailOptions(courseId: number) {
  return queryOptions({
    queryKey: courseKeys.detail(courseId),

    queryFn: ({ signal }) => {
      return getCourse(courseId, signal);
    },
  });
}

export function popularPlacesOptions(category: string | null) {
  return queryOptions({
    queryKey: courseKeys.popularPlaces(category),

    queryFn: ({ signal }) => {
      return getPopularPlaces(category, signal);
    },

    staleTime: COURSE_LIST_STALE_MS,
  });
}

export function useCourseSearch(condition: CourseSearchCondition) {
  return useInfiniteQuery(courseSearchOptions(condition));
}

export function useCourseRecommendations() {
  return useInfiniteQuery(courseRecommendationsOptions());
}

/** 라우트에서 받은 코스 id가 숫자가 아니면(`null`) 요청하지 않는다. */
export function useCourse(courseId: number | null) {
  return useQuery({ ...courseDetailOptions(courseId ?? 0), enabled: courseId !== null });
}

export function usePopularPlaces(category: string | null) {
  return useQuery(popularPlacesOptions(category));
}

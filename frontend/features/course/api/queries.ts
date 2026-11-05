import { infiniteQueryOptions, queryOptions, useInfiniteQuery, useQuery } from "@tanstack/react-query";

import { getCourse, getCourseRecommendations, getPopularPlaces, searchCourses, type CourseSearchCondition } from "@/features/course/api/course.api";

/** 검색 한 페이지의 코스 수. limit 상한은 DEC-20 미정이라 기본값을 쓴다(TBD). */
const COURSE_SEARCH_PAGE_SIZE = 20;
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

export function courseSearchOptions(condition: CourseSearchCondition) {
  return infiniteQueryOptions({
    queryKey: courseKeys.search(condition),

    queryFn: ({ pageParam, signal }) => {
      return searchCourses({ ...condition, cursor: pageParam, limit: COURSE_SEARCH_PAGE_SIZE }, signal);
    },

    initialPageParam: null as string | null,

    getNextPageParam: (lastPage) => {
      return lastPage.nextCursor;
    },

    staleTime: COURSE_LIST_STALE_MS,
  });
}

export function courseRecommendationsOptions() {
  return queryOptions({
    queryKey: courseKeys.recommendations(),

    queryFn: ({ signal }) => {
      return getCourseRecommendations(signal);
    },

    staleTime: COURSE_LIST_STALE_MS,
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
  return useQuery(courseRecommendationsOptions());
}

/** 라우트에서 받은 코스 id가 숫자가 아니면(`null`) 요청하지 않는다. */
export function useCourse(courseId: number | null) {
  return useQuery({ ...courseDetailOptions(courseId ?? 0), enabled: courseId !== null });
}

export function usePopularPlaces(category: string | null) {
  return useQuery(popularPlacesOptions(category));
}

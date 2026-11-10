import { infiniteQueryOptions, queryOptions, useInfiniteQuery, useQuery } from "@tanstack/react-query";

import { getCourse, getCourseRecommendations, getPopularPlaces, searchCourses, type CourseSearchCondition } from "@/features/course/api/course.api";

const COURSE_LIST_STALE_MS = 60_000;

export const courseKeys = {
  all: ["course"] as const,

  search: (condition: CourseSearchCondition) => {
    return [...courseKeys.all, "search", condition] as const;
  },

  recommendations: (category: string | null) => {
    return [...courseKeys.all, "recommendations", category] as const;
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
      return searchCourses({ ...condition, cursor: pageParam }, signal);
    },

    initialPageParam: null as string | null,

    getNextPageParam: (lastPage) => {
      return lastPage.nextCursor;
    },

    staleTime: COURSE_LIST_STALE_MS,
  });
}

export function courseRecommendationsOptions(category: string | null) {
  return infiniteQueryOptions({
    queryKey: courseKeys.recommendations(category),

    queryFn: ({ pageParam, signal }) => {
      return getCourseRecommendations({ category: category ?? undefined, cursor: pageParam ?? undefined }, signal);
    },

    initialPageParam: null as string | null,

    getNextPageParam: (lastPage) => {
      return lastPage.nextCursor;
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

export function useCourseRecommendations(category: string | null) {
  return useInfiniteQuery(courseRecommendationsOptions(category));
}

/** 라우트에서 받은 코스 id가 숫자가 아니면(`null`) 요청하지 않는다. */
export function useCourse(courseId: number | null) {
  return useQuery({ ...courseDetailOptions(courseId ?? 0), enabled: courseId !== null });
}

export function usePopularPlaces(category: string | null) {
  return useQuery(popularPlacesOptions(category));
}

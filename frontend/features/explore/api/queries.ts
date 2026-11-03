import { useMutation, useQuery } from "@tanstack/react-query";

import type { CategoryId, CourseFilters } from "../model";
import { addPlacesToRoom, fetchCourse, fetchHome, fetchRanking, fetchRooms, searchCourses } from "./explore.api";

export const exploreKeys = {
  home: ["explore", "home"] as const,

  ranking: (category: CategoryId, subcategory: string) => {
    return ["explore", "ranking", category, subcategory] as const;
  },

  search: (query: string, filters: CourseFilters) => {
    return ["explore", "courses", "search", query, filters] as const;
  },

  course: (courseId: string) => {
    return ["explore", "courses", "detail", courseId] as const;
  },
};

export function useHome() {
  return useQuery({ queryKey: exploreKeys.home, queryFn: fetchHome });
}

export function useRanking(category: CategoryId, subcategory: string) {
  return useQuery({ queryKey: exploreKeys.ranking(category, subcategory),

    queryFn: () => {
      return fetchRanking(category, subcategory);
    } });
}

export function useCourseSearch(query: string, filters: CourseFilters) {
  return useQuery({
    queryKey: exploreKeys.search(query, filters),

    queryFn: () => {
      return searchCourses(query, filters);
    },
  });
}

export function useCourse(courseId: string) {
  return useQuery({ queryKey: exploreKeys.course(courseId),

    queryFn: () => {
      return fetchCourse(courseId);
    } });
}

export function useRooms(enabled: boolean) {
  return useQuery({ queryKey: ["explore", "rooms"] as const, queryFn: fetchRooms, enabled });
}

export function useAddPlacesToRoom() {
  return useMutation({ mutationFn: addPlacesToRoom });
}

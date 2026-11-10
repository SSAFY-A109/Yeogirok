import { api } from "@/api/client";
import {
  toCourseDetail,
  toCourseSearchPage,
  toFeaturedCourse,
  toPopularPlace,
  type CourseDetail,
  type CourseDetailDto,
  type CourseDurationDto,
  type CourseRecommendationsPage,
  type CourseRecommendationsParamsDto,
  type CourseRecommendationsResponseDto,
  type CourseSearchPage,
  type CourseSearchParamsDto,
  type CourseSearchResponseDto,
  type CourseTransportModeDto,
  type PopularPlace,
  type PopularPlacesResponseDto,
} from "@/features/course/model";

export type CourseSearchCondition = {
  query: string;
  regionCode: string | null;
  duration: Exclude<CourseDurationDto, "ALL"> | null;
  transportMode: CourseTransportModeDto | null;
};

export type SearchCoursesInput = CourseSearchCondition & { cursor: string | null };

/** 공개된 코스 검색. 정렬은 서버 기본값(POPULAR, 가져간 여행방 수 내림차순)을 쓴다. */
export async function searchCourses(input: SearchCoursesInput, signal?: AbortSignal): Promise<CourseSearchPage> {
  const params: CourseSearchParamsDto = {
    q: input.query || undefined,
    regionCode: input.regionCode ?? undefined,
    duration: input.duration ?? undefined,
    transportMode: input.transportMode ?? undefined,
    cursor: input.cursor ?? undefined,
  };

  const { data } = await api.get<CourseSearchResponseDto>("/courses", { params, signal });
  return toCourseSearchPage(data);
}

/** 메인 추천 코스 조회. 커서를 이어 보내 최초 조회의 집계 구간을 유지한다. */
export async function getCourseRecommendations(params: CourseRecommendationsParamsDto, signal?: AbortSignal): Promise<CourseRecommendationsPage> {
  const { data } = await api.get<CourseRecommendationsResponseDto>("/courses/recommendations", { params, signal });
  return { courses: data.items.map(toFeaturedCourse), nextCursor: data.nextCursor };
}

/** 공개된 코스 상세 조회. 없거나 공개 해제된 코스는 404 CO001. */
export async function getCourse(courseId: number, signal?: AbortSignal): Promise<CourseDetail> {
  const { data } = await api.get<CourseDetailDto>(`/courses/${courseId}`, { signal });
  return toCourseDetail(data);
}

/** 메인 인기 장소 조회(최대 5개). 카테고리를 주지 않으면 전체다. */
export async function getPopularPlaces(category: string | null, signal?: AbortSignal): Promise<PopularPlace[]> {
  const { data } = await api.get<PopularPlacesResponseDto>("/main/popular-places", {
    params: { category: category ?? undefined },
    signal,
  });

  return data.items.map(toPopularPlace);
}

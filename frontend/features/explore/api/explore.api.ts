// API 명세 확정 전 임시 구현: JS mock을 비동기로 감싸 실제 통신과 같은 모양으로 쓴다.
// 명세가 나오면 이 파일의 함수 본문만 axios 호출로 바꾼다(규칙 90).
import { categories, courses, featuredCourses, rankings, rooms, subcategories } from "../mocks/explore.mock";
import { matchesFilters, type AddPlacesInput, type AddPlacesResult, type CategoryId, type CourseDetail, type CourseFilters, type CourseSummary, type HomeData, type RankedPlace, type Room } from "../model";

const MOCK_DELAY_MS = 350;

function delay<T>(value: T): Promise<T> {
  return new Promise((resolve) => {
    setTimeout(() => {
      return resolve(value);
    }, MOCK_DELAY_MS);
  });
}

export function fetchHome(): Promise<HomeData> {
  return delay({
    featured: featuredCourses,
    categories: categories as HomeData["categories"],
    subcategories: subcategories as HomeData["subcategories"],
  });
}

export function fetchRanking(category: CategoryId, subcategory: string): Promise<RankedPlace[]> {
  const list = (rankings as Record<CategoryId, RankedPlace[]>)[category] ?? [];

  return delay(subcategory === "전체" ? list : list.filter((place) => {
    return place.subcategory === subcategory;
  }));
}

export function searchCourses(query: string, filters: CourseFilters): Promise<CourseSummary[]> {
  const keyword = query.trim().toLowerCase();

  const matched = courses.filter((course) => {
    const keywordMatched = keyword === "" || [course.title, course.region, ...course.places.map((place) => {
      return place.name;
    })].some((text) => {
      return text.toLowerCase().includes(keyword);
    });

    return keywordMatched && matchesFilters(course, filters);
  });

  return delay(matched.sort((a, b) => {
    return b.forkCount - a.forkCount;
  }));
}

export function fetchCourse(courseId: string): Promise<CourseDetail | null> {
  return delay((courses.find((course) => {
    return course.id === courseId;
  }) as CourseDetail | undefined) ?? null);
}

export function fetchRooms(): Promise<Room[]> {
  return delay(rooms);
}

export async function addPlacesToRoom({ roomId, placeIds }: AddPlacesInput): Promise<AddPlacesResult> {
  const room = rooms.find((item) => {
    return item.id === roomId;
  });

  if (!room || placeIds.length === 0) {
    throw new Error("add-places-failed");
  }

  return delay({ roomName: room.name, addedCount: placeIds.length });
}

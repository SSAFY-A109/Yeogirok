import type {
  CourseDetailDto,
  CourseSummaryDto,
  ImportCountBasisDto,
  MainCourseSummaryDto,
  MainPlaceDto,
  PlaceDto,
} from "../../../features/course/model";

import { findCatalogPlace } from "./place";

type VisitSeed = [visitId: number, catalogPlaceId: number, hasPhoto: boolean];

type CourseSeed = {
  id: number;
  title: string;
  description: string;
  regionCode: string;
  regionName: string;
  transportMode: "CAR" | "TRANSIT" | null;
  authorIds: number[];
  importCount: number;
  importCount30d: number;
  /** 일차별 방문. 빈 배열은 장소가 없는 일차다. */
  days: VisitSeed[][];
};

/** 공개 코스 예시. 장소 좌표는 공개 관광지 목록(fixtures/place)에서 가져오고, 사진 URL은 더미 주소다. */
const COURSE_SEEDS: CourseSeed[] = [
  {
    id: 501,
    title: "강릉 바다 따라 2박 3일",
    description: "바다를 따라 카페와 시장을 천천히 둘러본 코스예요.",
    regionCode: "51",
    regionName: "강릉",
    transportMode: "CAR",
    authorIds: [1401, 1402, 1403],
    importCount: 31,
    importCount30d: 12,
    days: [
      [[701, 101, true], [702, 102, false]],
      [[703, 103, true], [704, 108, false], [705, 109, false]],
      [[706, 111, true], [707, 110, false], [708, 104, false]],
    ],
  },
  {
    id: 502,
    title: "강릉 카페 투어 당일치기",
    description: "기차로 가서 버스로 이동하며 카페만 들른 하루예요.",
    regionCode: "51",
    regionName: "강릉",
    transportMode: "TRANSIT",
    authorIds: [1404],
    importCount: 18,
    importCount30d: 9,
    days: [[[711, 120, true], [712, 117, true], [713, 121, false], [714, 114, false]]],
  },
  {
    id: 503,
    title: "비 오는 날 강릉 실내 코스",
    description: "비가 와도 걱정 없는 박물관과 시장 위주의 코스예요.",
    regionCode: "51",
    regionName: "강릉",
    transportMode: "CAR",
    authorIds: [1405, 1406],
    importCount: 7,
    importCount30d: 5,
    days: [
      [[721, 108, true], [722, 115, false]],
      [[723, 116, true], [724, 123, false]],
    ],
  },
  {
    id: 504,
    title: "정동진 해돋이와 바다열차",
    description: "해돋이를 보고 바다열차를 탄 1박 2일이에요.",
    regionCode: "51",
    regionName: "강릉",
    transportMode: "TRANSIT",
    authorIds: [1407, 1408],
    importCount: 12,
    importCount30d: 12,
    days: [
      [[731, 118, true], [732, 122, false]],
      [[733, 114, true]],
    ],
  },
  {
    id: 505,
    title: "제주 동쪽 1박 2일",
    description: "일출봉과 재래시장을 묶은 짧은 제주 여행이에요.",
    regionCode: "50",
    regionName: "제주",
    transportMode: "CAR",
    authorIds: [1409],
    importCount: 4,
    importCount30d: 2,
    days: [[[741, 201, true]], [[742, 202, true]]],
  },
  {
    id: 506,
    title: "부산 영도 한 바퀴",
    description: "버스로 영도를 한 바퀴 돈 당일 코스예요.",
    regionCode: "26",
    regionName: "부산",
    transportMode: "TRANSIT",
    authorIds: [1410, 1411],
    importCount: 9,
    importCount30d: 3,
    days: [[[751, 301, true], [752, 302, false], [753, 303, false]]],
  },
  {
    id: 507,
    title: "속초 시장과 바다",
    description: "둘째 날은 숙소에서 쉬었어요.",
    regionCode: "51",
    regionName: "속초",
    transportMode: null,
    authorIds: [1412],
    importCount: 2,
    importCount30d: 1,
    days: [[[761, 401, true]], [], [[762, 402, false], [763, 403, false]]],
  },
];

const PHOTO_HOST = "https://images.yeogirok.test";

function photoUrl(visitId: number): string {
  return `${PHOTO_HOST}/visits/${visitId}.jpg`;
}

function toPlace(catalogPlaceId: number): PlaceDto {
  const place = findCatalogPlace(String(catalogPlaceId));

  if (place === null) {
    throw new Error(`목 장소가 없습니다: ${catalogPlaceId}`);
  }

  return {
    provider: place.provider,
    providerPlaceId: place.providerPlaceId,
    name: place.name,
    latitude: place.latitude,
    longitude: place.longitude,
    address: place.address,
    categoryCode: place.categoryCode,
    categoryName: place.categoryName,
    imageUrl: null,
    imageAttribution: null,
  };
}

function authorsOf(seed: CourseSeed) {
  return seed.authorIds.map((id) => {
    return { id, profileImageUrl: null };
  });
}

function visitsOf(seed: CourseSeed) {
  return seed.days.flatMap((visits, index) => {
    return visits.map(([visitId, catalogPlaceId, hasPhoto]) => {
      return { day: index + 1, visitId, place: toPlace(catalogPlaceId), hasPhoto };
    });
  });
}

/** 검색 필터에 쓰는 목 전용 메타(응답에는 넣지 않는다). */
export type MockCourse = {
  regionCode: string;
  categoryCodes: string[];
  searchText: string[];
  /** 모든 일차에 공개 사진이 있어 메인 상단 노출 대상인지. */
  featuredEligible: boolean;
  summary: CourseSummaryDto;
  main: MainCourseSummaryDto;
  detail: CourseDetailDto;
};

function toMockCourse(seed: CourseSeed): MockCourse {
  const visits = visitsOf(seed);
  const dayCount = seed.days.length;

  const firstPhotoVisitByDay = seed.days.map((_, index) => {
    return visits.find((visit) => {
      return visit.day === index + 1 && visit.hasPhoto;
    });
  });

  const photoVisits = firstPhotoVisitByDay.filter((visit) => {
    return visit !== undefined;
  });

  const common = {
    id: seed.id,
    title: seed.title,
    authors: authorsOf(seed),
    regionName: seed.regionName,
    placeCount: visits.length,
    dayCount,
    nightCount: dayCount - 1,
  };

  return {
    regionCode: seed.regionCode,

    categoryCodes: visits.flatMap((visit) => {
      return visit.place.categoryCode === null ? [] : [visit.place.categoryCode];
    }),

    searchText: [seed.title, ...visits.map((visit) => {
      return visit.place.name;
    })],

    featuredEligible: photoVisits.length === dayCount,

    summary: {
      ...common,
      thumbnailUrl: photoVisits[0] ? photoUrl(photoVisits[0].visitId) : null,
      transportMode: seed.transportMode,

      routePreview: visits.slice(0, 3).map((visit) => {
        return visit.place.name;
      }),

      importCount: seed.importCount,
    },

    main: {
      ...common,
      thumbnailUrl: photoVisits[0] ? photoUrl(photoVisits[0].visitId) : "",
      transportMode: seed.transportMode ?? "CAR",
      importCount30d: seed.importCount30d,

      previewPlaces: photoVisits.slice(0, 3).map((visit) => {
        return {
          visitId: visit.visitId,
          day: visit.day,
          name: visit.place.name,
          categoryCode: visit.place.categoryCode,
          categoryName: visit.place.categoryName,
          imageUrl: photoUrl(visit.visitId),
        };
      }),
    },

    detail: {
      id: seed.id,
      title: seed.title,
      description: seed.description,
      authors: authorsOf(seed),

      days: seed.days.map((_, index) => {
        return {
          day: index + 1,

          items: visits.filter((visit) => {
            return visit.day === index + 1;
          }).map((visit, order) => {
            return {
              visitId: visit.visitId,
              place: visit.place,
              order: order + 1,
              photos: visit.hasPhoto ? [{ imageId: visit.visitId + 100, url: photoUrl(visit.visitId), attribution: null }] : [],
            };
          }),
        };
      }),

      dayCount,
      nightCount: dayCount - 1,
      transportMode: seed.transportMode,
      version: 1,
    },
  };
}

export const mockCourses: MockCourse[] = COURSE_SEEDS.map(toMockCourse);

export function findMockCourse(courseId: number): MockCourse | null {
  return mockCourses.find((course) => {
    return course.summary.id === courseId;
  }) ?? null;
}

export const importCountBasisFixture: ImportCountBasisDto = {
  type: "IMPORT_COUNT_30D",
  periodStart: "2026-10-05T12:00:00+09:00",
  periodEnd: "2026-11-04T12:00:00+09:00",
};

type PopularSeed = [catalogPlaceId: number, regionName: string, importCount30d: number];

/** 최근 30일 인기 장소 집계 예시. providerPlaceId는 공개 관광지 목록 id와 같다. */
const POPULAR_SEEDS: PopularSeed[] = [
  [111, "강릉", 198],
  [112, "강릉", 152],
  [113, "강릉", 121],
  [114, "강릉", 96],
  [201, "제주", 88],
  [302, "부산", 61],
  [115, "강릉", 140],
  [116, "강릉", 77],
  [108, "강릉", 70],
  [301, "부산", 45],
  [117, "강릉", 132],
  [118, "강릉", 101],
  [119, "강릉", 115],
  [103, "강릉", 64],
  [120, "강릉", 160],
  [121, "강릉", 98],
  [101, "강릉", 90],
  [122, "강릉", 83],
  [123, "강릉", 109],
  [104, "강릉", 99],
  [202, "제주", 52],
];

export const popularPlaceFixtures: { place: MainPlaceDto; importCount30d: number }[] = POPULAR_SEEDS.map(([catalogPlaceId, regionName, importCount30d]) => {
  return { place: { ...toPlace(catalogPlaceId), regionCode: null, regionName }, importCount30d };
});

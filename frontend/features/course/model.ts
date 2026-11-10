import { assertId, ContractMismatchError, toKnownEnum } from "../../lib/contract";

// 원천: 팀 Notion "API 명세" — 공개된 코스 검색(GET /courses), 메인 추천 코스 조회(GET /courses/recommendations),
// 공개된 코스 상세 조회(GET /courses/{courseId}), 메인 인기 장소 조회(GET /main/popular-places).
// 2026-11-04 사용자가 전달한 명세 기준. 명세는 "설계 초안 / 미구현"이라 서버 반영 후 Swagger로 다시 대조한다.
// 메인 인기 장소는 /main 경로지만 코스에서 가져간 집계라 코스(CO) 도메인에 둔다(공통 오류 C003만 쓴다).

// #region 열거형

export const COURSE_TRANSPORT_MODES = ["CAR", "TRANSIT"] as const;
export type CourseTransportModeDto = (typeof COURSE_TRANSPORT_MODES)[number];
export type CourseTransportMode = CourseTransportModeDto | "UNKNOWN";

export const COURSE_TRANSPORT_MODE_LABEL: Readonly<Record<CourseTransportMode, string>> = {
  CAR: "자차",
  TRANSIT: "대중교통",
  UNKNOWN: "이동 수단 확인 필요",
};

/** 검색 기간 조건. 미지정은 서버가 ALL로 본다. */
export const COURSE_DURATIONS = ["DAY_TRIP", "ONE_NIGHT_TWO_DAYS", "TWO_NIGHTS_OR_MORE"] as const;
export type CourseDurationDto = "ALL" | (typeof COURSE_DURATIONS)[number];

export const PLACE_PROVIDERS = ["TOUR_API", "MANUAL"] as const;
export type PlaceProvider = (typeof PLACE_PROVIDERS)[number] | "UNKNOWN";

// #endregion

// #region DTO

export type PublicAuthorDto = { id: number; profileImageUrl: string | null };

export type CourseSummaryDto = {
  id: number;
  title: string;
  authors: PublicAuthorDto[];
  regionName: string;
  thumbnailUrl: string | null;
  placeCount: number;
  dayCount: number;
  nightCount: number;
  transportMode: string | null;
  routePreview: string[];
  importCount: number;
};

export type CourseSearchResponseDto = { items: CourseSummaryDto[]; totalCount: number; nextCursor: string | null };

export type CourseSearchParamsDto = {
  q?: string;
  regionCode?: string;
  category?: string;
  sort?: string;
  duration?: CourseDurationDto;
  transportMode?: CourseTransportModeDto;
  cursor?: string;
  limit?: number;
};

export type ImportCountBasisDto = { type: string; periodStart: string; periodEnd: string };

export type MainCoursePreviewPlaceDto = {
  visitId: number;
  day: number;
  name: string;
  categoryCode: string | null;
  categoryName: string | null;
  imageUrl: string;
};

export type MainCourseSummaryDto = {
  id: number;
  title: string;
  authors: PublicAuthorDto[];
  regionName: string;
  thumbnailUrl: string;
  placeCount: number;
  dayCount: number;
  nightCount: number;
  transportMode: string;
  importCount30d: number;
  previewPlaces: MainCoursePreviewPlaceDto[];
};

export type CourseRecommendationsResponseDto = {
  items: MainCourseSummaryDto[];
  nextCursor: string | null;
  basis: ImportCountBasisDto;
};

export type CourseRecommendationsParamsDto = { category?: string; regionCode?: string; cursor?: string; limit?: number };

/** 공통 Place. 전역 숫자 id가 없고 provider + providerPlaceId로 식별한다(MANUAL은 providerPlaceId가 null). */
export type PlaceDto = {
  provider: string;
  providerPlaceId: string | null;
  name: string;
  latitude: number;
  longitude: number;
  address: string | null;
  categoryCode: string | null;
  categoryName: string | null;
  imageUrl: string | null;
  imageAttribution: string | null;
};

/** 메인 인기 장소의 Place. 공통 Place에 지역 필드가 더 있다. */
export type MainPlaceDto = PlaceDto & { regionCode: string | null; regionName: string | null };

export type PublicPhotoDto = { imageId: number; url: string; attribution: string | null };

export type CourseVisitDto = { visitId: number; place: PlaceDto; order: number; photos: PublicPhotoDto[] };

export type CourseDayDto = { day: number; items: CourseVisitDto[] };

export type CourseDetailDto = {
  id: number;
  title: string;
  description: string;
  authors: PublicAuthorDto[];
  days: CourseDayDto[];
  dayCount: number;
  nightCount: number;
  transportMode: string | null;
  version: number;
};

export type PopularPlaceDto = { rank: number; place: MainPlaceDto; importCount30d: number };

export type PopularPlacesResponseDto = { items: PopularPlaceDto[]; basis: ImportCountBasisDto };

// #endregion

// #region 화면 모델

export type CourseAuthor = { id: number; profileImageUrl: string | null };

type CourseLength = { dayCount: number; nightCount: number };

export type CourseSummary = CourseLength & {
  id: number;
  title: string;
  authors: CourseAuthor[];
  regionName: string;
  thumbnailUrl: string | null;
  placeCount: number;
  transportMode: CourseTransportMode | null;
  /** 방문 순서대로 표시할 대표 장소명. */
  routePreview: string[];
  /** 이 코스에서 장소를 가져간 현재 여행방 수(사람 수가 아니다). */
  importCount: number;
};

export type CourseSearchPage = { courses: CourseSummary[]; totalCount: number; nextCursor: string | null };

export type FeaturedPreviewPlace = { visitId: number; day: number; name: string; categoryName: string | null; imageUrl: string };

export type FeaturedCourse = CourseLength & {
  id: number;
  title: string;
  authors: CourseAuthor[];
  regionName: string;
  thumbnailUrl: string;
  placeCount: number;
  transportMode: CourseTransportMode;
  /** 최근 30일에 이 코스의 장소를 가져간 여행방 수. */
  importCount30d: number;
  previewPlaces: FeaturedPreviewPlace[];
};

export type Coordinate = { latitude: number; longitude: number };

export type CoursePlace = {
  /** 선택·지도 핀에 쓰는 문자열 키(visitId). */
  id: string;
  visitId: number;
  day: number;
  order: number;
  name: string;
  categoryCode: string | null;
  categoryName: string | null;
  address: string | null;
  /** 첫 공개 사진, 없으면 장소 대표 이미지. */
  imageUrl: string | null;
  photoUrls: string[];
  coordinate: Coordinate;
};

export type CourseDay = { day: number; places: CoursePlace[] };

export type CourseContent = CourseLength & {
  title: string;
  description: string;
  authors: CourseAuthor[];
  /** 장소가 없는 일차도 그대로 둔다. */
  days: CourseDay[];
  /** 일차·방문 순서대로 펼친 장소. */
  places: CoursePlace[];
  transportMode: CourseTransportMode | null;
};

export type CourseDetail = CourseContent & { id: number; version: number };
export type CoursePreviewDto = Pick<CourseDetailDto, "title" | "description" | "authors" | "days">;

export type PopularPlace = {
  /** 목록 키. provider와 장소 식별값으로 만든다. */
  key: string;
  rank: number;
  provider: PlaceProvider;
  providerPlaceId: string | null;
  name: string;
  /** 지역명, 없으면 주소. 둘 다 없으면 `null`. */
  areaName: string | null;
  categoryCode: string | null;
  categoryName: string | null;
  imageUrl: string | null;
  coordinate: Coordinate;
  /** 최근 30일에 이 장소를 가져간 여행방 수. */
  importCount30d: number;
};

// #endregion

// #region 변환

function assertCount(value: unknown, field: string): number {
  if (typeof value !== "number" || !Number.isSafeInteger(value) || value < 0) {
    throw new ContractMismatchError(field);
  }

  return value;
}

function toCoordinate(latitude: unknown, longitude: unknown): Coordinate {
  if (typeof latitude !== "number" || !Number.isFinite(latitude) || latitude < -90 || latitude > 90) {
    throw new ContractMismatchError("latitude");
  }

  if (typeof longitude !== "number" || !Number.isFinite(longitude) || longitude < -180 || longitude > 180) {
    throw new ContractMismatchError("longitude");
  }

  return { latitude, longitude };
}

function toCourseLength(dto: { dayCount: number; nightCount: number }): CourseLength {
  return { dayCount: assertCount(dto.dayCount, "dayCount"), nightCount: assertCount(dto.nightCount, "nightCount") };
}

function toNullableTransportMode(value: string | null): CourseTransportMode | null {
  return value === null ? null : toKnownEnum(value, COURSE_TRANSPORT_MODES);
}

function toAuthor(dto: PublicAuthorDto): CourseAuthor {
  return { id: assertId(dto.id, "authors.id"), profileImageUrl: dto.profileImageUrl };
}

export function toCourseSummary(dto: CourseSummaryDto): CourseSummary {
  return {
    ...toCourseLength(dto),
    id: assertId(dto.id, "id"),
    title: dto.title,
    authors: dto.authors.map(toAuthor),
    regionName: dto.regionName,
    thumbnailUrl: dto.thumbnailUrl,
    placeCount: assertCount(dto.placeCount, "placeCount"),
    transportMode: toNullableTransportMode(dto.transportMode),
    routePreview: dto.routePreview,
    importCount: assertCount(dto.importCount, "importCount"),
  };
}

export function toCourseSearchPage(dto: CourseSearchResponseDto): CourseSearchPage {
  return {
    courses: dto.items.map(toCourseSummary),
    totalCount: assertCount(dto.totalCount, "totalCount"),
    nextCursor: dto.nextCursor,
  };
}

export function toFeaturedCourse(dto: MainCourseSummaryDto): FeaturedCourse {
  return {
    ...toCourseLength(dto),
    id: assertId(dto.id, "id"),
    title: dto.title,
    authors: dto.authors.map(toAuthor),
    regionName: dto.regionName,
    thumbnailUrl: dto.thumbnailUrl,
    placeCount: assertCount(dto.placeCount, "placeCount"),
    transportMode: toKnownEnum(dto.transportMode, COURSE_TRANSPORT_MODES),
    importCount30d: assertCount(dto.importCount30d, "importCount30d"),

    previewPlaces: dto.previewPlaces.map((place) => {
      return {
        visitId: assertId(place.visitId, "previewPlaces.visitId"),
        day: assertCount(place.day, "previewPlaces.day"),
        name: place.name,
        categoryName: place.categoryName,
        imageUrl: place.imageUrl,
      };
    }),
  };
}

function toCoursePlace(day: number, dto: CourseVisitDto): CoursePlace {
  const visitId = assertId(dto.visitId, "days.items.visitId");

  return {
    id: String(visitId),
    visitId,
    day,
    order: assertCount(dto.order, "days.items.order"),
    name: dto.place.name,
    categoryCode: dto.place.categoryCode,
    categoryName: dto.place.categoryName,
    address: dto.place.address,
    imageUrl: dto.photos[0]?.url ?? dto.place.imageUrl,

    photoUrls: dto.photos.map((photo) => {
      return photo.url;
    }),

    coordinate: toCoordinate(dto.place.latitude, dto.place.longitude),
  };
}

function toCourseContent(dto: CoursePreviewDto) {
  const days = [...dto.days].sort((a, b) => {
    return a.day - b.day;
  }).map((group) => {
    const day = assertCount(group.day, "days.day");

    return {
      day,

      places: [...group.items].sort((a, b) => {
        return a.order - b.order;
      }).map((item) => {
        return toCoursePlace(day, item);
      }),
    };
  });

  return {
    title: dto.title,
    description: dto.description,
    authors: dto.authors.map(toAuthor),
    days,

    places: days.flatMap((group) => {
      return group.places;
    }),
  };
}

export function toCourseDetail(dto: CourseDetailDto): CourseDetail {
  return {
    ...toCourseContent(dto),
    ...toCourseLength(dto),
    id: assertId(dto.id, "id"),
    transportMode: toNullableTransportMode(dto.transportMode),
    version: assertCount(dto.version, "version"),
  };
}

export function toCoursePreview(dto: CoursePreviewDto): CourseContent {
  const content = toCourseContent(dto);
  const dayCount = content.days.at(-1)?.day ?? 0;
  return { ...content, dayCount, nightCount: Math.max(0, dayCount - 1), transportMode: null };
}

export function toPopularPlace(dto: PopularPlaceDto): PopularPlace {
  const { place } = dto;
  const provider = toKnownEnum(place.provider, PLACE_PROVIDERS);

  return {
    key: [provider, place.providerPlaceId ?? place.name, place.latitude, place.longitude].join(":"),
    rank: assertCount(dto.rank, "rank"),
    provider,
    providerPlaceId: place.providerPlaceId,
    name: place.name,
    areaName: place.regionName ?? place.address,
    categoryCode: place.categoryCode,
    categoryName: place.categoryName,
    imageUrl: place.imageUrl,
    coordinate: toCoordinate(place.latitude, place.longitude),
    importCount30d: assertCount(dto.importCount30d, "importCount30d"),
  };
}

// #endregion

// #region 표시

/** 여행 기간 표시. 숙박이 없으면 "당일", 있으면 "N박 M일". */
export function formatCourseLength({ dayCount, nightCount }: CourseLength): string {
  return nightCount === 0 ? "당일" : `${nightCount}박 ${dayCount}일`;
}

/** "장소 8곳 · 2박 3일 · 자차"처럼 코스 요약을 잇는다. 이동 수단을 모르면(null) 뺀다. */
export function formatCourseFacts(course: CourseLength & { placeCount: number; transportMode: CourseTransportMode | null }): string {
  return [
    `장소 ${course.placeCount}곳`,
    formatCourseLength(course),
    course.transportMode === null ? null : COURSE_TRANSPORT_MODE_LABEL[course.transportMode],
  ].filter((part) => {
    return part !== null;
  }).join(" · ");
}

// #endregion

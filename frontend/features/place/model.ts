import type { Category } from "../common/model";
import { toPlaceKey, type AddCustomPlaceRequestDto, type RoomPlace } from "../room/model";
import { ContractMismatchError } from "../../lib/contract";

// API-026 여행지 검색은 서버에 아직 없어 팀 Notion "API 명세" 상세 페이지(2026-11-10)를 따른다. 서버에 올라오면 Swagger로 다시 맞춘다.
// 장소는 전역 숫자 id 없이 `provider`+`providerPlaceId`로 구별하고, 카테고리는 이름 없이 코드만 온다.
// 사진 출처(`imageAttribution`)는 보여 줄 자리가 화면에 없어 쓰지 않는다(TBD).

/** 코드와 이름을 함께 주는 응답(여행방 후보·일정)의 카테고리. 검색·추천 응답은 코드만 준다. */
export type PlaceCategoryDto = { code: string; name: string };

/** 검색(API-026)·추천(API-034) 응답의 장소. `provider`는 TOUR_API·MANUAL이다. */
export type PlaceDto = {
  provider: string;
  providerPlaceId: string | null;
  name: string;
  latitude: number;
  longitude: number;
  address: string | null;
  categoryCode: string | null;
  imageUrl: string | null;
  imageAttribution: string | null;
};

export type SearchPlacesResponseDto = { items: PlaceDto[]; nextCursor: string | null; totalCount: number };

export type PlaceCategory = { code: string; name: string };

/** 담기 요청(API-029)에서 장소를 가리키는 값. */
export type PlaceRef = { provider: string; providerPlaceId: string };

export type Place = {
  /** 목록 키. 담을 수 있는 장소는 여행방 후보의 `placeKey`와 같은 값이라 이미 담긴 곳인지 가릴 때도 쓴다. */
  key: string;
  /** `providerPlaceId`가 없는 장소는 가리킬 수 없어 `null`이다. 이런 장소는 담을 수 없다. */
  ref: PlaceRef | null;
  name: string;
  categoryCode: string | null;
  address: string | null;
  photoUrl: string | null;
  coordinate: { latitude: number; longitude: number };
};

export type PlaceSearchPage = {
  places: Place[];
  nextCursor: string | null;
  /** 검색어에 맞는 전체 장소 수(서버 값). */
  totalCount: number;
};

export function toPlaceCategory(dto: PlaceCategoryDto | null): PlaceCategory | null {
  if (dto === null) {
    return null;
  }

  if (typeof dto.code !== "string" || typeof dto.name !== "string") {
    throw new ContractMismatchError("category");
  }

  return { code: dto.code, name: dto.name };
}

function toCoordinate(latitude: number, longitude: number): Place["coordinate"] {
  if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90) {
    throw new ContractMismatchError("latitude");
  }

  if (!Number.isFinite(longitude) || longitude < -180 || longitude > 180) {
    throw new ContractMismatchError("longitude");
  }

  return { latitude, longitude };
}

function toPlaceRef(dto: PlaceDto): PlaceRef | null {
  if (typeof dto.provider !== "string" || dto.provider === "") {
    throw new ContractMismatchError("provider");
  }

  if (dto.providerPlaceId === null) {
    return null;
  }

  if (typeof dto.providerPlaceId !== "string" || dto.providerPlaceId === "") {
    throw new ContractMismatchError("providerPlaceId");
  }

  return { provider: dto.provider, providerPlaceId: dto.providerPlaceId };
}

export function toPlace(dto: PlaceDto): Place {
  const ref = toPlaceRef(dto);
  const coordinate = toCoordinate(dto.latitude, dto.longitude);

  return {
    key: ref === null
      ? `${dto.provider}:${dto.name}:${coordinate.latitude},${coordinate.longitude}`
      : toPlaceKey(ref.provider, ref.providerPlaceId),
    ref,
    name: dto.name,
    categoryCode: dto.categoryCode,
    address: dto.address,
    photoUrl: dto.imageUrl,
    coordinate,
  };
}

export function toPlaceSearchPage(dto: SearchPlacesResponseDto): PlaceSearchPage {
  if (!Number.isSafeInteger(dto.totalCount) || dto.totalCount < 0) {
    throw new ContractMismatchError("totalCount");
  }

  return { places: dto.items.map(toPlace), nextCursor: dto.nextCursor, totalCount: dto.totalCount };
}

/** 카테고리 코드의 표시 이름을 공통 카테고리 목록(API-079)에서 찾는다. 목록을 아직 못 받았거나 목록에 없으면 `null`. */
export function findCategoryName(categories: readonly Category[] | undefined, code: string | null): string | null {
  if (code === null) {
    return null;
  }

  return categories?.find((category) => {
    return category.code === code;
  })?.name ?? null;
}

/** 지도(카카오)에서 찾은 장소 중 투어API에 없는 곳. 직접 추가(API-031)로 담는다. */
export type MapPlace = {
  key: string;
  name: string;
  address: string | null;
  photoUrl: null;
  coordinate: { latitude: number; longitude: number };
};

/** 지도 결과와 이름이 같고 이 거리(m) 안인 투어API 장소는 같은 곳으로 보고 투어API 장소로 담는다. */
const TOUR_MATCH_METERS = 100;
/** 이미 담은 직접 추가 후보와 이름이 같고 이 거리(m) 안이면 담은 곳으로 본다. */
const ADDED_MATCH_METERS = 50;
const EARTH_RADIUS_METERS = 6_371_000;
const RADIANS_PER_DEGREE = Math.PI / 180;

/** 카카오 장소 검색 결과 한 건(`lib/native/kakao-place-search`의 `KakaoPlace`와 같은 모양). */
type MapSearchItem = { name: string; address: string | null; latitude: number; longitude: number };

type NamedCoordinate = { name: string; coordinate: { latitude: number; longitude: number } };

/** 두 좌표 사이의 거리(m). 지구를 구로 보고 잰다(하버사인). */
function distanceMeters(from: NamedCoordinate["coordinate"], to: NamedCoordinate["coordinate"]): number {
  const latitudeDelta = (to.latitude - from.latitude) * RADIANS_PER_DEGREE;
  const longitudeDelta = (to.longitude - from.longitude) * RADIANS_PER_DEGREE;

  const haversine = Math.sin(latitudeDelta / 2) ** 2 +
    Math.cos(from.latitude * RADIANS_PER_DEGREE) * Math.cos(to.latitude * RADIANS_PER_DEGREE) * Math.sin(longitudeDelta / 2) ** 2;

  return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1, Math.sqrt(haversine)));
}

function normalizePlaceName(name: string): string {
  return name.replace(/\s+/g, "").toLowerCase();
}

/** 이름(공백·대소문자 무시)이 같고 `maxMeters` 안에 있으면 같은 장소로 본다. */
function isSamePlace(a: NamedCoordinate, b: NamedCoordinate, maxMeters: number): boolean {
  return normalizePlaceName(a.name) === normalizePlaceName(b.name) && distanceMeters(a.coordinate, b.coordinate) <= maxMeters;
}

/**
 * 검색 결과 한 줄. 지도(카카오) 결과 순서를 따르되, 투어API에도 있는 곳은 사진·카테고리가 있는 투어API 장소(`tour`, API-029로 담음)로,
 * 없는 곳은 지도 장소(`map`, API-031로 직접 추가)로 보인다.
 */
export type MergedSearchItem = { kind: "tour"; place: Place } | { kind: "map"; place: MapPlace };

/**
 * 지도 검색 결과를 투어API 결과와 합친다.
 * 주소 검색 결과(이름이 곧 주소)는 빼고, 지도 결과와 이름이 같고 `TOUR_MATCH_METERS` 안인 투어API 장소(담을 수 있는 곳만)로 바꾼다.
 * 같은 장소는 한 번만 보인다.
 */
export function mergeSearchResults(results: readonly MapSearchItem[], tourPlaces: readonly Place[]): MergedSearchItem[] {
  const items: MergedSearchItem[] = [];
  const usedKeys = new Set<string>();

  for (const result of results) {
    const place: MapPlace = {
      key: `KAKAO:${result.name}:${result.latitude},${result.longitude}`,
      name: result.name,
      address: result.address,
      photoUrl: null,
      coordinate: { latitude: result.latitude, longitude: result.longitude },
    };

    const tourPlace = tourPlaces.find((candidate) => {
      return candidate.ref !== null && isSamePlace(candidate, place, TOUR_MATCH_METERS);
    });

    if (tourPlace !== undefined) {
      if (!usedKeys.has(tourPlace.key)) {
        usedKeys.add(tourPlace.key);
        items.push({ kind: "tour", place: tourPlace });
      }
    } else if (result.address !== result.name && !usedKeys.has(place.key)) {
      usedKeys.add(place.key);
      items.push({ kind: "map", place });
    }
  }

  return items;
}

/** 여행방에 같은 곳을 직접 추가한 후보가 이미 있는지. 서버는 같은 곳도 새로 담으므로 앱에서 막는다. */
export function isMapPlaceAdded(place: MapPlace, roomPlaces: readonly Pick<RoomPlace, "name" | "coordinate" | "source">[]): boolean {
  return roomPlaces.some((roomPlace) => {
    return roomPlace.source === "MANUAL" && isSamePlace(roomPlace, place, ADDED_MATCH_METERS);
  });
}

/** 직접 추가(API-031) 요청 본문. 주소가 없으면 보내지 않는다. */
export function toCustomPlaceRequest({ name, address, coordinate }: MapPlace): AddCustomPlaceRequestDto {
  return address === null ? { name, ...coordinate } : { name, address, ...coordinate };
}

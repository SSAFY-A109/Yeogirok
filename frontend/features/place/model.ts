import type { Category } from "../common/model";
import { toPlaceKey } from "../room/model";
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

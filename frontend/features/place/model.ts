import { assertId, ContractMismatchError } from "../../lib/contract";

// 가정(TBD): API-026 요청·응답 상세는 명세 미확정이다(2026-11-03 Swagger paths 비어 있음).
// 필드는 Figma W12 여행지 검색 화면에서 역산했다. 명세가 나오면 이름과 형식을 맞춘다.

export type PlaceCategoryDto = { code: string; name: string };

export type PlaceDto = {
  placeId: number;
  name: string;
  category: PlaceCategoryDto | null;
  address: string | null;
  photoUrl: string | null;
  latitude: number;
  longitude: number;
};

export type SearchPlacesResponseDto = { items: PlaceDto[]; nextCursor: string | null };

export type PlaceCategory = { code: string; name: string };

export type Place = {
  id: number;
  name: string;
  category: PlaceCategory | null;
  address: string | null;
  photoUrl: string | null;
  coordinate: { latitude: number; longitude: number };
};

export type PlaceSearchPage = { places: Place[]; nextCursor: string | null };

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

export function toPlace(dto: PlaceDto): Place {
  return {
    id: assertId(dto.placeId, "placeId"),
    name: dto.name,
    category: toPlaceCategory(dto.category),
    address: dto.address,
    photoUrl: dto.photoUrl,
    coordinate: toCoordinate(dto.latitude, dto.longitude),
  };
}

export function toPlaceSearchPage(dto: SearchPlacesResponseDto): PlaceSearchPage {
  return { places: dto.items.map(toPlace), nextCursor: dto.nextCursor };
}

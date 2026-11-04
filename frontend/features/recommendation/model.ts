import { toPlaceCategory, type PlaceCategory, type PlaceCategoryDto } from "../place/model";
import { assertId } from "../../lib/contract";

// 가정(TBD): API-033(=080)·081·034 요청·응답 상세는 명세 미확정이다. 필드는 Figma W10·W11에서 역산했다.
// 필수 입력 항목(DEC-15)도 미정이라 클라이언트에서 필수 검사를 하지 않는다.
// 이동 문구(routeHint)는 서버가 계산한 값을 그대로 표시한다(서버 파생값).

export type RecommendationPreferenceDto = { categoryCodes: string[]; regionCode: string | null };

export type RecommendedPlaceDto = {
  placeId: number;
  name: string;
  category: PlaceCategoryDto | null;
  photoUrl: string | null;
  routeHint: string | null;
  reason: string;
};

export type PlaceRecommendationsDto = { requestId: number; criteria: string[]; items: RecommendedPlaceDto[] };

export type RecommendationPreference = { categoryCodes: string[]; regionCode: string | null };

export type RecommendedPlace = {
  placeId: number;
  name: string;
  category: PlaceCategory | null;
  photoUrl: string | null;
  routeHint: string | null;
  reason: string;
};

export type PlaceRecommendations = { requestId: number; criteria: string[]; places: RecommendedPlace[] };

export function toRecommendationPreference(dto: RecommendationPreferenceDto): RecommendationPreference {
  return { categoryCodes: dto.categoryCodes, regionCode: dto.regionCode };
}

export function toPlaceRecommendations(dto: PlaceRecommendationsDto): PlaceRecommendations {
  return {
    requestId: assertId(dto.requestId, "requestId"),
    criteria: dto.criteria,

    places: dto.items.map((item) => {
      return {
        placeId: assertId(item.placeId, "items.placeId"),
        name: item.name,
        category: toPlaceCategory(item.category),
        photoUrl: item.photoUrl,
        routeHint: item.routeHint,
        reason: item.reason,
      };
    }),
  };
}

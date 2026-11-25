import { toPlace, type Place, type PlaceDto, type PlaceRef } from "../place/model";
import { assertId, ContractMismatchError } from "../../lib/contract";

// API-033·034·081은 서버에 아직 없어 팀 Notion "API 명세" 상세 페이지(2026-11-10)를 따른다. 서버에 올라오면 Swagger로 다시 맞춘다.
// TBD: API-081은 상세 페이지를 확인하지 못해 API-033 응답과 같은 모양으로 가정했다. 저장한 조건이 없을 때는 빈 본문(`data: null`)이라고 가정했다.
// TBD: API-033 요청의 `version`이 조건의 version인지 여행방 version인지 명세에 설명이 없다.
//      응답의 `version`을 받아 두었다가 다음 저장에 다시 보낸다(처음에는 API-081 응답의 값, 저장한 조건이 없으면 1).
// 추천 방식(`mode`)·만든 시각(`generatedAt`)·추천 근거(`reasonEvidence`)는 보여 줄 자리가 화면에 없어 쓰지 않는다.

/** 저장한 조건이 없을 때 API-033에 보내는 `version`(TBD). */
export const INITIAL_PREFERENCE_VERSION = 1;

// #region DTO

export type RecommendationPreferenceValuesDto = { regionCode: string | null; categoryCodes: string[]; skipped: boolean };

/**
 * API-033 요청. W10 필수 입력만 보낸다. `regionCode`는 시/군/구 코드, `categoryCodes`는 API-079의 코드(1개 이상)이고
 * `skipped`는 `false`만 받는다. 그 밖의 선택 필드(startAt·endAt·companions·tasteKeywords·transportMode·style·includeMeals·freeText)는 보내지 않는다.
 */
export type SaveRecommendationPreferenceRequestDto = { version: number; regionCode: string; categoryCodes: string[]; skipped: false };

/** API-033·081 응답. */
export type RecommendationPreferenceDto = { roomId: number; preferences: RecommendationPreferenceValuesDto; version: number };

/** API-034 요청. 둘 다 선택이다. */
export type RecommendPlacesRequestDto = { limit?: number; excludedPlaces?: PlaceRef[] };

export type RecommendedPlaceDto = { rank: number; place: PlaceDto; reason: string; reasonEvidence: string[] };

export type PlaceRecommendationsDto = {
  recommendationRequestId: number;
  mode: string;
  items: RecommendedPlaceDto[];
  generatedAt: string;
};

// #endregion

// #region 화면 모델

/** 추천을 실행할 수 있는 조건. 지역은 시/군/구 코드, 카테고리는 1개 이상이다. */
export type RecommendationConditions = { regionCode: string; categoryCodes: string[] };

export type RecommendationPreference = {
  categoryCodes: string[];
  regionCode: string | null;
  /** 다음 저장(API-033)에 보낼 값. */
  version: number;
};

export type RecommendedPlace = {
  /** 추천 순위. 담을 때(API-029) 함께 보낸다. */
  rank: number;
  place: Place;
  reason: string;
};

export type PlaceRecommendations = {
  /** 추천 요청 id(`recommendationRequestId`). 담을 때(API-029) 함께 보낸다. */
  requestId: number;
  places: RecommendedPlace[];
};

// #endregion

export function toRecommendationPreference(dto: RecommendationPreferenceDto): RecommendationPreference {
  const { preferences, version } = dto;

  if (typeof version !== "number" || !Number.isSafeInteger(version)) {
    throw new ContractMismatchError("version");
  }

  if (typeof preferences !== "object" || preferences === null || !Array.isArray(preferences.categoryCodes)) {
    throw new ContractMismatchError("preferences");
  }

  return { categoryCodes: preferences.categoryCodes, regionCode: preferences.regionCode ?? null, version };
}

export function toSaveRecommendationPreferenceRequest(
  conditions: RecommendationConditions,
  version: number,
): SaveRecommendationPreferenceRequestDto {
  return { version, regionCode: conditions.regionCode, categoryCodes: conditions.categoryCodes, skipped: false };
}

function assertRank(value: unknown): number {
  if (typeof value !== "number" || !Number.isSafeInteger(value) || value < 1) {
    throw new ContractMismatchError("items.rank");
  }

  return value;
}

export function toPlaceRecommendations(dto: PlaceRecommendationsDto): PlaceRecommendations {
  return {
    requestId: assertId(dto.recommendationRequestId, "recommendationRequestId"),

    places: dto.items.map((item) => {
      return {
        rank: assertRank(item.rank),
        place: toPlace(item.place),
        reason: item.reason,
      };
    }),
  };
}

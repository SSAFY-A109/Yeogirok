import type { RoomPlaceInfoDto } from "../../../features/room/model";

import { findCategory } from "./common";

/** 목 장소의 공급자. 외부 장소는 `provider`+`providerPlaceId`로 구별한다(API-026·029 명세). */
export const MOCK_PLACE_PROVIDER = "TOUR_API";

type PlaceSeed = [placeId: number, name: string, categoryCode: string | null, address: string, latitude: number, longitude: number];

/** 주소 앞부분으로 고르는 시·군·구 코드(`fixtures/common.ts`의 지역 목록과 같은 값). */
const REGION_CODES: readonly [prefix: string, code: string][] = [
  ["강릉시", "51150"],
  ["속초시", "51210"],
  ["제주시", "50110"],
  ["서귀포시", "50130"],
  ["부산 영도구", "26200"],
];

/** 검색·추천·담기에 쓰는 공개 관광지 목록. 좌표는 공개 장소의 대략적인 값이다. 사진은 넣지 않는다. */
const PLACE_SEEDS: PlaceSeed[] = [
  [101, "안목해변 카페거리", "CAFE", "강릉시 강문동", 37.7717, 128.9474],
  [102, "경포호 산책길", "NATURE", "강릉시 경포동", 37.7955, 128.9083],
  [103, "초당순두부마을", "FOOD", "강릉시 초당동", 37.7905, 128.9147],
  [104, "주문진수산시장", "SHOPPING", "강릉시 주문진읍", 37.8935, 128.8253],
  [105, "주문진해변", "NATURE", "강릉시 주문진읍", 37.9, 128.8205],
  [106, "주문진 등대", "SIGHT", "강릉시 주문진읍", 37.893, 128.8306],
  [107, "주문진항 방파제", null, "강릉시 주문진읍", 37.8913, 128.8307],
  [108, "오죽헌", "CULTURE", "강릉시 죽헌동", 37.7794, 128.8784],
  [109, "강릉중앙시장", "SHOPPING", "강릉시 성남동", 37.7545, 128.8983],
  [110, "사천해변", "NATURE", "강릉시 사천면", 37.8376, 128.877],
  [111, "경포해변", "NATURE", "강릉시 강문동", 37.8055, 128.9087],
  // 112~123: 메인 인기 장소(features/explore/mocks/explore.mock.js rankings)와 같은 id. 새 여행방에 이어 담을 때 쓴다.
  [112, "경포호", "NATURE", "강릉시 저동", 37.7957, 128.8996],
  [113, "주문진항", "NATURE", "강릉시 주문진읍", 37.8932, 128.8271],
  [114, "강문해변", "NATURE", "강릉시 강문동", 37.7955, 128.9185],
  [115, "참소리박물관", "CULTURE", "강릉시 저동", 37.7958, 128.9174],
  [116, "선교장", "CULTURE", "강릉시 운정동", 37.786, 128.884],
  [117, "안목 커피거리", "SIGHT", "강릉시 견소동", 37.7728, 128.9476],
  [118, "정동진역", "SIGHT", "강릉시 강동면", 37.6916, 129.0343],
  [119, "중앙시장 먹거리", "FOOD", "강릉시 성남동", 37.7547, 128.8972],
  [120, "테라로사 본점", "CAFE", "강릉시 구정면", 37.7437, 128.9068],
  [121, "봉봉방앗간", "CAFE", "강릉시 명주동", 37.7536, 128.8987],
  [122, "정동진 바다열차", "ACTIVITY", "강릉시 강동면", 37.6916, 129.0343],
  [123, "중앙시장", "SHOPPING", "강릉시 성남동", 37.7547, 128.8972],
  [201, "성산일출봉", "NATURE", "서귀포시 성산읍", 33.4581, 126.9425],
  [202, "동문재래시장", "SHOPPING", "제주시 이도1동", 33.5123, 126.5276],
  [301, "흰여울문화마을", "CULTURE", "부산 영도구 영선동", 35.0781, 129.0453],
  [302, "태종대", "NATURE", "부산 영도구 동삼동", 35.0531, 129.0874],
  [303, "영도 해녀촌", "FOOD", "부산 영도구 동삼동", 35.0756, 129.0805],
  [401, "속초관광수산시장", "SHOPPING", "속초시 중앙동", 38.2048, 128.5903],
  [402, "영금정", "SIGHT", "속초시 영랑동", 38.2133, 128.6007],
  [403, "아바이마을", "CULTURE", "속초시 청호동", 38.2006, 128.5999],
];

/** 주소의 마지막 단어(동·읍·면). 장소 행 보조 문구에 쓴다. */
export function toAreaName(address: string | null): string | null {
  return address?.split(" ").at(-1) ?? null;
}

function toRegionCode(address: string): string | null {
  return REGION_CODES.find(([prefix]) => {
    return address.startsWith(prefix);
  })?.[1] ?? null;
}

/** 서버 `PlaceInfo` 모양의 목 장소 목록. `providerPlaceId`는 위 표의 숫자를 문자열로 쓴다. 사진은 넣지 않는다. */
export const placeCatalog: RoomPlaceInfoDto[] = PLACE_SEEDS.map(([placeId, name, categoryCode, address, latitude, longitude]) => {
  return {
    provider: MOCK_PLACE_PROVIDER,
    providerPlaceId: String(placeId),
    name,
    latitude,
    longitude,
    address,
    categoryCode,
    categoryName: categoryCode === null ? null : findCategory(categoryCode)?.name ?? null,
    regionCode: toRegionCode(address),
    regionName: toAreaName(address),
    imageUrl: null,
    imageAttribution: null,
  };
});

export function findCatalogPlace(providerPlaceId: string): RoomPlaceInfoDto | null {
  return placeCatalog.find((place) => {
    return place.providerPlaceId === providerPlaceId;
  }) ?? null;
}

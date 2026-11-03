import type { PlaceDto } from "../../../features/place/model";

import { findCategory } from "./common";

type PlaceSeed = [placeId: number, name: string, categoryCode: string | null, address: string, latitude: number, longitude: number];

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
  [201, "성산일출봉", "NATURE", "서귀포시 성산읍", 33.4581, 126.9425],
  [202, "동문재래시장", "SHOPPING", "제주시 이도1동", 33.5123, 126.5276],
  [301, "흰여울문화마을", "CULTURE", "부산 영도구 영선동", 35.0781, 129.0453],
  [302, "태종대", "NATURE", "부산 영도구 동삼동", 35.0531, 129.0874],
  [303, "영도 해녀촌", "FOOD", "부산 영도구 동삼동", 35.0756, 129.0805],
  [401, "속초관광수산시장", "SHOPPING", "속초시 중앙동", 38.2048, 128.5903],
  [402, "영금정", "SIGHT", "속초시 영랑동", 38.2133, 128.6007],
  [403, "아바이마을", "CULTURE", "속초시 청호동", 38.2006, 128.5999],
];

export const placeCatalog: PlaceDto[] = PLACE_SEEDS.map(([placeId, name, categoryCode, address, latitude, longitude]) => {
  return {
    placeId,
    name,
    category: categoryCode === null ? null : findCategory(categoryCode),
    address,
    photoUrl: null,
    latitude,
    longitude,
  };
});

export function findCatalogPlace(placeId: number): PlaceDto | null {
  return placeCatalog.find((place) => {
    return place.placeId === placeId;
  }) ?? null;
}

/** 주소의 마지막 단어(동·읍·면). 장소 행 보조 문구에 쓴다. */
export function toAreaName(address: string | null): string | null {
  return address?.split(" ").at(-1) ?? null;
}

import type { VisitAreaDto } from "../../../features/statistics/model";

/** 방문 분포를 모은 기간(일). DEC-02 결정 전 예시 값이다(Figma W15 "최근 30일"). */
export const VISIT_PERIOD_DAYS = 30;

/** 강릉·속초 일대의 방문 분포 예시. 좌표는 공개 장소의 대표 좌표이고 숫자는 Figma W15·W15-A 시안 값이다. */
export const visitAreasFixture: VisitAreaDto[] = [
  {
    areaId: "gangneung-anmok",
    name: "안목해변 일대",
    latitude: 37.772,
    longitude: 128.9475,
    visitorCount: 312,
    visitCount: 401,
    categoryShares: [
      { categoryCode: "CAFE", categoryName: "카페", percent: 62 },
      { categoryCode: "NATURE", categoryName: "자연 힐링", percent: 28 },
      { categoryCode: "FOOD", categoryName: "맛집", percent: 10 },
    ],
  },
  {
    areaId: "gangneung-gyeongpo-beach",
    name: "경포해변 일대",
    latitude: 37.8057,
    longitude: 128.9085,
    visitorCount: 228,
    visitCount: 290,
    categoryShares: [
      { categoryCode: "NATURE", categoryName: "자연 힐링", percent: 55 },
      { categoryCode: "ACTIVITY", categoryName: "체험 액티비티", percent: 25 },
      { categoryCode: "FOOD", categoryName: "맛집", percent: 20 },
    ],
  },
  {
    areaId: "gangneung-gyeongpo-lake",
    name: "경포호 일대",
    latitude: 37.7955,
    longitude: 128.8963,
    visitorCount: 164,
    visitCount: 212,
    categoryShares: [
      { categoryCode: "NATURE", categoryName: "자연 힐링", percent: 74 },
      { categoryCode: "FOOD", categoryName: "맛집", percent: 18 },
      { categoryCode: "CULTURE", categoryName: "역사 문화", percent: 8 },
    ],
  },
  {
    areaId: "gangneung-ojukheon",
    name: "오죽헌 일대",
    latitude: 37.7792,
    longitude: 128.8786,
    visitorCount: 141,
    visitCount: 168,
    categoryShares: [
      { categoryCode: "CULTURE", categoryName: "역사 문화", percent: 81 },
      { categoryCode: "NATURE", categoryName: "자연 힐링", percent: 19 },
    ],
  },
  {
    areaId: "gangneung-jungang-market",
    name: "강릉중앙시장 일대",
    latitude: 37.7545,
    longitude: 128.899,
    visitorCount: 96,
    visitCount: 133,
    categoryShares: [
      { categoryCode: "SHOPPING", categoryName: "쇼핑 시장", percent: 48 },
      { categoryCode: "FOOD", categoryName: "맛집", percent: 47 },
      { categoryCode: "CULTURE", categoryName: "역사 문화", percent: 5 },
    ],
  },
  {
    areaId: "gangneung-chodang",
    name: "초당순두부마을 일대",
    latitude: 37.791,
    longitude: 128.915,
    visitorCount: 57,
    visitCount: 70,
    categoryShares: [
      { categoryCode: "FOOD", categoryName: "맛집", percent: 88 },
      { categoryCode: "CAFE", categoryName: "카페", percent: 12 },
    ],
  },
  {
    areaId: "gangneung-gangmun",
    name: "강문해변 일대",
    latitude: 37.7952,
    longitude: 128.9188,
    visitorCount: 38,
    visitCount: 45,
    categoryShares: [
      { categoryCode: "NATURE", categoryName: "자연 힐링", percent: 60 },
      { categoryCode: "CAFE", categoryName: "카페", percent: 40 },
    ],
  },
  {
    areaId: "sokcho-beach",
    name: "속초해수욕장 일대",
    latitude: 38.1906,
    longitude: 128.6017,
    visitorCount: 205,
    visitCount: 260,
    categoryShares: [
      { categoryCode: "NATURE", categoryName: "자연 힐링", percent: 58 },
      { categoryCode: "ACTIVITY", categoryName: "체험 액티비티", percent: 27 },
      { categoryCode: "FOOD", categoryName: "맛집", percent: 15 },
    ],
  },
  {
    areaId: "sokcho-jungang-market",
    name: "속초관광수산시장 일대",
    latitude: 38.2047,
    longitude: 128.5904,
    visitorCount: 177,
    visitCount: 231,
    categoryShares: [
      { categoryCode: "FOOD", categoryName: "맛집", percent: 64 },
      { categoryCode: "SHOPPING", categoryName: "쇼핑 시장", percent: 36 },
    ],
  },
];

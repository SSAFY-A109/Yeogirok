/** 방문 분포 목이 세는 발자취 묶음. 실제 개인 발자취가 아니라, 같은 좌표·카테고리로 남은 발자취를 `count`개로 묶은 더미 값이다. */
export type MockFootprintSeed = {
  latitude: number;
  longitude: number;
  categoryCodes: string[];
  count: number;
};

/**
 * 강릉·속초·정동진 일대의 방문 분포 예시. 좌표는 공개 장소의 대표 좌표 근처이고, 수는 Figma W15·W15-A 시안 값에서 나눴다.
 * 한 장소 안에서도 카테고리별로 조금씩 떨어뜨려, 가까이 보면 여러 칸으로 나뉘고 멀리 보면 한 칸으로 묶이게 했다.
 * 카테고리를 여러 개 고른 묶음도 섞어 칸의 카테고리별 수를 더하면 전체보다 커지는 경우를 보여 준다.
 */
export const visitFootprintSeeds: readonly MockFootprintSeed[] = [
  // 안목해변
  { latitude: 37.772, longitude: 128.9475, categoryCodes: ["CAFE"], count: 220 },
  { latitude: 37.7735, longitude: 128.95, categoryCodes: ["CAFE", "NATURE"], count: 29 },
  { latitude: 37.7745, longitude: 128.944, categoryCodes: ["NATURE"], count: 112 },
  { latitude: 37.7695, longitude: 128.9455, categoryCodes: ["FOOD"], count: 40 },
  // 경포해변
  { latitude: 37.8057, longitude: 128.9085, categoryCodes: ["NATURE"], count: 160 },
  { latitude: 37.8085, longitude: 128.911, categoryCodes: ["ACTIVITY"], count: 72 },
  { latitude: 37.803, longitude: 128.906, categoryCodes: ["FOOD", "NATURE"], count: 58 },
  // 경포호
  { latitude: 37.7955, longitude: 128.8963, categoryCodes: ["NATURE"], count: 157 },
  { latitude: 37.798, longitude: 128.8935, categoryCodes: ["FOOD"], count: 38 },
  { latitude: 37.793, longitude: 128.899, categoryCodes: ["CULTURE"], count: 17 },
  // 오죽헌
  { latitude: 37.7792, longitude: 128.8786, categoryCodes: ["CULTURE"], count: 136 },
  { latitude: 37.7815, longitude: 128.881, categoryCodes: ["NATURE", "CULTURE"], count: 32 },
  // 강릉중앙시장·단오공원
  { latitude: 37.7545, longitude: 128.899, categoryCodes: ["SHOPPING", "FOOD"], count: 64 },
  { latitude: 37.756, longitude: 128.9015, categoryCodes: ["FOOD"], count: 62 },
  { latitude: 37.7525, longitude: 128.897, categoryCodes: ["CULTURE"], count: 7 },
  { latitude: 37.7609, longitude: 128.9003, categoryCodes: ["EVENT"], count: 23 },
  // 초당순두부마을·강문해변
  { latitude: 37.791, longitude: 128.915, categoryCodes: ["FOOD"], count: 62 },
  { latitude: 37.7925, longitude: 128.9175, categoryCodes: ["CAFE"], count: 8 },
  { latitude: 37.7952, longitude: 128.9188, categoryCodes: ["NATURE"], count: 27 },
  { latitude: 37.797, longitude: 128.921, categoryCodes: ["CAFE"], count: 18 },
  // 정동진
  { latitude: 37.6905, longitude: 129.0345, categoryCodes: ["SIGHT", "NATURE"], count: 54 },
  // 속초해수욕장
  { latitude: 38.1906, longitude: 128.6017, categoryCodes: ["NATURE"], count: 151 },
  { latitude: 38.193, longitude: 128.604, categoryCodes: ["ACTIVITY"], count: 70 },
  { latitude: 38.1885, longitude: 128.5995, categoryCodes: ["FOOD"], count: 39 },
  // 속초관광수산시장
  { latitude: 38.2047, longitude: 128.5904, categoryCodes: ["FOOD", "SHOPPING"], count: 148 },
  { latitude: 38.2065, longitude: 128.593, categoryCodes: ["SHOPPING"], count: 83 },
];

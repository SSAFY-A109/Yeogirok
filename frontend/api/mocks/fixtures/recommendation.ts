/** 추천 결과 예시(Figma W11). 이동 문구와 추천 이유는 서버가 만드는 값이다. */
export const recommendationSeeds: { placeId: number; routeHint: string; reason: string }[] = [
  { placeId: 108, routeHint: "경포호에서 차로 10분", reason: "'역사 문화'를 골랐고, 경포호 바로 옆이라 오전 동선에 넣기 쉬워요." },
  { placeId: 109, routeHint: "강릉역에서 차로 5분", reason: "'맛집'을 골랐고, 둘째 날 돌아가기 전에 들르기 좋아요." },
  { placeId: 110, routeHint: "주문진 가는 길에 차로 15분", reason: "주문진수산시장 가는 길에 있어 이동이 거의 늘지 않아요." },
  { placeId: 111, routeHint: "경포호에서 걸어서 15분", reason: "'자연 힐링'을 골랐고, 산책길과 이어져 저녁에 들르기 좋아요." },
];

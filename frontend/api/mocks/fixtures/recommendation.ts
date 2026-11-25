/** 추천 결과 예시(Figma W11). 추천 이유와 근거는 서버가 만드는 값이다. `providerPlaceId`는 `fixtures/place.ts`의 장소다. */
export const recommendationSeeds: { providerPlaceId: string; reason: string; reasonEvidence: string[] }[] = [
  { providerPlaceId: "108", reason: "'역사 문화'를 골랐고, 경포호 바로 옆이라 오전 동선에 넣기 쉬워요.", reasonEvidence: ["취향 키워드 일치"] },
  { providerPlaceId: "109", reason: "'맛집'을 골랐고, 둘째 날 돌아가기 전에 들르기 좋아요.", reasonEvidence: ["취향 키워드 일치"] },
  { providerPlaceId: "110", reason: "주문진수산시장 가는 길에 있어 이동이 거의 늘지 않아요.", reasonEvidence: ["담은 장소와 가까움"] },
  { providerPlaceId: "111", reason: "'자연 힐링'을 골랐고, 산책길과 이어져 저녁에 들르기 좋아요.", reasonEvidence: ["취향 키워드 일치"] },
];

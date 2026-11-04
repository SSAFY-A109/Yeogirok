// 여행방 담기 mock 데이터. 코스·인기 장소를 여행방에 가져오는 API(API-030) 명세를 받기 전까지 쓰는 임시 원천이다(TBD).
// 코스 검색·추천·상세와 인기 장소는 MSW(api/mocks/handlers/course.ts)로 옮겼다.

/** @type {{ id: string, name: string, dates: string, memberCount: number, placeCount: number }[]} */
export const rooms = [
  { id: "room-1", name: "강릉 우정여행", dates: "11.08 – 11.09", memberCount: 4, placeCount: 5 },
  { id: "room-2", name: "제주 가을 여행", dates: "날짜 미정", memberCount: 2, placeCount: 0 },
];

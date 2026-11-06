import type { CreateFootprintResponseDto } from "../../features/footprint/model";
import type { RecommendationPreferenceDto } from "../../features/recommendation/model";

import { createRoomFixtures, type MockRoom } from "./fixtures/room";

/** 처리한 멱등키. 같은 키로 같은 내용이 오면 저장한 발자취를 다시 돌려준다(backend `IdempotencyService`). */
type MockIdempotencyRecord = { requestHash: string; footprintId: number };

type MockDb = {
  rooms: Map<number, MockRoom>;
  preferences: Map<number, RecommendationPreferenceDto>;
  footprints: CreateFootprintResponseDto[];
  footprintRequests: Map<string, MockIdempotencyRecord>;
  nextRoomId: number;
  nextRoomPlaceId: number;
  nextRecommendationRequestId: number;
  nextFootprintId: number;
};

function createDb(): MockDb {
  const rooms = createRoomFixtures();

  return {
    rooms: new Map(rooms.map((room) => {
      return [room.detail.roomId, room];
    })),

    preferences: new Map(),
    footprints: [],
    footprintRequests: new Map(),
    nextRoomId: 100,
    nextRoomPlaceId: 9000,
    nextRecommendationRequestId: 1,
    nextFootprintId: 1,
  };
}

/** 앱 실행 중 목 서버의 메모리 상태. 생성·수정·담기·빼기 결과가 화면 사이에서 이어지게 한다. */
export const mockDb: MockDb = createDb();

/** 테스트에서 목 상태를 처음으로 되돌린다. */
export function resetMockDb(): void {
  Object.assign(mockDb, createDb());
}

/** 장소 수·일정 요약처럼 다른 값에서 나오는 필드를 다시 맞춘다. */
export function syncRoomDerivedFields(room: MockRoom): void {
  room.detail.placeCount = room.places.length;
  room.detail.memberCount = room.detail.members.length;
  room.detail.schedule = room.schedule === null ? null : { createdAt: room.schedule.createdAt };
}

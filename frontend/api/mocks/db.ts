import type { CreateFootprintResponseDto } from "../../features/footprint/model";
import type { RecommendationPreferenceDto } from "../../features/recommendation/model";
import type { ScheduleBodyDto } from "../../features/schedule/model";

import { createRoomFixtures, type MockRoom } from "./fixtures/room";
import { createTipFixtures, type MockTip } from "./fixtures/tip";
import type { MockIdempotentResponse, MockUpload } from "./fixtures/media";
import { createRecordFixtures, type MockRecord } from "./fixtures/record";

/** 처리한 멱등키. 같은 키로 같은 내용이 오면 저장한 발자취를 다시 돌려준다(backend `IdempotencyService`). */
type MockIdempotencyRecord = { requestHash: string; footprintId: number };

/** 목 서버가 들고 있는 일정 초안 작업. `readyAt`이 지나야 완료로 보인다. */
export type MockScheduleDraft = {
  draftId: number;
  roomId: number;
  idempotencyKey: string;
  /** 같은 멱등키로 다른 내용을 보냈는지 가리는 요청 본문. */
  requestBody: string;
  baseVersion: number | null;
  readyAt: number;
  schedule: ScheduleBodyDto | null;
  failureCode: string | null;
};

type MockDb = {
  tips: Map<number, MockTip>;
  uploads: Map<string, MockUpload>;
  tipIdempotency: Map<string, MockIdempotentResponse>;
  uploadIdempotency: Map<string, MockIdempotentResponse>;
  records: Map<number, MockRecord>;
  recordIdempotency: Map<string, MockIdempotentResponse>;
  nextRecordEntityId: number;
  nextTipId: number;
  nextUploadId: number;
  rooms: Map<number, MockRoom>;
  preferences: Map<number, RecommendationPreferenceDto>;
  footprints: CreateFootprintResponseDto[];
  footprintRequests: Map<string, MockIdempotencyRecord>;
  scheduleDrafts: Map<number, MockScheduleDraft>;
  nextRoomId: number;
  nextRoomPlaceId: number;
  nextRecommendationRequestId: number;
  nextFootprintId: number;
  nextScheduleDraftId: number;
};

function createDb(): MockDb {
  const rooms = createRoomFixtures();

  return {
    tips: createTipFixtures(),
    uploads: new Map(),
    tipIdempotency: new Map(),
    uploadIdempotency: new Map(),
    records: createRecordFixtures(rooms),
    recordIdempotency: new Map(),
    nextRecordEntityId: 2000,
    nextTipId: 100,
    nextUploadId: 1,

    rooms: new Map(rooms.map((room) => {
      return [room.detail.roomId, room];
    })),

    preferences: new Map(),
    footprints: [],
    footprintRequests: new Map(),
    scheduleDrafts: new Map(),
    nextRoomId: 100,
    nextRoomPlaceId: 9000,
    nextRecommendationRequestId: 1,
    nextFootprintId: 1,
    nextScheduleDraftId: 1,
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

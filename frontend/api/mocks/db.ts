import type { CreateFootprintResponseDto } from "../../features/footprint/model";
import type { RecommendationPreferenceDto } from "../../features/recommendation/model";
import type { ChatMessageDto } from "../../features/room/chat-model";
import type { AddRoomPlacesResponseDto, ChatReadStateDto, CreateRoomResponseDto, ImportCoursePlacesResponseDto } from "../../features/room/model";

import { chatReadKey, createChatMessageFixtures, createChatReadFixtures, toChatReadState } from "./fixtures/chat";
import { createRoomFixtures, type MockRoom } from "./fixtures/room";
import type { MockScheduleDraft } from "./fixtures/schedule";
import { createFootprintFixtures, type MockFootprint } from "./fixtures/footprint";
import { createTipFixtures, type MockTip } from "./fixtures/tip";
import type { MockIdempotentResponse, MockUpload } from "./fixtures/media";

/** 처리한 멱등키. 같은 키로 같은 내용이 오면 저장한 발자취를 다시 돌려준다(backend `IdempotencyService`). */
type MockIdempotencyRecord = { requestHash: string; response: CreateFootprintResponseDto };

type MockDb = {
  tips: Map<number, MockTip>;
  uploads: Map<string, MockUpload>;
  tipIdempotency: Map<string, MockIdempotentResponse>;
  uploadIdempotency: Map<string, MockIdempotentResponse>;
  nextTipId: number;
  nextUploadId: number;
  rooms: Map<number, MockRoom>;
  /** 여행방별 채팅 메시지(오래된 → 최신). 키는 여행방 id다. */
  roomMessages: Map<number, ChatMessageDto[]>;
  /** 참여자별 채팅 읽은 위치(메시지 id). 키는 `chatReadKey(여행방 id, 사용자 id)`다. */
  chatLastReadIds: Map<string, number>;
  roomCreateRequests: Map<string, { requestBody: string; response: CreateRoomResponseDto }>;
  courseImportRequests: Map<string, { requestBody: string; response: ImportCoursePlacesResponseDto }>;
  /** API-029 담기 요청. 키는 `여행방 id:멱등키`다. */
  roomPlaceRequests: Map<string, { requestBody: string; response: AddRoomPlacesResponseDto }>;
  preferences: Map<number, RecommendationPreferenceDto>;
  footprints: Map<number, MockFootprint>;
  footprintRequests: Map<string, MockIdempotencyRecord>;
  /** 일정 초안. 키는 `draftKey`다. */
  scheduleDrafts: Map<string, MockScheduleDraft>;
  nextRoomId: number;
  nextRoomPlaceId: number;
  nextRecommendationRequestId: number;
  nextFootprintId: number;
  /** 일정 초안 키·작업 id를 만들 때 쓰는 일련번호. */
  nextScheduleSeq: number;
};

function createDb(): MockDb {
  const rooms = createRoomFixtures();
  const footprints = createFootprintFixtures();

  return {
    tips: createTipFixtures(),
    uploads: new Map(),
    tipIdempotency: new Map(),
    uploadIdempotency: new Map(),
    nextTipId: 100,
    nextUploadId: 1,

    rooms: new Map(rooms.map((room) => {
      return [room.id, room];
    })),

    roomMessages: createChatMessageFixtures(),
    chatLastReadIds: createChatReadFixtures(),
    roomCreateRequests: new Map(),
    courseImportRequests: new Map(),
    roomPlaceRequests: new Map(),

    preferences: new Map(),

    footprints: new Map(footprints.map((item) => {
      return [item.id, item];
    })),

    footprintRequests: new Map(),
    scheduleDrafts: new Map(),
    nextRoomId: 100,
    nextRoomPlaceId: 9000,
    nextRecommendationRequestId: 1,

    nextFootprintId: Math.max(...footprints.map((item) => {
      return item.id;
    })) + 1,

    nextScheduleSeq: 1,
  };
}

/** 앱 실행 중 목 서버의 메모리 상태. 생성·수정·담기·빼기 결과가 화면 사이에서 이어지게 한다. */
export const mockDb: MockDb = createDb();

/** 참여자의 채팅 읽음 상태(API-020·022·076·088이 함께 쓴다). 저장된 메시지와 읽은 위치로 계산한다. */
export function getMockChatReadState(roomId: number, userId: number): ChatReadStateDto {
  const lastReadMessageId = mockDb.chatLastReadIds.get(chatReadKey(roomId, userId)) ?? null;
  return toChatReadState(mockDb.roomMessages.get(roomId) ?? [], lastReadMessageId, userId);
}

/** 테스트에서 목 상태를 처음으로 되돌린다. */
export function resetMockDb(): void {
  Object.assign(mockDb, createDb());
}

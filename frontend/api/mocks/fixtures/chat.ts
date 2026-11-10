import type { ChatMessageDto, ChatSenderDto } from "../../../features/room/chat-model";
import type { ChatReadStateDto } from "../../../features/room/model";

import { MOCK_ME } from "./room";

const JIYUN: ChatSenderDto = { id: MOCK_ME.userId, nickname: MOCK_ME.nickname };
const MINSEO: ChatSenderDto = { id: 2, nickname: "민서" };
const HAJUN: ChatSenderDto = { id: 3, nickname: "하준" };

/** 탈퇴한 회원이 보낸 메시지. 서버는 `sender.id`·`clientMessageId`를 `null`로, 이름을 "탈퇴한 사용자"로 준다. */
const WITHDRAWN: ChatSenderDto = { id: null, nickname: "탈퇴한 사용자" };

type MessageSeed = [sender: ChatSenderDto, sentAt: string, body: string];

/** 1번 방(강릉 우정여행)의 대화. 마지막 4개는 Figma W09-2와 같고, 그 앞은 이전 대화를 더 받는 흐름을 확인하는 데 쓴다. */
const GANGNEUNG_MESSAGES: MessageSeed[] = [
  [MINSEO, "2026-10-30 19:02", "다음 주 강릉 가는 거 맞지?"],
  [JIYUN, "2026-10-30 19:03", "응! 8일 일요일 출발이야"],
  [HAJUN, "2026-10-30 19:05", "나 차 가져갈게. 몇 시에 모일까?"],
  [JIYUN, "2026-10-30 19:06", "아침 8시 어때?"],
  [MINSEO, "2026-10-30 19:07", "좋아 8시"],
  [WITHDRAWN, "2026-10-30 19:10", "나는 이번엔 못 갈 것 같아. 다음에 같이 가자"],
  [HAJUN, "2026-10-30 19:12", "아쉽다. 다음엔 꼭 같이 가자"],
  [JIYUN, "2026-10-31 11:20", "해솔님 코스에서 안목해변 카페거리 담아 뒀어"],
  [MINSEO, "2026-10-31 11:22", "오 거기 가 보고 싶었어"],
  [HAJUN, "2026-10-31 11:25", "커피는 내가 살게"],
  [MINSEO, "2026-10-31 11:26", "그럼 점심은 내가!"],
  [JIYUN, "2026-10-31 11:30", "경포호 산책길도 같이 담았어"],
  [HAJUN, "2026-10-31 11:41", "산책 좋다. 날씨만 좋으면"],
  [MINSEO, "2026-10-31 14:03", "숙소는 어디로 잡을까?"],
  [JIYUN, "2026-10-31 14:05", "경포 쪽이 이동하기 편할 것 같아"],
  [HAJUN, "2026-10-31 14:08", "주차 되는 곳이면 좋겠어"],
  [MINSEO, "2026-10-31 14:10", "찾아볼게"],
  [MINSEO, "2026-11-01 10:15", "숙소 예약했어. 주차도 돼"],
  [JIYUN, "2026-11-01 10:16", "고마워!"],
  [HAJUN, "2026-11-01 10:20", "저녁은 뭐 먹지?"],
  [MINSEO, "2026-11-01 10:22", "초당순두부마을 어때? AI 추천에 떠서 담아 놨어"],
  [JIYUN, "2026-11-01 10:24", "좋아 순두부 먹자"],
  [HAJUN, "2026-11-01 10:30", "나도 좋아"],
  [JIYUN, "2026-11-01 20:45", "장소 4곳 모였으니 내일 일정 만들어 볼게"],
  [HAJUN, "2026-11-01 20:47", "부탁해"],
  [MINSEO, "2026-11-02 21:04", "주문진수산시장도 담아 놨어!"],
  [HAJUN, "2026-11-02 21:06", "근데 첫날에 다 넣으면 빡빡하지 않아?"],
  [JIYUN, "2026-11-02 21:10", "AI 일정 돌려 보니 첫날엔 주문진 들르면 18시까지 못 돌아온대. 2일차로 옮길게"],
  [MINSEO, "2026-11-02 21:11", "좋아, 둘째 날 아침에 가자"],
];

const GANGNEUNG_ROOM_ID = 1;

/** 1번 방에서 내가 읽은 마지막 메시지(내가 보낸 21:10 메시지). 그 뒤 민서의 메시지가 안 읽은 메시지다. */
const GANGNEUNG_LAST_READ_MESSAGE_ID = 28;

/** 여행방별 메시지 초기 상태(오래된 → 최신). 메시지 id는 방과 상관없이 1부터 이어진다. 1번 방에만 대화가 있다. */
export function createChatMessageFixtures(): Map<number, ChatMessageDto[]> {
  const messages = GANGNEUNG_MESSAGES.map(([sender, sentAt, body], index): ChatMessageDto => {
    const id = index + 1;
    return { id, sender, body, createdAt: `${sentAt}:00+09`, clientMessageId: sender.id === null ? null : `mock-client-message-${id}` };
  });

  return new Map([[GANGNEUNG_ROOM_ID, messages]]);
}

/** 참여자별 읽은 위치의 키. */
export function chatReadKey(roomId: number, userId: number): string {
  return `${roomId}:${userId}`;
}

/** 참여자별 읽은 위치 초기 상태. 키는 `chatReadKey`다. 없으면 한 번도 읽지 않은 것이다. */
export function createChatReadFixtures(): Map<string, number> {
  return new Map([[chatReadKey(GANGNEUNG_ROOM_ID, MOCK_ME.userId), GANGNEUNG_LAST_READ_MESSAGE_ID]]);
}

/** 읽은 위치보다 큰 타인 메시지가 있으면 안 읽음이다(서버 `ChatReadStates`). 읽은 위치가 없으면 타인 메시지 전체를 본다. */
export function toChatReadState(messages: readonly ChatMessageDto[], lastReadMessageId: number | null, userId: number): ChatReadStateDto {
  const hasUnreadMessages = messages.some((message) => {
    return message.id > (lastReadMessageId ?? 0) && message.sender.id !== userId;
  });

  return { lastReadMessageId, hasUnreadMessages };
}

import type {
  ChatReadStateDto,
  RoomDetailDto,
  RoomListItemDto,
  RoomPlaceDto,
  RoomPlaceSourceDto,
  TransportModeDto,
} from "../../../features/room/model";
import type { ScheduleViewDto } from "../../../features/schedule/model";

import { findCatalogPlace } from "./place";
import { planFixtureSchedule } from "./schedule";

export type MockMember = { userId: number; nickname: string; profileImageUrl: string | null; isCreator: boolean };

/** 목에서 "나"로 쓰는 사용자. `GET /users/me` 목 응답의 id와 같다. */
export const MOCK_ME: MockMember = { userId: 1, nickname: "지윤", profileImageUrl: null, isCreator: true };

/** 생성자를 포함한 최대 참여 인원(서버 `MAX_MEMBERS`). */
export const ROOM_MEMBER_LIMIT = 10;

const NICKNAMES = ["민서", "하준", "윤호", "서연", "도윤", "수아", "예준", "하린", "시우", "유나", "현우"];

const others: MockMember[] = NICKNAMES.map((nickname, index) => {
  return { userId: index + 2, nickname, profileImageUrl: null, isCreator: false };
});

/**
 * 목 서버가 들고 있는 여행방. 응답 DTO는 `toRoomListItem`·`toRoomDetail`로 만든다.
 * `places`에는 뺀 후보도 `removedAt`과 함께 남긴다(API-094로 복원한다).
 */
export type MockRoom = {
  id: number;
  title: string;
  startDate: string | null;
  endDate: string | null;
  transportMode: TransportModeDto;
  members: MockMember[];
  places: RoomPlaceDto[];
  recordIds: number[];
  /** 여행방 version. 정보 수정·후보 담기·빼기·복원·일정 저장 때마다 1 오른다. */
  version: number;
  /** 확정 일정(API-036 `schedule`). 없으면 `null`. */
  schedule: ScheduleViewDto | null;
  /** 확정 일정이 있는 방에서 후보 목록이 바뀌면 `true`, 일정을 새로 저장하면 `false`. */
  scheduleNeedsRecalculation: boolean;
  inviteToken: string;
};

export function createRoomPlaceFixture(
  id: number,
  providerPlaceId: string,
  sourceType: RoomPlaceSourceDto,
  addedBy: MockMember = MOCK_ME,
  sourceRecordId: number | null = null,
): RoomPlaceDto {
  const place = findCatalogPlace(providerPlaceId);

  if (!place) {
    throw new Error(`목 장소 목록에 없는 providerPlaceId: ${providerPlaceId}`);
  }

  return { id, place, sourceType, sourceRecordId, sourceRecord: null, addedBy: addedBy.userId, removedAt: null };
}

/** 빼지 않은 후보. */
export function getActivePlaces(room: MockRoom): RoomPlaceDto[] {
  return room.places.filter((place) => {
    return place.removedAt == null;
  });
}

/** @param chatReadState 요청한 사람의 채팅 읽음 상태(`getMockChatReadState`). */
export function toRoomListItem(room: MockRoom, chatReadState: ChatReadStateDto): RoomListItemDto {
  return {
    id: room.id,
    title: room.title,
    startDate: room.startDate,
    endDate: room.endDate,
    transportMode: room.transportMode,
    memberCount: room.members.length,

    members: room.members.map(({ userId, nickname, profileImageUrl }) => {
      return { userId, nickname, profileImageUrl };
    }),

    placeCount: getActivePlaces(room).length,
    recordIds: room.recordIds,
    chatReadState,
  };
}

/** @param chatReadState 요청한 사람의 채팅 읽음 상태(`getMockChatReadState`). */
export function toRoomDetail(room: MockRoom, chatReadState: ChatReadStateDto): RoomDetailDto {
  return {
    id: room.id,
    title: room.title,
    startDate: room.startDate,
    endDate: room.endDate,
    transportMode: room.transportMode,

    members: room.members.map(({ userId, nickname, isCreator }) => {
      return { userId, nickname, isCreator };
    }),

    // 상세의 후보에는 removedAt 을 싣지 않는다(서버 `RoomDetailResponse.Place`).
    places: getActivePlaces(room).map(({ id, place, sourceType, sourceRecordId, sourceRecord, addedBy }) => {
      return { id, place, sourceType, sourceRecordId, sourceRecord, addedBy };
    }),

    recordIds: room.recordIds,
    version: room.version,
    chatReadState,
    schedule: room.schedule,
  };
}

type RoomSeed = Pick<MockRoom, "id" | "title" | "startDate" | "endDate" | "transportMode" | "members" | "places" | "recordIds">;

type ScheduleDaySeed = { date: string; roomPlaceIds: number[] };

const FIXTURE_FIRST_DAY_START = "10:00:00";
const FIXTURE_DAY_START = "09:00:00";
const FIXTURE_DAY_END = "18:00:00";

function makeRoom(seed: RoomSeed, scheduleDays: ScheduleDaySeed[] | null = null): MockRoom {
  const schedule = scheduleDays === null ? null : planFixtureSchedule({
    places: seed.places,
    transportMode: seed.transportMode,

    days: scheduleDays.map((day, index) => {
      return { ...day, startTime: index === 0 ? FIXTURE_FIRST_DAY_START : FIXTURE_DAY_START, endTime: FIXTURE_DAY_END };
    }),
  });

  return { ...seed, version: 1, schedule, scheduleNeedsRecalculation: false, inviteToken: `mock-invite-${seed.id}` };
}

/**
 * 여행방 초기 상태(오늘 2026-11-03 기준).
 * 1 강릉: 일정 만들 준비됨(W09) · 2 제주: 날짜 미정·혼자(W06-L2) · 3 부산: 지난 여행·일정 있음 · 4 속초: 정원 가득·일정 있음(W08-F, W06-E2)
 * 5·6: 초대 링크 확인용 · 7 속초 당일치기: 지난 여행·기록 없음
 */
export function createRoomFixtures(): MockRoom[] {
  return [
    makeRoom({
      id: 1,
      title: "강릉 우정여행",
      startDate: "2026-11-08",
      endDate: "2026-11-09",
      transportMode: "CAR",
      members: [MOCK_ME, others[0], others[1]],
      recordIds: [],

      places: [
        createRoomPlaceFixture(1001, "101", "COURSE", MOCK_ME, 1),
        createRoomPlaceFixture(1002, "102", "COURSE", MOCK_ME, 1),
        createRoomPlaceFixture(1003, "103", "AI", others[0]),
        createRoomPlaceFixture(1004, "104", "SEARCH", others[1]),
      ],
    }),
    makeRoom({
      id: 2,
      title: "제주 가을 여행",
      startDate: null,
      endDate: null,
      transportMode: "TRANSIT",
      members: [MOCK_ME],
      recordIds: [],
      places: [createRoomPlaceFixture(2001, "201", "SEARCH")],
    }),
    makeRoom(
      {
        id: 3,
        title: "부산 영도 1박 2일",
        startDate: "2026-10-03",
        endDate: "2026-10-04",
        transportMode: "CAR",
        members: [MOCK_ME, others[2]],
        recordIds: [1],

        places: [
          createRoomPlaceFixture(3001, "301", "SEARCH"),
          createRoomPlaceFixture(3002, "302", "SEARCH"),
          createRoomPlaceFixture(3003, "303", "AI"),
        ],
      },
      [
        { date: "2026-10-03", roomPlaceIds: [3001, 3003] },
        { date: "2026-10-04", roomPlaceIds: [3002] },
      ],
    ),
    makeRoom(
      {
        id: 4,
        title: "속초 주말 여행",
        startDate: "2026-11-21",
        endDate: "2026-11-22",
        transportMode: "TRANSIT",
        members: [{ ...others[2], isCreator: true }, { ...MOCK_ME, isCreator: false }, ...others.slice(3, 11)],
        recordIds: [],

        places: [
          createRoomPlaceFixture(4001, "401", "SEARCH"),
          createRoomPlaceFixture(4002, "402", "COURSE", others[3], 1),
          createRoomPlaceFixture(4003, "403", "AI"),
        ],
      },
      [
        { date: "2026-11-21", roomPlaceIds: [4001, 4002] },
        { date: "2026-11-22", roomPlaceIds: [4003] },
      ],
    ),
    // 5·6은 내가 아직 참여하지 않은 방이다. 초대 링크(`/invite/mock-invite-5`, `-6`)로 참여(API-025)와 정원 초과(RO007)를 확인한다.
    makeRoom({
      id: 5,
      title: "여수 밤바다 여행",
      startDate: "2026-12-05",
      endDate: "2026-12-06",
      transportMode: "CAR",
      members: [{ ...others[4], isCreator: true }, others[5]],
      recordIds: [],
      places: [],
    }),
    makeRoom({
      id: 6,
      title: "전주 한옥마을 당일치기",
      startDate: "2026-12-12",
      endDate: "2026-12-12",
      transportMode: "TRANSIT",
      members: [{ ...others[0], isCreator: true }, ...others.slice(1, ROOM_MEMBER_LIMIT)],
      recordIds: [],
      places: [],
    }),
    // 7은 기록이 없는 지난 여행이다. 기록 만들기(API-057)를 확인한다.
    makeRoom({
      id: 7,
      title: "속초 당일치기",
      startDate: "2026-09-21",
      endDate: "2026-09-21",
      transportMode: "CAR",
      members: [MOCK_ME],
      recordIds: [],
      places: [],
    }),
  ];
}

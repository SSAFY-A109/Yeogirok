import type { RoomDetailDto, RoomMemberDto, RoomPlaceDto } from "../../../features/room/model";
import type { RoomScheduleDto } from "../../../features/schedule/model";

import { findCatalogPlace, toAreaName } from "./place";
import { planSchedule } from "./schedule";

/** 목에서 "나"로 쓰는 사용자. */
export const MOCK_ME: RoomMemberDto = { userId: 1, nickname: "지윤", profileImageUrl: null, me: true };

export const ROOM_MEMBER_LIMIT = 10;

const NICKNAMES = ["민서", "하준", "윤호", "서연", "도윤", "수아", "예준", "하린", "시우", "유나", "현우"];

const others: RoomMemberDto[] = NICKNAMES.map((nickname, index) => {
  return { userId: index + 2, nickname, profileImageUrl: null, me: false };
});

export function createRoomPlaceFixture(
  roomPlaceId: number,
  placeId: number,
  source: RoomPlaceDto["source"],
  courseOwnerNickname: string | null = null,
  addedBy: RoomMemberDto = MOCK_ME,
): RoomPlaceDto {
  const place = findCatalogPlace(placeId);

  if (!place) {
    throw new Error(`목 장소 목록에 없는 placeId: ${placeId}`);
  }

  return {
    roomPlaceId,
    placeId,
    name: place.name,
    category: place.category,
    areaName: toAreaName(place.address),
    photoUrl: place.photoUrl,
    source,
    courseOwnerNickname,
    addedBy: { userId: addedBy.userId, nickname: addedBy.nickname },
  };
}

export type MockRoom = { detail: RoomDetailDto; places: RoomPlaceDto[]; schedule: RoomScheduleDto | null };

const FIXTURE_STAY_MINUTES = 60;

/** 목 일정. 날마다 정한 순서로 목 계산기가 시각과 이동 시간을 채운다. */
function makeSchedule(
  places: RoomPlaceDto[],
  transportMode: string,
  createdAt: string,
  days: { date: string; roomPlaceIds: number[] }[],
): RoomScheduleDto {
  const body = planSchedule({
    roomPlaces: places,
    transportMode,

    request: {
      pace: "NORMAL",

      days: days.map((day, index) => {
        return {
          date: day.date,
          startTime: index === 0 ? "10:00" : "09:00",
          endTime: "18:00",

          stops: day.roomPlaceIds.map((roomPlaceId) => {
            return { roomPlaceId, stayMinutes: FIXTURE_STAY_MINUTES };
          }),
        };
      }),
    },
  });

  return { ...body, version: 1, createdAt };
}

function makeRoom(
  detail: Omit<RoomDetailDto, "memberCount" | "memberLimit" | "placeCount" | "schedule">,
  places: RoomPlaceDto[],
  schedule: RoomScheduleDto | null = null,
): MockRoom {
  return {
    detail: {
      ...detail,
      memberCount: detail.members.length,
      memberLimit: ROOM_MEMBER_LIMIT,
      placeCount: places.length,
      schedule: schedule === null ? null : { createdAt: schedule.createdAt },
    },
    places,
    schedule,
  };
}

function makeRoomWithSchedule(
  detail: Omit<RoomDetailDto, "memberCount" | "memberLimit" | "placeCount" | "schedule">,
  places: RoomPlaceDto[],
  createdAt: string,
  days: { date: string; roomPlaceIds: number[] }[],
): MockRoom {
  return makeRoom(detail, places, makeSchedule(places, detail.transportMode, createdAt, days));
}

/**
 * 여행방 초기 상태(오늘 2026-11-03 기준).
 * 1 강릉: 일정 만들 준비됨(W09) · 2 제주: 날짜 미정·혼자(W06-L2) · 3 부산: 지난 여행·기록 있음 · 4 속초: 정원 가득·일정 있음(W08-F, W06-E2)
 */
export function createRoomFixtures(): MockRoom[] {
  return [
    makeRoom(
      { roomId: 1, name: "강릉 우정여행", startDate: "2026-11-08", endDate: "2026-11-09", transportMode: "CAR", members: [MOCK_ME, others[0], others[1]], recordId: null },
      [
        createRoomPlaceFixture(1001, 101, "COURSE", "해솔"),
        createRoomPlaceFixture(1002, 102, "COURSE", "해솔"),
        createRoomPlaceFixture(1003, 103, "AI_RECOMMENDATION"),
        createRoomPlaceFixture(1004, 104, "SEARCH"),
      ],
    ),
    makeRoom(
      { roomId: 2, name: "제주 가을 여행", startDate: null, endDate: null, transportMode: "PUBLIC_TRANSIT", members: [MOCK_ME], recordId: null },
      [createRoomPlaceFixture(2001, 201, "SEARCH")],
    ),
    makeRoomWithSchedule(
      { roomId: 3, name: "부산 영도 1박 2일", startDate: "2026-10-03", endDate: "2026-10-04", transportMode: "CAR", members: [MOCK_ME, others[2]], recordId: 1 },
      [
        createRoomPlaceFixture(3001, 301, "SEARCH"),
        createRoomPlaceFixture(3002, 302, "SEARCH"),
        createRoomPlaceFixture(3003, 303, "AI_RECOMMENDATION"),
      ],
      "2026-09-28 21:00:00+09",
      [
        { date: "2026-10-03", roomPlaceIds: [3001, 3003] },
        { date: "2026-10-04", roomPlaceIds: [3002] },
      ],
    ),
    makeRoomWithSchedule(
      { roomId: 4, name: "속초 주말 여행", startDate: "2026-11-21", endDate: "2026-11-22", transportMode: "PUBLIC_TRANSIT", members: [MOCK_ME, ...others.slice(3, 12)], recordId: null },
      [
        createRoomPlaceFixture(4001, 401, "SEARCH"),
        createRoomPlaceFixture(4002, 402, "COURSE", "해솔"),
        createRoomPlaceFixture(4003, 403, "AI_RECOMMENDATION"),
      ],
      "2026-11-01 20:10:00+09",
      [
        { date: "2026-11-21", roomPlaceIds: [4001, 4002] },
        { date: "2026-11-22", roomPlaceIds: [4003] },
      ],
    ),
  ];
}

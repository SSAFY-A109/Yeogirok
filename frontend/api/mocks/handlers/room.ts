import { delay, http } from "msw";

import type {
  AddRoomPlacesRequestDto,
  CreateRoomRequestDto,
  RoomDetailDto,
  RoomSummaryDto,
  UpdateRoomRequestDto,
} from "../../../features/room/model";

import { mockDb, syncRoomDerivedFields } from "../db";
import { findCatalogPlace, toAreaName } from "../fixtures/place";
import { MOCK_ME, ROOM_MEMBER_LIMIT, type MockRoom } from "../fixtures/room";
import { fail, MOCK_DELAY_MS, ok, readId, roomNotFound } from "./respond";

const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

function toSummary({ detail }: MockRoom): RoomSummaryDto {
  return {
    roomId: detail.roomId,
    name: detail.name,
    startDate: detail.startDate,
    endDate: detail.endDate,
    transportMode: detail.transportMode,
    memberCount: detail.memberCount,
    members: detail.members.slice(0, 3),
    recordId: detail.recordId,
    recordIds: detail.recordId == null ? [] : [detail.recordId],
  };
}

function findRoom(roomId: string | readonly string[] | undefined): MockRoom | null {
  const id = readId(roomId);
  return id === null ? null : mockDb.rooms.get(id) ?? null;
}

/** 출발일과 돌아오는 날은 함께 정하거나 함께 비우고, 출발일이 앞이어야 한다(C016). */
function isValidDateRange(startDate: string | null, endDate: string | null): boolean {
  if (startDate === null || endDate === null) {
    return startDate === endDate;
  }

  return DATE_PATTERN.test(startDate) && DATE_PATTERN.test(endDate) && startDate <= endDate;
}

/** 새 기간 밖으로 빠진 날을 일정에서 지운다. 빠진 장소는 후보 목록에 그대로 남는다. */
function trimSchedule(room: MockRoom): void {
  if (room.schedule === null) {
    return;
  }

  const { startDate, endDate } = room.detail;

  const days = room.schedule.days.filter((day) => {
    return startDate !== null && endDate !== null && day.date >= startDate && day.date <= endDate;
  });

  if (days.length === room.schedule.days.length) {
    return;
  }

  room.schedule = days.length === 0 ? null : { ...room.schedule, days, version: room.schedule.version + 1 };
}

export const roomHandlers = [
  http.get("*/api/v1/rooms", async () => {
    await delay(MOCK_DELAY_MS);
    return ok({ rooms: [...mockDb.rooms.values()].map(toSummary) });
  }),

  http.post("*/api/v1/rooms", async ({ request }) => {
    await delay(MOCK_DELAY_MS);
    const body = (await request.json()) as CreateRoomRequestDto;

    if (typeof body.name !== "string" || body.name.trim() === "") {
      return fail(400, "C001", "name은 필수입니다.");
    }

    if (!isValidDateRange(body.startDate, body.endDate)) {
      return fail(400, "C016", "시작·종료 날짜의 범위나 순서가 올바르지 않습니다.");
    }

    const detail: RoomDetailDto = {
      roomId: mockDb.nextRoomId,
      name: body.name.trim(),
      startDate: body.startDate,
      endDate: body.endDate,
      transportMode: body.transportMode,
      memberCount: 1,
      members: [MOCK_ME],
      recordId: null,
      memberLimit: ROOM_MEMBER_LIMIT,
      placeCount: 0,
      schedule: null,
    };

    mockDb.nextRoomId += 1;
    mockDb.rooms.set(detail.roomId, { detail, places: [], schedule: null });
    return ok(detail, 201);
  }),

  http.get("*/api/v1/rooms/:roomId", async ({ params }) => {
    await delay(MOCK_DELAY_MS);
    const room = findRoom(params.roomId);
    return room === null ? roomNotFound() : ok(room.detail);
  }),

  http.patch("*/api/v1/rooms/:roomId", async ({ params, request }) => {
    await delay(MOCK_DELAY_MS);
    const room = findRoom(params.roomId);

    if (room === null) {
      return roomNotFound();
    }

    const body = (await request.json()) as UpdateRoomRequestDto;
    const next = { ...room.detail, ...body };

    if (typeof next.name !== "string" || next.name.trim() === "") {
      return fail(400, "C001", "name은 필수입니다.");
    }

    if (!isValidDateRange(next.startDate, next.endDate)) {
      return fail(400, "C016", "시작·종료 날짜의 범위나 순서가 올바르지 않습니다.");
    }

    room.detail = { ...next, name: next.name.trim() };
    trimSchedule(room);
    syncRoomDerivedFields(room);
    return ok(room.detail);
  }),

  http.delete("*/api/v1/rooms/:roomId/members/me", async ({ params }) => {
    await delay(MOCK_DELAY_MS);
    const room = findRoom(params.roomId);

    if (room === null) {
      return roomNotFound();
    }

    const roomDeleted = room.detail.members.length <= 1;
    mockDb.rooms.delete(room.detail.roomId);
    return ok({ roomDeleted });
  }),

  http.post("*/api/v1/rooms/:roomId/invitations", async ({ params }) => {
    await delay(MOCK_DELAY_MS);
    const room = findRoom(params.roomId);

    if (room === null) {
      return roomNotFound();
    }

    const code = (room.detail.roomId * 7919).toString(36).toUpperCase().padStart(4, "7");
    return ok({ invitationUrl: `https://yeogirok.app/j/${code}`, expiresAt: null }, 201);
  }),

  http.get("*/api/v1/rooms/:roomId/places", async ({ params }) => {
    await delay(MOCK_DELAY_MS);
    const room = findRoom(params.roomId);
    return room === null ? roomNotFound() : ok({ places: room.places });
  }),

  http.post("*/api/v1/rooms/:roomId/places", async ({ params, request }) => {
    await delay(MOCK_DELAY_MS);
    const room = findRoom(params.roomId);

    if (room === null) {
      return roomNotFound();
    }

    const body = (await request.json()) as AddRoomPlacesRequestDto;

    if (!Array.isArray(body.placeIds) || body.placeIds.length === 0) {
      return fail(400, "C001", "placeIds는 비어 있을 수 없습니다.");
    }

    const existing = new Set(room.places.map((place) => {
      return place.placeId;
    }));

    const added = body.placeIds.flatMap((placeId) => {
      const place = findCatalogPlace(placeId);

      if (place === null || existing.has(placeId)) {
        return [];
      }

      existing.add(placeId);
      const roomPlaceId = mockDb.nextRoomPlaceId;
      mockDb.nextRoomPlaceId += 1;

      return [{
        roomPlaceId,
        placeId,
        name: place.name,
        category: place.category,
        areaName: toAreaName(place.address),
        photoUrl: place.photoUrl,
        source: body.source,
        courseOwnerNickname: null,
        addedBy: { userId: MOCK_ME.userId, nickname: MOCK_ME.nickname },
      }];
    });

    room.places = [...room.places, ...added];
    syncRoomDerivedFields(room);
    return ok({ places: added }, 201);
  }),

  http.delete("*/api/v1/rooms/:roomId/places/:roomPlaceId", async ({ params }) => {
    await delay(MOCK_DELAY_MS);
    const room = findRoom(params.roomId);
    const roomPlaceId = readId(params.roomPlaceId);

    if (room === null) {
      return roomNotFound();
    }

    room.places = room.places.filter((place) => {
      return place.roomPlaceId !== roomPlaceId;
    });

    // 목은 일정에서 그 장소만 빼고 시각은 다시 계산하지 않는다. 일정이 바뀌었으므로 버전은 올린다.
    if (room.schedule !== null) {
      const isOther = (stop: { roomPlaceId: number }) => {
        return stop.roomPlaceId !== roomPlaceId;
      };

      room.schedule = {
        ...room.schedule,
        version: room.schedule.version + 1,

        days: room.schedule.days.map((day) => {
          return { ...day, stops: day.stops.filter(isOther), unplaced: day.unplaced.filter(isOther) };
        }),
      };
    }

    syncRoomDerivedFields(room);
    return ok(null);
  }),
];

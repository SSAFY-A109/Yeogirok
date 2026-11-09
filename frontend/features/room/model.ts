import type { MainPlaceDto } from "../course/model";
import { toPlaceCategory, type PlaceCategory, type PlaceCategoryDto } from "../place/model";
import { assertId, toKnownEnum } from "../../lib/contract";
import {
  compareLocalDate,
  parseNullableLocalDate,
  parseServerDateTime,
  todayLocalDate,
  type LocalDate,
} from "../../lib/date";

// API-021·030 외의 DTO는 화면 선개발용 가정(TBD)이다.
// 필드는 Figma "여행방 함께 장소 모으기" 섹션(W06~W12)에서 역산했다. 명세가 나오면 이름과 형식을 맞춘다.
// 여행방 나가기는 API 목록에 없는 엔드포인트(DELETE /rooms/{roomId}/members/me)를 가정했다 — 백엔드 요청 필요.

export const TRANSPORT_MODES = ["CAR", "PUBLIC_TRANSIT"] as const;
export type TransportModeDto = (typeof TRANSPORT_MODES)[number];
export type TransportMode = TransportModeDto | "UNKNOWN";

export const TRANSPORT_MODE_LABEL: Readonly<Record<TransportMode, string>> = {
  CAR: "자차",
  PUBLIC_TRANSIT: "대중교통",
  UNKNOWN: "이동 수단 확인 필요",
};

export const ROOM_PLACE_SOURCES = ["COURSE", "AI_RECOMMENDATION", "SEARCH", "CUSTOM"] as const;
export type RoomPlaceSource = (typeof ROOM_PLACE_SOURCES)[number] | "UNKNOWN";

/** 일정을 만들 수 있는 최소 후보 장소 수. Figma W09-S 시안 값이며 확정 전이다(TBD). */
export const MIN_PLACES_FOR_SCHEDULE = 3;

// #region DTO

export type RoomMemberDto = { userId: number; nickname: string; profileImageUrl: string | null; me: boolean };

export type RoomSummaryDto = {
  roomId: number;
  name: string;
  startDate: string | null;
  endDate: string | null;
  transportMode: string;
  memberCount: number;
  members: RoomMemberDto[];
  recordId: number | null;
};

export type RoomsResponseDto = { rooms: RoomSummaryDto[] };

export type RoomDetailDto = RoomSummaryDto & {
  memberLimit: number;
  placeCount: number;
  schedule: { createdAt: string } | null;
};

export type RoomFormValues = {
  name: string;
  startDate: string | null;
  endDate: string | null;
  transportMode: TransportModeDto;
};

export type CreateRoomRequestDto = Omit<RoomFormValues, "name" | "transportMode"> & {
  title: string;
  transportMode: "CAR" | "TRANSIT";
};

export type CreateRoomResponseDto = { id: number; version: number; isCreator: boolean };

export type UpdateRoomRequestDto = Partial<RoomFormValues>;

export type RoomInvitationDto = { invitationUrl: string; expiresAt: string | null };

export type RoomPlaceDto = {
  roomPlaceId: number;
  placeId: number;
  name: string;
  category: PlaceCategoryDto | null;
  areaName: string | null;
  photoUrl: string | null;
  source: string;
  courseOwnerNickname: string | null;
  addedBy: { userId: number; nickname: string } | null;
};

export type RoomPlacesResponseDto = { places: RoomPlaceDto[] };

export type AddRoomPlacesRequestDto = {
  placeIds: number[];
  source: "SEARCH" | "AI_RECOMMENDATION";
  recommendationRequestId: number | null;
};

export type ImportCoursePlacesRequestDto = { recordId: number; visitIds: number[] };

export type ImportedCoursePlaceDto = {
  id: number;
  place: MainPlaceDto;
  sourceType: "SEARCH" | "COURSE" | "AI" | "MANUAL";
  sourceRecordId: number | null;
  addedBy: number;
};

export type ImportCoursePlacesResponseDto = { added: ImportedCoursePlaceDto[]; version: number };

export type LeaveRoomResponseDto = { roomDeleted: boolean };

// #endregion

// #region 화면 모델

export type RoomMember = {
  id: number;
  nickname: string;
  /** 아바타에 쓰는 이름 첫 글자. */
  initial: string;
  profileImageUrl: string | null;
  isMe: boolean;
};

export type RoomSummary = {
  id: number;
  name: string;
  startDate: LocalDate | null;
  endDate: LocalDate | null;
  transportMode: TransportMode;
  memberCount: number;
  members: RoomMember[];
  recordId: number | null;
};

export type RoomDetail = RoomSummary & {
  memberLimit: number;
  placeCount: number;
  scheduleCreatedAt: Date | null;
};

export type RoomPlace = {
  id: number;
  placeId: number;
  name: string;
  category: PlaceCategory | null;
  areaName: string | null;
  photoUrl: string | null;
  source: RoomPlaceSource;
  courseOwnerNickname: string | null;
  /** 이 장소를 담은 일행. 탈퇴 등으로 알 수 없으면 `null`. */
  addedBy: { id: number; nickname: string; initial: string } | null;
};

export type RoomInvitation = { url: string; expiresAt: Date | null };

// #endregion

function toRoomMember(dto: RoomMemberDto): RoomMember {
  return {
    id: assertId(dto.userId, "members.userId"),
    nickname: dto.nickname,
    initial: dto.nickname.slice(0, 1),
    profileImageUrl: dto.profileImageUrl,
    isMe: dto.me,
  };
}

export function toRoomSummary(dto: RoomSummaryDto): RoomSummary {
  return {
    id: assertId(dto.roomId, "roomId"),
    name: dto.name,
    startDate: parseNullableLocalDate(dto.startDate, "startDate"),
    endDate: parseNullableLocalDate(dto.endDate, "endDate"),
    transportMode: toKnownEnum(dto.transportMode, TRANSPORT_MODES),
    memberCount: dto.memberCount,
    members: dto.members.map(toRoomMember),
    recordId: dto.recordId === null ? null : assertId(dto.recordId, "recordId"),
  };
}

export function toRoomDetail(dto: RoomDetailDto): RoomDetail {
  return {
    ...toRoomSummary(dto),
    memberLimit: dto.memberLimit,
    placeCount: dto.placeCount,
    scheduleCreatedAt: dto.schedule === null ? null : parseServerDateTime(dto.schedule.createdAt, "schedule.createdAt"),
  };
}

export function toRoomPlace(dto: RoomPlaceDto): RoomPlace {
  return {
    id: assertId(dto.roomPlaceId, "roomPlaceId"),
    placeId: assertId(dto.placeId, "placeId"),
    name: dto.name,
    category: toPlaceCategory(dto.category),
    areaName: dto.areaName,
    photoUrl: dto.photoUrl,
    source: toKnownEnum(dto.source, ROOM_PLACE_SOURCES),
    courseOwnerNickname: dto.courseOwnerNickname,
    addedBy: dto.addedBy === null ? null : { id: assertId(dto.addedBy.userId, "addedBy.userId"), nickname: dto.addedBy.nickname, initial: dto.addedBy.nickname.slice(0, 1) },
  };
}

export function toRoomInvitation(dto: RoomInvitationDto): RoomInvitation {
  return {
    url: dto.invitationUrl,
    expiresAt: dto.expiresAt === null ? null : parseServerDateTime(dto.expiresAt, "expiresAt"),
  };
}

/** 여행 날짜가 정해졌는지. 출발일과 돌아오는 날은 함께 정하거나 함께 비운다. */
export function hasTripDates(room: Pick<RoomSummary, "startDate" | "endDate">): room is { startDate: LocalDate; endDate: LocalDate } {
  return room.startDate !== null && room.endDate !== null;
}

/** 돌아오는 날이 오늘보다 앞이면 지난 여행이다. 날짜가 없으면 다가오는 여행으로 본다. */
export function isPastTrip(room: Pick<RoomSummary, "endDate">, today: LocalDate = todayLocalDate()): boolean {
  return room.endDate !== null && compareLocalDate(room.endDate, today) < 0;
}

/** 장소 행 보조 문구의 담은 경로(`해솔님 코스`, `AI 추천`, `직접 검색`). */
export function getRoomPlaceSourceLabel(place: Pick<RoomPlace, "source" | "courseOwnerNickname">): string | null {
  switch (place.source) {
    case "COURSE":
      return place.courseOwnerNickname === null ? "공개 코스" : `${place.courseOwnerNickname}님 코스`;
    case "AI_RECOMMENDATION":
      return "AI 추천";
    case "SEARCH":
      return "직접 검색";
    case "CUSTOM":
      return "직접 추가";
    default:
      return null;
  }
}

/** 여행방 상세 하단 "일정 만들기" 영역의 상태(Figma W09-S). */
export type ScheduleReadiness =
  | { kind: "HAS_SCHEDULE"; createdAt: Date }
  | { kind: "NO_DATES" }
  | { kind: "NOT_ENOUGH_PLACES"; minimum: number }
  | { kind: "READY" };

export function getScheduleReadiness(room: Pick<RoomDetail, "startDate" | "endDate" | "scheduleCreatedAt">, placeCount: number): ScheduleReadiness {
  if (room.scheduleCreatedAt !== null) {
    return { kind: "HAS_SCHEDULE", createdAt: room.scheduleCreatedAt };
  }

  if (!hasTripDates(room)) {
    return { kind: "NO_DATES" };
  }

  if (placeCount < MIN_PLACES_FOR_SCHEDULE) {
    return { kind: "NOT_ENOUGH_PLACES", minimum: MIN_PLACES_FOR_SCHEDULE };
  }

  return { kind: "READY" };
}

/** 정원이 찼는지(Figma W08-F). */
export function isRoomFull(room: Pick<RoomDetail, "memberCount" | "memberLimit">): boolean {
  return room.memberCount >= room.memberLimit;
}

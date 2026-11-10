import type { MainPlaceDto } from "../course/model";
import type { PlaceCategory } from "../place/model";
import { assertId, ContractMismatchError, toKnownEnum } from "../../lib/contract";
import { compareLocalDate, parseLocalDate, parseNullableLocalDate, todayLocalDate, type LocalDate } from "../../lib/date";

// 여행방(API-020~025·028·031·032·087·094) DTO는 서버 계약을 따른다.
// Swagger는 요청 필드 이름만 보여 줘서 응답 모양은 backend/dev의 room dto(2026-11-10, 8778cb2)와 대조했다.
// API-029는 서버에 아직 없어 팀 Notion "API 명세" 상세 페이지(2026-11-10)를 따른다. 서버에 올라오면 Swagger로 다시 맞춘다.

export const TRANSPORT_MODES = ["CAR", "TRANSIT"] as const;
export type TransportModeDto = (typeof TRANSPORT_MODES)[number];
export type TransportMode = TransportModeDto | "UNKNOWN";

export const TRANSPORT_MODE_LABEL: Readonly<Record<TransportMode, string>> = {
  CAR: "자차",
  TRANSIT: "대중교통",
  UNKNOWN: "이동 수단 확인 필요",
};

export const ROOM_PLACE_SOURCES = ["SEARCH", "COURSE", "AI", "MANUAL"] as const;
export type RoomPlaceSourceDto = (typeof ROOM_PLACE_SOURCES)[number];
export type RoomPlaceSource = RoomPlaceSourceDto | "UNKNOWN";

/** 일정 초안을 만들 수 있는 최소 후보 장소 수. 서버(API-037)는 3곳 미만이면 C003으로 거절한다. */
export const MIN_PLACES_FOR_SCHEDULE = 3;

/** 생성자를 포함한 최대 참여 인원(팀 확정 정책). 응답에는 없어 상수로 둔다. 가득 차면 참여 요청은 RO007이다. */
export const ROOM_MEMBER_LIMIT = 10;

// #region DTO

/** API-020 목록의 참여자. */
export type RoomListMemberDto = { userId: number; nickname: string; profileImageUrl: string | null };

/** API-022 상세의 참여자. 사진 주소는 주지 않는다. */
export type RoomDetailMemberDto = { userId: number; nickname: string; isCreator: boolean };

export type ChatReadStateDto = { lastReadMessageId: number | null; hasUnreadMessages: boolean };

export type RoomListItemDto = {
  id: number;
  title: string;
  startDate: string | null;
  endDate: string | null;
  transportMode: string;
  memberCount: number;
  members: RoomListMemberDto[];
  placeCount: number;
  recordIds: number[];
  chatReadState: ChatReadStateDto;
};

export type RoomsResponseDto = { items: RoomListItemDto[]; nextCursor: string | null };

/** 후보가 참조하는 장소 정보. 전역 장소 id는 주지 않고 `provider`+`providerPlaceId`로 구별한다. */
export type RoomPlaceInfoDto = {
  provider: string;
  providerPlaceId: string | null;
  name: string;
  latitude: number;
  longitude: number;
  address: string | null;
  categoryCode: string | null;
  categoryName: string | null;
  regionCode: string | null;
  regionName: string | null;
  imageUrl: string | null;
  imageAttribution: string | null;
};

export type RoomPlaceDto = {
  id: number;
  place: RoomPlaceInfoDto;
  sourceType: string;
  sourceRecordId: number | null;
  /** 원본 공개 코스. 서버가 아직 채우지 않아 항상 `null`이다. */
  sourceRecord?: { title?: string | null } | null;
  /** 담은 일행의 사용자 id. */
  addedBy: number;
  /** 뺀 시각. API-028 `state`가 REMOVED·ALL일 때만 값이 있다. */
  removedAt?: string | null;
};

export type RoomPlacesResponseDto = { items: RoomPlaceDto[]; version: number };

export type RoomDetailDto = {
  id: number;
  title: string;
  startDate: string | null;
  endDate: string | null;
  transportMode: string;
  members: RoomDetailMemberDto[];
  places: RoomPlaceDto[];
  recordIds: number[];
  version: number;
  chatReadState: ChatReadStateDto;
  /** API-036과 같은 구조의 확정 일정. 없으면 `null`. 내용은 일정 도메인이 API-036으로 읽는다. */
  schedule: object | null;
};

export type RoomFormValues = {
  name: string;
  startDate: string | null;
  endDate: string | null;
  transportMode: TransportModeDto;
};

export type CreateRoomRequestDto = {
  title: string;
  startDate: string | null;
  endDate: string | null;
  transportMode: TransportModeDto;
};

export type CreateRoomResponseDto = { id: number; version: number; isCreator: boolean };

/** 화면이 고른 변경. 생략한 항목은 지금 값을 유지한다. 날짜는 둘을 함께 보낸다(둘 다 `null`이면 날짜 미정). */
export type RoomChanges = {
  name?: string;
  startDate?: string | null;
  endDate?: string | null;
  transportMode?: TransportModeDto;
};

/** API-023 요청. `previewOnly`가 `true`면 저장하지 않고 일정에 생길 변화만 돌려준다. */
export type UpdateRoomRequestDto = {
  version: number;
  title?: string;
  startDate?: string | null;
  endDate?: string | null;
  transportMode?: TransportModeDto;
  previewOnly?: boolean;
};

export type AffectedScheduleItemDto = { scheduleItemId: number; roomPlaceId: number; name: string; visitDate: string };

export type UpdateRoomResponseDto = {
  id: number;
  version: number;
  applied: boolean;
  scheduleNeedsRecalculation: boolean;
  affectedItems: AffectedScheduleItemDto[];
  schedule: object | null;
};

/** API-024 응답. 링크는 여행방마다 하나이고 만료가 없어 `expiresAt`은 항상 `null`이다. */
export type RoomInvitationDto = { invitationId: number; inviteToken: string; expiresAt: string | null; inviteUrl: string };

export type AcceptRoomInvitationRequestDto = { inviteToken: string };

export type AcceptRoomInvitationResponseDto = { roomId: number; membershipStatus: string; isCreator: boolean };

/** 장소를 가리키는 값. 직접 추가한 장소(MANUAL)는 `providerPlaceId`가 없다. */
export type PlaceRefDto = { provider: string; providerPlaceId: string };

export type AddRoomPlaceItemDto =
  | { placeRef: PlaceRefDto; sourceType: "SEARCH" }
  | { placeRef: PlaceRefDto; sourceType: "AI"; recommendationRequestId: number; rank: number };

/** API-029 요청(Notion 명세). */
export type AddRoomPlacesRequestDto = { items: AddRoomPlaceItemDto[] };

export type AddRoomPlacesResponseDto = { added: RoomPlaceDto[]; addedCount: number; version: number };

export type ImportCoursePlacesRequestDto = { recordId: number; visitIds: number[] };

export type ImportedCoursePlaceDto = {
  id: number;
  place: MainPlaceDto;
  sourceType: RoomPlaceSourceDto;
  sourceRecordId: number | null;
  addedBy: number;
};

export type ImportCoursePlacesResponseDto = { added: ImportedCoursePlaceDto[]; version: number };

export type RemoveRoomPlaceResponseDto = { removed: boolean; version: number; scheduleNeedsRecalculation: boolean; undoExpiresAt: string | null };

/** API-094 요청. `action`은 RESTORE만 받는다. */
export type RestoreRoomPlaceRequestDto = { version: number; action: "RESTORE" };

export type RestoreRoomPlaceResponseDto = { restored: boolean; roomPlaceId: number; version: number; scheduleNeedsRecalculation: boolean };

// #endregion

// #region 화면 모델

export type RoomMember = {
  id: number;
  nickname: string;
  /** 아바타에 쓰는 이름 첫 글자. */
  initial: string;
  /** 목록 응답에만 있다. 상세 응답은 사진을 주지 않아 `null`이다. */
  profileImageUrl: string | null;
};

export type RoomSummary = {
  id: number;
  name: string;
  startDate: LocalDate | null;
  endDate: LocalDate | null;
  transportMode: TransportMode;
  memberCount: number;
  members: RoomMember[];
  /** 빼지 않은 후보 장소 수. */
  placeCount: number;
  /** 이 여행방의 여행 기록. 방마다 기록은 하나까지다. 없으면 `null`. */
  recordId: number | null;
};

export type RoomDetail = RoomSummary & {
  memberLimit: number;
  /** 여행방 version. 수정·후보 빼기·복원·일정 초안 요청에 함께 보낸다. */
  version: number;
  /** 확정 일정이 있는지. */
  hasSchedule: boolean;
};

export type RoomPlace = {
  id: number;
  name: string;
  category: PlaceCategory | null;
  /** 지역 이름(서버 `regionName`). */
  areaName: string | null;
  photoUrl: string | null;
  coordinate: { latitude: number; longitude: number };
  /** 같은 장소인지 가릴 때 쓰는 값. 직접 추가한 장소는 후보마다 다르다. */
  placeKey: string;
  source: RoomPlaceSource;
  /** 원본 공개 코스 제목. 서버가 주지 않으면 `null`. */
  sourceTitle: string | null;
  /** 이 장소를 담은 일행의 사용자 id. */
  addedById: number;
};

export type RoomPlaceList = { places: RoomPlace[]; version: number };

export type RoomInvitation = { url: string; token: string };

/** 날짜를 줄이면 새 기간 밖으로 빠지는 일정 항목. */
export type AffectedScheduleItem = { scheduleItemId: number; roomPlaceId: number; name: string; visitDate: LocalDate };

export type RoomUpdateResult = { roomId: number; version: number; applied: boolean; affectedItems: AffectedScheduleItem[] };

// #endregion

function toRoomMember(dto: { userId: number; nickname: string; profileImageUrl?: string | null }): RoomMember {
  return {
    id: assertId(dto.userId, "members.userId"),
    nickname: dto.nickname,
    initial: dto.nickname.slice(0, 1),
    profileImageUrl: dto.profileImageUrl ?? null,
  };
}

function assertVersion(value: unknown): number {
  if (typeof value !== "number" || !Number.isSafeInteger(value)) {
    throw new ContractMismatchError("version");
  }

  return value;
}

function toRecordId(recordIds: number[]): number | null {
  return recordIds.length === 0 ? null : assertId(recordIds[0], "recordIds");
}

export function toRoomSummary(dto: RoomListItemDto): RoomSummary {
  return {
    id: assertId(dto.id, "id"),
    name: dto.title,
    startDate: parseNullableLocalDate(dto.startDate, "startDate"),
    endDate: parseNullableLocalDate(dto.endDate, "endDate"),
    transportMode: toKnownEnum(dto.transportMode, TRANSPORT_MODES),
    memberCount: dto.memberCount,
    members: dto.members.map(toRoomMember),
    placeCount: dto.placeCount,
    recordId: toRecordId(dto.recordIds),
  };
}

export function toRoomDetail(dto: RoomDetailDto): RoomDetail {
  return {
    id: assertId(dto.id, "id"),
    name: dto.title,
    startDate: parseNullableLocalDate(dto.startDate, "startDate"),
    endDate: parseNullableLocalDate(dto.endDate, "endDate"),
    transportMode: toKnownEnum(dto.transportMode, TRANSPORT_MODES),
    memberCount: dto.members.length,
    members: dto.members.map(toRoomMember),
    placeCount: dto.places.length,
    recordId: toRecordId(dto.recordIds),
    memberLimit: ROOM_MEMBER_LIMIT,
    version: assertVersion(dto.version),
    hasSchedule: dto.schedule !== null,
  };
}

function toCoordinate(latitude: number, longitude: number): RoomPlace["coordinate"] {
  if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90) {
    throw new ContractMismatchError("place.latitude");
  }

  if (!Number.isFinite(longitude) || longitude < -180 || longitude > 180) {
    throw new ContractMismatchError("place.longitude");
  }

  return { latitude, longitude };
}

/** `provider`와 `providerPlaceId`로 만든 장소 키. 검색·추천 결과가 이미 담긴 곳인지 가릴 때 쓴다. */
export function toPlaceKey(provider: string, providerPlaceId: string): string {
  return `${provider}:${providerPlaceId}`;
}

export function toRoomPlace(dto: RoomPlaceDto): RoomPlace {
  const id = assertId(dto.id, "id");
  const { place } = dto;

  return {
    id,
    name: place.name,
    category: place.categoryCode === null || place.categoryName === null ? null : { code: place.categoryCode, name: place.categoryName },
    areaName: place.regionName,
    photoUrl: place.imageUrl,
    coordinate: toCoordinate(place.latitude, place.longitude),
    placeKey: place.providerPlaceId === null ? `${place.provider}:room-place-${id}` : toPlaceKey(place.provider, place.providerPlaceId),
    source: toKnownEnum(dto.sourceType, ROOM_PLACE_SOURCES),
    sourceTitle: dto.sourceRecord?.title ?? null,
    addedById: assertId(dto.addedBy, "addedBy"),
  };
}

export function toRoomPlaceList(dto: RoomPlacesResponseDto): RoomPlaceList {
  return { places: dto.items.map(toRoomPlace), version: assertVersion(dto.version) };
}

export function toRoomInvitation(dto: RoomInvitationDto): RoomInvitation {
  if (typeof dto.inviteUrl !== "string" || dto.inviteUrl === "") {
    throw new ContractMismatchError("inviteUrl");
  }

  if (typeof dto.inviteToken !== "string" || dto.inviteToken === "") {
    throw new ContractMismatchError("inviteToken");
  }

  return { url: dto.inviteUrl, token: dto.inviteToken };
}

export function toRoomUpdateResult(dto: UpdateRoomResponseDto): RoomUpdateResult {
  return {
    roomId: assertId(dto.id, "id"),
    version: assertVersion(dto.version),
    applied: dto.applied,

    affectedItems: dto.affectedItems.map((item) => {
      return {
        scheduleItemId: assertId(item.scheduleItemId, "affectedItems.scheduleItemId"),
        roomPlaceId: assertId(item.roomPlaceId, "affectedItems.roomPlaceId"),
        name: item.name,
        visitDate: parseLocalDate(item.visitDate, "affectedItems.visitDate"),
      };
    }),
  };
}

/** 화면이 고른 변경을 API-023 요청으로 바꾼다. */
export function toUpdateRoomRequest(changes: RoomChanges, version: number, previewOnly: boolean): UpdateRoomRequestDto {
  const request: UpdateRoomRequestDto = { version };

  if (changes.name !== undefined) {
    request.title = changes.name;
  }

  if (changes.startDate !== undefined || changes.endDate !== undefined) {
    request.startDate = changes.startDate ?? null;
    request.endDate = changes.endDate ?? null;
  }

  if (changes.transportMode !== undefined) {
    request.transportMode = changes.transportMode;
  }

  if (previewOnly) {
    request.previewOnly = true;
  }

  return request;
}

/** 여행 날짜가 정해졌는지. 출발일과 돌아오는 날은 함께 정하거나 함께 비운다. */
export function hasTripDates(room: Pick<RoomSummary, "startDate" | "endDate">): room is { startDate: LocalDate; endDate: LocalDate } {
  return room.startDate !== null && room.endDate !== null;
}

/** 돌아오는 날이 오늘보다 앞이면 지난 여행이다. 날짜가 없으면 다가오는 여행으로 본다. */
export function isPastTrip(room: Pick<RoomSummary, "endDate">, today: LocalDate = todayLocalDate()): boolean {
  return room.endDate !== null && compareLocalDate(room.endDate, today) < 0;
}

/** 장소 행 보조 문구의 담은 경로(`강릉 바다 따라 2박 3일`, `AI 추천`, `직접 검색`). 코스는 서버가 준 원본 코스 제목을 쓴다. */
export function getRoomPlaceSourceLabel(place: Pick<RoomPlace, "source" | "sourceTitle">): string | null {
  switch (place.source) {
    case "COURSE":
      return place.sourceTitle ?? "공개 코스";
    case "AI":
      return "AI 추천";
    case "SEARCH":
      return "직접 검색";
    case "MANUAL":
      return "직접 추가";
    default:
      return null;
  }
}

/** 여행방 상세 하단 "일정 만들기" 영역의 상태(Figma W09-S). */
export type ScheduleReadiness =
  | { kind: "HAS_SCHEDULE" }
  | { kind: "NO_DATES" }
  | { kind: "NOT_ENOUGH_PLACES"; minimum: number }
  | { kind: "READY" };

export function getScheduleReadiness(room: Pick<RoomDetail, "startDate" | "endDate" | "hasSchedule">, placeCount: number): ScheduleReadiness {
  if (room.hasSchedule) {
    return { kind: "HAS_SCHEDULE" };
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

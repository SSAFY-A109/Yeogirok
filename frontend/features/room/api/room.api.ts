import { api, idempotentRequestConfig, TIMEOUT_MUTATION_MS } from "@/api/client";
import {
  toRoomDetail,
  toRoomInvitation,
  toRoomPlace,
  toRoomPlaceList,
  toRoomSummary,
  toRoomUpdateResult,
  toUpdateRoomRequest,
  type AcceptRoomInvitationRequestDto,
  type AcceptRoomInvitationResponseDto,
  type AddRoomPlaceItemDto,
  type AddRoomPlacesRequestDto,
  type AddRoomPlacesResponseDto,
  type CreateRoomRequestDto,
  type CreateRoomResponseDto,
  type ImportCoursePlacesRequestDto,
  type ImportCoursePlacesResponseDto,
  type RemoveRoomPlaceResponseDto,
  type RestoreRoomPlaceRequestDto,
  type RestoreRoomPlaceResponseDto,
  type RoomChanges,
  type RoomDetail,
  type RoomDetailDto,
  type RoomFormValues,
  type RoomInvitation,
  type RoomInvitationDto,
  type RoomPlace,
  type RoomPlaceList,
  type RoomPlacesResponseDto,
  type RoomsResponseDto,
  type RoomSummary,
  type RoomUpdateResult,
  type UpdateRoomResponseDto,
} from "@/features/room/model";
import { assertId, ContractMismatchError } from "@/lib/contract";

/** API-020 한 쪽의 여행방 수. 서버 허용 범위는 1~50이다. */
const ROOMS_PAGE_SIZE = 50;

/** 목록을 끝까지 받을 때 넘기는 쪽 수의 상한. 커서가 끝나지 않는 응답에서 멈추기 위한 값이다. */
const MAX_ROOM_PAGES = 20;

/**
 * API-020 참여 여행방 목록. 화면이 다가오는 여행·지난 여행으로 나눠 보여 주므로 다음 커서가 없을 때까지 모두 받는다.
 */
export async function getRooms(signal?: AbortSignal): Promise<RoomSummary[]> {
  const rooms: RoomSummary[] = [];
  let cursor: string | null = null;

  for (let page = 0; page < MAX_ROOM_PAGES; page += 1) {
    const response = await api.get<RoomsResponseDto>("/rooms", {
      params: { cursor: cursor ?? undefined, limit: ROOMS_PAGE_SIZE },
      signal,
    });

    const body: RoomsResponseDto = response.data;
    rooms.push(...body.items.map(toRoomSummary));
    cursor = body.nextCursor;

    if (cursor === null) {
      return rooms;
    }
  }

  throw new ContractMismatchError("nextCursor");
}

/** API-022 여행방 상세. */
export async function getRoom(roomId: number, signal?: AbortSignal): Promise<RoomDetail> {
  const { data } = await api.get<RoomDetailDto>(`/rooms/${roomId}`, { signal });
  return toRoomDetail(data);
}

export type CreateRoomInput = { values: RoomFormValues; idempotencyKey: string };

/** API-021 여행방 생성. 응답은 상세 정보 없이 생성된 방의 식별자를 준다. */
export async function createRoom({ values, idempotencyKey }: CreateRoomInput): Promise<number> {
  const request: CreateRoomRequestDto = {
    title: values.name,
    startDate: values.startDate,
    endDate: values.endDate,
    transportMode: values.transportMode,
  };

  const { data } = await api.post<CreateRoomResponseDto>("/rooms", request, idempotentRequestConfig(idempotencyKey));

  return assertId(data.id, "id");
}

export type UpdateRoomInput = { roomId: number; changes: RoomChanges; version: number; previewOnly: boolean };

/**
 * API-023 여행방 정보·이동 수단 수정. `previewOnly`면 저장하지 않고 날짜를 줄였을 때 일정에서 빠질 항목만 돌려준다.
 * 그사이 여행방이 바뀌었으면 409 C004, 일정이 있는 방의 날짜를 비우면 409 RO010이다.
 */
export async function updateRoom({ roomId, changes, version, previewOnly }: UpdateRoomInput): Promise<RoomUpdateResult> {
  const request = toUpdateRoomRequest(changes, version, previewOnly);
  const { data } = await api.patch<UpdateRoomResponseDto>(`/rooms/${roomId}`, request, { timeout: TIMEOUT_MUTATION_MS });
  return toRoomUpdateResult(data);
}

/** API-087 여행방 나가기. 응답 본문이 없다. 마지막 일행이 나가면 서버가 여행방을 삭제한다. */
export async function leaveRoom(roomId: number): Promise<void> {
  await api.delete<void>(`/rooms/${roomId}/members/me`, { timeout: TIMEOUT_MUTATION_MS });
}

export type CreateRoomInvitationInput = { roomId: number; idempotencyKey: string };

/** API-024 여행방 초대 링크 생성·조회. 링크는 여행방마다 하나라 다시 불러도 같은 링크를 준다. */
export async function createRoomInvitation({ roomId, idempotencyKey }: CreateRoomInvitationInput, signal?: AbortSignal): Promise<RoomInvitation> {
  const { data } = await api.post<RoomInvitationDto>(`/rooms/${roomId}/invitations`, null, { ...idempotentRequestConfig(idempotencyKey), signal });
  return toRoomInvitation(data);
}

export type AcceptRoomInvitationInput = { inviteToken: string; idempotencyKey: string };

/** API-025 초대 링크로 여행방 참여. 이미 참여한 방이면 인원을 늘리지 않고 같은 결과를 준다. 정원이 찼으면 409 RO007이다. */
export async function acceptRoomInvitation({ inviteToken, idempotencyKey }: AcceptRoomInvitationInput): Promise<{ roomId: number }> {
  const request: AcceptRoomInvitationRequestDto = { inviteToken };
  const { data } = await api.post<AcceptRoomInvitationResponseDto>("/room-invitations/accept", request, idempotentRequestConfig(idempotencyKey));
  return { roomId: assertId(data.roomId, "roomId") };
}

/** API-028 `state`. ACTIVE는 빼지 않은 후보, ALL은 뺀 후보까지다. */
export type RoomPlaceState = "ACTIVE" | "ALL";

/**
 * API-028 여행방 후보 장소 목록. 함께 오는 여행방 version은 빼기·복원 요청에 쓴다.
 * 확정 일정은 뺀 후보를 가리키는 항목도 그대로 두므로, 일정의 장소 이름을 찾을 때는 ALL로 받는다.
 */
export async function getRoomPlaces(roomId: number, state: RoomPlaceState, signal?: AbortSignal): Promise<RoomPlaceList> {
  const { data } = await api.get<RoomPlacesResponseDto>(`/rooms/${roomId}/places`, { params: { state }, signal });
  return toRoomPlaceList(data);
}

export type AddRoomPlacesInput = { roomId: number; items: AddRoomPlaceItemDto[]; idempotencyKey: string };

export type AddRoomPlacesResult = { places: RoomPlace[]; version: number };

/** API-029 검색·AI 추천 장소 담기. 새로 담긴 후보와 담은 뒤의 여행방 version을 돌려준다. */
export async function addRoomPlaces({ roomId, items, idempotencyKey }: AddRoomPlacesInput): Promise<AddRoomPlacesResult> {
  const request: AddRoomPlacesRequestDto = { items };
  const { data } = await api.post<AddRoomPlacesResponseDto>(`/rooms/${roomId}/places`, request, idempotentRequestConfig(idempotencyKey));
  return { places: data.added.map(toRoomPlace), version: data.version };
}

export type ImportCoursePlacesInput = { roomId: number; request: ImportCoursePlacesRequestDto; idempotencyKey: string };

/** API-030 현재 공개된 코스의 선택 방문을 여행방으로 가져온다. */
export async function importCoursePlaces({ roomId, request, idempotencyKey }: ImportCoursePlacesInput): Promise<number> {
  const { data } = await api.post<ImportCoursePlacesResponseDto>(`/rooms/${roomId}/place-imports`, request, idempotentRequestConfig(idempotencyKey));
  return data.added.length;
}

export type RoomPlaceVersionInput = { roomId: number; roomPlaceId: number; version: number };

/** API-032 담은 후보 장소 빼기. 일정에 들어간 항목은 그대로 남는다. 바뀐 여행방 version을 돌려준다. */
export async function deleteRoomPlace({ roomId, roomPlaceId, version }: RoomPlaceVersionInput): Promise<number> {
  const { data } = await api.delete<RemoveRoomPlaceResponseDto>(`/rooms/${roomId}/places/${roomPlaceId}`, {
    params: { version },
    timeout: TIMEOUT_MUTATION_MS,
  });

  return data.version;
}

/** API-094 뺀 후보 장소 복원. 시간 제한이 없다. 바뀐 여행방 version을 돌려준다. */
export async function restoreRoomPlace({ roomId, roomPlaceId, version }: RoomPlaceVersionInput): Promise<number> {
  const request: RestoreRoomPlaceRequestDto = { version, action: "RESTORE" };

  const { data } = await api.patch<RestoreRoomPlaceResponseDto>(`/rooms/${roomId}/places/${roomPlaceId}`, request, {
    timeout: TIMEOUT_MUTATION_MS,
  });

  return data.version;
}

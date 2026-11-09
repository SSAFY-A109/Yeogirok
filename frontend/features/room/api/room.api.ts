import { api, idempotentRequestConfig, TIMEOUT_MUTATION_MS } from "@/api/client";
import {
  toRoomDetail,
  toRoomInvitation,
  toRoomPlace,
  toRoomSummary,
  type AddRoomPlacesRequestDto,
  type CreateRoomRequestDto,
  type CreateRoomResponseDto,
  type ImportCoursePlacesRequestDto,
  type ImportCoursePlacesResponseDto,
  type LeaveRoomResponseDto,
  type RoomDetail,
  type RoomDetailDto,
  type RoomFormValues,
  type RoomInvitation,
  type RoomInvitationDto,
  type RoomPlace,
  type RoomPlacesResponseDto,
  type RoomsResponseDto,
  type RoomSummary,
  type UpdateRoomRequestDto,
} from "@/features/room/model";
import { assertId } from "@/lib/contract";

/** API-020 참여 여행방 목록. */
export async function getRooms(signal?: AbortSignal): Promise<RoomSummary[]> {
  const { data } = await api.get<RoomsResponseDto>("/rooms", { signal });
  return data.rooms.map(toRoomSummary);
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
    transportMode: values.transportMode === "PUBLIC_TRANSIT" ? "TRANSIT" : values.transportMode,
  };

  const { data } = await api.post<CreateRoomResponseDto>("/rooms", request, idempotentRequestConfig(idempotencyKey));

  return assertId(data.id, "id");
}

export type UpdateRoomInput = { roomId: number; request: UpdateRoomRequestDto };

/** API-023 여행방 정보·이동 수단 수정. */
export async function updateRoom({ roomId, request }: UpdateRoomInput): Promise<RoomDetail> {
  const { data } = await api.patch<RoomDetailDto>(`/rooms/${roomId}`, request, { timeout: TIMEOUT_MUTATION_MS });
  return toRoomDetail(data);
}

/** 여행방 나가기(가정한 엔드포인트, TBD). 마지막 일행이 나가면 서버가 여행방을 삭제한다. */
export async function leaveRoom(roomId: number): Promise<{ roomDeleted: boolean }> {
  const { data } = await api.delete<LeaveRoomResponseDto>(`/rooms/${roomId}/members/me`, { timeout: TIMEOUT_MUTATION_MS });
  return { roomDeleted: data.roomDeleted };
}

/** API-024 여행방 초대 링크 생성. 초대 화면에 들어올 때 링크를 받아 오는 용도로 쓴다. */
export async function createRoomInvitation(roomId: number, signal?: AbortSignal): Promise<RoomInvitation> {
  const { data } = await api.post<RoomInvitationDto>(`/rooms/${roomId}/invitations`, null, { timeout: TIMEOUT_MUTATION_MS, signal });
  return toRoomInvitation(data);
}

/** API-028 여행방 후보 장소 목록. */
export async function getRoomPlaces(roomId: number, signal?: AbortSignal): Promise<RoomPlace[]> {
  const { data } = await api.get<RoomPlacesResponseDto>(`/rooms/${roomId}/places`, { signal });
  return data.places.map(toRoomPlace);
}

export type AddRoomPlacesInput = { roomId: number; request: AddRoomPlacesRequestDto };

/** API-029 검색·AI 추천 장소 담기. 새로 담긴 장소를 돌려준다. */
export async function addRoomPlaces({ roomId, request }: AddRoomPlacesInput): Promise<RoomPlace[]> {
  const { data } = await api.post<RoomPlacesResponseDto>(`/rooms/${roomId}/places`, request, { timeout: TIMEOUT_MUTATION_MS });
  return data.places.map(toRoomPlace);
}

export type ImportCoursePlacesInput = { roomId: number; request: ImportCoursePlacesRequestDto; idempotencyKey: string };

/** API-030 현재 공개된 코스의 선택 방문을 여행방으로 가져온다. */
export async function importCoursePlaces({ roomId, request, idempotencyKey }: ImportCoursePlacesInput): Promise<number> {
  const { data } = await api.post<ImportCoursePlacesResponseDto>(`/rooms/${roomId}/place-imports`, request, idempotentRequestConfig(idempotencyKey));
  return data.added.length;
}

export type DeleteRoomPlaceInput = { roomId: number; roomPlaceId: number };

/** API-032 담은 후보 장소 빼기. */
export async function deleteRoomPlace({ roomId, roomPlaceId }: DeleteRoomPlaceInput): Promise<void> {
  await api.delete<void>(`/rooms/${roomId}/places/${roomPlaceId}`, { timeout: TIMEOUT_MUTATION_MS });
}

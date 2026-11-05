import { api, TIMEOUT_MUTATION_MS } from "@/api/client";
import { IDEMPOTENCY_HEADER } from "@/api/idempotency";
import {
  toRoomSchedule,
  toScheduleDraft,
  type ApplyScheduleDraftRequestDto,
  type CreateScheduleDraftRequestDto,
  type CreateScheduleDraftResponseDto,
  type RoomSchedule,
  type RoomScheduleDto,
  type ScheduleDraft,
  type ScheduleDraftDto,
} from "@/features/schedule/model";
import { assertId } from "@/lib/contract";

/** API-036 여행방 일정. 일정이 없으면 `null`. */
export async function getRoomSchedule(roomId: number, signal?: AbortSignal): Promise<RoomSchedule | null> {
  const { data } = await api.get<RoomScheduleDto | undefined>(`/rooms/${roomId}/schedule`, { signal });
  return data === undefined ? null : toRoomSchedule(data);
}

export type CreateScheduleDraftInput = { roomId: number; request: CreateScheduleDraftRequestDto; idempotencyKey: string };

/** API-037 일정 초안 만들기(작업). 진행과 결과는 API-038로 확인한다. "다시 계산"도 이 요청이다. */
export async function createScheduleDraft({ roomId, request, idempotencyKey }: CreateScheduleDraftInput): Promise<{ draftId: number }> {
  const { data } = await api.post<CreateScheduleDraftResponseDto>(`/rooms/${roomId}/schedule-drafts`, request, {
    timeout: TIMEOUT_MUTATION_MS,
    headers: { [IDEMPOTENCY_HEADER]: idempotencyKey },
  });

  return { draftId: assertId(data.draftId, "draftId") };
}

/** API-038 만든 일정 초안과 진행 상태. */
export async function getScheduleDraft(roomId: number, draftId: number, signal?: AbortSignal): Promise<ScheduleDraft> {
  const { data } = await api.get<ScheduleDraftDto>(`/rooms/${roomId}/schedule-drafts/${draftId}`, { signal });
  return toScheduleDraft(data);
}

export type ApplyScheduleDraftInput = { roomId: number; draftId: number; baseVersion: number | null };

/** API-039 검토한 초안을 여행방 일정으로 적용한다. 그사이 일정이 바뀌었으면 409 C008. */
export async function applyScheduleDraft({ roomId, draftId, baseVersion }: ApplyScheduleDraftInput): Promise<RoomSchedule> {
  const request: ApplyScheduleDraftRequestDto = { draftId, baseVersion };
  const { data } = await api.put<RoomScheduleDto>(`/rooms/${roomId}/schedule`, request, { timeout: TIMEOUT_MUTATION_MS });
  return toRoomSchedule(data);
}

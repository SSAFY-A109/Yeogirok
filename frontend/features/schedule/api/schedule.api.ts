import { api, idempotentRequestConfig } from "@/api/client";
import {
  toSavedSchedule,
  toScheduleDraft,
  toScheduleDraftJob,
  type AdjustScheduleDraftRequestDto,
  type CreateScheduleDraftRequestDto,
  type DraftJobResponseDto,
  type RoomSchedule,
  type SavedSchedule,
  type SaveScheduleRequestDto,
  type ScheduleDraft,
  type ScheduleDraftDto,
  type ScheduleDraftJob,
  type ScheduleResponseDto,
} from "@/features/schedule/model";

/** API-036 여행방 확정 일정. 일정이 없으면 `null`. */
export async function getRoomSchedule(roomId: number, signal?: AbortSignal): Promise<RoomSchedule | null> {
  const { data } = await api.get<ScheduleResponseDto>(`/rooms/${roomId}/schedule`, { signal });
  return toSavedSchedule(data).schedule;
}

export type CreateScheduleDraftInput = { roomId: number; request: CreateScheduleDraftRequestDto; idempotencyKey: string };

/** API-037 일정 초안 만들기(작업, 202). 진행과 결과는 API-038로 확인한다. */
export async function createScheduleDraft({ roomId, request, idempotencyKey }: CreateScheduleDraftInput): Promise<ScheduleDraftJob> {
  const { data } = await api.post<DraftJobResponseDto>(`/rooms/${roomId}/schedule-drafts`, request, idempotentRequestConfig(idempotencyKey));
  return toScheduleDraftJob(data);
}

/** API-038 일정 초안과 계산 진행 상태. */
export async function getScheduleDraft(roomId: number, draftKey: string, signal?: AbortSignal): Promise<ScheduleDraft> {
  const { data } = await api.get<ScheduleDraftDto>(`/rooms/${roomId}/schedule-drafts/${encodeURIComponent(draftKey)}`, { signal });
  return toScheduleDraft(data);
}

export type AdjustScheduleDraftInput = { roomId: number; draftKey: string; request: AdjustScheduleDraftRequestDto; idempotencyKey: string };

/** API-084 고친 초안을 서버에서 다시 계산한다(작업, 202). 끝나면 API-038의 `draftVersion`이 1 오른다. 버전이 다르면 409 C004. */
export async function adjustScheduleDraft({ roomId, draftKey, request, idempotencyKey }: AdjustScheduleDraftInput): Promise<ScheduleDraftJob> {
  const { data } = await api.patch<DraftJobResponseDto>(
    `/rooms/${roomId}/schedule-drafts/${encodeURIComponent(draftKey)}`,
    request,
    idempotentRequestConfig(idempotencyKey),
  );

  return toScheduleDraftJob(data);
}

export type SaveScheduleInput = { roomId: number; request: SaveScheduleRequestDto; idempotencyKey: string };

/** API-039 고친 초안을 여행방 일정으로 저장한다. 그사이 여행방이나 초안이 바뀌었으면 409 C004, 종료 시각을 넘기면 422 SC002. */
export async function saveSchedule({ roomId, request, idempotencyKey }: SaveScheduleInput): Promise<SavedSchedule> {
  const { data } = await api.put<ScheduleResponseDto>(`/rooms/${roomId}/schedule`, request, idempotentRequestConfig(idempotencyKey));
  return toSavedSchedule(data);
}

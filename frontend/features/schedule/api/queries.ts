import { queryOptions, useMutation, useQuery, useQueryClient, type QueryClient } from "@tanstack/react-query";
import { useRef } from "react";

import { createIdempotencyKey } from "@/api/idempotency";
import { resolveRoomVersion, roomKeys, roomOptions, roomPlacesOptions, storeRoomVersion } from "@/features/room/api/queries";
import { hasTripDates } from "@/features/room/model";
import { scheduleKeys } from "@/features/schedule/api/keys";
import { adjustScheduleDraft, createScheduleDraft, getRoomSchedule, getScheduleDraft, saveSchedule } from "@/features/schedule/api/schedule.api";
import { isDraftInProgress, isScheduleConflict } from "@/features/schedule/errors";
import {
  getSavedDayTimes,
  getTripDates,
  isScheduleDraftSettled,
  toCreateDraftRequest,
  type AdjustScheduleDraftRequestDto,
  type CreateScheduleDraftRequestDto,
  type DayTimeRange,
  type RoomSchedule,
  type SaveScheduleRequestDto,
} from "@/features/schedule/model";
import { useIdempotencyKeyFor } from "@/hooks/use-idempotency-key";

/** 초안 진행 확인 간격. */
export const SCHEDULE_DRAFT_POLL_MS = 1_500;

/** 이만큼 지나도 끝나지 않으면 화면이 "아직 만들고 있어요"를 보여 주고 자동 진행 확인을 멈춘다. */
export const SCHEDULE_DRAFT_MAX_WAIT_MS = 120_000;

const MAX_AUTO_CHECKS = SCHEDULE_DRAFT_MAX_WAIT_MS / SCHEDULE_DRAFT_POLL_MS;

/** 쿼리마다 계산이 끝난 초안을 마지막으로 받았을 때의 확인 횟수. 다시 계산할 때마다 최대 대기를 처음부터 세려고 둔다. */
const settledCheckCounts = new WeakMap<object, number>();

export function roomScheduleOptions(roomId: number) {
  return queryOptions({
    queryKey: scheduleKeys.room(roomId),

    queryFn: ({ signal }) => {
      return getRoomSchedule(roomId, signal);
    },
  });
}

/**
 * 일정 초안. 처음 만드는 중이거나 다시 계산하는 중이면 끝날 때까지 다시 확인한다.
 * 최대 대기만큼 확인해도 끝나지 않으면 자동 확인을 멈춘다("다시 확인"은 화면이 직접 부른다).
 * 진행 확인 요청이 실패해도 초안 실패로 보지 않고 다음 확인을 기다린다.
 */
export function scheduleDraftOptions(roomId: number, draftKey: string) {
  return queryOptions({
    queryKey: scheduleKeys.draft(roomId, draftKey),

    queryFn: ({ signal }) => {
      return getScheduleDraft(roomId, draftKey, signal);
    },

    refetchInterval: (query) => {
      const checks = query.state.dataUpdateCount + query.state.errorUpdateCount;

      if (isScheduleDraftSettled(query.state.data)) {
        settledCheckCounts.set(query, checks);
        return false;
      }

      return checks - (settledCheckCounts.get(query) ?? 0) >= MAX_AUTO_CHECKS ? false : SCHEDULE_DRAFT_POLL_MS;
    },
  });
}

/** 여행방 일정. `enabled`가 false면 요청하지 않는다(일정이 없는 여행방 등). */
export function useRoomSchedule(roomId: number, { enabled = true }: { enabled?: boolean } = {}) {
  return useQuery({ ...roomScheduleOptions(roomId), enabled });
}

export function useScheduleDraft(roomId: number, draftKey: string) {
  return useQuery(scheduleDraftOptions(roomId, draftKey));
}

// #region 멱등키

/**
 * 요청 내용으로 멱등키를 고른다. 응답을 받지 못해 같은 내용을 다시 보낼 때는 같은 키를 쓴다.
 * 서버가 이미 접수한 뒤 같은 내용을 다시 보낼 때는(계산에 실패한 초안을 다시 만들기·다시 계산) 새 키를 쓴다.
 * 같은 키로 보내면 서버가 새로 계산하지 않고 앞 작업을 그대로 돌려주기 때문이다.
 */
function useRequestKey() {
  const keyFor = useIdempotencyKeyFor();
  const acceptedCountRef = useRef(0);

  return {
    keyOf: (request: object) => {
      return keyFor(JSON.stringify({ accepted: acceptedCountRef.current, request }));
    },

    markAccepted: () => {
      acceptedCountRef.current += 1;
    },
  };
}

// #endregion

// #region 초안 만들기(API-037)

export type ScheduleDraftPlan = {
  roomId: number;
  days: readonly DayTimeRange[];
  /** 이어받을 저장된 일정("일정 조정하기"). 새로 만들면 `null`. */
  keepSchedule: RoomSchedule | null;
};

/**
 * API-037 요청을 만든다. 서버는 지금 빼지 않은 후보 전부와 여행방 version을 요구하므로(아니면 C003·C004)
 * 보내기 직전에 후보 목록을 다시 받고, 함께 받은 version을 쓴다.
 */
async function buildDraftRequest(queryClient: QueryClient, { roomId, days, keepSchedule }: ScheduleDraftPlan): Promise<CreateScheduleDraftRequestDto> {
  const { places } = await queryClient.fetchQuery({ ...roomPlacesOptions(roomId), staleTime: 0 });
  const baseVersion = await resolveRoomVersion(queryClient, roomId);

  const roomPlaceIds = places.map((place) => {
    return place.id;
  });

  return toCreateDraftRequest({ baseVersion, roomPlaceIds, days, keepSchedule });
}

/** 여행방이 그사이 바뀌어 거절됐으면(409 C004) 다음 요청이 최신 version과 후보를 쓰도록 다시 받는다. */
function refreshRoomAfterConflict(queryClient: QueryClient, roomId: number, error: unknown): void {
  if (isScheduleConflict(error)) {
    void queryClient.invalidateQueries({ queryKey: roomKeys.detail(roomId) });
    void queryClient.invalidateQueries({ queryKey: roomKeys.places(roomId) });
    void queryClient.invalidateQueries({ queryKey: scheduleKeys.room(roomId) });
  }
}

/** 초안 만들기(작업 생성, W13). 자동으로 다시 보내지 않는다. */
export function useCreateScheduleDraft() {
  const queryClient = useQueryClient();
  const requestKey = useRequestKey();

  return useMutation({
    mutationFn: async (plan: ScheduleDraftPlan) => {
      const request = await buildDraftRequest(queryClient, plan);
      return createScheduleDraft({ roomId: plan.roomId, request, idempotencyKey: requestKey.keyOf(request) });
    },

    retry: false,
    onSuccess: requestKey.markAccepted,

    onError: (error, { roomId }) => {
      refreshRoomAfterConflict(queryClient, roomId, error);
    },
  });
}

export type SavedScheduleDraftStart =
  | { kind: "DRAFT"; draftKey: string }
  /** 저장된 일정이 없다. */
  | { kind: "NO_SCHEDULE" }
  /** 시작·종료 시각이 없는 여행 날짜가 있어 조건(W13)부터 정해야 한다. */
  | { kind: "NEEDS_CONDITIONS" };

async function startSavedScheduleDraft(queryClient: QueryClient, roomId: number): Promise<SavedScheduleDraftStart> {
  const [schedule, room] = await Promise.all([
    queryClient.fetchQuery({ ...roomScheduleOptions(roomId), staleTime: 0 }),
    queryClient.fetchQuery({ ...roomOptions(roomId), staleTime: 0 }),
  ]);

  if (schedule === null) {
    return { kind: "NO_SCHEDULE" };
  }

  const days = hasTripDates(room) ? getSavedDayTimes(schedule, getTripDates(room.startDate, room.endDate)) : null;

  if (days === null) {
    return { kind: "NEEDS_CONDITIONS" };
  }

  const request = await buildDraftRequest(queryClient, { roomId, days, keepSchedule: schedule });
  const { draftKey } = await createScheduleDraft({ roomId, request, idempotencyKey: createIdempotencyKey() });
  return { kind: "DRAFT", draftKey };
}

/**
 * "일정 조정하기"(W14)의 초안. 저장된 일정의 항목과 일차 조건을 이어받아 만든다(API-037 `fixedItems`).
 * 화면에 들어온 것이 곧 요청이라 쿼리로 두고, 들어올 때마다 지금 일정으로 새로 만든다(캐시를 남기지 않는다).
 * 작업 생성이므로 연결이 돌아오거나 화면에 다시 왔다고 자동으로 다시 보내지 않는다.
 */
function savedScheduleDraftOptions(queryClient: QueryClient, roomId: number) {
  return queryOptions({
    queryKey: scheduleKeys.savedDraft(roomId),

    queryFn: () => {
      return startSavedScheduleDraft(queryClient, roomId);
    },

    staleTime: Infinity,
    gcTime: 0,
    retry: false,
    refetchOnReconnect: false,
    refetchOnWindowFocus: false,
  });
}

export function useSavedScheduleDraft(roomId: number) {
  const queryClient = useQueryClient();
  return useQuery(savedScheduleDraftOptions(queryClient, roomId));
}

// #endregion

// #region 다시 계산(API-084)과 저장(API-039)

export type AdjustScheduleDraftVariables = { roomId: number; draftKey: string; request: AdjustScheduleDraftRequestDto };

/**
 * 다시 계산(작업 생성). 접수되면 초안을 계산 중으로 표시하고 끝날 때까지 다시 확인한다.
 * 409 C004면 초안과 여행방을 다시 받아 화면이 "반영되지 않았다"를 알릴 수 있게 한다.
 */
export function useAdjustScheduleDraft() {
  const queryClient = useQueryClient();
  const requestKey = useRequestKey();

  return useMutation({
    mutationFn: ({ roomId, draftKey, request }: AdjustScheduleDraftVariables) => {
      return adjustScheduleDraft({ roomId, draftKey, request, idempotencyKey: requestKey.keyOf({ draftKey, request }) });
    },

    retry: false,

    onSuccess: (_job, { roomId, draftKey }) => {
      const { queryKey } = scheduleDraftOptions(roomId, draftKey);
      requestKey.markAccepted();

      queryClient.setQueryData(queryKey, (draft) => {
        return draft?.status === "READY" ? { ...draft, recalculating: true } : draft;
      });

      void queryClient.invalidateQueries({ queryKey });
    },

    onError: (error, { roomId, draftKey }) => {
      refreshRoomAfterConflict(queryClient, roomId, error);

      if (isScheduleConflict(error) || isDraftInProgress(error)) {
        void queryClient.invalidateQueries({ queryKey: scheduleKeys.draft(roomId, draftKey) });
      }
    },
  });
}

export type SaveScheduleVariables = { roomId: number; request: SaveScheduleRequestDto };

/**
 * 고친 초안 저장. 응답 일정과 새 여행방 version을 캐시에 넣고, 일정 유무가 들어 있는 여행방 상세를 다시 받는다.
 * 409 C004면 덮어쓰지 않고 초안·여행방·일정을 다시 받아 화면이 "반영되지 않았다"를 알릴 수 있게 한다.
 */
export function useSaveSchedule() {
  const queryClient = useQueryClient();
  const requestKey = useRequestKey();

  return useMutation({
    mutationFn: ({ roomId, request }: SaveScheduleVariables) => {
      return saveSchedule({ roomId, request, idempotencyKey: requestKey.keyOf(request) });
    },

    retry: false,

    onSuccess: ({ schedule, version }, { roomId }) => {
      queryClient.setQueryData(roomScheduleOptions(roomId).queryKey, schedule);
      storeRoomVersion(queryClient, roomId, version);
      void queryClient.invalidateQueries({ queryKey: roomKeys.detail(roomId) });
      void queryClient.invalidateQueries({ queryKey: scheduleKeys.room(roomId) });
    },

    onError: (error, { roomId, request }) => {
      refreshRoomAfterConflict(queryClient, roomId, error);

      if (isScheduleConflict(error) || isDraftInProgress(error)) {
        void queryClient.invalidateQueries({ queryKey: scheduleKeys.draft(roomId, request.draftKey) });
      }
    },
  });
}

// #endregion

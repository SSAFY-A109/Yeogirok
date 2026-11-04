import { queryOptions, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { roomKeys } from "@/features/room/api/queries";
import { scheduleKeys } from "@/features/schedule/api/keys";
import {
  applyScheduleDraft,
  createScheduleDraft,
  getRoomSchedule,
  getScheduleDraft,
  type ApplyScheduleDraftInput,
  type CreateScheduleDraftInput,
} from "@/features/schedule/api/schedule.api";
import { isScheduleConflict } from "@/features/schedule/errors";
import { isScheduleDraftFinished } from "@/features/schedule/model";

/** 초안 진행 확인 간격. */
export const SCHEDULE_DRAFT_POLL_MS = 1_500;

/** 이만큼 지나도 끝나지 않으면 화면이 "아직 만들고 있어요"를 보여 주고 진행 확인을 멈춘다. */
export const SCHEDULE_DRAFT_MAX_WAIT_MS = 120_000;

export function roomScheduleOptions(roomId: number) {
  return queryOptions({
    queryKey: scheduleKeys.room(roomId),

    queryFn: ({ signal }) => {
      return getRoomSchedule(roomId, signal);
    },
  });
}

/**
 * 일정 초안. 끝난 초안은 바뀌지 않으므로 다시 받지 않는다.
 * `poll`이 false면 진행 확인을 멈춘다(최대 대기 초과). 진행 확인 요청이 실패해도 초안 실패로 보지 않고 다음 확인을 기다린다.
 */
export function scheduleDraftOptions(roomId: number, draftId: number, { poll = true }: { poll?: boolean } = {}) {
  return queryOptions({
    queryKey: scheduleKeys.draft(roomId, draftId),

    queryFn: ({ signal }) => {
      return getScheduleDraft(roomId, draftId, signal);
    },

    staleTime: Infinity,

    refetchInterval: (query) => {
      return poll && !isScheduleDraftFinished(query.state.data) ? SCHEDULE_DRAFT_POLL_MS : false;
    },
  });
}

/** 여행방 일정. `enabled`가 false면 요청하지 않는다(일정이 없는 여행방 등). */
export function useRoomSchedule(roomId: number, { enabled = true }: { enabled?: boolean } = {}) {
  return useQuery({ ...roomScheduleOptions(roomId), enabled });
}

export function useScheduleDraft(roomId: number, draftId: number, { poll = true }: { poll?: boolean } = {}) {
  return useQuery(scheduleDraftOptions(roomId, draftId, { poll }));
}

/** 초안 만들기·다시 계산(작업 생성). 중복 생성을 막는 멱등키를 쓰므로 자동으로 다시 보내지 않는다. */
export function useCreateScheduleDraft() {
  return useMutation({
    mutationFn: (input: CreateScheduleDraftInput) => {
      return createScheduleDraft(input);
    },

    retry: false,
  });
}

/**
 * 초안 적용. 응답 일정을 캐시에 넣고, 일정 만든 시각이 들어 있는 여행방 상세를 다시 받는다.
 * 409 C008이면 최신 일정을 다시 받아 화면이 "반영되지 않았다"를 알릴 수 있게 한다.
 */
export function useApplyScheduleDraft() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (input: ApplyScheduleDraftInput) => {
      return applyScheduleDraft(input);
    },

    retry: false,

    onSuccess: (schedule, { roomId }) => {
      queryClient.setQueryData(scheduleKeys.room(roomId), schedule);
      void queryClient.invalidateQueries({ queryKey: roomKeys.detail(roomId) });
    },

    onError: (error, { roomId }) => {
      if (isScheduleConflict(error)) {
        void queryClient.invalidateQueries({ queryKey: scheduleKeys.room(roomId) });
        void queryClient.invalidateQueries({ queryKey: roomKeys.detail(roomId) });
      }
    },
  });
}

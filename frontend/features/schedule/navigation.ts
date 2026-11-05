import { router } from "expo-router";

import { toScheduleParams, type ScheduleSource } from "@/features/schedule/route-params";

/** 일정 조건 화면(W13)으로 간다. 쌓여 있으면 그 화면으로 돌아가고(입력 유지), 없으면 지금 화면을 바꾼다. */
export function goToScheduleConditions(roomId: number) {
  router.dismissTo({ pathname: "/rooms/[roomId]/schedule/new", params: { roomId: String(roomId) } });
}

/** 여행방 상세의 일정 탭으로 돌아간다. 쌓인 조건·초안 화면은 닫는다. */
export function goToRoomSchedule(roomId: number) {
  router.dismissTo({ pathname: "/rooms/[roomId]", params: { roomId: String(roomId), tab: "schedule" } });
}

/**
 * 일정 초안 화면(W14)이 보여 줄 일정을 바꾼다. 화면은 그대로 두고 파라미터만 바꾼다.
 * `from`은 다시 계산하는 동안 대신 보여 줄 이전 일정이다.
 */
export function showSchedule(source: ScheduleSource, from: ScheduleSource | null = null) {
  router.setParams(toScheduleParams(source, from));
}

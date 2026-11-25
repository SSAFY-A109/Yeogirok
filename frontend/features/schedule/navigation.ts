import { router } from "expo-router";

import { KEEP_SAVED_PARAM } from "@/features/schedule/route-params";

/**
 * 일정 조건 화면(W13)으로 간다. 쌓여 있으면 그 화면으로 돌아가고(입력 유지), 없으면 지금 화면을 바꾼다.
 * `keepSaved`면 저장된 일정의 날짜별 장소를 이어받는 초안을 만든다.
 */
export function goToScheduleConditions(roomId: number, { keepSaved = false }: { keepSaved?: boolean } = {}) {
  const params = keepSaved ? { roomId: String(roomId), keep: KEEP_SAVED_PARAM } : { roomId: String(roomId) };
  router.dismissTo({ pathname: "/rooms/[roomId]/schedule/new", params });
}

/** 여행방 상세의 일정 탭으로 돌아간다. 쌓인 조건·초안 화면은 닫는다. */
export function goToRoomSchedule(roomId: number) {
  router.dismissTo({ pathname: "/rooms/[roomId]", params: { roomId: String(roomId), tab: "schedule" } });
}

/** 만든 초안을 검토·조정하는 화면(W14)으로 간다. */
export function goToScheduleDraft(roomId: number, draftKey: string) {
  router.push({ pathname: "/rooms/[roomId]/schedule/draft", params: { roomId: String(roomId), draftKey } });
}

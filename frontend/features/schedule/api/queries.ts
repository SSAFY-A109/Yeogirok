import { queryOptions, useQuery } from "@tanstack/react-query";

import { getRoomSchedule } from "@/features/schedule/api/schedule.api";

export const scheduleKeys = {
  all: ["schedule"] as const,

  room: (roomId: number) => {
    return [...scheduleKeys.all, "room", roomId] as const;
  },
};

export function roomScheduleOptions(roomId: number) {
  return queryOptions({
    queryKey: scheduleKeys.room(roomId),

    queryFn: ({ signal }) => {
      return getRoomSchedule(roomId, signal);
    },
  });
}

/** 여행방 일정. `enabled`가 false면 요청하지 않는다(일정이 없는 여행방 등). */
export function useRoomSchedule(roomId: number, { enabled = true }: { enabled?: boolean } = {}) {
  return useQuery({ ...roomScheduleOptions(roomId), enabled });
}

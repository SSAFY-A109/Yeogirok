import { api } from "@/api/client";
import { toRoomSchedule, type RoomSchedule, type RoomScheduleDto } from "@/features/schedule/model";

/** API-036 여행방 일정. 일정이 없으면 `null`. */
export async function getRoomSchedule(roomId: number, signal?: AbortSignal): Promise<RoomSchedule | null> {
  const { data } = await api.get<RoomScheduleDto | undefined>(`/rooms/${roomId}/schedule`, { signal });
  return data === undefined ? null : toRoomSchedule(data);
}

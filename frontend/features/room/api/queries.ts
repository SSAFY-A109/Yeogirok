import { queryOptions, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import {
  addRoomPlaces,
  createRoom,
  createRoomInvitation,
  deleteRoomPlace,
  getRoom,
  getRoomPlaces,
  getRooms,
  leaveRoom,
  updateRoom,
  type AddRoomPlacesInput,
  type DeleteRoomPlaceInput,
  type UpdateRoomInput,
} from "@/features/room/api/room.api";
import type { CreateRoomRequestDto } from "@/features/room/model";
import { scheduleKeys } from "@/features/schedule/api/keys";

export const roomKeys = {
  all: ["room"] as const,

  list: () => {
    return [...roomKeys.all, "list"] as const;
  },

  detail: (roomId: number) => {
    return [...roomKeys.all, "detail", roomId] as const;
  },

  places: (roomId: number) => {
    return [...roomKeys.all, "places", roomId] as const;
  },

  invitation: (roomId: number) => {
    return [...roomKeys.all, "invitation", roomId] as const;
  },
};

export function roomsOptions() {
  return queryOptions({
    queryKey: roomKeys.list(),

    queryFn: ({ signal }) => {
      return getRooms(signal);
    },
  });
}

export function roomOptions(roomId: number) {
  return queryOptions({
    queryKey: roomKeys.detail(roomId),

    queryFn: ({ signal }) => {
      return getRoom(roomId, signal);
    },
  });
}

export function roomPlacesOptions(roomId: number) {
  return queryOptions({
    queryKey: roomKeys.places(roomId),

    queryFn: ({ signal }) => {
      return getRoomPlaces(roomId, signal);
    },
  });
}

/** 초대 링크. 화면에 머무는 동안 같은 링크를 쓰고, 다시 들어오면 새로 받는다(유효 기간은 DEC-12 TBD). */
export function roomInvitationOptions(roomId: number) {
  return queryOptions({
    queryKey: roomKeys.invitation(roomId),

    queryFn: ({ signal }) => {
      return createRoomInvitation(roomId, signal);
    },

    staleTime: Infinity,
    gcTime: 0,
  });
}

export function useRooms() {
  return useQuery(roomsOptions());
}

export function useRoom(roomId: number) {
  return useQuery(roomOptions(roomId));
}

export function useRoomPlaces(roomId: number) {
  return useQuery(roomPlacesOptions(roomId));
}

/** 정원이 찼으면(`enabled: false`) 링크를 받지 않는다. */
export function useRoomInvitation(roomId: number, { enabled = true }: { enabled?: boolean } = {}) {
  return useQuery({ ...roomInvitationOptions(roomId), enabled });
}

/** 생성한 여행방 상세를 캐시에 넣고 목록을 다시 받는다. */
export function useCreateRoom() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (request: CreateRoomRequestDto) => {
      return createRoom(request);
    },

    onSuccess: (room) => {
      queryClient.setQueryData(roomKeys.detail(room.id), room);
      void queryClient.invalidateQueries({ queryKey: roomKeys.list() });
    },
  });
}

/** 날짜·이동 수단이 바뀌면 일정도 달라지므로 일정 캐시까지 다시 받는다. */
export function useUpdateRoom() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (input: UpdateRoomInput) => {
      return updateRoom(input);
    },

    onSuccess: (room) => {
      queryClient.setQueryData(roomKeys.detail(room.id), room);
      void queryClient.invalidateQueries({ queryKey: roomKeys.list() });
      void queryClient.invalidateQueries({ queryKey: roomKeys.places(room.id) });
      void queryClient.invalidateQueries({ queryKey: scheduleKeys.room(room.id) });
    },
  });
}

/**
 * 목록만 다시 받는다. 나간 방의 상세·장소·일정 캐시는 바로 지우지 않는다.
 * 지우면 닫히는 중인 화면이 그 방을 다시 요청해 "찾을 수 없음"이 잠깐 보인다. 화면이 닫히면 gcTime 뒤에 정리된다.
 */
export function useLeaveRoom() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (roomId: number) => {
      return leaveRoom(roomId);
    },

    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: roomKeys.list() });
    },
  });
}

export function useAddRoomPlaces() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (input: AddRoomPlacesInput) => {
      return addRoomPlaces(input);
    },

    onSuccess: (_places, { roomId }) => {
      void queryClient.invalidateQueries({ queryKey: roomKeys.places(roomId) });
      void queryClient.invalidateQueries({ queryKey: roomKeys.detail(roomId) });
    },
  });
}

export function useDeleteRoomPlace() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (input: DeleteRoomPlaceInput) => {
      return deleteRoomPlace(input);
    },

    onSettled: (_result, _error, { roomId }) => {
      void queryClient.invalidateQueries({ queryKey: roomKeys.places(roomId) });
      void queryClient.invalidateQueries({ queryKey: roomKeys.detail(roomId) });
    },
  });
}

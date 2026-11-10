import { queryOptions, useMutation, useQuery, useQueryClient, type QueryClient } from "@tanstack/react-query";

import { isApiError } from "@/api/errors";
import { createIdempotencyKey } from "@/api/idempotency";
import { courseKeys } from "@/features/course/api/queries";
import {
  acceptRoomInvitation,
  addRoomPlaces,
  createRoom,
  createRoomInvitation,
  deleteRoomPlace,
  getRoom,
  getRoomPlaces,
  getRooms,
  importCoursePlaces,
  leaveRoom,
  restoreRoomPlace,
  updateRoom,
} from "@/features/room/api/room.api";
import type { RoomChanges, RoomPlaceList } from "@/features/room/model";
import { scheduleKeys } from "@/features/schedule/api/keys";

/** 요청한 version이 지금 여행방 version과 다르다는 공통 code(409). */
const VERSION_MISMATCH_CODE = "C004";

/**
 * 여행방 version을 싣는 변경 요청은 차례로 보낸다. 앞 요청의 응답으로 받은 새 version을 다음 요청이 써야 하기 때문이다.
 * 같은 scope의 mutation은 앞의 것이 끝난 뒤에 실행된다.
 */
const ROOM_VERSION_SCOPE = { id: "room-version" } as const;

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

  /** 뺀 후보까지 포함한 목록. `places` 키로 시작해 후보 목록을 무효화하면 함께 다시 받는다. */
  allPlaces: (roomId: number) => {
    return [...roomKeys.places(roomId), "all"] as const;
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
      return getRoomPlaces(roomId, "ACTIVE", signal);
    },
  });
}

export function roomAllPlacesOptions(roomId: number) {
  return queryOptions({
    queryKey: roomKeys.allPlaces(roomId),

    queryFn: ({ signal }) => {
      return getRoomPlaces(roomId, "ALL", signal);
    },
  });
}

/**
 * 초대 링크(API-024). 링크는 여행방마다 하나이고 만료되지 않아 다시 받아도 같은 값이다.
 * 받기만 하는 요청이라 멱등키는 부를 때마다 새로 만든다.
 */
export function roomInvitationOptions(roomId: number) {
  return queryOptions({
    queryKey: roomKeys.invitation(roomId),

    queryFn: ({ signal }) => {
      return createRoomInvitation({ roomId, idempotencyKey: createIdempotencyKey() }, signal);
    },

    staleTime: Infinity,
  });
}

// #region 여행방 version

/**
 * 요청에 실을 여행방 version. 상세와 후보 목록 캐시 중 더 새 값을 쓰고, 둘 다 없으면 상세를 받아 온다.
 * 여행방 version은 정보 수정·후보 담기·빼기·복원·일정 저장 때마다 오른다.
 */
export async function resolveRoomVersion(queryClient: QueryClient, roomId: number): Promise<number> {
  const detailVersion = queryClient.getQueryData(roomOptions(roomId).queryKey)?.version;
  const placesVersion = queryClient.getQueryData(roomPlacesOptions(roomId).queryKey)?.version;

  if (detailVersion !== undefined || placesVersion !== undefined) {
    return Math.max(detailVersion ?? 0, placesVersion ?? 0);
  }

  const room = await queryClient.fetchQuery(roomOptions(roomId));
  return room.version;
}

/** 변경 응답으로 받은 새 version을 캐시에 적어, 이어지는 요청이 낡은 version을 보내지 않게 한다. */
export function storeRoomVersion(queryClient: QueryClient, roomId: number, version: number): void {
  queryClient.setQueryData(roomOptions(roomId).queryKey, (room) => {
    return room === undefined ? room : { ...room, version };
  });

  queryClient.setQueryData(roomPlacesOptions(roomId).queryKey, (list) => {
    return list === undefined ? list : { ...list, version };
  });
}

/** version이 달라 거절됐으면(409 C004) 여행방을 다시 받아 다음 요청이 최신 version을 쓰게 한다. */
function refreshRoomAfterConflict(queryClient: QueryClient, roomId: number, error: unknown): void {
  if (isApiError(error) && error.code === VERSION_MISMATCH_CODE) {
    void queryClient.invalidateQueries({ queryKey: roomKeys.detail(roomId) });
    void queryClient.invalidateQueries({ queryKey: roomKeys.places(roomId) });
  }
}

/** 후보 목록이 바뀌면 상세의 장소 수, 목록의 장소 수, 일정의 재계산 필요 표시가 함께 달라진다. */
function invalidateAfterPlacesChanged(queryClient: QueryClient, roomId: number): void {
  void queryClient.invalidateQueries({ queryKey: roomKeys.places(roomId) });
  void queryClient.invalidateQueries({ queryKey: roomKeys.detail(roomId) });
  void queryClient.invalidateQueries({ queryKey: roomKeys.list() });
}

// #endregion

function selectPlaces(list: RoomPlaceList) {
  return list.places;
}

export function useRooms() {
  return useQuery(roomsOptions());
}

export function useRoom(roomId: number) {
  return useQuery(roomOptions(roomId));
}

/** 빼지 않은 후보 장소. 함께 받은 여행방 version은 `resolveRoomVersion`이 캐시에서 읽는다. */
export function useRoomPlaces(roomId: number) {
  return useQuery({ ...roomPlacesOptions(roomId), select: selectPlaces });
}

/** 뺀 후보까지 포함한 후보 장소. 일정 항목(`roomPlaceId`)의 장소 이름·카테고리를 찾는 데 쓴다. */
export function useAllRoomPlaces(roomId: number, { enabled = true }: { enabled?: boolean } = {}) {
  return useQuery({ ...roomAllPlacesOptions(roomId), select: selectPlaces, enabled });
}

/** 정원이 찼으면(`enabled: false`) 링크를 받지 않는다. */
export function useRoomInvitation(roomId: number, { enabled = true }: { enabled?: boolean } = {}) {
  return useQuery({ ...roomInvitationOptions(roomId), enabled });
}

/** 생성한 여행방을 목록에 반영한다. 상세는 상세 화면에서 조회한다. */
export function useCreateRoom() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: createRoom,
    retry: false,

    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: roomKeys.list() });
    },
  });
}

export type UpdateRoomVariables = { roomId: number; changes: RoomChanges; previewOnly?: boolean };

/**
 * 여행방 정보 수정(API-023). `previewOnly`면 캐시를 건드리지 않는다.
 * 저장하면 날짜·이동 수단에 따라 일정도 달라지므로 일정 캐시까지 다시 받는다.
 */
export function useUpdateRoom() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ roomId, changes, previewOnly = false }: UpdateRoomVariables) => {
      const version = await resolveRoomVersion(queryClient, roomId);
      return updateRoom({ roomId, changes, version, previewOnly });
    },

    retry: false,
    scope: ROOM_VERSION_SCOPE,

    onSuccess: (result) => {
      if (!result.applied) {
        return;
      }

      storeRoomVersion(queryClient, result.roomId, result.version);
      void queryClient.invalidateQueries({ queryKey: roomKeys.detail(result.roomId) });
      void queryClient.invalidateQueries({ queryKey: roomKeys.list() });
      void queryClient.invalidateQueries({ queryKey: scheduleKeys.room(result.roomId) });
    },

    onError: (error, { roomId }) => {
      refreshRoomAfterConflict(queryClient, roomId, error);
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
    mutationFn: leaveRoom,
    retry: false,

    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: roomKeys.list() });
    },
  });
}

/** 초대 링크로 참여(API-025). 참여한 여행방이 목록에 보이게 한다. */
export function useAcceptRoomInvitation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: acceptRoomInvitation,
    retry: false,

    onSuccess: ({ roomId }) => {
      void queryClient.invalidateQueries({ queryKey: roomKeys.list() });
      void queryClient.invalidateQueries({ queryKey: roomKeys.detail(roomId) });
    },
  });
}

export function useAddRoomPlaces() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: addRoomPlaces,
    retry: false,
    scope: ROOM_VERSION_SCOPE,

    onSuccess: ({ version }, { roomId }) => {
      storeRoomVersion(queryClient, roomId, version);
    },

    onSettled: (_result, _error, { roomId }) => {
      invalidateAfterPlacesChanged(queryClient, roomId);
    },
  });
}

export function useImportCoursePlaces() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: importCoursePlaces,
    retry: false,

    onSettled: (_count, _error, { roomId }) => {
      invalidateAfterPlacesChanged(queryClient, roomId);
      void queryClient.invalidateQueries({ queryKey: courseKeys.all });
    },
  });
}

export type RoomPlaceVariables = { roomId: number; roomPlaceId: number };

/** 후보 장소 빼기(API-032). 뺀 장소는 `useRestoreRoomPlace`로 되돌릴 수 있다. */
export function useDeleteRoomPlace() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ roomId, roomPlaceId }: RoomPlaceVariables) => {
      const version = await resolveRoomVersion(queryClient, roomId);
      return deleteRoomPlace({ roomId, roomPlaceId, version });
    },

    retry: false,
    scope: ROOM_VERSION_SCOPE,

    onSuccess: (version, { roomId }) => {
      storeRoomVersion(queryClient, roomId, version);
    },

    onError: (error, { roomId }) => {
      refreshRoomAfterConflict(queryClient, roomId, error);
    },

    onSettled: (_version, _error, { roomId }) => {
      invalidateAfterPlacesChanged(queryClient, roomId);
    },
  });
}

/** 뺀 후보 장소 복원(API-094). */
export function useRestoreRoomPlace() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ roomId, roomPlaceId }: RoomPlaceVariables) => {
      const version = await resolveRoomVersion(queryClient, roomId);
      return restoreRoomPlace({ roomId, roomPlaceId, version });
    },

    retry: false,
    scope: ROOM_VERSION_SCOPE,

    onSuccess: (version, { roomId }) => {
      storeRoomVersion(queryClient, roomId, version);
    },

    onError: (error, { roomId }) => {
      refreshRoomAfterConflict(queryClient, roomId, error);
    },

    onSettled: (_version, _error, { roomId }) => {
      invalidateAfterPlacesChanged(queryClient, roomId);
    },
  });
}

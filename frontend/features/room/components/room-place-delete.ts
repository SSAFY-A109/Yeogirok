import { useCallback, useEffect, useRef, useState } from "react";

import { useToast } from "@/components/ui/toast";
import { useDeleteRoomPlace } from "@/features/room/api/queries";
import { getRoomErrorMessage, ROOM_ERROR_FALLBACK } from "@/features/room/errors";
import type { RoomPlace } from "@/features/room/model";

/** 되돌리기 토스트를 띄워 두는 시간. 토스트가 사라지는 때에 맞춰 실제로 삭제한다. */
const UNDO_DURATION_MS = 4_000;

type PendingTimers = Map<number, ReturnType<typeof setTimeout>>;

/** 기다리던 삭제 타이머를 멈추고 지운다. 기다리던 삭제가 없었으면 `false`. */
function cancelPendingTimer(timers: PendingTimers, roomPlaceId: number): boolean {
  const timer = timers.get(roomPlaceId);

  if (timer === undefined) {
    return false;
  }

  clearTimeout(timer);
  timers.delete(roomPlaceId);
  return true;
}

/** 빼기를 눌러 숨긴 장소를 뺀 목록. */
export function getVisibleRoomPlaces(places: RoomPlace[], hiddenIds: ReadonlySet<number>): RoomPlace[] {
  return places.filter((place) => {
    return !hiddenIds.has(place.id);
  });
}

/**
 * 후보 장소 빼기(되돌리기 가능, Figma W09 "후보에서 뺐어요 되돌리기").
 * 누르면 목록에서 바로 숨기고 토스트가 사라질 때 서버에서 삭제한다. 되돌리기를 누르면 취소한다.
 * 다른 장소를 또 빼면 앞 토스트가 새 토스트로 바뀌므로 앞의 삭제는 바로 보낸다. 화면을 떠날 때도 남은 삭제를 바로 보낸다.
 * 삭제에 실패하면 그 장소를 다시 보이게 하고 오류 토스트를 띄운다.
 */
export function useRoomPlaceRemoval(roomId: number) {
  const [hiddenIds, setHiddenIds] = useState<ReadonlySet<number>>(() => {
    return new Set();
  });

  const timersRef = useRef<PendingTimers>(new Map());
  const { mutateAsync: deleteRoomPlace } = useDeleteRoomPlace();
  const toast = useToast();

  const unhide = useCallback((roomPlaceId: number) => {
    setHiddenIds((current) => {
      const next = new Set(current);
      next.delete(roomPlaceId);
      return next;
    });
  }, []);

  const commit = useCallback((roomPlaceId: number) => {
    cancelPendingTimer(timersRef.current, roomPlaceId);

    void deleteRoomPlace({ roomId, roomPlaceId }).catch((error: unknown) => {
      unhide(roomPlaceId);
      toast.show({ message: getRoomErrorMessage(error, ROOM_ERROR_FALLBACK.deletePlace) });
    });
  }, [deleteRoomPlace, roomId, toast, unhide]);

  useEffect(() => {
    const timers = timersRef.current;

    return () => {
      if (timers.size === 0) {
        return;
      }

      toast.hide();
      [...timers.keys()].forEach(commit);
    };
  }, [commit, toast]);

  const undo = (roomPlaceId: number) => {
    if (cancelPendingTimer(timersRef.current, roomPlaceId)) {
      unhide(roomPlaceId);
    }
  };

  const remove = (place: RoomPlace) => {
    [...timersRef.current.keys()].forEach(commit);

    setHiddenIds((current) => {
      return new Set(current).add(place.id);
    });

    timersRef.current.set(place.id, setTimeout(() => {
      commit(place.id);
    }, UNDO_DURATION_MS));

    toast.show({
      message: "후보에서 뺐어요",

      action: {
        label: "되돌리기",

        onPress: () => {
          undo(place.id);
        },
      },

      durationMs: UNDO_DURATION_MS,
    });
  };

  return { hiddenIds, remove };
}

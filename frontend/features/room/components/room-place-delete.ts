import { useCallback, useState } from "react";

import { useToast } from "@/components/ui/toast";
import { useDeleteRoomPlace, useRestoreRoomPlace } from "@/features/room/api/queries";
import { getRoomErrorMessage, ROOM_ERROR_FALLBACK } from "@/features/room/errors";
import type { RoomPlace } from "@/features/room/model";

/** 되돌리기 토스트를 띄워 두는 시간. 복원(API-094)에는 시간 제한이 없고, 이 시간은 토스트가 보이는 동안일 뿐이다. */
const UNDO_TOAST_DURATION_MS = 4_000;

/** 빼기를 눌러 숨긴 장소를 뺀 목록. */
export function getVisibleRoomPlaces(places: RoomPlace[], hiddenIds: ReadonlySet<number>): RoomPlace[] {
  return places.filter((place) => {
    return !hiddenIds.has(place.id);
  });
}

/**
 * 후보 장소 빼기(되돌리기 가능, Figma W09 "후보에서 뺐어요 되돌리기").
 * 누르면 목록에서 바로 숨기고 서버에서 뺀다(API-032). 토스트의 되돌리기를 누르면 복원한다(API-094).
 * 빼기에 실패하면 그 장소를 다시 보이게 하고 오류 토스트를 띄운다.
 */
export function useRoomPlaceRemoval(roomId: number) {
  const [hiddenIds, setHiddenIds] = useState<ReadonlySet<number>>(() => {
    return new Set();
  });

  const { mutateAsync: deleteRoomPlace } = useDeleteRoomPlace();
  const { mutateAsync: restoreRoomPlace } = useRestoreRoomPlace();
  const toast = useToast();

  const unhide = useCallback((roomPlaceId: number) => {
    setHiddenIds((current) => {
      const next = new Set(current);
      next.delete(roomPlaceId);
      return next;
    });
  }, []);

  const restore = (roomPlaceId: number) => {
    restoreRoomPlace({ roomId, roomPlaceId }).then(
      () => {
        unhide(roomPlaceId);
      },
      (error: unknown) => {
        toast.show({ message: getRoomErrorMessage(error, ROOM_ERROR_FALLBACK.restorePlace) });
      },
    );
  };

  const remove = (place: RoomPlace) => {
    setHiddenIds((current) => {
      return new Set(current).add(place.id);
    });

    deleteRoomPlace({ roomId, roomPlaceId: place.id }).then(
      () => {
        toast.show({
          message: "후보에서 뺐어요",

          action: {
            label: "되돌리기",

            onPress: () => {
              restore(place.id);
            },
          },

          durationMs: UNDO_TOAST_DURATION_MS,
        });
      },
      (error: unknown) => {
        unhide(place.id);
        toast.show({ message: getRoomErrorMessage(error, ROOM_ERROR_FALLBACK.deletePlace) });
      },
    );
  };

  return { hiddenIds, remove };
}

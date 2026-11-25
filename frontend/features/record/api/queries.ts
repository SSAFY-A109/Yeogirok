import { useRef } from "react";
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { isApiError } from "@/api/errors";
import { createIdempotencyKey } from "@/api/idempotency";
import { roomKeys } from "@/features/room/api/queries";
import { createRecord, getRecord, getRecords } from "./record.api";

export const recordKeys = {
  list: ["record", "list"] as const,

  detail: (id: number) => {
    return ["record", id] as const;
  },
};

export function useRecords() {
  return useInfiniteQuery({
    queryKey: recordKeys.list,
    initialPageParam: undefined as string | undefined,

    queryFn: ({ pageParam, signal }) => {
      return getRecords(pageParam, signal);
    },

    getNextPageParam: (last) => {
      return last.nextCursor ?? undefined;
    },
  });
}

export function useRecord(id: number) {
  return useQuery({ queryKey: recordKeys.detail(id),

    queryFn: ({ signal }) => {
      return getRecord(id, signal);
    } });
}

export function useCreateRecord(onOpen: (id: number) => void) {
  const client = useQueryClient();
  const keys = useRef(new Map<number, string>());
  return useMutation({
    mutationFn: async (roomId: number) => {
      const key = keys.current.get(roomId) ?? createIdempotencyKey();
      keys.current.set(roomId, key);

      try {
        return (await createRecord(roomId, key)).recordId;
      } catch (error) {
        if (!isApiError(error) || ![0, 409].includes(error.status)) {
          throw error;
        }

        const { items } = await getRecords(undefined, undefined, roomId);

        const existing = items.find((item) => {
          return item.roomId === roomId;
        });

        if (existing) {
          return existing.id;
        }

        throw error;
      }
    },

    retry: false,

    onSuccess: (id, roomId) => {
      keys.current.delete(roomId);
      void client.invalidateQueries({ queryKey: recordKeys.list });
      void client.invalidateQueries({ queryKey: roomKeys.list() });
      void client.invalidateQueries({ queryKey: roomKeys.detail(roomId) });
      onOpen(id);
    },
  });
}

/** 같은 사용자 입력의 재전송은 동일한 키를 사용한다. 쓰기 요청은 자동으로 재시도하지 않는다. */
export function useRecordWrite(id: number) {
  const client = useQueryClient();
  const attempt = useRef<{ signature: string; key: string } | null>(null);
  const active = useRef(false);
  const mutation = useMutation({
    mutationFn: ({ send, key }: { send: (key: string) => Promise<unknown>; key: string }) => {
      return send(key);
    },

    retry: false,
    gcTime: 0,

    onSettled: async () => {
      await client.invalidateQueries({ queryKey: recordKeys.detail(id) });
      void client.invalidateQueries({ queryKey: recordKeys.list });
    },
  });

  async function run(signature: string, send: (key: string) => Promise<unknown>) {
    if (active.current) {
      return false;
    }

    if (isApiError(mutation.error) && mutation.error.status === 0 && attempt.current?.signature !== signature) {
      return false;
    }

    active.current = true;

    if (attempt.current?.signature !== signature) {
      attempt.current = { signature, key: createIdempotencyKey() };
    }

    try {
      await mutation.mutateAsync({ send, key: attempt.current.key });
      attempt.current = null;
      return true;
    } catch {
      return false;
    } finally {
      active.current = false;
    }
  }

  return { ...mutation, run };
}

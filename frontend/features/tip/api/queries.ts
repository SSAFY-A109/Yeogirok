import { infiniteQueryOptions, queryOptions, useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useCallback } from "react";

import { createIdempotencyKey } from "@/api/idempotency";
import { isApiError } from "@/api/errors";
import { meOptions, userKeys } from "@/features/user/api/queries";
import { forgetPendingTip, getPendingTipIds, rememberPendingTip } from "@/features/tip/pending";
import {
  createTip, deleteTip, getMyTip, getMyTips, getTipStatus, retryTip, searchTips, setTipReaction, updateTip, viewTip,
  type CreateTipInput, type DeleteTipInput, type RetryTipInput, type TipReactionInput, type UpdateTipInput,
} from "@/features/tip/api/tip.api";
import { isTipOperationPending, validateTipLocation, type LocationEvidenceDto, type MyTipsRequestDto, type TipDetail, type TipPublicationStatus } from "@/features/tip/model";

export const TIP_STATUS_POLL_MS = 2_000;
export const TIP_STATUS_MAX_WAIT_MS = 120_000;
const TIP_PAGE_SIZE = 10;

/** 측정할 때마다 새 세션을 만든다. 위치·측정 시각을 쿼리 키에 넣지 않는다. */
export function createTipLocationSession(location: LocationEvidenceDto) {
  return { id: createIdempotencyKey(), location: validateTipLocation(location) };
}

export type TipLocationSession = ReturnType<typeof createTipLocationSession>;

export const tipKeys = {
  all: ["tip"] as const,

  nearby: () => {
    return ["tip", "nearby"] as const;
  },

  search: (sessionId: string) => {
    return ["tip", "nearby", sessionId] as const;
  },

  detail: (tipId: number) => {
    return ["tip", "detail", tipId] as const;
  },

  view: (tipId: number, sessionId: string) => {
    return [...tipKeys.detail(tipId), sessionId] as const;
  },

  mine: () => {
    return ["tip", "mine"] as const;
  },

  myList: (status?: string) => {
    return [...tipKeys.mine(), "list", status ?? null] as const;
  },

  myDetail: (tipId: number) => {
    return [...tipKeys.mine(), "detail", tipId] as const;
  },

  status: (tipId: number, jobId?: string) => {
    const key = ["tip", "status", tipId] as const;
    return jobId === undefined ? key : [...key, jobId] as const;
  },
};

export function nearbyTipsOptions(session: TipLocationSession) {
  return infiniteQueryOptions({
    queryKey: tipKeys.search(session.id),

    queryFn: ({ pageParam, signal }) => {
      return searchTips({ location: session.location, cursor: pageParam ?? undefined, limit: TIP_PAGE_SIZE }, signal);
    },

    initialPageParam: null as string | null,

    getNextPageParam: (page) => {
      return page.nextCursor;
    },

    gcTime: 0,
    staleTime: 0,
    retry: false,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  });
}

export function tipViewOptions(tipId: number, session: TipLocationSession) {
  return queryOptions({
    queryKey: tipKeys.view(tipId, session.id),

    queryFn: ({ signal }) => {
      return viewTip({ tipId, location: session.location }, signal);
    },

    gcTime: 0,
    staleTime: 0,
    retry: false,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  });
}

export function tipStatusOptions(tipId: number, jobId?: string) {
  return queryOptions({
    queryKey: tipKeys.status(tipId, jobId),

    queryFn: ({ signal }) => {
      return getTipStatus(tipId, signal);
    },

    retry: false,

    refetchInterval: (query) => {
      if (isApiError(query.state.error) && query.state.error.status >= 400 && query.state.error.status < 500) {
        return false;
      }

      const checks = query.state.dataUpdateCount + query.state.errorUpdateCount;
      const status = query.state.data;
      const pending = status === undefined || isTipOperationPending(status) ||
        (jobId !== undefined && status.jobId !== jobId && status.status !== "DELETED");
      return pending && checks < TIP_STATUS_MAX_WAIT_MS / TIP_STATUS_POLL_MS ? TIP_STATUS_POLL_MS : false;
    },
  });
}

export function myTipsOptions(status?: string) {
  return infiniteQueryOptions({
    queryKey: tipKeys.myList(status),

    queryFn: ({ pageParam, signal }) => {
      const request: MyTipsRequestDto = { status, cursor: pageParam ?? undefined, limit: TIP_PAGE_SIZE };
      return getMyTips(request, signal);
    },

    initialPageParam: null as string | null,

    getNextPageParam: (page) => {
      return page.nextCursor;
    },
  });
}

export function myTipOptions(tipId: number) {
  return queryOptions({
    queryKey: tipKeys.myDetail(tipId),

    queryFn: ({ signal }) => {
      return getMyTip(tipId, signal);
    },
  });
}

export function useNearbyTips(session: TipLocationSession) {
  return useInfiniteQuery(nearbyTipsOptions(session));
}

export function useTipView(tipId: number, session: TipLocationSession) {
  return useQuery(tipViewOptions(tipId, session));
}

export function useTipStatus(tipId: number | null, jobId?: string) {
  return useQuery({ ...tipStatusOptions(tipId ?? 0, jobId), enabled: tipId !== null });
}

export function useMyTips(status?: string) {
  return useInfiniteQuery(myTipsOptions(status));
}

export function useMyTip(tipId: number) {
  return useQuery(myTipOptions(tipId));
}

export function useReadMyTip() {
  return useMutation({
    mutationFn: (tipId: number) => {
      return getMyTip(tipId);
    },

    retry: false,
  });
}

export function useDiscoverTips() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: async (location: LocationEvidenceDto) => {
      const session = createTipLocationSession(location);
      const result = await client.fetchInfiniteQuery(nearbyTipsOptions(session));
      return result.pages[0];
    },

    retry: false,
  });
}

export function useReadTipAtLocation() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ tipId, location }: { tipId: number; location: LocationEvidenceDto }) => {
      return client.fetchQuery(tipViewOptions(tipId, createTipLocationSession(location)));
    },

    retry: false,
  });
}

export function useRecoverTip() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: async () => {
      const me = await client.ensureQueryData(meOptions());

      for (const tipId of await getPendingTipIds(me.id)) {
        try {
          const status = await getTipStatus(tipId);

          if (isTipOperationPending(status) || status.operationStatus === "FAILED" || status.status === "FAILED" || status.operationStatus === null || status.operationStatus === "UNKNOWN") {
            return tipId;
          }

          if (status.operationStatus === "SUCCEEDED" || status.status === "DELETED") {
            await forgetPendingTip(me.id, tipId);
          }
        } catch (error) {
          if (isApiError(error) && ["T002", "T012"].includes(error.code)) {
            await forgetPendingTip(me.id, tipId);
          } else {
            throw error;
          }
        }
      }

      const registering = await getMyTips({ status: "REGISTERING", limit: TIP_PAGE_SIZE });

      if (registering.items.length) {
        return registering.items[0].id;
      }

      const failed = await getMyTips({ status: "FAILED", limit: TIP_PAGE_SIZE });
      return failed.items[0]?.id ?? null;
    },

    retry: false,
  });
}

export function useCreateTip() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: async (input: CreateTipInput) => {
      const me = await client.ensureQueryData(meOptions());
      const result = await createTip(input);
      return { ...result, recoveryStored: await rememberPendingTip(me.id, result.tipId) };
    },

    retry: false,

    onSuccess: () => {
      void client.invalidateQueries({ queryKey: tipKeys.mine() });
      void client.invalidateQueries({ queryKey: userKeys.me() });
    },
  });
}

export function useUpdateTip() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: async (input: UpdateTipInput) => {
      const me = await client.ensureQueryData(meOptions());
      const result = await updateTip(input);
      return { ...result, recoveryStored: await rememberPendingTip(me.id, result.tipId) };
    },

    retry: false,

    onSettled: (_data, _error, { tipId }) => {
      void client.resetQueries({ queryKey: tipKeys.status(tipId) });
      void client.invalidateQueries({ queryKey: tipKeys.myDetail(tipId) });
    },
  });
}

export function useRetryTip() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (input: RetryTipInput) => {
      return retryTip(input);
    },

    retry: false,

    onSuccess: () => {
      void client.invalidateQueries({ queryKey: tipKeys.mine() });
    },

    onSettled: (_data, _error, { tipId }) => {
      void client.resetQueries({ queryKey: tipKeys.status(tipId) });
    },
  });
}

export function useDeleteTip() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (input: DeleteTipInput) => {
      return deleteTip(input);
    },

    retry: false,

    onSuccess: (_data, { tipId }) => {
      client.removeQueries({ queryKey: tipKeys.myDetail(tipId) });
      client.removeQueries({ queryKey: tipKeys.detail(tipId) });
      client.removeQueries({ queryKey: tipKeys.status(tipId) });
      void client.invalidateQueries({ queryKey: tipKeys.mine() });
      void client.invalidateQueries({ queryKey: userKeys.me() });
      void client.invalidateQueries({ queryKey: tipKeys.nearby(), refetchType: "none" });
    },
  });
}

export function useTipReaction() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (input: TipReactionInput) => {
      return setTipReaction(input);
    },

    retry: false,

    onMutate: async ({ tipId, reaction }) => {
      await client.cancelQueries({ queryKey: tipKeys.detail(tipId) });
      const previous = client.getQueriesData<TipDetail>({ queryKey: tipKeys.detail(tipId) });

      client.setQueriesData<TipDetail>({ queryKey: tipKeys.detail(tipId) }, (detail) => {
        return detail ? { ...detail, reactions: { ...detail.reactions, myReaction: reaction === "NONE" ? null : reaction } } : detail;
      });

      return { previous };
    },

    onError: (_error, _input, context) => {
      for (const [key, detail] of context?.previous ?? []) {
        client.setQueryData(key, detail);
      }
    },

    onSuccess: (result) => {
      client.setQueriesData<TipDetail>({ queryKey: tipKeys.detail(result.tipId) }, (detail) => {
        return detail ? { ...detail, reactions: { likes: result.likes, dislikes: result.dislikes, myReaction: result.myReaction } } : detail;
      });
    },

    onSettled: (_data, _error, { tipId }) => {
      void client.invalidateQueries({ queryKey: tipKeys.detail(tipId), refetchType: "none" });
    },
  });
}

/** 게시 작업 완료 후에만 호출한다. 기존 공개본을 진행 중 수정안으로 덮어쓰지 않는다. */
export function useTipPublicationRefresh() {
  const client = useQueryClient();

  return useCallback((status: TipPublicationStatus) => {
    if (status.operationStatus === "SUCCEEDED" || status.status === "DELETED") {
      void client.invalidateQueries({ queryKey: tipKeys.mine() });
      void client.invalidateQueries({ queryKey: tipKeys.nearby(), refetchType: "none" });
      void client.invalidateQueries({ queryKey: tipKeys.detail(status.tipId), refetchType: "none" });
    }
  }, [client]);
}

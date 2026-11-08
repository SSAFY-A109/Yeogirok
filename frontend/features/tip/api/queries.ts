import { infiniteQueryOptions, mutationOptions, queryOptions, useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { userKeys } from "@/features/user/api/queries";
import { deleteTip, getMyTip, getMyTips, getTipStatus, updateTip } from "./tip.api";
import { isTipOperationPending } from "../model";

const POLL_MS = 2_000;
const MAX_STATUS_CHECKS = 60;

export const tipKeys = {
  mine: ["tip", "mine"] as const,
  lists: ["tip", "mine", "list"] as const,

  list: (status?: string) => {
    return [...tipKeys.lists, status ?? null] as const;
  },

  detail: (tipId: number) => {
    return [...tipKeys.mine, "detail", tipId] as const;
  },

  status: (tipId: number) => {
    return [...tipKeys.mine, "status", tipId] as const;
  },
};

function myTipsOptions(status?: string) {
  return infiniteQueryOptions({
    queryKey: tipKeys.list(status),
    initialPageParam: null as string | null,

    queryFn: ({ pageParam, signal }) => {
      return getMyTips({ status, cursor: pageParam ?? undefined }, signal);
    },

    getNextPageParam: (page) => {
      return page.nextCursor;
    },
  });
}

function myTipOptions(tipId: number) {
  return queryOptions({
    queryKey: tipKeys.detail(tipId),

    queryFn: ({ signal }) => {
      return getMyTip(tipId, signal);
    },
  });
}

function tipStatusOptions(tipId: number, jobId?: string) {
  return queryOptions({
    queryKey: [...tipKeys.status(tipId), jobId ?? null],

    queryFn: ({ signal }) => {
      return getTipStatus(tipId, signal);
    },

    refetchInterval: (query) => {
      const checks = query.state.dataUpdateCount + query.state.errorUpdateCount;
      const status = query.state.data;
      const waiting = isTipOperationPending(status) || (jobId !== undefined && status?.jobId !== jobId);
      return !query.state.error && waiting && checks < MAX_STATUS_CHECKS ? POLL_MS : false;
    },
  });
}

const readTipOptions = mutationOptions({
  mutationFn: (tipId: number) => {
    return getMyTip(tipId);
  },
});
const updateTipOptions = mutationOptions({ mutationFn: updateTip });
const deleteTipOptions = mutationOptions({ mutationFn: deleteTip });

export function useMyTips(status?: string) {
  return useInfiniteQuery(myTipsOptions(status));
}

export function useMyTip(tipId: number) {
  return useQuery(myTipOptions(tipId));
}

export function useTipStatus(tipId: number, jobId?: string) {
  return useQuery(tipStatusOptions(tipId, jobId));
}

export function useReadMyTip() {
  return useMutation(readTipOptions);
}

export function useUpdateTip() {
  const client = useQueryClient();
  return useMutation({
    ...updateTipOptions,

    onSuccess: (_result, { tipId }) => {
      void client.invalidateQueries({ queryKey: tipKeys.status(tipId) });
    },
  });
}

export function useDeleteTip() {
  const client = useQueryClient();
  return useMutation({
    ...deleteTipOptions,

    onSuccess: (_result, { tipId }) => {
      client.removeQueries({ queryKey: tipKeys.detail(tipId) });
      client.removeQueries({ queryKey: tipKeys.status(tipId) });
      void client.invalidateQueries({ queryKey: tipKeys.mine });
      void client.invalidateQueries({ queryKey: userKeys.me() });
    },
  });
}

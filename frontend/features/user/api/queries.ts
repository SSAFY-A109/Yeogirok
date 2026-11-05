import { queryOptions, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { authKeys } from "@/features/auth/api/queries";
import type { SocialProvider } from "@/features/auth/model";
import { getMe } from "@/features/user/api/user.api";
import { withdrawWithReauthentication } from "@/features/user/withdraw";

export const userKeys = {
  all: ["user"] as const,

  me: () => {
    return [...userKeys.all, "me"] as const;
  },
};

export function meOptions() {
  return queryOptions({
    queryKey: userKeys.me(),

    queryFn: ({ signal }) => {
      return getMe(signal);
    },
  });
}

export function useMe() {
  return useQuery(meOptions());
}

/** 탈퇴 버튼용. 성공하면 사용자별 캐시를 모두 지우고 비로그인 상태로 바꾼다. */
export function useWithdraw() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (provider: SocialProvider) => {
      return withdrawWithReauthentication(provider);
    },

    retry: false,

    onSuccess: () => {
      queryClient.clear();
      queryClient.setQueryData(authKeys.session, false);
    },
  });
}

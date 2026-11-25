import { mutationOptions, queryOptions, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { performSocialLogin } from "../sign-in";
import { signOutThisDevice } from "../sign-out";
import { getLoginProvider, getTokens } from "../token-store";

export const authKeys = {
  session: ["auth", "session"] as const,
  loginProvider: ["auth", "login-provider"] as const,
};

const loginProviderOptions = queryOptions({
  queryKey: authKeys.loginProvider,

  queryFn: async () => {
    const [provider, tokens] = await Promise.all([getLoginProvider(), getTokens()]);
    return { provider, hasSession: Boolean(tokens) };
  },

  staleTime: Infinity,
  retry: false,
});

export function useLoginProvider() {
  return useQuery(loginProviderOptions);
}

export const sessionOptions = queryOptions({
  queryKey: authKeys.session,

  queryFn: async () => {
    return Boolean(await getTokens());
  },

  staleTime: Infinity,
  retry: false,
});

const socialLoginOptions = mutationOptions({
  mutationFn: performSocialLogin,
  retry: false,
});

export function useSession() {
  return useQuery(sessionOptions);
}

export function useSocialLogin() {
  const queryClient = useQueryClient();

  return useMutation({
    ...socialLoginOptions,

    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: authKeys.session });
      await queryClient.invalidateQueries({ queryKey: authKeys.loginProvider });
    },
  });
}

/** 로그아웃에 성공하면 사용자별 캐시를 지우고 비로그인 상태로 바꾼다. */
export function useSignOut() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: signOutThisDevice,
    retry: false,

    onSuccess: () => {
      queryClient.removeQueries({
        predicate: (query) => {
          return query.queryKey[0] !== authKeys.session[0] || query.queryKey[1] !== authKeys.session[1];
        },
      });
      queryClient.setQueryData(authKeys.session, false);
    },
  });
}

import { mutationOptions, queryOptions, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { performSocialLogin } from "../sign-in";
import { getTokens } from "../token-store";

export const authKeys = {
  session: ["auth", "session"] as const,
};

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
    },
  });
}

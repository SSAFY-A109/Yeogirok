import { useRef } from "react";

import { useSocialLogin } from "@/features/auth/api/queries";
import { LoginScreen } from "@/features/auth/components/login-screen";
import { getLoginErrorMessage } from "@/features/auth/errors";
import type { SocialProvider } from "@/features/auth/model";

export function SocialLoginScreen({ onSuccess }: { onSuccess?: () => void }) {
  const mutation = useSocialLogin();
  const submitting = useRef(false);

  function login(provider: SocialProvider) {
    if (submitting.current) {
      return;
    }

    submitting.current = true;
    mutation.mutate(provider, {
      onSuccess,

      onSettled: () => {
        submitting.current = false;
      },
    });
  }

  return (
    <LoginScreen
      onLogin={login}
      pendingProvider={mutation.isPending ? mutation.variables : undefined}
      error={getLoginErrorMessage(mutation.error)}
    />
  );
}

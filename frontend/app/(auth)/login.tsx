import { router } from "expo-router";

import { SocialLoginScreen } from "@/features/auth/components/social-login-screen";

export default function Login() {
  return <SocialLoginScreen onSuccess={() => {
    router.replace("/");
  }} />;
}

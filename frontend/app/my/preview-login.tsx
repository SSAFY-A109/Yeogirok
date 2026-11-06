import { router } from "expo-router";

import { LoginScreen } from "@/features/auth/components/login-screen";

export default function MyPreviewLoginRoute() {
  return <LoginScreen error={null} onLogin={() => {
    router.dismissTo("/my");
  }} />;
}

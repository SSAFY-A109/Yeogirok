import { router } from "expo-router";

import { LoginScreen } from "@/features/auth/components/login-screen";
import { useMyMock } from "@/features/user/mocks/my-provider";

export default function MyPreviewLoginRoute() {
  const { previewLogin } = useMyMock();

  return <LoginScreen error={null} onLogin={(provider) => {
    previewLogin(provider);
    router.dismissTo("/my");
  }} />;
}

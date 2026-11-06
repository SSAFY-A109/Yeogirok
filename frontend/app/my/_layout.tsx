import { Stack, type ErrorBoundaryProps } from "expo-router";

import { Screen } from "@/components/ui/screen";
import { MyUnavailable } from "@/features/user/components/my-page";
import { MyMockProvider } from "@/features/user/mocks/my-provider";

export default function MyLayout() {
  return <MyMockProvider><Stack screenOptions={{ headerShown: false }} /></MyMockProvider>;
}

export function ErrorBoundary({ retry }: ErrorBoundaryProps) {
  return <Screen><MyUnavailable title="My 화면을 표시하지 못했어요" onRetry={() => {
    void retry();
  }} /></Screen>;
}

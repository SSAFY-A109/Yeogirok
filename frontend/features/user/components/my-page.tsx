import type { ReactNode } from "react";
import { KeyboardAvoidingView, Platform, View } from "react-native";
import { router } from "expo-router";
import { CircleAlert } from "lucide-react-native";

import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import { Screen } from "@/components/ui/screen";
import { ScreenHeader } from "@/components/ui/screen-header";
import { Skeleton } from "@/components/ui/skeleton";
import { Text } from "@/components/ui/text";

export function MyPage({ title, children, testID }: { title: string; children: ReactNode; testID: string }) {
  return (
    <Screen testID={testID}>
      <KeyboardAvoidingView behavior={Platform.OS === "ios" ? "padding" : "height"} className="flex-1">
        <ScreenHeader title={title} testID={`${testID}.header`} onBack={() => {
          if (router.canGoBack()) {
            router.back();
          } else {
            router.replace("/my");
          }
        }} />
        {children}
      </KeyboardAvoidingView>
    </Screen>
  );
}

export function MyLoading() {
  return (
    <View accessibilityLabel="불러오는 중" className="gap-4 px-5 py-6" testID="my.loading">
      <Skeleton className="h-14 w-14 rounded-full" />
      <Skeleton className="h-6 w-skeleton-heading" />
      <Skeleton className="h-24 w-full" />
      <Skeleton className="h-32 w-full" />
    </View>
  );
}

export function MyUnavailable({ title = "내용을 찾을 수 없어요", onRetry }: { title?: string; onRetry?: () => void }) {
  return (
    <View className="flex-1 items-center justify-center gap-6 px-5" testID="my.error">
      <Icon as={CircleAlert} size={36} className="text-muted-foreground" />
      <Text accessibilityRole="alert" className="text-center text-h3">{title}</Text>
      <Button variant="outline" onPress={onRetry ?? (() => {
        router.replace("/my");
      })}><Text>{onRetry ? "다시 시도" : "My로 돌아가기"}</Text></Button>
    </View>
  );
}

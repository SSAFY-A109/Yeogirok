import { View, type ViewProps } from "react-native";

import { cn } from "@/lib/utils";
import { Skeleton } from "./skeleton";
import { Spinner } from "./spinner";
import { Text } from "./text";

type PageLoadingProps = ViewProps & {
  type?: "skeleton" | "spinner";
  title?: string;
  description?: string;
};

export function PageLoading({ className, type = "skeleton", title = "여행 코스를 불러오는 중", description, ...props }: PageLoadingProps) {
  const message = description ?? (type === "skeleton" ? "코스 정보를 불러오고 있어요." : "잠시만 기다려 주세요.");

  return (
    <View accessibilityRole="progressbar" accessibilityLabel={`${title}. ${message}`} accessibilityState={{ busy: true }} className={cn("min-h-56 w-full gap-4 rounded-sm border border-border bg-card p-6", type === "spinner" && "items-center justify-center", className)} {...props}>
      {type === "skeleton" ? (
        <View accessible={false} className="gap-4">
          <Skeleton className="h-4 w-skeleton-heading max-w-full rounded-xs bg-border" />
          <View className="flex-row gap-3">
            <Skeleton className="size-20 rounded-xs bg-border" />
            <View className="min-w-0 flex-1 gap-3">
              <Skeleton className="h-3.5 w-skeleton-line max-w-full rounded-xs bg-border" />
              <Skeleton className="h-3 w-32 max-w-full rounded-xs bg-border" />
              <Skeleton className="h-3 w-20 max-w-full rounded-xs bg-border" />
            </View>
          </View>
        </View>
      ) : (
        <>
          <Spinner accessible={false} />
          <Text className="text-center text-label">{title}</Text>
        </>
      )}
      <Text className={cn("text-caption text-muted-foreground", type === "spinner" && "text-center")}>{message}</Text>
    </View>
  );
}

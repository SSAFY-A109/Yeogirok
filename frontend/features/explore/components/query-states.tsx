import { View } from "react-native";

import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Text } from "@/components/ui/text";

export function ListSkeleton({ rows = 4, testID }: { rows?: number; testID?: string }) {
  return (
    <View accessibilityLabel="불러오는 중" accessibilityRole="progressbar" className="gap-4" testID={testID}>
      {Array.from({ length: rows }, (_, index) => {
        return (
          <View key={index} className="flex-row gap-4">
            <Skeleton className="size-20 rounded-sm" />
            <View className="flex-1 gap-2 py-1">
              <Skeleton className="h-5 w-skeleton-heading" />
              <Skeleton className="h-4 w-skeleton-line" />
            </View>
          </View>
        );
      })}
    </View>
  );
}

export function InlineMessage({ title, description, actionLabel, onAction, testID }: { title: string; description?: string; actionLabel?: string; onAction?: () => void; testID?: string }) {
  return (
    <View className="items-center gap-3 rounded-lg border border-border bg-card px-5 py-8" testID={testID}>
      <Text className="text-center text-h3 text-foreground">{title}</Text>
      {description && <Text className="text-center text-body-sm text-foreground-secondary">{description}</Text>}
      {actionLabel && onAction && (
        <Button variant="outline" onPress={onAction} accessibilityLabel={actionLabel}>
          <Text>{actionLabel}</Text>
        </Button>
      )}
    </View>
  );
}

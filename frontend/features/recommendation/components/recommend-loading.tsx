import { ScrollView, View } from "react-native";

import { Button } from "@/components/ui/button";
import { PageLoading } from "@/components/ui/page-loading";
import { Text } from "@/components/ui/text";

type RecommendLoadingProps = {
  /** `error`: 같은 자리에 실패 안내와 "다시 시도"를 보여 준다. */
  status: "pending" | "error";
  placeCount: number;
  conditionLabels: readonly string[];
  errorMessage: string;
  onRetry: () => void;
};

/** AI 추천 처리 중(Figma W10-1). 실패해도 고른 조건은 그대로 보여 준다. */
export function RecommendLoading({ status, placeCount, conditionLabels, errorMessage, onRetry }: RecommendLoadingProps) {
  return (
    <ScrollView className="flex-1" contentContainerClassName="items-center gap-5 px-5 pb-6 pt-10" testID="recommend.loading">
      {status === "pending" ? (
        <PageLoading
          type="spinner"
          title="동선에 맞는 장소를 찾는 중"
          description={placeCount > 0 ? `담은 ${placeCount}곳과 고른 조건을 함께 보고 있어요` : "고른 조건을 보고 있어요"}
        />
      ) : (
        <View
          accessibilityLiveRegion="polite"
          className="min-h-56 w-full items-center justify-center gap-4 rounded-sm border border-border bg-card p-6"
          testID="recommend.error"
        >
          <Text accessibilityRole="alert" className="text-center text-label text-foreground">
            {errorMessage}
          </Text>
          <Button variant="outline" onPress={onRetry} testID="recommend.retry">
            <Text>다시 시도</Text>
          </Button>
        </View>
      )}
      {conditionLabels.length > 0 ? (
        <View className="w-full gap-3 rounded-md bg-muted p-4">
          <Text className="text-label-sm text-muted-foreground">고른 조건</Text>
          <View className="flex-row flex-wrap gap-2">
            {conditionLabels.map((label, index) => {
              return (
                <View key={`${index}-${label}`} className="h-8 items-center justify-center rounded-sm border border-border-strong bg-card px-3">
                  <Text className="text-label text-foreground-secondary">{label}</Text>
                </View>
              );
            })}
          </View>
        </View>
      ) : null}
    </ScrollView>
  );
}

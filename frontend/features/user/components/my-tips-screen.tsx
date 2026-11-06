import { useState } from "react";
import { FlatList, View } from "react-native";
import { router } from "expo-router";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Text } from "@/components/ui/text";
import { useToast } from "@/components/ui/toast";
import { useDeleteTip, useMyTips, useReadMyTip } from "@/features/tip/api/queries";
import { getTipErrorMessage } from "@/features/tip/errors";
import { formatMonthDay, toLocalDate } from "@/lib/date";
import { MyLoading, MyPage, MyUnavailable } from "./my-page";

export function MyTipsScreen() {
  const query = useMyTips();
  const read = useReadMyTip();
  const deletion = useDeleteTip();
  const toast = useToast();
  const [deleting, setDeleting] = useState<{ tipId: number; version: number } | null>(null);
  const [error, setError] = useState<string | null>(null);

  const items = query.data?.pages.flatMap((page) => {
    return page.items;
  }) ?? [];

  return <MyPage title="내 팁" testID="my-tips.screen">
    {query.isPending ? <MyLoading /> : query.isError && !query.data ? <MyUnavailable title={getTipErrorMessage(query.error)} onRetry={() => {
      void query.refetch();
    }} /> : <FlatList
      data={items} keyExtractor={(item) => {
        return String(item.id);
      }} contentContainerClassName="gap-3 px-5 py-4"
      refreshing={query.isRefetching} onRefresh={() => {
        void query.refetch();
      }}
      onEndReached={() => {
        if (query.hasNextPage && !query.isFetchingNextPage && !query.isFetchNextPageError) {
          void query.fetchNextPage();
        }
      }}
      ListHeaderComponent={<View className="gap-3 pb-2"><Text className="text-h3">내가 남긴 AR 팁</Text><Text className="text-caption text-muted-foreground">본문과 사진은 원격으로 관리할 수 있어요. AR 위치 재배치는 현장에서 진행해요.</Text>{error && <Text accessibilityRole="alert" className="text-body-sm text-destructive">{error}</Text>}</View>}
      ListEmptyComponent={<View className="gap-6 py-6"><Text className="text-center text-h3">아직 남긴 팁이 없어요</Text><Button testID="my-tips.create" onPress={() => {
        router.navigate("/ar-tip");
      }}><Text>AR 팁 남기러 가기</Text></Button></View>}
      ListFooterComponent={query.isFetchNextPageError ? <Button onPress={() => {
        void query.fetchNextPage();
      }}><Text>목록 더 불러오기</Text></Button> : query.isFetchingNextPage ? <Text className="text-center text-caption">불러오는 중</Text> : undefined}
      renderItem={({ item }) => {
        return <View className="gap-3 rounded-md border border-border bg-card p-4" testID={`my-tips.card-${item.id}`}>
          <Text className="text-label">팁 #{item.id} · {item.status === "PUBLISHED" ? "공개 중" : item.status === "REGISTERING" ? "등록 중" : item.status === "FAILED" ? "등록 실패" : "상태 확인 필요"}</Text>
          <Text className="text-caption text-muted-foreground">{formatMonthDay(toLocalDate(item.createdAt))} 등록</Text>
          <View className="flex-row gap-2"><Button variant="outline" className="flex-1" testID={`my-tips.edit-${item.id}`} onPress={() => {
            router.push({ pathname: "/my/tips/[tipId]", params: { tipId: item.id } });
          }}><Text>본문·사진 수정</Text></Button>
          <Button variant="outline" className="flex-1" disabled={read.isPending || deletion.isPending} testID={`my-tips.delete-${item.id}`} onPress={() => {
            setError(null);
            read.mutate(item.id, { onSuccess: (tip) => {
              setDeleting({ tipId: tip.tipId, version: tip.version });
            },

            onError: (cause) => {
              setError(getTipErrorMessage(cause));
            } });
          }}><Text className="text-destructive">삭제</Text></Button></View>
        </View>;
      }} />}
    <ConfirmDialog open={deleting !== null} onOpenChange={(open) => {
      if (!open) {
        setDeleting(null);
      }
    }} title="이 팁을 삭제할까요?" description="공개 검색과 내 팁 목록에서 제외돼요." confirmLabel="삭제" confirmTone="destructive" loading={deletion.isPending} testID="my-tips.delete-dialog" onConfirm={() => {
      if (deleting) {
        deletion.mutate(deleting, { onSuccess: () => {
          setDeleting(null); toast.show({ message: "팁을 삭제했어요" });
        },

        onError: (cause) => {
          setError(getTipErrorMessage(cause)); setDeleting(null);
        } });
      }
    }} />
  </MyPage>;
}

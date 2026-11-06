import { useState } from "react";
import { FlatList, View } from "react-native";
import { router, useLocalSearchParams } from "expo-router";
import { ScanEye } from "lucide-react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { DesignAsset } from "@/components/ui/design-asset";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { useToast } from "@/components/ui/toast";
import { useMyMock } from "../mocks/my-provider";
import { MyLoading, MyPage, MyUnavailable } from "./my-page";

export function MyTipsScreen() {
  const insets = useSafeAreaInsets();
  const { tips, deleteTip } = useMyMock();
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const { preview } = useLocalSearchParams<{ preview?: string }>();
  const toast = useToast();
  const items = preview === "empty" ? [] : tips;

  return (
    <MyPage title="내 팁" testID="my-tips.screen">
      {preview === "loading" ? <MyLoading /> : preview === "error" ? <MyUnavailable title="팁을 불러오지 못했어요" onRetry={() => {
        router.setParams({ preview: undefined });
      }} /> : (
        <FlatList
          data={items}
          keyExtractor={(item) => {
            return item.id;
          }}
          contentContainerClassName={items.length ? "gap-3 px-5 pt-4" : "flex-grow justify-center px-5"}
          contentContainerStyle={{ paddingBottom: insets.bottom + (items.length ? 32 : 96) }}
          ListHeaderComponent={items.length ? (
            <View className="gap-3 pb-1">
              <View className="flex-row items-center justify-between gap-2"><Text className="flex-1 text-h3">내가 남긴 AR 팁 {items.length}개</Text><Text className="text-caption text-foreground-secondary">최신순</Text></View>
              <Text className="text-caption text-foreground-secondary">내가 등록한 팁을 한곳에서 관리해요.</Text>
            </View>
          ) : undefined}
          ListEmptyComponent={<View className="items-center gap-6" testID="my-tips.empty"><Icon as={ScanEye} size={40} className="text-icon" /><Text className="text-center text-h3">아직 남긴 팁이 없어요</Text><Text className="text-center text-body-sm text-foreground-secondary">{"여행지에서 발견한 나만의 팁을\nAR로 남겨 보세요."}</Text><Button size="lg" className="w-full" testID="my-tips.create" onPress={() => {
            router.navigate("/ar-tip");
          }}><Text>AR 팁 남기러 가기</Text></Button></View>}
          renderItem={({ item }) => {
            return (
              <View className="gap-3 rounded-md border border-border bg-card p-4" testID={`my-tips.card-${item.id}`}>
                <View className="flex-row items-start gap-3">
                  <DesignAsset source={item.photo} className="rounded-sm" style={{ width: 72, height: 72 }} contentFit="cover" accessibilityLabel={`${item.category} 팁 사진`} />
                  <View className="flex-1 gap-2"><Text className="text-label text-foreground-secondary">{item.category} · {item.registeredAt} 등록</Text><Text className="text-body-sm text-foreground-secondary" numberOfLines={2}>{item.body || "내용 없음"}</Text></View>
                </View>
                <View className="flex-row gap-2">
                  <Button variant="outline" className="flex-1" accessibilityLabel={`${item.category} 팁 수정`} testID={`my-tips.edit-${item.id}`} onPress={() => {
                    router.push({ pathname: "/my/tips/[tipId]", params: { tipId: item.id } });
                  }}><Text>수정</Text></Button>
                  <Button variant="outline" className="flex-1" accessibilityLabel={`${item.category} 팁 삭제`} testID={`my-tips.delete-${item.id}`} onPress={() => {
                    setDeletingId(item.id);
                  }}><Text className="text-destructive">삭제</Text></Button>
                </View>
              </View>
            );
          }}
        />
      )}
      <ConfirmDialog open={deletingId !== null} onOpenChange={(open) => {
        if (!open) {
          setDeletingId(null);
        }
      }} title="이 팁을 삭제할까요?" description={"선택한 팁을 삭제해요.\n삭제한 팁은 복구할 수 없어요."} confirmLabel="삭제" confirmTone="destructive" testID="my-tips.delete-dialog" onConfirm={() => {
        if (deletingId) {
          deleteTip(deletingId);
          setDeletingId(null);
          toast.show({ message: "팁을 삭제했어요" });
        }
      }} />
    </MyPage>
  );
}

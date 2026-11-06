import { useState } from "react";
import { ScrollView, View } from "react-native";
import { router, useLocalSearchParams } from "expo-router";
import { Info, Lock } from "lucide-react-native";

import { Badge } from "@/components/ui/badge";
import { BottomCta } from "@/components/ui/bottom-cta";
import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/components/ui/toast";
import type { MyFootprint } from "../mocks/my-data";
import { useMyMock } from "../mocks/my-provider";
import { MyFootprintMap } from "./my-footprint-map";
import { MyPage, MyUnavailable } from "./my-page";

export function MyGuestbookScreen() {
  const { footprintId } = useLocalSearchParams<{ footprintId?: string }>();
  const { footprints } = useMyMock();

  const footprint = footprints.find((item) => {
    return item.id === footprintId;
  });

  return <MyPage title="방명록" testID="my-guestbook.screen">{footprint ? <GuestbookForm key={footprint.id} footprint={footprint} /> : <MyUnavailable />}</MyPage>;
}

function GuestbookForm({ footprint }: { footprint: MyFootprint }) {
  const [body, setBody] = useState(footprint.body);
  const [failed, setFailed] = useState(false);
  const { preview } = useLocalSearchParams<{ preview?: string }>();
  const { saveGuestbook } = useMyMock();
  const toast = useToast();

  return (
    <>
      <ScrollView keyboardShouldPersistTaps="handled" contentContainerClassName="pb-6">
        <MyFootprintMap footprints={[footprint]} selected />
        <View className="gap-6 px-5 pt-4">
          <View className="gap-1"><Text className="text-label">{footprint.place} {footprint.address}</Text><Text className="text-caption text-foreground-secondary">{footprint.visitedAt} 방문</Text></View>
          <View className="gap-3"><Text className="text-label">카테고리</Text><View className="flex-row gap-1">{footprint.categories.map((value) => {
            return <Badge key={value}><Text>{value}</Text></Badge>;
          })}</View></View>
          <View className="gap-3"><View className="flex-row items-center gap-2"><Text className="text-label">방명록</Text><View className="flex-row items-center gap-1 rounded-full bg-muted px-2 py-1"><Icon as={Lock} size={12} className="text-icon" /><Text className="text-label-sm text-foreground-secondary">나만 볼 수 있어요</Text></View></View><Textarea value={body} onChangeText={setBody} maxLength={200} placeholder="여행의 순간을 기록해 보세요" accessibilityLabel="나만 보는 방명록, 최대 200자" testID="my-guestbook.body" /><Text className="text-right text-caption text-muted-foreground tabular-nums">{body.length} / 200</Text></View>
          <View className="flex-row gap-2"><Icon as={Info} size={16} className="text-muted-foreground" /><Text className="flex-1 text-caption text-muted-foreground">고쳐도 방문 횟수는 늘지 않아요. 위치 방문 시각 카테고리는 바꿀 수 없어요.</Text></View>
          {failed && <Text accessibilityRole="alert" className="text-body-sm text-destructive" testID="my-guestbook.save-error">저장하지 못했어요. 입력한 내용은 유지했어요. 다시 시도해 주세요.</Text>}
        </View>
      </ScrollView>
      <BottomCta><Button size="lg" className="w-full" disabled={body === footprint.body} testID="my-guestbook.save" onPress={() => {
        if (preview === "error") {
          setFailed(true);
          router.setParams({ preview: undefined });

          return;
        }

        saveGuestbook(footprint.id, body);
        toast.show({ message: "방명록을 저장했어요" });
        router.back();
      }}><Text>저장</Text></Button></BottomCta>
    </>
  );
}
